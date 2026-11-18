package cn.minerealms.assimploader.client;

import cn.minerealms.assimploader.AssimpLoader;
import cn.minerealms.assimploader.workbench.client.ModelWorkbenchScreen;
import cn.minerealms.assimploader.workbench.client.WorkbenchExtensions;
import cn.minerealms.assimploader.workbench.model.ModelTransform;
import cn.minerealms.assimploader.workbench.model.WorkbenchDocument;
import com.google.gson.GsonBuilder;
import net.minecraft.client.Minecraft;
import net.minecraft.client.Screenshot;
import net.minecraft.client.gui.components.AbstractWidget;
import net.minecraft.client.gui.screens.TitleScreen;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import org.lwjgl.opengl.GL11;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.Map;

/** Explicit disposable-project acceptance; leaves the game and final editor open. */
@Mod.EventBusSubscriber(modid=AssimpLoader.MODID,value=Dist.CLIENT)
public final class WorkbenchSmokeTest {
    private static final boolean ENABLED=Boolean.getBoolean("workbenchtest.enabled");
    private static final long START=System.currentTimeMillis();
    private static final Map<String,Object> RESULT=new LinkedHashMap<>();
    private static final ArrayList<String> SHOTS=new ArrayList<>();
    private static ModelWorkbenchScreen screen;
    private static boolean done,opening,pending;
    private static int stage;
    private static long changed=START;
    private static long acceptanceStarted;
    private WorkbenchSmokeTest(){}

    @SubscribeEvent public static void tick(TickEvent.ClientTickEvent event) {
        if(!ENABLED||done||pending||event.phase!=TickEvent.Phase.END)return;
        var mc=Minecraft.getInstance();
        try {
            long timeout=Boolean.getBoolean("workbenchtest.afterSync")?900_000:240_000;
            require(System.currentTimeMillis()-START<timeout,"Workbench acceptance timed out waiting for world/synchronization");
            if(acceptanceStarted!=0)require(System.currentTimeMillis()-acceptanceStarted<240_000,"Workbench acceptance timed out at stage "+stage);
            if(mc.player==null||mc.level==null) {
                if(!opening&&mc.screen instanceof TitleScreen&&mc.getOverlay()==null) {
                    opening=true;mc.createWorldOpenFlows().loadLevel(new TitleScreen(),System.getProperty("workbenchtest.world"));
                }
                return;
            }
            if(mc.getOverlay()!=null)return;
            if(Boolean.getBoolean("workbenchtest.afterSync")&&!Files.isRegularFile(mc.gameDirectory.toPath().resolve("character-sync-smoke-owner-result.json")))return;
            if(stage==0) {
                if(mc.screen!=null)return;
                acceptanceStarted=System.currentTimeMillis();
                mc.options.pauseOnLostFocus=false;mc.options.guiScale().set(2);mc.resizeDisplay();
                screen=new ModelWorkbenchScreen();mc.setScreen(screen);stage=1;changed=System.currentTimeMillis();return;
            }
            if(screen.busy())return;
            if(stage==1) {
                screen.importPath(Path.of(System.getProperty("workbenchtest.asset")));stage=2;changed=System.currentTimeMillis();return;
            }
            if(screen.session()==null)return;
            require(!screen.previewFailed(),"Workbench GPU preview failed");
            require(GL11.glGetError()==GL11.GL_NO_ERROR,"Workbench left an OpenGL error");
            if(System.currentTimeMillis()-changed<2500)return;
            if(stage==2) {
                if(!screen.previewRendered())return;
                var textures=screen.scene().getMaterials().stream().map(material->cn.minerealms.assimploader.render.ModelTextureCache.status(screen.scene(),material)).toList();
                RESULT.put("materials",textures);
                require(textures.stream().noneMatch(cn.minerealms.assimploader.render.ModelTextureCache.TextureStatus::failed),"Workbench material preparation failed");
                if(textures.stream().anyMatch(status->!status.ready()))return;
                int expectedMaps=textures.stream().mapToInt(status->(status.normalImage()?1:0)+(status.specularImage()?1:0)).sum();
                RESULT.put("previewPbrTextureCount",screen.previewPbrTextureCount());
                if(screen.previewPbrTextureCount()<expectedMaps)return;
                var session=screen.session();var original=session.document();
                session.edit(d->d.withTransform(ModelTransform.IDENTITY.withScale(.75f,.75f,.75f)));
                require(session.canUndo(),"Undo unavailable");session.undo();
                require(session.document().equals(original),"Undo did not restore document");
                require(session.canRedo(),"Redo unavailable");session.redo();
                session.revert();require(!session.dirty(),"Revert left draft dirty");
                var material=screen.scene().getMaterials().get(0);float originalRough=material.getPbrInputs().roughnessFactor();
                var edit=original.materials().get(0);
                session.edit(d->d.withMaterial(0,edit.withSurface(.65f,.23f,1.5f)));
                require(material.getPbrInputs().roughnessFactor()==originalRough,"Editing modified imported source material");
                session.edit(d->d.withSocket("acceptance",new WorkbenchDocument.Socket(screen.scene().getRootNode().getName(),ModelTransform.IDENTITY)));
                var selection=ModelWorkbenchScreen.class.getDeclaredField("selectedSocket");selection.setAccessible(true);selection.set(screen,"acceptance");
                var history=ModelWorkbenchScreen.class.getDeclaredMethod("history",boolean.class);history.setAccessible(true);
                history.invoke(screen,false);require(selection.get(screen).equals(""),"Undo left a deleted socket selected");
                history.invoke(screen,true);require(session.document().sockets().containsKey("acceptance"),"Redo did not restore socket");
                RESULT.put("socketUndoRedo",true);
                screen.save();stage=3;changed=System.currentTimeMillis();return;
            }
            if(stage==3) {
                require(!screen.session().dirty(),"Atomic project save did not mark saved revision");
                RESULT.put("triangles",screen.scene().getMeshes().stream().mapToInt(m->m.getIndices().length/3).sum());
                RESULT.put("skeleton",screen.scene().hasSkeleton());
                RESULT.put("project",screen.session().document().assetId());
                capture(mc,"desktop",()->{stage=4;mc.options.guiScale().set(3);mc.resizeDisplay();changed=System.currentTimeMillis();});return;
            }
            if(stage==4) {
                require(screen.previewRendered(),"Compact preview did not draw");
                for(var child:screen.children())if(child instanceof AbstractWidget w&&w.visible)
                    require(w.getX()>=0&&w.getY()>=0&&w.getX()+w.getWidth()<=screen.width&&w.getY()+w.getHeight()<=screen.height,"Visible widget outside compact screen: "+w.getMessage());
                capture(mc,"compact",()->{
                    mc.options.guiScale().set(2);mc.resizeDisplay();
                    var context=new WorkbenchExtensions.Context(screen.scene(),screen.session(),screen);
                    var extensions=WorkbenchExtensions.available(context);RESULT.put("extensions",extensions.stream().map(WorkbenchExtensions.Extension::id).toList());
                    if(!extensions.isEmpty()){mc.setScreen(extensions.get(0).open(context));stage=5;changed=System.currentTimeMillis();}
                    else finish(mc,true,"");
                });return;
            }
            if(stage==5) {
                var field=mc.screen.getClass().getDeclaredField("renderer");field.setAccessible(true);
                var preview=(cn.minerealms.assimploader.lookdev.preview.PreviewGpuRenderer)field.get(mc.screen);
                if(preview==null||preview.pbrTextureCount()<((Number)RESULT.get("previewPbrTextureCount")).intValue())return;
                RESULT.put("characterPreviewPbrTextureCount",preview.pbrTextureCount());
                capture(mc,"rigworks",()->finish(mc,true,""));
            }
        }catch(Exception|LinkageError error){AssimpLoader.LOGGER.error("[WorkbenchSmoke] stage="+stage,error);finish(mc,false,error.toString());}
    }
    private static void capture(Minecraft mc,String view,Runnable next) {
        pending=true;String name="workbench-"+START+"-"+view+".png";
        Screenshot.grab(mc.gameDirectory,name,mc.getMainRenderTarget(),ignored->mc.execute(()->{
            pending=false;SHOTS.add(name);next.run();
        }));
    }
    private static void finish(Minecraft mc,boolean pass,String failure) {
        done=true;RESULT.put("passed",pass);RESULT.put("failure",failure);RESULT.put("screenshots",SHOTS);
        try{Files.writeString(mc.gameDirectory.toPath().resolve("workbench-smoke-result.json"),new GsonBuilder().setPrettyPrinting().create().toJson(RESULT));}
        catch(Exception e){AssimpLoader.LOGGER.error("Cannot write workbench result",e);}
        AssimpLoader.LOGGER.info("[WorkbenchSmoke] {} {}",pass?"PASS":"FAIL",failure);
    }
    private static void require(boolean condition,String message){if(!condition)throw new IllegalStateException(message);}
}
