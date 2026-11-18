package cn.minerealms.rigworks.character.client;

import cn.minerealms.assimploader.AssimpLoader;
import cn.minerealms.assimploader.workbench.client.ModelWorkbenchScreen;
import cn.minerealms.assimploader.workbench.client.WorkbenchExtensions;
import cn.minerealms.assimploader.workbench.io.WorkbenchCodec;
import cn.minerealms.rigworks.RigWorks;
import cn.minerealms.rigworks.character.core.CharacterProfile;
import cn.minerealms.rigworks.character.core.CharacterVisualConfig;
import cn.minerealms.rigworks.character.core.PlayerAnimationController;
import cn.minerealms.rigworks.character.core.PlayerVisualInput;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonParser;
import net.minecraft.client.Minecraft;
import net.minecraft.client.Screenshot;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.gui.screens.TitleScreen;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.loading.FMLPaths;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.attribute.FileTime;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.concurrent.CompletableFuture;

/** Opt-in disposable-world checks for the real extension Save/Apply/open/reload paths. */
@Mod.EventBusSubscriber(modid=RigWorks.MODID,value=Dist.CLIENT)
public final class WorkbenchPersistenceSmokeTest {
    private static final boolean ENABLED=Boolean.getBoolean("rigworkspersistence.enabled");
    private static final long START=System.currentTimeMillis();
    private static final Map<String,Object> RESULT=new LinkedHashMap<>();
    private static boolean opening,done,importing;
    private static int stage;
    private static long changed;
    private static String profile,script;
    private static CharacterVisualConfig original;
    private static ModelWorkbenchScreen workbench;
    private static Screen editor;
    private static Path project;
    private static CompletableFuture<?> saving;
    private WorkbenchPersistenceSmokeTest(){}

    @SubscribeEvent public static void tick(TickEvent.ClientTickEvent event) {
        if(!ENABLED||done||event.phase!=TickEvent.Phase.END)return;
        var mc=Minecraft.getInstance();
        try {
            require(System.currentTimeMillis()-START<300_000,"Persistence acceptance timed out at stage "+stage);
            if(mc.player==null||mc.level==null) {
                if(!opening&&mc.screen instanceof TitleScreen&&mc.getOverlay()==null) {
                    opening=true;mc.createWorldOpenFlows().loadLevel(new TitleScreen(),System.getProperty("rigworkspersistence.world"));
                }
                return;
            }
            if(mc.getOverlay()!=null||LocalPlayerCharacter.diagnostics().status()!=LocalPlayerCharacter.Status.READY)return;
            if(stage==0) {
                if(mc.screen!=null)return;
                original=LocalPlayerCharacter.currentVisualConfig();profile=LocalPlayerCharacter.settings().profile();script=CharacterScriptRuntime.active();
                require(!original.profile().humanoid().isEmpty(),"Acceptance requires an authored character rig");
                require(script!=null,"Acceptance requires the original KubeJS controller");
                mc.options.pauseOnLostFocus=false;
                workbench=new ModelWorkbenchScreen();mc.setScreen(workbench);advance();return;
            }
            if(workbench.busy()||System.currentTimeMillis()-changed<750)return;
            if(stage==1) {
                if(workbench.session()==null) {
                    if(!importing){importing=true;workbench.importPath(Path.of(System.getProperty("rigworkspersistence.asset")));}
                    return;
                }
                openEditor(mc);advance();return;
            }
            if(!Boolean.TRUE.equals(field(editor,"ready")))return;
            if(stage==2) {
                var imported=visual();
                require(profile.equals(field(editor,"profileId")),"Copied FBX lost original character identity");
                require(imported.equals(original),"Copied FBX lost authored rig/action/equipment configuration");
                RESULT.put("inheritedOriginalConfiguration",true);
                update(scale(imported,1.2));saving=save();advance();return;
            }
            if(stage==3) {
                if(!saved())return;
                require(!workbench.session().dirty(),"Character Save did not persist the owning project");
                require(close(LocalPlayerCharacter.currentVisualConfig().profile().scale(),1.2),"Apply did not resize the character");
                verifyScript(mc);
                project=workbench.projectPath();require(Files.isRegularFile(project),"Project was not written");
                editor.onClose();workbench.open(project);advance();return;
            }
            if(stage==4) {
                openEditor(mc);advance();return;
            }
            if(stage==5) {
                require(close(visual().profile().scale(),1.2),"Reopened editor lost saved scale");
                RESULT.put("saveApplyReopenScale",visual().profile().scale());
                var file=characterFile();Files.writeString(file,scale(visual(),1.4).json(),StandardCharsets.UTF_8);
                Files.setLastModifiedTime(file,FileTime.fromMillis(Math.max(System.currentTimeMillis(),Files.getLastModifiedTime(project).toMillis()+2000)));
                LocalPlayerCharacter.reloadSaved();advance();return;
            }
            if(stage==6) {
                require(close(LocalPlayerCharacter.currentVisualConfig().profile().scale(),1.4),"Reload retained cached character JSON");
                editor.onClose();openEditor(mc);advance();return;
            }
            if(stage==7) {
                require(close(visual().profile().scale(),1.4),"Newer character file was masked by an older project snapshot");
                RESULT.put("manualCharacterJsonReloadScale",visual().profile().scale());
                editor.onClose();
                require(workbench.session().dirty(),"Newer character configuration was not staged into the owning project");
                workbench.session().revert();
                var document=WorkbenchCodec.read(Files.readString(project));var ext=document.extensions().getAsJsonObject("rigworks");
                ext.add("visual",JsonParser.parseString(scale(visual(),1.6).json()));
                Files.writeString(project,WorkbenchCodec.write(document.withExtension("rigworks",ext)));
                Files.setLastModifiedTime(project,FileTime.fromMillis(Files.getLastModifiedTime(characterFile()).toMillis()+2000));
                workbench.open(project);advance();return;
            }
            if(stage==8) {openEditor(mc);advance();return;}
            if(stage==9) {
                require(close(visual().profile().scale(),1.6),"Manual project edit was hidden by older cached configuration");
                saving=save();advance();return;
            }
            if(stage==10) {
                if(!saved())return;
                require(close(LocalPlayerCharacter.currentVisualConfig().profile().scale(),1.6),"Newest project was not applied");
                verifyScript(mc);RESULT.put("manualProjectReopenScale",visual().profile().scale());
                require(WorkbenchCodec.read(Files.readString(project)).equals(workbench.session().document()),"Disk project differs from the saved editor document");
                RESULT.put("diskEqualsSavedEditor",true);RESULT.put("project",project.toString());
                RESULT.put("profile",profile);RESULT.put("controller",script);
                advance();return;
            }
            if(stage==11) {
                var statuses=workbench.scene().getMaterials().stream()
                        .map(material->cn.minerealms.assimploader.render.ModelTextureCache.status(workbench.scene(),material)).toList();
                require(statuses.stream().noneMatch(cn.minerealms.assimploader.render.ModelTextureCache.TextureStatus::failed),"Preview texture preparation failed");
                if(statuses.stream().anyMatch(status->!status.ready()))return;
                var renderer=(cn.minerealms.assimploader.lookdev.preview.PreviewGpuRenderer)field(editor,"renderer");
                if(renderer==null||renderer.pbrTextureCount()<10)return;
                RESULT.put("previewPbrTextureCount",renderer.pbrTextureCount());RESULT.put("texturesReady",true);
                Screenshot.grab(mc.gameDirectory,"workbench-persistence.png",mc.getMainRenderTarget(),message->{});
                finish(mc,null);
            }
        }catch(Exception|LinkageError error){finish(mc,error);}
    }
    private static void openEditor(Minecraft mc) {
        var context=new WorkbenchExtensions.Context(workbench.scene(),workbench.session(),workbench);
        var extension=WorkbenchExtensions.available(context).stream().filter(value->value.id().equals("rigworks")).findFirst().orElseThrow();
        editor=extension.open(context);mc.setScreen(editor);
    }
    private static CharacterVisualConfig visual()throws Exception{return (CharacterVisualConfig)field(editor,"visual");}
    private static Object field(Object value,String name)throws Exception {
        var field=value.getClass().getDeclaredField(name);field.setAccessible(true);return field.get(value);
    }
    private static void update(CharacterVisualConfig config)throws Exception {
        var method=editor.getClass().getDeclaredMethod("update",CharacterVisualConfig.class);method.setAccessible(true);method.invoke(editor,config);
    }
    private static CompletableFuture<?> save()throws Exception {
        var method=editor.getClass().getDeclaredMethod("save",boolean.class);method.setAccessible(true);return (CompletableFuture<?>)method.invoke(editor,true);
    }
    private static boolean saved(){if(!saving.isDone())return false;saving.join();return true;}
    private static CharacterVisualConfig scale(CharacterVisualConfig config,double size) {
        var p=config.profile();var updated=new CharacterProfile(p.model(),size,p.humanoid(),p.animations(),p.physicsDefinition(),
                p.blendSeconds(),p.movementThreshold(),p.headLook(),p.solverDefinition(),p.modelYawDegrees(),p.equipmentDefinition(),p.timing());
        return new CharacterVisualConfig(updated,config.equipment(),config.transform(),config.modelHash(),config.rig(),config.physics(),config.options());
    }
    private static Path characterFile(){return FMLPaths.CONFIGDIR.get().resolve("rigworks/characters/"+CharacterVisualConfig.hash(profile.getBytes(StandardCharsets.UTF_8))+".json");}
    private static boolean close(double value,double expected){return Math.abs(value-expected)<.0001;}
    @SuppressWarnings("unchecked")
    private static void verifyScript(Minecraft mc)throws Exception {
        require(profile.equals(LocalPlayerCharacter.settings().profile()),"Apply switched to a generated profile");
        require(script.equals(CharacterScriptRuntime.active()),"Apply lost the active KubeJS controller");
        var bindingField=LocalPlayerCharacter.class.getDeclaredField("active");bindingField.setAccessible(true);var binding=bindingField.get(null);
        var session=(CharacterScriptRuntime.Session)field(binding,"scripts");
        var controller=(PlayerAnimationController)field(binding,"controller");
        var config=LocalPlayerCharacter.currentVisualConfig();
        for(int i=0;i<3;i++) {
            String intent=i==0?"walk":i==1?"run":"idle";
            var input=PlayerVisualInput.builder(10_000+i,500+i*.05).motion(i==2?0:4.3,0,0).sprinting(i==1).build();
            require(session.run(mc.player,config.profile(),controller,input),"Original script did not accept input");
            var state=(Map<String,Object>)field(session,"state");
            require(intent.equals(String.valueOf(state.get("lusia:intent"))),"Script ignored motion input: "+intent);
        }
        require(CharacterScriptRuntime.failure().isEmpty(),"Script failed: "+CharacterScriptRuntime.failure());
        RESULT.put("syntheticScriptWalkRunIdle",true);
    }
    private static void advance(){stage++;changed=System.currentTimeMillis();}
    private static void finish(Minecraft mc,Throwable error) {
        done=true;RESULT.put("passed",error==null);RESULT.put("failure",error==null?"":error.toString());RESULT.put("stage",stage);RESULT.put("clientLeftOpen",true);
        try{Files.writeString(mc.gameDirectory.toPath().resolve("workbench-persistence-result.json"),new GsonBuilder().setPrettyPrinting().create().toJson(RESULT));}
        catch(Exception writeError){AssimpLoader.LOGGER.error("Cannot write persistence acceptance",writeError);}
        if(error!=null)AssimpLoader.LOGGER.error("[WorkbenchPersistence] FAILED stage="+stage,error);
        else AssimpLoader.LOGGER.info("[WorkbenchPersistence] PASS; client remains open");
    }
    private static void require(boolean value,String message){if(!value)throw new IllegalStateException(message);}
}
