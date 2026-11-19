package cn.minerealms.assimploader.workbench.client;

import cn.minerealms.assimploader.AssimpLoader;
import cn.minerealms.assimploader.client.ui.FlatUi;
import cn.minerealms.assimploader.client.ui.LookDevNavigation;
import cn.minerealms.assimploader.lookdev.preview.LoadedModelCatalog;
import cn.minerealms.assimploader.lookdev.preview.PreviewCamera;
import cn.minerealms.assimploader.lookdev.preview.PreviewGpuRenderer;
import cn.minerealms.assimploader.lookdev.preview.PreviewSceneData;
import cn.minerealms.assimploader.model.AssimpScene;
import cn.minerealms.assimploader.core.AssimpModelLoader;
import cn.minerealms.assimploader.workbench.io.WorkbenchProjectStore;
import cn.minerealms.assimploader.workbench.io.WorkbenchRecovery;
import cn.minerealms.assimploader.workbench.model.*;
import cn.minerealms.assimploader.util.ScreenCompat;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.AbstractWidget;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.components.Tooltip;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import org.joml.Matrix4f;
import org.joml.Vector3f;
import org.lwjgl.glfw.GLFW;

import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CancellationException;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;
import java.util.function.Consumer;
import java.util.function.Supplier;
import java.util.function.UnaryOperator;

/** A lightweight asset editor, sharing the engine's imported scenes and immutable edit document. */
public final class ModelWorkbenchScreen extends Screen implements WorkbenchExtensions.ProjectSaver {
    private static final ThreadPoolExecutor IO=new ThreadPoolExecutor(1,1,30,TimeUnit.SECONDS,new ArrayBlockingQueue<>(4),r->{Thread t=new Thread(r,"AssimpLoader-workbench");t.setDaemon(true);return t;},new ThreadPoolExecutor.AbortPolicy());
    private enum Section { ASSETS, NODES, MATERIALS, SOCKETS }
    private enum Operation { NORMAL, RECOVERY, DISCARD, SAVE }
    private record Row(String label,String key,int depth,int index) {}
    private record Label(String text,int x,int y) {}
    private record OpenedProject(WorkbenchProjectStore.Project project,WorkbenchProjectStore.Recovery recovery) {}
    private record ImportedProject(WorkbenchProjectStore.Project project,WorkbenchDocument sourceDefaults) {}
    private record SavedProject(WorkbenchDocument document,String warning) {}
    private final PreviewCamera camera=new PreviewCamera();
    private final WorkbenchGizmo gizmo=new WorkbenchGizmo();
    private final Vector3f focus=new Vector3f();
    private final List<Label> labels=new ArrayList<>();
    private final List<EditBox> fields=new ArrayList<>();
    private final List<AbstractWidget> inspectorWidgets=new ArrayList<>();
    private WorkbenchProjectStore store;
    private WorkbenchProjectStore.Project project;
    private WorkbenchRecovery recovery;
    private WorkbenchSession session;
    private AssimpScene scene;
    private SceneMetadata metadata;
    private PreviewSceneData data;
    private PreviewGpuRenderer renderer;
    private WorkbenchPose pose;
    private Section section=Section.ASSETS;
    private List<Path> projects=List.of();
    private List<LoadedModelCatalog.Entry> loaded=List.of();
    private List<Row> rows=List.of();
    private List<String> issues=List.of();
    private EditBox search;
    private int left,right,sectionColumns=2,scroll,inspectorScroll,inspectorMaxScroll,dragButton=-1,request;
    private String selectedNode="",selectedSocket="",status="",query="";
    private int selectedMaterial=-1;
    private boolean busy,playing,loop=true,rendered,failed,showSkeleton=true,catalogRequested;
    private Operation operation=Operation.NORMAL;
    private double clock;
    private float speed=1;
    private long lastNanos,generation=-1,draftRevision=-1;
    private int autosaveTicks;
    private Button undo,redo,save;

    public ModelWorkbenchScreen(){super(Component.literal("Model Workbench"));}
    @Override protected void init(){
        if(store==null){
            String configured=System.getProperty("assimploader.workbenchDirectory","");
            store=new WorkbenchProjectStore(configured.isBlank()?minecraft.gameDirectory.toPath().resolve("config/assimploader/workbench"):Path.of(configured));
        }
        left=Math.max(116,Math.min(190,width/5));right=Math.max(152,Math.min(218,width/4));
        LookDevNavigation.install(this,this::addRenderableWidget,LookDevNavigation.Page.WORKBENCH);
        int tx=8;
        tool(tx,FlatUi.Icon.IMPORT,"Import FBX or folder",()->pathDialog(true));tx+=30;
        tool(tx,FlatUi.Icon.OPEN,"Open project",()->pathDialog(false));tx+=30;
        save=tool(tx,FlatUi.Icon.SAVE,"Save project",this::save);tx+=34;
        undo=tool(tx,FlatUi.Icon.UNDO,"Undo",()->history(false));tx+=30;
        redo=tool(tx,FlatUi.Icon.REDO,"Redo",()->history(true));tx+=34;
        tool(tx,FlatUi.Icon.FIT,"Frame selection",this::fit);tx+=34;
        tx=viewportX()+8;
        for(var mode:WorkbenchGizmo.Mode.values()){
            var icon=switch(mode){case MOVE->FlatUi.Icon.MOVE;case ROTATE->FlatUi.Icon.ROTATE;case SCALE->FlatUi.Icon.SCALE;};
            addRenderableWidget(FlatUi.tool(tx,viewportY()+8,icon,Component.literal(mode.name()),()->gizmo.mode()==mode,b->gizmo.mode(mode)));tx+=30;
        }
        addRenderableWidget(FlatUi.button(width-right+8,52,(right-24)/2,26,Component.literal("Apply"),b->apply()));
        addRenderableWidget(FlatUi.button(width-right+16+(right-24)/2,52,(right-24)/2,26,Component.literal("Export"),b->export()));
        String[] tabNames={"Files","Tree","Mats","Slots"};
        String[] tabTips={"Asset files and projects","Node hierarchy","Materials","Sockets"};
        int widestTab=0;for(String name:tabNames)widestTab=Math.max(widestTab,font.width(name));
        sectionColumns=(left-22)/4>=widestTab+20?4:2;
        int tabWidth=(left-16-(sectionColumns-1)*2)/sectionColumns;
        for(int i=0;i<4;i++){
            Section s=Section.values()[i];
            var tab=FlatUi.choice(8+(i%sectionColumns)*(tabWidth+2),86+(i/sectionColumns)*26,tabWidth,22,Component.literal(tabNames[i]),()->section==s,b->{commit();section=s;scroll=0;refreshRows();rebuildWidgets();});
            tab.setTooltip(Tooltip.create(Component.literal(tabTips[i])));addRenderableWidget(tab);
        }
        search=FlatUi.field(font,8,filterY(),left-16,26,Component.literal("Filter"));search.setMaxLength(128);search.setHint(Component.literal("Filter"));search.setValue(query);
        search.setResponder(s->{query=s;scroll=0;refreshRows();});addRenderableWidget(search);
        labels.clear();fields.clear();inspectorWidgets.clear();
        var before=java.util.Collections.newSetFromMap(new java.util.IdentityHashMap<net.minecraft.client.gui.components.events.GuiEventListener,Boolean>());
        before.addAll(children());buildInspector();
        for(var child:children())if(!before.contains(child)&&child instanceof AbstractWidget widget)inspectorWidgets.add(widget);
        int bottom=inspectorWidgets.stream().mapToInt(w->w.getY()+w.getHeight()).max().orElse(112);
        inspectorMaxScroll=Math.max(0,bottom+inspectorScroll-(height-32));
        for(var widget:inspectorWidgets)widget.visible=widget.getY()>=110&&widget.getY()+widget.getHeight()<=height-28;
        buildTimeline();
        loaded=LoadedModelCatalog.snapshot(AssimpModelLoader.getLoadedModels());refreshRows();
        generation=AssimpModelLoader.getCacheGeneration();
        if(!catalogRequested&&!busy){catalogRequested=true;task("Reading projects",()->{try{return store.listProjects();}catch(Exception e){throw new RuntimeException(e);}},list->{projects=list;refreshRows();});}
        lastNanos=System.nanoTime();
    }
    private Button tool(int x,FlatUi.Icon icon,String tip,Runnable action){return addRenderableWidget(FlatUi.icon(x,52,icon,Component.literal(tip),b->{commit();action.run();}));}
    private int viewportX(){return left+1;} private int viewportY(){return 86;}
    private int viewportWidth(){return Math.max(1,width-left-right-2);} private int viewportHeight(){return Math.max(1,height-viewportY()-94);}
    private int inspectorX(){return width-right+8;}
    private int timelineY(){return height-82;}
    private int filterY(){return 88+((Section.values().length+sectionColumns-1)/sectionColumns)*26;}
    private int rowsY(){return filterY()+32;}
    private int rowCount(){return Math.max(1,(height-rowsY()-32)/22);}
    private void commit(){if(session!=null)session.commitTransaction();}
    private void edit(UnaryOperator<WorkbenchDocument> change){if(session==null)return;commit();session.edit(change);refreshIssues();}
    private void history(boolean forward){if(session==null)return;if(forward)session.redo();else session.undo();syncClock();refreshIssues();refreshRows();rebuildWidgets();}
    private void refreshRows(){
        var list=new ArrayList<Row>();String filter=query.toLowerCase(Locale.ROOT);
        if(section==Section.ASSETS){
            for(int i=0;i<projects.size();i++){Path p=projects.get(i);list.add(new Row(p.getParent().getFileName().toString(),p.toString(),0,i));}
            for(int i=0;i<loaded.size();i++){var model=loaded.get(i);list.add(new Row(model.name(),"loaded:"+i,0,i));}
        }else if(metadata!=null){
            switch(section){
                case NODES->{list.add(new Row("Model transform","",0,-1));for(var node:metadata.nodes())list.add(new Row(node.name(),node.path(),node.depth()+1,-1));}
                case MATERIALS->{for(var material:metadata.materials())list.add(new Row(material.name(),"material:"+material.index(),0,material.index()));}
                case SOCKETS->{list.add(new Row("+ New socket","+",0,-1));for(String name:session.document().sockets().keySet().stream().sorted().toList())list.add(new Row(name,name,0,-1));}
                default->{}
            }
        }
        rows=list.stream().filter(row->row.label.toLowerCase(Locale.ROOT).contains(filter)||row.key.toLowerCase(Locale.ROOT).contains(filter)).toList();
        if(session!=null&&!selectedSocket.isEmpty()&&!session.document().sockets().containsKey(selectedSocket))selectedSocket="";
        scroll=Math.min(scroll,Math.max(0,rows.size()-rowCount()));
    }
    private void select(Row row){
        commit();
        if(section==Section.ASSETS){
            if(!allowReplace())return;
            if(row.key.startsWith("loaded:")){var entry=loaded.get(row.index);task("Inspecting model",()->SceneMetadata.inspect(entry.scene()),meta->load(entry.scene(),meta,null,null,entry.name()));}
            else open(Path.of(row.key));return;
        }
        if(section==Section.MATERIALS){selectedMaterial=row.index;selectedNode="";selectedSocket="";}
        else if(section==Section.NODES){selectedNode=row.key;selectedMaterial=-1;selectedSocket="";}
        else if(section==Section.SOCKETS){
            if(row.key.equals("+")){if(metadata.nodes().isEmpty())return;String name="socket_"+(session.document().sockets().size()+1);String node=selectedNode.isEmpty()?metadata.nodes().get(0).path():selectedNode;
                edit(doc->doc.withSocket(name,new WorkbenchDocument.Socket(node,ModelTransform.IDENTITY)));selectedSocket=name;refreshRows();}
            else selectedSocket=row.key;
            selectedMaterial=-1;
        }
        inspectorScroll=0;rebuildWidgets();
    }
    private boolean allowReplace(){if(session!=null&&session.dirty()){status="Save or revert current edits before switching projects";return false;}return !busy;}
    public void importPath(Path input){
        if(!allowReplace())return;
        task("Importing "+input.getFileName(),()->{try{return withRecovery(store.importAsset(input));}catch(Exception e){throw new RuntimeException(e);}},this::loadOpened);
    }
    public void open(Path input){
        if(!allowReplace())return;
        task("Opening project",()->{try{return withRecovery(store.open(input));}catch(Exception e){throw new RuntimeException(e);}},this::loadOpened);
    }
    private OpenedProject withRecovery(WorkbenchProjectStore.Project project){return new OpenedProject(project,store.readRecovery(project.documentPath(),project.document()));}
    private void loadOpened(OpenedProject opened){
        if(!allowReplace())return;
        load(opened.project());
        if(!opened.recovery().issues().isEmpty()){
            var all=new ArrayList<>(metadata.issues());all.addAll(opened.recovery().issues());
            metadata=new SceneMetadata(metadata.nodes(),metadata.meshes(),metadata.materials(),metadata.clips(),metadata.bounds(),all,
                    metadata.skinned(),metadata.embeddedTextures(),metadata.sourceFormat(),metadata.sourceGenerator());
            status="Opened saved project; recovery draft ignored";
        }
        opened.recovery().document().ifPresent(draft->{session.set(draft);status="Recovered unsaved draft";});
        notifyProjectOpened();syncClock();refreshIssues();refreshRows();rebuildWidgets();
    }
    private void notifyProjectOpened(){
        try{
            var context=context();
            for(var extension:WorkbenchExtensions.available(context)){
                try{extension.opened(context);}
                catch(RuntimeException|LinkageError failure){openedExtensionFailed(extension.id(),failure);}
            }
        }catch(RuntimeException|LinkageError failure){openedExtensionFailed("extension discovery",failure);}
    }
    private void openedExtensionFailed(String id,Throwable failure){
        String issue="Project opened; "+id+" extension failed: "+failure.getMessage();
        var all=new ArrayList<>(metadata.issues());all.add(issue);
        metadata=new SceneMetadata(metadata.nodes(),metadata.meshes(),metadata.materials(),metadata.clips(),metadata.bounds(),all,
                metadata.skinned(),metadata.embeddedTextures(),metadata.sourceFormat(),metadata.sourceGenerator());
        status=issue;AssimpLoader.LOGGER.warn("Workbench extension failed while opening a project: {}",id,failure);
    }
    private void load(WorkbenchProjectStore.Project project){load(project.scene(),project.metadata(),project,project.document(),project.document().assetId());}
    private void load(AssimpScene scene,SceneMetadata meta,WorkbenchProjectStore.Project project,WorkbenchDocument document,String name){
        if(recovery!=null)recovery.invalidatePendingWrites();
        releaseRenderer();this.scene=scene;this.metadata=meta;this.project=project;
        recovery=project==null?null:new WorkbenchRecovery(store,project.documentPath());
        if(document==null)document=WorkbenchDocument.defaults(scene,name,scene.getSourcePath()==null?"source.fbx":scene.getSourcePath().getFileName().toString(),"");
        session=new WorkbenchSession(document);data=new PreviewSceneData(scene);pose=new WorkbenchPose(scene);
        selectedNode=selectedSocket="";selectedMaterial=-1;rendered=failed=false;section=Section.NODES;scroll=0;inspectorScroll=0;
        syncClock();refreshIssues();fit();status="";draftRevision=-1;rebuildWidgets();
        if(project!=null&&!projects.contains(project.documentPath())){var list=new ArrayList<>(projects);list.add(project.documentPath());projects=List.copyOf(list);}
    }
    private void syncClock(){if(session==null)return;var a=session.document().animation();clock=a.time();playing=a.playing();loop=a.loop();speed=a.speed();}
    private void refreshIssues(){if(session==null)return;var all=new ArrayList<>(metadata.issues());all.addAll(session.document().validateReferences(metadata));issues=List.copyOf(all);}
    private void fit(){
        if(data==null||session==null)return;
        Matrix4f transform=session.document().transform().matrix();transform.transformPosition(data.center(),focus);
        float radius=data.radius()*Math.max(Math.abs(session.document().transform().sx()),Math.max(Math.abs(session.document().transform().sy()),Math.abs(session.document().transform().sz())));
        camera.fit(radius,(float)viewportWidth()/viewportHeight());
    }
    private ModelTransform selectedTransform(){
        var doc=session.document();
        if(!selectedSocket.isEmpty())return doc.sockets().get(selectedSocket).transform();
        return selectedNode.isEmpty()?doc.transform():doc.nodes().getOrDefault(selectedNode,WorkbenchDocument.NodeOverride.DEFAULT).transform();
    }
    private void setTransform(ModelTransform transform,boolean transaction){
        if(session==null)return;if(transaction)session.beginTransaction();
        if(!selectedSocket.isEmpty()){var socket=session.document().sockets().get(selectedSocket);session.edit(d->d.withSocket(selectedSocket,new WorkbenchDocument.Socket(socket.node(),transform)));}
        else if(selectedNode.isEmpty())session.edit(d->d.withTransform(transform));
        else session.edit(d->d.withNode(selectedNode,new WorkbenchDocument.NodeOverride(transform,d.nodes().getOrDefault(selectedNode,WorkbenchDocument.NodeOverride.DEFAULT).visible())));
    }
    private void buildInspector(){
        int x=inspectorX(),w=right-16,y=112-inspectorScroll;
        if(session==null)return;
        if(selectedMaterial>=0){buildMaterial(x,y,w);return;}
        if(!selectedSocket.isEmpty()){
            var socket=session.document().sockets().get(selectedSocket);
            textField("Bone",socket.node(),x,y,w,s->{if(metadata.nodes().stream().anyMatch(n->n.path().equals(s)||n.name().equals(s)))edit(d->d.withSocket(selectedSocket,new WorkbenchDocument.Socket(s,socket.transform())));});y+=44;
        }else if(!selectedNode.isEmpty()){
            addRenderableWidget(FlatUi.toggle(x,y,w,24,Component.literal("Visible"),()->session.document().nodes().getOrDefault(selectedNode,WorkbenchDocument.NodeOverride.DEFAULT).visible(),b->{
                var n=session.document().nodes().getOrDefault(selectedNode,WorkbenchDocument.NodeOverride.DEFAULT);edit(d->d.withNode(selectedNode,new WorkbenchDocument.NodeOverride(n.transform(),!n.visible())));
            }));y+=32;
        }
        String[] names={"Position (m)","Rotation (degrees)","Scale"};
        for(int row=0;row<3;row++){
            labels.add(new Label(names[row],x,y));y+=13;
            for(int axis=0;axis<3;axis++){final int component=row*3+axis;int cell=(w-6)/3;
                var f=numberField("",component(selectedTransform(),component),x+axis*(cell+3),y,cell,v->{var t=selectedTransform();float[] values=values(t);values[component]=v;setTransform(new ModelTransform(values[0],values[1],values[2],values[3],values[4],values[5],values[6],values[7],values[8]),true);});
                f.setTooltip(Tooltip.create(Component.literal(names[row]+" "+"XYZ".substring(axis,axis+1))));}
            y+=34;
        }
        final int resetY=y;
        addRenderableWidget(FlatUi.button(x,resetY,w,24,Component.literal("Reset transform"),b->{setTransform(ModelTransform.IDENTITY,false);commit();rebuildWidgets();}));y+=32;
        if(!selectedSocket.isEmpty()){addRenderableWidget(FlatUi.button(x,y,w,24,Component.literal("Remove socket"),b->{edit(d->d.withSocket(selectedSocket,null));selectedSocket="";refreshRows();rebuildWidgets();}));y+=32;}
        if(selectedNode.isEmpty()&&selectedSocket.isEmpty()){
            float h=data.size().y;
            numberField("Target height (blocks)",h*Math.abs(session.document().transform().sy()),x,y+14,w,v->{if(v>0&&h>0){float scale=v/h;setTransform(selectedTransform().withScale(scale,scale,scale),true);}});y+=52;
        }
        addRenderableWidget(FlatUi.toggle(x,y,w,24,Component.literal("Skeleton / sockets"),()->showSkeleton,b->showSkeleton=!showSkeleton));y+=32;
        for(var extension:WorkbenchExtensions.available(context())){
            var e=extension;addRenderableWidget(FlatUi.button(x,y,w,24,Component.literal(e.title()),b->{commit();minecraft.setScreen(e.open(context()));}));y+=30;
        }
        addRenderableWidget(FlatUi.button(x,y,w,24,Component.literal("Revert saved"),b->revertSaved(false)));
    }
    private WorkbenchExtensions.Context context(){return new WorkbenchExtensions.Context(scene,session,this);}
    private void buildMaterial(int x,int y,int w){
        String[] names={"Red","Green","Blue","Opacity","Metallic","Roughness","HDR emission","Alpha cutoff"};
        var m=session.document().materials().get(selectedMaterial);if(m==null)return;
        float[] values={m.r(),m.g(),m.b(),m.a(),m.metallic(),m.roughness(),m.emission(),m.cutoff()};
        int cell=(w-6)/2;
        for(int i=0;i<8;i++){final int index=i;int yy=y+(i/2)*44;
            numberField(names[i],values[i],x+(i%2)*(cell+6),yy+13,cell,v->{var current=session.document().materials().get(selectedMaterial);float[] a={current.r(),current.g(),current.b(),current.a(),current.metallic(),current.roughness(),current.emission(),current.cutoff()};a[index]=v;
                session.beginTransaction();session.edit(d->d.withMaterial(selectedMaterial,new WorkbenchDocument.MaterialOverride(a[0],a[1],a[2],a[3],a[4],a[5],a[6],current.alphaMode(),a[7],current.doubleSided(),current.textures())));});}
        y+=180;
        for(int i=0;i<3;i++){var mode=WorkbenchDocument.AlphaMode.values()[i];addRenderableWidget(FlatUi.choice(x+i*w/3,y,w/3-1,23,Component.literal(mode.name()),()->session.document().materials().get(selectedMaterial).alphaMode()==mode,b->{var c=session.document().materials().get(selectedMaterial);edit(d->d.withMaterial(selectedMaterial,new WorkbenchDocument.MaterialOverride(c.r(),c.g(),c.b(),c.a(),c.metallic(),c.roughness(),c.emission(),mode,c.cutoff(),c.doubleSided(),c.textures())));}));}
        y+=28;
        addRenderableWidget(FlatUi.toggle(x,y,w,23,Component.literal("Double sided"),()->session.document().materials().get(selectedMaterial).doubleSided(),b->{var c=session.document().materials().get(selectedMaterial);edit(d->d.withMaterial(selectedMaterial,new WorkbenchDocument.MaterialOverride(c.r(),c.g(),c.b(),c.a(),c.metallic(),c.roughness(),c.emission(),c.alphaMode(),c.cutoff(),!c.doubleSided(),c.textures())));}));y+=28;
        addRenderableWidget(FlatUi.button(x,y,w,24,Component.literal("Texture slots"),b->{commit();minecraft.setScreen(new TextureScreen(this,selectedMaterial));}));
    }
    private EditBox numberField(String name,float value,int x,int y,int w,Consumer<Float> apply){
        if(!name.isBlank())labels.add(new Label(name,x,y-12));EditBox f=FlatUi.field(font,x,y,w,26,Component.literal(name));f.setMaxLength(24);f.setValue(format(value));
        f.setResponder(s->{try{float n=Float.parseFloat(s);if(!Float.isFinite(n))throw new IllegalArgumentException();apply.accept(n);f.setTextColor(FlatUi.TEXT);}catch(RuntimeException invalid){f.setTextColor(FlatUi.ERROR);}});
        fields.add(f);return addRenderableWidget(f);
    }
    private EditBox textField(String name,String value,int x,int y,int w,Consumer<String> apply){
        labels.add(new Label(name,x,y-12));EditBox field=FlatUi.field(font,x,y,w,26,Component.literal(name));field.setMaxLength(4096);field.setValue(value);field.setResponder(apply);fields.add(field);return addRenderableWidget(field);
    }
    private void buildTimeline(){
        if(session==null)return;int y=timelineY(),x=left+8,w=viewportWidth()-16;
        addRenderableWidget(FlatUi.icon(x,y,playing?FlatUi.Icon.PAUSE:FlatUi.Icon.PLAY,Component.literal(playing?"Pause":"Play"),b->{playing=!playing;animationEdit();rebuildWidgets();}));
        addRenderableWidget(FlatUi.icon(x+30,y,FlatUi.Icon.PREVIOUS,Component.literal("Previous clip"),b->cycleClip(-1)));
        addRenderableWidget(FlatUi.icon(x+60,y,FlatUi.Icon.NEXT,Component.literal("Next clip"),b->cycleClip(1)));
        if(w>210)addRenderableWidget(FlatUi.toggle(x+94,y,72,26,Component.literal("Loop"),()->loop,b->{loop=!loop;animationEdit();}));
        if(w>310){
            labels.add(new Label("Speed",x+174,y+9));
            numberField("",speed,x+210,y,64,v->{speed=Math.max(-16,Math.min(16,v));animationEdit();}).setTooltip(Tooltip.create(Component.literal("Playback speed")));
        }
    }
    private void cycleClip(int delta){if(metadata.clips().isEmpty())return;var clips=metadata.clips();int current=-1;for(int i=0;i<clips.size();i++)if(clips.get(i).name().equals(session.document().animation().clip()))current=i;
        String name=clips.get(Math.floorMod(current+delta,clips.size())).name();clock=0;edit(d->d.withAnimation(new WorkbenchDocument.AnimationPreview(name,0,speed,loop,playing)));rebuildWidgets();}
    private void animationEdit(){if(session!=null)edit(d->d.withAnimation(new WorkbenchDocument.AnimationPreview(d.animation().clip(),clock,speed,loop,playing)));}
    private double duration(){return session==null?0:metadata.clips().stream().filter(c->c.name().equals(session.document().animation().clip())).mapToDouble(SceneMetadata.ClipInfo::seconds).findFirst().orElse(0);}
    @Override public void tick(){
        //? if forge {
        // 1.21 deleted EditBox#tick(); the caret now advances inside EditBox#renderWidget.
        if(search!=null)search.tick();fields.forEach(EditBox::tick);
        //?}
        if(save!=null)save.active=session!=null&&!busy;if(undo!=null)undo.active=session!=null&&session.canUndo();if(redo!=null)redo.active=session!=null&&session.canRedo();
        long current=AssimpModelLoader.getCacheGeneration();if(generation!=current){releaseRenderer();generation=current;rendered=failed=false;status="Resources reloaded";}
        if(++autosaveTicks>=100){autosaveTicks=0;if(project!=null&&session.dirty()&&session.revision()!=draftRevision&&!busy){
            var doc=session.document();var base=session.savedDocument();var pending=recovery;long revision=session.revision(),epoch=pending.generation();
            task("Saving recovery",()->{try{return pending.save(epoch,base,doc);}catch(Exception e){throw new RuntimeException(e);}},written->{if(written){draftRevision=revision;status="Recovery draft saved";}},Operation.RECOVERY);
        }}
    }
    @Override public void render(GuiGraphics g,int mx,int my,float partial){
        g.fill(0,0,width,height,FlatUi.BACKGROUND);LookDevNavigation.render(g,width);
        g.fill(0,46,width,84,FlatUi.SURFACE);g.fill(0,84,left,height,FlatUi.SURFACE);g.fill(width-right,84,width,height,FlatUi.SURFACE);
        g.fill(viewportX(),viewportY(),width-right-1,viewportY()+viewportHeight(),0xff292e33);
        g.fill(left,84,left+1,height,FlatUi.LINE);g.fill(width-right-1,84,width-right,height,FlatUi.LINE);
        long now=System.nanoTime();double elapsed=lastNanos==0?0:Math.min(.1,(now-lastNanos)*1e-9);lastNanos=now;
        if(playing&&session!=null){double end=duration();clock=Math.max(0,clock+elapsed*speed);if(end>0&&clock>end){if(loop)clock%=end;else{clock=end;playing=false;}}}
        if(scene!=null&&session!=null&&!failed){
            try{
                pose.sample(session.document(),clock);g.flush();if(renderer==null)renderer=new PreviewGpuRenderer(scene,data);
                var window=minecraft.getWindow();double scale=window.getGuiScale();
                renderer.render(camera,(int)Math.round(viewportX()*scale),(int)Math.round(window.getHeight()-(viewportY()+viewportHeight())*scale),
                        Math.max(1,(int)Math.round(viewportWidth()*scale)),Math.max(1,(int)Math.round(viewportHeight()*scale)),session.document(),pose,focus,selectedMaterial);
                rendered=true;g.enableScissor(viewportX(),viewportY(),viewportX()+viewportWidth(),viewportY()+viewportHeight());renderOverlays(g,mx,my);g.disableScissor();
            }catch(Exception|LinkageError e){failed=true;status="Preview: "+e.getMessage();AssimpLoader.LOGGER.error("Workbench preview failed",e);}
        }
        renderRows(g,mx,my);
        g.enableScissor(width-right,84,width,height-24);
        String selected=selectedMaterial>=0?"Material: "+metadata.materials().get(selectedMaterial).name():!selectedSocket.isEmpty()?selectedSocket:!selectedNode.isEmpty()?pose.nodeName(selectedNode):"Model transform";
        g.drawString(font,FlatUi.clip(font,session==null?"Inspector":selected,right-16),inspectorX(),91,FlatUi.ACCENT,false);
        g.disableScissor();
        g.enableScissor(width-right,110,width,height-26);
        for(var label:labels)if(label.x>=width-right)g.drawString(font,FlatUi.clip(font,label.text,right-16),label.x,label.y,FlatUi.MUTED,false);
        g.disableScissor();
        for(var label:labels)if(label.x<width-right)g.drawString(font,label.text,label.x,label.y,FlatUi.MUTED,false);
        if(session!=null){int y=timelineY();g.fill(left,y-8,width-right,y-7,FlatUi.LINE);
            String clip=session.document().animation().clip();g.drawString(font,FlatUi.clip(font,clip.isEmpty()?"Bind pose":clip,viewportWidth()-104),left+8,y+34,FlatUi.TEXT,false);
            g.drawString(font,String.format(Locale.ROOT,"%.2f / %.2f s",clock,duration()),Math.max(left+8,width-right-104),y+34,FlatUi.MUTED,false);
            int xx=left+8,ww=viewportWidth()-16;g.fill(xx,y+52,xx+ww,y+56,FlatUi.LINE);g.fill(xx,y+52,xx+(int)(ww*(duration()>0?clock/duration():0)),y+56,FlatUi.ACCENT);
        }
        g.fill(0,height-23,width,height,FlatUi.FIELD);
        String footer=busy?status:!status.isEmpty()?status:session==null?"":(session.dirty()?"* ":"")+session.document().assetId()+"  |  "+data.triangles()+" triangles  |  "+issues.size()+" issues";
        g.drawString(font,FlatUi.clip(font,footer,width-16),8,height-16,failed?FlatUi.ERROR:FlatUi.MUTED,false);
        for(var widget:renderables)if(!inspectorWidgets.contains(widget))widget.render(g,mx,my,partial);
        g.enableScissor(width-right,110,width,height-26);
        inspectorWidgets.forEach(widget->widget.render(g,mx,my,partial));
        g.disableScissor();
        if(!gizmo.tooltip().isEmpty()&&insideViewport(mx,my))g.renderTooltip(font,Component.literal(gizmo.tooltip()),mx,my);
        if(my>=height-23&&!issues.isEmpty())g.renderComponentTooltip(font,issues.stream().limit(12).<Component>map(Component::literal).toList(),mx,my);
    }
    private void renderRows(GuiGraphics g,int mx,int my){
        g.enableScissor(0,rowsY(),left,height-28);
        for(int i=scroll;i<rows.size()&&i<scroll+rowCount();i++){Row r=rows.get(i);int y=rowsY()+(i-scroll)*22;
            boolean chosen=section==Section.NODES?r.key.equals(selectedNode):section==Section.MATERIALS?r.index==selectedMaterial:section==Section.SOCKETS?r.key.equals(selectedSocket):false;
            if(chosen||mx<left&&my>=y&&my<y+22)g.fill(0,y,left,y+21,chosen?FlatUi.SELECTED:0xff30363b);
            int indent=Math.min(r.depth*8,40);g.drawString(font,FlatUi.clip(font,r.label,left-indent-16),8+indent,y+7,chosen?FlatUi.ACCENT:FlatUi.TEXT,false);
        }g.disableScissor();
    }
    private Matrix4f viewportProjection(){return camera.projection((float)viewportWidth()/viewportHeight()).mul(camera.view()).translate(-focus.x,-focus.y,-focus.z);}
    private void renderOverlays(GuiGraphics g,int mx,int my){
        Matrix4f p=viewportProjection(),root=session.document().transform().matrix();
        if(showSkeleton)for(var node:metadata.nodes()){
            var matrix=new Matrix4f(root).mul(pose.node(node.path()));var point=cn.minerealms.assimploader.client.transform.GizmoMath.project(p,matrix.getTranslation(new Vector3f()),viewportWidth(),viewportHeight());
            if(point!=null){int xx=viewportX()+(int)point.x(),yy=viewportY()+(int)point.y();int color=node.path().equals(selectedNode)?0xffffdb80:0x884cd8cf;g.fill(xx-1,yy-1,xx+2,yy+2,color);}
        }
        if(showSkeleton)for(var socket:session.document().sockets().entrySet()){
            Matrix4f m=new Matrix4f(root).mul(pose.node(socket.getValue().node())).mul(socket.getValue().transform().matrix());
            var point=cn.minerealms.assimploader.client.transform.GizmoMath.project(p,m.getTranslation(new Vector3f()),viewportWidth(),viewportHeight());
            if(point!=null)g.drawString(font,socket.getKey(),viewportX()+(int)point.x()+5,viewportY()+(int)point.y(),0xffffda88,true);
        }
        if(selectedMaterial>=0)return;
        Matrix4f base=new Matrix4f();
        if(!selectedSocket.isEmpty())base.set(root).mul(pose.node(session.document().sockets().get(selectedSocket).node()));
        else if(!selectedNode.isEmpty())base.set(root).mul(pose.node(selectedNode)).mul(selectedTransform().matrix().invert());
        gizmo.update(p,base,selectedTransform(),viewportX(),viewportY(),viewportWidth(),viewportHeight(),camera.distance()*.13f,
                t->{setTransform(t,false);},()->{playing=false;session.beginTransaction();},()->{session.commitTransaction();rebuildWidgets();});
        gizmo.render(g,mx,my);
    }
    @Override public boolean mouseClicked(double x,double y,int button){
        if(busy&&operation==Operation.DISCARD)return true;
        commit();
        if(super.mouseClicked(x,y,button))return true;
        if(x<left&&y>=rowsY()&&y<height-28){int i=scroll+((int)y-rowsY())/22;if(i>=0&&i<rows.size())select(rows.get(i));return true;}
        if(session!=null&&x>left&&x<width-right&&y>=timelineY()+46&&y<=timelineY()+64){playing=false;clock=Math.max(0,Math.min(duration(),(x-left-8)/(viewportWidth()-16)*duration()));animationEdit();return true;}
        if(insideViewport(x,y)){if(selectedMaterial<0&&gizmo.click(x,y,button))return true;dragButton=button;setFocused(null);return true;}
        return false;
    }
    @Override public boolean mouseDragged(double x,double y,int button,double dx,double dy){
        if(busy&&operation==Operation.DISCARD)return true;
        if(gizmo.drag(x,y))return true;
        if(dragButton>=0){if(dragButton==1||dragButton==2||hasShiftDown())camera.pan(dx,dy,viewportHeight());else camera.orbit(dx,dy);return true;}
        return super.mouseDragged(x,y,button,dx,dy);
    }
    @Override public boolean mouseReleased(double x,double y,int button){if(gizmo.release())return true;dragButton=-1;return super.mouseReleased(x,y,button);}
    private boolean scrolled(double x,double y,double delta){
        if(busy&&operation==Operation.DISCARD)return true;
        if(x<left){scroll=Math.max(0,Math.min(Math.max(0,rows.size()-rowCount()),scroll-(int)Math.signum(delta)*3));return true;}
        if(x>=width-right&&y>=110&&y<height-24){
            commit();inspectorScroll=Math.max(0,Math.min(inspectorMaxScroll,inspectorScroll-(int)Math.signum(delta)*28));rebuildWidgets();return true;
        }
        if(insideViewport(x,y)){if(hasShiftDown())camera.pan(0,delta*18,viewportHeight());else camera.zoom(delta);return true;}return false;
    }
    //? if forge {
    @Override public boolean mouseScrolled(double x,double y,double delta){return scrolled(x,y,delta)||super.mouseScrolled(x,y,delta);}
    //?}
    //? if neoforge {
    /*
    // 1.21 split the scroll delta into horizontal and vertical axes; only the vertical one is used here.
    @Override public boolean mouseScrolled(double x,double y,double deltaX,double deltaY){return scrolled(x,y,deltaY)||super.mouseScrolled(x,y,deltaX,deltaY);}
    *///?}
    @Override public boolean keyPressed(int key,int scan,int modifiers){
        if(busy&&operation==Operation.DISCARD)return true;
        if(hasControlDown()&&key==GLFW.GLFW_KEY_S){commit();save();return true;}
        if(hasControlDown()&&key==GLFW.GLFW_KEY_Z){history(hasShiftDown());return true;}
        if(hasControlDown()&&key==GLFW.GLFW_KEY_Y){history(true);return true;}
        if(!(getFocused() instanceof EditBox)&&key==GLFW.GLFW_KEY_F){fit();return true;}
        if(key==GLFW.GLFW_KEY_ESCAPE){gizmo.cancel();if(session!=null)session.cancelTransaction();}
        return super.keyPressed(key,scan,modifiers);
    }
    private boolean insideViewport(double x,double y){return x>=viewportX()&&x<viewportX()+viewportWidth()&&y>=viewportY()&&y<viewportY()+viewportHeight();}
    @Override public void onFilesDrop(List<Path> paths){if(!paths.isEmpty())importPath(paths.get(0));}
    private void pathDialog(boolean importing){commit();minecraft.setScreen(new PathScreen(this,importing));}
    public void save(){
        if(session==null)return;
        commit();animationEdit();
        saveProject(session).thenAccept(saved->{
            try{applyExtensions(saved);}catch(RuntimeException error){status="Project saved; extension apply failed: "+error.getMessage();AssimpLoader.LOGGER.warn("Saved workbench extension could not be applied",error);}
        });
    }
    @Override public Path projectPath(){return project==null?null:project.documentPath();}
    @Override public CompletableFuture<WorkbenchDocument> saveProject(WorkbenchSession expectedSession){
        if(minecraft==null||!minecraft.isSameThread())return CompletableFuture.failedFuture(new IllegalStateException("Save must start on the client thread"));
        if(expectedSession==null||expectedSession!=session||scene==null)return saveRejected("This edit session no longer belongs to the workbench");
        if(busy&&operation!=Operation.RECOVERY)return saveRejected("Wait for the current workbench operation before saving");
        commit();
        if(recovery!=null)recovery.invalidatePendingWrites();
        if(project!=null)return persistProject(expectedSession);
        var source=scene;var initial=session.document();var resources=minecraft.getResourceManager();
        return task("Creating project",()->{
            try{return new ImportedProject(WorkbenchAssets.importLoaded(store,source,resources),
                    WorkbenchDocument.defaults(source,initial.assetId(),initial.sourcePath(),initial.contentHash()));}
            catch(Exception e){throw new RuntimeException(e);}
        },imported->adoptProject(expectedSession,imported),Operation.SAVE).thenCompose(imported->persistProject(expectedSession));
    }
    private CompletableFuture<WorkbenchDocument> saveRejected(String message){status=message;return CompletableFuture.failedFuture(new IllegalStateException(message));}
    private void adoptProject(WorkbenchSession target,ImportedProject imported){
        if(session!=target)throw new IllegalStateException("Workbench edit session changed during import");
        var copied=imported.project();
        target.rebase(document->document.rebaseAsset(imported.sourceDefaults(),copied.document()));
        releaseRenderer();scene=copied.scene();metadata=copied.metadata();project=copied;
        recovery=new WorkbenchRecovery(store,copied.documentPath());
        data=new PreviewSceneData(scene);pose=new WorkbenchPose(scene);rendered=failed=false;draftRevision=-1;
        if(!projects.contains(copied.documentPath())){var list=new ArrayList<>(projects);list.add(copied.documentPath());projects=List.copyOf(list);}
        refreshIssues();refreshRows();rebuildWidgets();
    }
    private CompletableFuture<WorkbenchDocument> persistProject(WorkbenchSession target){
        if(session!=target||project==null)return saveRejected("Workbench edit session changed before saving");
        // First-copy IO may have taken time: capture the latest edits only after adopting its portable paths.
        commit();var snapshot=target.document();var file=project.documentPath();
        return task("Saving project",()->{
            try{
                store.save(file,snapshot);
                String warning="";
                try{store.discardDraft(file);}catch(java.io.IOException cleanup){warning="recovery cleanup failed";AssimpLoader.LOGGER.warn("Project saved but recovery cleanup failed: {}",file,cleanup);}
                return new SavedProject(snapshot,warning);
            }catch(Exception e){throw new RuntimeException(e);}
        },saved->{
            target.markSaved(saved.document());
            project=new WorkbenchProjectStore.Project(project.directory(),file,scene,saved.document(),metadata);
            draftRevision=-1;autosaveTicks=0;
            status="Saved "+file.getParent().getFileName()+(target.dirty()?" (newer edits remain)":"")+
                    (saved.warning().isEmpty()?"":"; "+saved.warning());
        },Operation.SAVE).thenApply(SavedProject::document);
    }
    private void export(){if(project==null){status="Save this project before exporting";return;}var p=project;var doc=session.document();Path destination=store.root().resolve("exports").resolve(project.directory().getFileName()+".zip");task("Exporting",()->{try{return store.exportZip(p,doc,destination);}catch(Exception e){throw new RuntimeException(e);}},path->status="Exported "+path.getFileName());}
    private void apply(){
        if(session==null)return;
        commit();
        var extensions=WorkbenchExtensions.available(context());
        if(extensions.stream().noneMatch(WorkbenchExtensions.Extension::handlesApplication))
            WorkbenchRuntime.apply(scene,session.document());
        applyExtensions();
        status="Applied locally";
    }
    private void applyExtensions(){for(var extension:WorkbenchExtensions.available(context()))extension.apply(context());}
    private void applyExtensions(WorkbenchDocument persisted){
        var savedContext=new WorkbenchExtensions.Context(scene,new WorkbenchSession(persisted),this);
        for(var extension:WorkbenchExtensions.available(savedContext))extension.apply(savedContext);
    }
    private void revertSaved(boolean close){
        if(session==null)return;
        if(busy&&operation!=Operation.RECOVERY){status="Wait for the current operation before discarding edits";return;}
        commit();gizmo.cancel();dragButton=-1;setFocused(null);
        var target=session;
        if(recovery==null){finishRevert(target,close);return;}
        var pending=recovery;pending.invalidatePendingWrites();
        task("Discarding recovery",()->{try{pending.discard();return target;}catch(Exception e){throw new RuntimeException(e);}},saved->finishRevert(saved,close),Operation.DISCARD);
    }
    private void finishRevert(WorkbenchSession target,boolean close){
        if(session!=target)return;
        session.revert();draftRevision=session.revision();autosaveTicks=0;
        syncClock();refreshIssues();refreshRows();rebuildWidgets();status="Reverted to saved project";
        if(close)LookDevNavigation.close(null);
    }
    private <T> CompletableFuture<T> task(String label,Supplier<T> work,Consumer<T> complete){
        return task(label,work,complete,Operation.NORMAL);
    }
    private <T> CompletableFuture<T> task(String label,Supplier<T> work,Consumer<T> complete,Operation next){
        // Queue explicit cleanup after an autosave and ignore its superseded UI callback.
        if(busy&&!((next==Operation.DISCARD||next==Operation.SAVE)&&operation==Operation.RECOVERY))
            return CompletableFuture.failedFuture(new IllegalStateException("Workbench is busy"));
        var completion=new CompletableFuture<T>();
        busy=true;operation=next;status=label;int token=++request;
        try{CompletableFuture.supplyAsync(work,IO).whenComplete((result,error)->minecraft.execute(()->{
            if(token!=request){completion.completeExceptionally(new CancellationException("Workbench operation superseded"));return;}
            busy=false;
            Throwable failure=error;
            if(failure==null){
                try{status="";complete.accept(result);completion.complete(result);return;}
                catch(RuntimeException callbackFailure){failure=callbackFailure;}
            }
            Throwable cause=failure;while(cause.getCause()!=null)cause=cause.getCause();
            status=cause.getMessage()==null?cause.toString():cause.getMessage();AssimpLoader.LOGGER.warn("Workbench operation failed: {}",label,cause);
            completion.completeExceptionally(cause);
        }));}catch(RuntimeException rejected){busy=false;status="Workbench IO queue is full";completion.completeExceptionally(rejected);}
        return completion;
    }
    private void releaseRenderer(){if(renderer!=null){renderer.close();renderer=null;}}
    @Override public void removed(){releaseRenderer();dragButton=-1;gizmo.cancel();commit();}
    @Override public boolean isPauseScreen(){return false;}
    @Override public void onClose(){if(busy&&operation==Operation.DISCARD)return;commit();if(session!=null&&session.dirty()){minecraft.setScreen(new UnsavedScreen(this));return;}LookDevNavigation.close(null);}
    public boolean previewRendered(){return rendered;} public boolean previewFailed(){return failed;}
    public int previewPbrTextureCount(){return renderer==null?0:renderer.pbrTextureCount();}
    public boolean busy(){return busy;}
    public WorkbenchSession session(){return session;} public AssimpScene scene(){return scene;}
    private static String format(float n){return String.format(Locale.ROOT,"%.5g",n);}
    private static float[] values(ModelTransform t){return new float[]{t.x(),t.y(),t.z(),t.rx(),t.ry(),t.rz(),t.sx(),t.sy(),t.sz()};}
    private static float component(ModelTransform t,int index){return values(t)[index];}

    private static final class PathScreen extends Screen {
        private final ModelWorkbenchScreen parent;private final boolean importing;private EditBox path;
        PathScreen(ModelWorkbenchScreen parent,boolean importing){super(Component.literal(importing?"Import asset":"Open project"));this.parent=parent;this.importing=importing;}
        @Override protected void init(){int w=Math.min(560,width-32),x=(width-w)/2,y=height/2;path=addRenderableWidget(FlatUi.field(font,x,y,w,26,Component.literal("Path")));path.setMaxLength(4096);path.setHint(Component.literal(importing?"FBX file or folder":"project.workbench.json"));
            addRenderableWidget(FlatUi.button(x,y+38,100,26,Component.literal(importing?"Import":"Open"),b->{String value=path.getValue().trim();minecraft.setScreen(parent);try{if(importing)parent.importPath(Path.of(value));else parent.open(Path.of(value));}catch(RuntimeException invalid){parent.status=invalid.getMessage();}}));
            addRenderableWidget(FlatUi.button(x+110,y+38,100,26,Component.literal("Cancel"),b->onClose()));setInitialFocus(path);}
        @Override public void render(GuiGraphics g,int x,int y,float p){ScreenCompat.renderBackground(this,g,x,y,p);g.drawCenteredString(font,title,width/2,height/2-28,FlatUi.TEXT);super.render(g,x,y,p);}
        @Override public void onFilesDrop(List<Path> files){if(!files.isEmpty()){minecraft.setScreen(parent);if(importing)parent.importPath(files.get(0));else parent.open(files.get(0));}}
        @Override public void onClose(){minecraft.setScreen(parent);}@Override public boolean isPauseScreen(){return false;}
    }
    private static final class UnsavedScreen extends Screen {
        private final ModelWorkbenchScreen parent;UnsavedScreen(ModelWorkbenchScreen parent){super(Component.literal("Unsaved project"));this.parent=parent;}
        @Override protected void init(){int x=width/2-150,y=height/2;addRenderableWidget(FlatUi.button(x,y,94,26,Component.literal("Save"),b->{minecraft.setScreen(parent);parent.save();}));
            addRenderableWidget(FlatUi.button(x+102,y,94,26,Component.literal("Discard"),b->{minecraft.setScreen(parent);parent.revertSaved(true);}));
            addRenderableWidget(FlatUi.button(x+204,y,94,26,Component.literal("Keep editing"),b->onClose()));}
        @Override public void render(GuiGraphics g,int x,int y,float p){ScreenCompat.renderBackground(this,g,x,y,p);g.drawCenteredString(font,title,width/2,height/2-28,FlatUi.TEXT);super.render(g,x,y,p);}
        @Override public void onClose(){minecraft.setScreen(parent);}@Override public boolean isPauseScreen(){return false;}
    }
    private static final class TextureScreen extends Screen {
        private static final String[] SLOTS={"albedo","normal","specular","roughness","metallic","occlusion","emissive"};
        private final ModelWorkbenchScreen parent;private final int material;private String error="";TextureScreen(ModelWorkbenchScreen parent,int material){super(Component.literal("Texture slots"));this.parent=parent;this.material=material;}
        @Override protected void init(){int w=Math.min(500,width-32),x=(width-w)/2,y=38;parent.session.beginTransaction();
            for(String slot:SLOTS){int yy=y;addRenderableWidget(FlatUi.button(x,yy,92,26,Component.literal(slot),b->{}));EditBox field=addRenderableWidget(FlatUi.field(font,x+100,yy,w-100,26,Component.literal(slot)));field.setMaxLength(4096);field.setValue(parent.session.document().materials().get(material).textures().getOrDefault(slot,""));field.setResponder(value->{try{var c=parent.session.document().materials().get(material);var paths=new java.util.LinkedHashMap<>(c.textures());if(value.isBlank())paths.remove(slot);else paths.put(slot,value);parent.session.edit(d->d.withMaterial(material,new WorkbenchDocument.MaterialOverride(c.r(),c.g(),c.b(),c.a(),c.metallic(),c.roughness(),c.emission(),c.alphaMode(),c.cutoff(),c.doubleSided(),paths)));error="";}catch(RuntimeException e){error=e.getMessage();}});y+=36;}
            addRenderableWidget(FlatUi.button(x,y,100,26,Component.literal("Apply"),b->{parent.session.commitTransaction();onClose();}));addRenderableWidget(FlatUi.button(x+110,y,100,26,Component.literal("Cancel"),b->{parent.session.cancelTransaction();onClose();}));}
        @Override public void render(GuiGraphics g,int x,int y,float p){ScreenCompat.renderBackground(this,g,x,y,p);g.drawCenteredString(font,title,width/2,16,FlatUi.TEXT);g.drawString(font,FlatUi.clip(font,error,width-32),16,height-20,FlatUi.ERROR,false);super.render(g,x,y,p);}
        @Override public void onClose(){parent.session.commitTransaction();minecraft.setScreen(parent);}@Override public boolean isPauseScreen(){return false;}
    }
}
