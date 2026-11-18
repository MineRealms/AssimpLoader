package cn.minerealms.rigworks.character.client;

import cn.minerealms.assimploader.AssimpLoader;
import cn.minerealms.assimploader.client.ui.FlatUi;
import cn.minerealms.assimploader.core.AsyncModelLoader;
import cn.minerealms.assimploader.lookdev.preview.*;
import cn.minerealms.assimploader.workbench.client.*;
import cn.minerealms.assimploader.workbench.model.*;
import cn.minerealms.rigworks.RigWorksConfig;
import cn.minerealms.rigworks.character.core.*;
import com.google.gson.JsonObject;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.components.AbstractWidget;
import net.minecraft.client.gui.components.Tooltip;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import org.joml.Quaternionf;
import org.joml.Vector3f;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.*;
import java.util.function.Consumer;

/** The player-specific editor is an engine workbench extension, with no dependency in the other direction. */
public final class RigWorksWorkbenchExtension implements WorkbenchExtensions.Extension {
    private static final String KEY="rigworks";
    private static boolean registered;
    private static final String[] ROLES={"root","hips","spine","chest","neck","head","left_shoulder","left_upper_arm","left_lower_arm","left_hand","right_shoulder","right_upper_arm","right_lower_arm","right_hand","left_upper_leg","left_lower_leg","left_foot","right_upper_leg","right_lower_leg","right_foot"};
    private static final String[] ACTIONS={"idle","walk","run","walk_backward","strafe_left","strafe_right","crouch","crouch_walk","jump","fall","swim","fly","elytra","ride","sleep","dead","use_item","swing","crawl","crawl_idle","ladder_up","ladder_down","ladder_idle","water_idle","lava","riptide","frozen","hurt","land"};
    public static void register(){if(!registered){registered=true;WorkbenchExtensions.register(new RigWorksWorkbenchExtension());}}
    @Override public String id(){return KEY;}
    @Override public String title(){return "Character / RigWorks";}
    @Override public boolean handlesApplication(){return true;}
    @Override public boolean supports(WorkbenchExtensions.Context context){return context.scene()!=null&&(context.scene().hasSkeleton()||context.session().document().extensions().has(KEY));}
    @Override public Screen open(WorkbenchExtensions.Context context){return new Editor(context);}
    @Override public void apply(WorkbenchExtensions.Context context) {
        var extension=context.session().document().extensions().getAsJsonObject(KEY);if(extension==null)return;
        try {
            String profile=extension.get("profileId").getAsString();var config=CharacterVisualConfig.parse(extension.get("visual").toString());
            CharacterVisualSharing.registerAsset(new ResourceLocation(config.profile().model()),context.scene());
            activate(profile,config);
        }catch(IOException|RuntimeException error){throw new IllegalArgumentException("Character apply failed: "+error.getMessage(),error);}
    }
    private static void activate(String profile,CharacterVisualConfig config)throws IOException {
        CharacterVisualSharing.save(profile,config);
        var profileSetting=RigWorksConfig.CLIENT.profile;boolean changed=!profile.equals(profileSetting.get());
        RigWorksConfig.update(edit->edit.set(profileSetting,profile).set(RigWorksConfig.CLIENT.enabled,true));
        LocalPlayerCharacter.refreshConfiguration();if(changed||LocalPlayerCharacter.currentAnimation()==null)LocalPlayerCharacter.reload();
    }
    private static String stem(WorkbenchExtensions.Context context) {
        return CharacterVisualConfig.hash(context.session().document().assetId().getBytes(StandardCharsets.UTF_8)).substring(0,24);
    }
    private static final class Editor extends Screen {
        private enum Page { ROOT, RIG, ACTIONS, SHARING }
        private record Label(String text,int x,int y){}
        private final WorkbenchExtensions.Context context;
        private final PreviewCamera camera=new PreviewCamera();
        private final PreviewSceneData data;
        private final WorkbenchPose pose;
        private final List<Label> labels=new ArrayList<>();
        private final List<AbstractWidget> inspector=new ArrayList<>();
        private final List<String> nodes,clips;
        private PreviewGpuRenderer renderer;
        private CharacterVisualConfig visual;
        private String profileId,status="Reading character asset identity";
        private Page page=Page.ROOT;
        private int panel,role,action,drag=-1,previewDelay,scroll,maxScroll;
        private boolean ready,failed,playing;
        private double clock;
        private long lastFrame;
        private WorkbenchDocument preview;
        Editor(WorkbenchExtensions.Context context) {
            super(Component.literal("Character Workbench"));this.context=context;
            data=new PreviewSceneData(context.scene());pose=new WorkbenchPose(context.scene());
            var metadata=SceneMetadata.inspect(context.scene());nodes=metadata.nodes().stream().map(SceneMetadata.NodeInfo::name).distinct().toList();
            clips=metadata.clips().stream().map(SceneMetadata.ClipInfo::name).toList();preview=context.session().document();
        }
        @Override protected void init() {
            panel=Math.min(274,Math.max(208,width/3));labels.clear();inspector.clear();int x=width-panel+8,w=panel-16;
            addRenderableWidget(FlatUi.button(8,8,72,24,Component.translatable("gui.back"),b->onClose()));
            addRenderableWidget(FlatUi.icon(88,8,FlatUi.Icon.FIT,Component.literal("Frame character"),b->fit()));
            for(int i=0;i<Page.values().length;i++) {
                var selected=Page.values()[i];String text=switch(selected){case ROOT->"Model";case RIG->"Rig";case ACTIONS->"Actions";case SHARING->"Share";};
                addRenderableWidget(FlatUi.choice(x+i*w/4,42,w/4-1,24,Component.literal(text),()->page==selected,b->{page=selected;scroll=0;rebuildWidgets();}));
            }
            if(ready) {
                int first=children().size();
                switch(page){case ROOT->rootFields(x,80,w);case RIG->rigFields(x,80,w);case ACTIONS->actionFields(x,80,w);case SHARING->sharingFields(x,80,w);}
                int bottom=80;for(int i=first;i<children().size();i++)if(children().get(i) instanceof AbstractWidget widget){inspector.add(widget);bottom=Math.max(bottom,widget.getY()+widget.getHeight());}
                maxScroll=Math.max(0,bottom-(height-86));scroll=Math.min(scroll,maxScroll);
                for(var widget:inspector){widget.setY(widget.getY()-scroll);widget.visible=widget.getY()>=73&&widget.getY()+widget.getHeight()<height-82;}
                int cell=(w-6)/2;
                addRenderableWidget(FlatUi.button(x,height-75,cell,24,Component.literal("Save"),b->save(false)));
                addRenderableWidget(FlatUi.button(x+cell+6,height-75,cell,24,Component.literal("Apply"),b->save(true)));
                addRenderableWidget(FlatUi.button(x,height-46,w,24,Component.literal("Equipment transform"),b->{save(true);EquipmentTransformScreen.open(this);}));
            }else if(!failed)load();
            fit();lastFrame=System.nanoTime();
        }
        private void load() {
            ready=false;failed=true;
            try {
                var saved=context.session().document().extensions().getAsJsonObject(KEY);
                if(saved!=null) {
                    profileId=saved.get("profileId").getAsString();visual=CharacterVisualConfig.parse(saved.get("visual").toString());
                } else if(LocalPlayerCharacter.currentAnimation()!=null&&LocalPlayerCharacter.currentAnimation().getScene()==context.scene()) {
                    profileId=LocalPlayerCharacter.settings().profile();visual=LocalPlayerCharacter.currentVisualConfig();
                }
                ResourceLocation model=visual==null?context.scene().getSourceResource():new ResourceLocation(visual.profile().model());
                if(model==null)model=new ResourceLocation("rigworks","models/workbench/"+stem(context)+"/model.fbx");
                CharacterVisualSharing.registerAsset(model,context.scene());ResourceLocation id=model;
                AsyncModelLoader.decodeAsync("rigworks:workbench-hash/"+stem(context)+"@"+cn.minerealms.assimploader.core.AssimpModelLoader.getCacheGeneration(),()->{
                    CharacterVisualSharing.modelHash(Minecraft.getInstance().getResourceManager(),id.toString());return context.scene();
                }).whenComplete((scene,error)->Minecraft.getInstance().execute(()->{
                    if(error!=null){status="Cannot read model: "+error.getMessage();return;}
                    try {
                        String hash=CharacterVisualSharing.cachedModelHash(Minecraft.getInstance().getResourceManager(),id.toString());
                        if(hash==null)throw new IllegalStateException("Model changed during import; reopen the character panel");
                        if(visual==null) {
                            var mapping=new LinkedHashMap<String,CharacterProfile.HumanoidBone>();
                            for(String name:ROLES){String candidate=suggest(nodes,name);if(candidate!=null)mapping.put(name,new CharacterProfile.HumanoidBone(candidate,CharacterProfile.Rotation.IDENTITY));}
                            var actions=new LinkedHashMap<String,String>();for(String name:ACTIONS){String candidate=suggest(clips,name);if(candidate!=null)actions.put(name,candidate);}
                            float h=data.size().y;double scale=Math.max(.001,Math.min(100,h>0?1.8/h:1));
                            var profile=new CharacterProfile(id.toString(),scale,mapping,actions,null,.15,.05,CharacterProfile.HeadLook.defaults());
                            visual=new CharacterVisualConfig(profile,CharacterEquipmentDefinition.defaults(profile),CharacterVisualConfig.Transform.IDENTITY,hash,null,null);
                            profileId="rigworks:workbench/"+stem(context)+"/profile.json";
                        }else visual=new CharacterVisualConfig(visual.profile(),visual.equipment(),visual.transform(),hash,visual.rig(),visual.physics(),visual.options());
                        ready=true;failed=false;status="";updatePreview();rebuildWidgets();
                    }catch(RuntimeException failure){status=failure.getMessage();}
                }));
            }catch(IOException|RuntimeException error){status=error.getMessage();}
        }
        private void label(String text,int x,int y){labels.add(new Label(text,x,y));}
        private void field(String title,String value,int x,int y,int w,Consumer<String> setter) {
            label(title,x,y);var box=FlatUi.field(font,x,y+13,w,23,Component.literal(title));box.setMaxLength(256);box.setValue(value);
            box.setResponder(text->{try{setter.accept(text);status="";}catch(RuntimeException error){status=error.getMessage();}});addRenderableWidget(box);
        }
        private void number(String title,float value,int x,int y,int w,Consumer<Float> setter) {
            field(title,String.format(Locale.ROOT,"%.4f",value),x,y,w,text->{float number=Float.parseFloat(text);if(!Float.isFinite(number))throw new IllegalArgumentException("Finite number required");setter.accept(number);});
        }
        private void update(CharacterVisualConfig next) {visual=next;store();updatePreview();previewDelay=4;}
        private void profile(CharacterProfile next) {
            var equipment=visual.equipment();var defaults=CharacterEquipmentDefinition.defaults(next);
            equipment=equipment.withHands(equipment.rightHand()==null?defaults.rightHand():equipment.rightHand(),equipment.leftHand()==null?defaults.leftHand():equipment.leftHand());
            update(new CharacterVisualConfig(next,equipment,visual.transform(),visual.modelHash(),visual.rig(),visual.physics(),visual.options()));
        }
        private CharacterProfile copy(double scale,float yaw,Map<String,CharacterProfile.HumanoidBone> bones,Map<String,String> actions) {
            var p=visual.profile();return new CharacterProfile(p.model(),scale,bones,actions,p.physicsDefinition(),p.blendSeconds(),p.movementThreshold(),p.headLook(),p.solverDefinition(),yaw,p.equipmentDefinition(),p.timing());
        }
        private void rootFields(int x,int y,int w) {
            label(profileId,x,y);y+=23;
            number("Model scale",(float)visual.profile().scale(),x,y,(w-6)/2,v->profile(copy(v,visual.profile().modelYawDegrees(),visual.profile().humanoid(),visual.profile().animations())));
            number("Forward yaw",visual.profile().modelYawDegrees(),x+(w+6)/2,y,(w-6)/2,v->profile(copy(visual.profile().scale(),v,visual.profile().humanoid(),visual.profile().animations())));y+=45;
            float[] values=rootValues(visual.transform());String[] names={"Position X","Y","Z","Rotation X","Y","Z","Scale X","Y","Z"};
            for(int i=0;i<9;i++) {int index=i;number(names[i],values[i],x+(i%3)*(w+3)/3,y+(i/3)*43,(w-6)/3,v->{var a=rootValues(visual.transform());a[index]=v;
                update(new CharacterVisualConfig(visual.profile(),visual.equipment(),new CharacterVisualConfig.Transform(a[0],a[1],a[2],a[3],a[4],a[5],a[6],a[7],a[8]),visual.modelHash(),visual.rig(),visual.physics(),visual.options()));});}
        }
        private void selector(String title,int index,String[] values,int x,int y,int w,Consumer<Integer> select) {
            label(title,x,y);addRenderableWidget(FlatUi.icon(x,y+13,FlatUi.Icon.PREVIOUS,Component.literal("Previous "+title),b->{select.accept(Math.floorMod(index-1,values.length));rebuildWidgets();}));
            label(values[index],x+34,y+21);addRenderableWidget(FlatUi.icon(x+w-26,y+13,FlatUi.Icon.NEXT,Component.literal("Next "+title),b->{select.accept((index+1)%values.length);rebuildWidgets();}));
        }
        private void rigFields(int x,int y,int w) {
            selector("Humanoid role",role,ROLES,x,y,w,i->role=i);y+=47;String name=ROLES[role];var bone=visual.profile().bone(name);
            field("Bone node",bone==null?"":bone.node(),x,y,w,text->{
                var bones=new LinkedHashMap<>(visual.profile().humanoid());
                if(text.isBlank())bones.remove(name);else {if(!nodes.contains(text))throw new IllegalArgumentException("Unknown model bone");var current=bones.get(name);bones.put(name,new CharacterProfile.HumanoidBone(text,current==null?CharacterProfile.Rotation.IDENTITY:current.referenceRotation(),current==null?CharacterProfile.Rotation.IDENTITY:current.nativeRotation()));}
                profile(copy(visual.profile().scale(),visual.profile().modelYawDegrees(),bones,visual.profile().animations()));
            });y+=43;
            if(bone==null)return;
            for(int kind=0;kind<2;kind++) {
                int target=kind;Vector3f euler=(kind==0?bone.referenceRotation():bone.nativeRotation()).quaternion().getEulerAnglesXYZ(new Vector3f()).mul((float)(180/Math.PI));
                label(kind==0?"Reference calibration (degrees)":"Native pose calibration (degrees)",x,y);y+=13;
                for(int axis=0;axis<3;axis++) {int component=axis;number("XYZ".substring(axis,axis+1),euler.get(axis),x+axis*(w+3)/3,y,(w-6)/3,v->{
                    var current=visual.profile().bone(name);var angles=(target==0?current.referenceRotation():current.nativeRotation()).quaternion().getEulerAnglesXYZ(new Vector3f());angles.setComponent(component,(float)Math.toRadians(v));
                    var q=new Quaternionf().rotationXYZ(angles.x,angles.y,angles.z);var rotation=new CharacterProfile.Rotation(q.x,q.y,q.z,q.w);
                    var bones=new LinkedHashMap<>(visual.profile().humanoid());bones.put(name,new CharacterProfile.HumanoidBone(current.node(),target==0?rotation:current.referenceRotation(),target==1?rotation:current.nativeRotation()));
                    profile(copy(visual.profile().scale(),visual.profile().modelYawDegrees(),bones,visual.profile().animations()));
                });}y+=53;
            }
        }
        private void actionFields(int x,int y,int w) {
            selector("Action",action,ACTIONS,x,y,w,i->action=i);y+=47;String intent=ACTIONS[action];
            field("Animation clip",visual.profile().animations().getOrDefault(intent,""),x,y,w,text->{
                var actions=new LinkedHashMap<>(visual.profile().animations());
                if(text.isBlank())actions.remove(intent);else {if(!clips.contains(text))throw new IllegalArgumentException("Unknown animation clip");actions.put(intent,text);}
                profile(copy(visual.profile().scale(),visual.profile().modelYawDegrees(),visual.profile().humanoid(),actions));clock=0;
            });y+=47;
            addRenderableWidget(FlatUi.toggle(x,y,w,24,Component.literal("Playback"),()->playing,b->playing=!playing));y+=33;
            addRenderableWidget(FlatUi.button(x,y,w,24,Component.literal("Next clip"),b->{if(clips.isEmpty())return;var actions=new LinkedHashMap<>(visual.profile().animations());int i=clips.indexOf(actions.get(intent));actions.put(intent,clips.get((i+1)%clips.size()));profile(copy(visual.profile().scale(),visual.profile().modelYawDegrees(),visual.profile().humanoid(),actions));clock=0;rebuildWidgets();}));
        }
        private void sharingFields(int x,int y,int w) {
            addRenderableWidget(FlatUi.toggle(x,y,w,24,Component.literal("Upload configurations"),CharacterVisualSharing::uploadEnabled,b->CharacterVisualSharing.uploadEnabled(!CharacterVisualSharing.uploadEnabled())));y+=32;
            addRenderableWidget(FlatUi.toggle(x,y,w,24,Component.literal("Display other characters"),CharacterVisualSharing::remoteEnabled,b->CharacterVisualSharing.remoteEnabled(!CharacterVisualSharing.remoteEnabled())));y+=40;
            String[] names={"Hand contacts","Rig physics","Ground placement","Character effects"};
            for(int i=0;i<4;i++){int index=i;addRenderableWidget(FlatUi.toggle(x,y,w,24,Component.literal(names[i]),()->options()[index],b->{var a=options();a[index]=!a[index];update(visual.withOptions(new CharacterVisualConfig.Options(a[0],a[1],a[2],a[3])));}));y+=30;}
            addRenderableWidget(FlatUi.button(x,y,w,24,Component.literal("Share saved configuration"),b->{save(true);CharacterVisualSharing.share(profileId,visual);}));
        }
        private boolean[] options(){var o=visual.options();return new boolean[]{o.handContacts(),o.physics(),o.groundPlacement(),o.effects()};}
        private void store(){var value=new JsonObject();value.addProperty("profileId",profileId);value.add("visual",CharacterVisualConfig.parseBounded(visual.json()));context.session().edit(document->document.withExtension(KEY,value));}
        private void save(boolean apply) {
            try {store();context.session().commitTransaction();if(apply)activate(profileId,visual);else CharacterVisualSharing.save(profileId,visual);status=apply?"Character applied":"Character configuration saved";}
            catch(IOException|RuntimeException error){status=error.getMessage();}
        }
        private void updatePreview() {
            var root=visual.transform();float scale=(float)visual.profile().scale();
            preview=context.session().document().withTransform(new ModelTransform(root.x(),root.y(),root.z(),root.rx(),root.ry()+visual.profile().modelYawDegrees(),root.rz(),root.sx()*scale,root.sy()*scale,root.sz()*scale));
            String clip=page==Page.ACTIONS?visual.profile().animations().getOrDefault(ACTIONS[action],""):preview.animation().clip();
            preview=preview.withAnimation(new WorkbenchDocument.AnimationPreview(clip,clock,1,true,playing));
        }
        private void fit(){float scale=visual==null?1:(float)visual.profile().scale();camera.fit(data.radius()*scale,(float)Math.max(1,width-panel)/Math.max(1,height-64));}
        @Override public void tick(){super.tick();if(previewDelay>0&&--previewDelay==0&&LocalPlayerCharacter.settings().profile().equals(profileId))CharacterVisualSharing.preview(profileId,visual);}
        @Override public void render(GuiGraphics graphics,int mx,int my,float partial) {
            graphics.fill(0,0,width,height,FlatUi.BACKGROUND);graphics.fill(width-panel,0,width,height,FlatUi.SURFACE);
            graphics.drawString(font,"Character Workbench",124,16,FlatUi.TEXT,false);
            if(ready)try {
                long now=System.nanoTime();if(playing)clock+=Math.min(.1,(now-lastFrame)*1e-9);lastFrame=now;updatePreview();pose.sample(preview,clock);
                graphics.flush();if(renderer==null)renderer=new PreviewGpuRenderer(context.scene(),data);var window=minecraft.getWindow();double scale=window.getGuiScale();
                Vector3f focus=preview.transform().matrix().transformPosition(data.center(),new Vector3f());
                renderer.render(camera,(int)(8*scale),(int)(window.getHeight()-(height-24)*scale),(int)(Math.max(1,width-panel-16)*scale),(int)(Math.max(1,height-66)*scale),preview,pose,focus,-1);
            }catch(Exception|LinkageError error){ready=false;status="Preview: "+error.getMessage();AssimpLoader.LOGGER.warn("Character workbench preview failed",error);}
            graphics.enableScissor(width-panel,73,width,height-82);
            for(var label:labels)graphics.drawString(font,FlatUi.clip(font,label.text,panel-16),label.x,label.y-scroll,FlatUi.MUTED,false);
            graphics.disableScissor();
            String message=status.isEmpty()?CharacterVisualSharing.status():status;graphics.fill(0,height-20,width,height,FlatUi.FIELD);graphics.drawString(font,FlatUi.clip(font,message,width-16),8,height-14,FlatUi.MUTED,false);
            super.render(graphics,mx,my,partial);
        }
        private boolean viewport(double x,double y){return x>=8&&x<width-panel-8&&y>=42&&y<height-24;}
        @Override public boolean mouseClicked(double x,double y,int button){if(viewport(x,y)&&(button==0||button==1||button==2)){drag=button;return true;}return super.mouseClicked(x,y,button);}
        @Override public boolean mouseDragged(double x,double y,int button,double dx,double dy){if(drag>=0){if(drag==0&&!hasShiftDown())camera.orbit(dx,dy);else camera.pan(dx,dy,height-66);return true;}return super.mouseDragged(x,y,button,dx,dy);}
        @Override public boolean mouseReleased(double x,double y,int button){if(drag>=0){drag=-1;return true;}return super.mouseReleased(x,y,button);}
        @Override public boolean mouseScrolled(double x,double y,double delta){if(viewport(x,y)){if(hasShiftDown())camera.pan(0,delta*12,height-66);else camera.zoom(delta);return true;}if(x>=width-panel&&y>=73&&y<height-82){scroll=(int)Math.max(0,Math.min(maxScroll,scroll-delta*26));rebuildWidgets();return true;}return super.mouseScrolled(x,y,delta);}
        @Override public void onClose(){context.session().commitTransaction();minecraft.setScreen(context.parent());}
        @Override public void removed(){if(renderer!=null){renderer.close();renderer=null;}}
        @Override public boolean isPauseScreen(){return false;}
    }
    private static float[] rootValues(CharacterVisualConfig.Transform t){return new float[]{t.x(),t.y(),t.z(),t.rx(),t.ry(),t.rz(),t.sx(),t.sy(),t.sz()};}
    private static String suggest(List<String> names,String role) {
        String normalized=role.replace("_","");
        return names.stream().filter(name->name.toLowerCase(Locale.ROOT).replaceAll("[^a-z0-9]","").equals(normalized)).findFirst().orElse(null);
    }
}
