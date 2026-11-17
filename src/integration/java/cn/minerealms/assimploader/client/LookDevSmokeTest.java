package cn.minerealms.assimploader.client;

import cn.minerealms.assimploader.AssimpLoader;
import cn.minerealms.assimploader.animation.AnimationState;
import cn.minerealms.rigworks.character.client.CharacterScriptRuntime;
import cn.minerealms.rigworks.character.client.LocalPlayerCharacter;
import cn.minerealms.assimploader.character.physics.BulletCharacterPhysics;
import cn.minerealms.assimploader.client.ui.LookDevNavigation;
import cn.minerealms.assimploader.config.AssimpLoaderConfig;
import cn.minerealms.assimploader.debug.MaterialDebugViews;
import cn.minerealms.assimploader.lookdev.ui.LoadedModelsScreen;
import cn.minerealms.assimploader.render.ModelTextureCache;
import net.minecraft.client.CameraType;
import net.minecraft.client.Minecraft;
import net.minecraft.client.Screenshot;
import net.minecraft.client.gui.components.AbstractWidget;
import net.minecraft.client.gui.screens.TitleScreen;
import net.minecraft.network.chat.Component;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.common.ForgeConfigSpec;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import org.lwjgl.opengl.GL11;

import java.nio.file.Files;
import java.util.LinkedHashMap;
import java.util.Map;

/** Explicit opt-in acceptance against a dedicated local world; never runs in normal play. */
@Mod.EventBusSubscriber(modid=AssimpLoader.MODID,value=Dist.CLIENT)
public final class LookDevSmokeTest {
    private static final boolean ENABLED=Boolean.getBoolean("lookdevtest.enabled");
    private static final String RUN=Long.toString(System.currentTimeMillis());
    private static final String[] STAGES=Boolean.getBoolean("lookdevtest.poseOnly")?new String[]{"vanilla_pose_probe"}:new String[]{"vanilla","physics_off","scripted","script_fallback","clips",
            "debug_hud","normal","ao","models","nodes","player","geometry","materials","rendering","hud",
            "compact_models","compact_nodes","compact_player","compact_geometry","compact_materials","compact_rendering","compact_hud"};
    private static int stage=-2,scriptTicks;
    private static long changed=System.currentTimeMillis(),passes;
    private static boolean done,pending,prepared;
    private static AnimationState oldOwner;
    private static BulletCharacterPhysics oldPhysics;
    private static LoadedModelsScreen modelScreen;
    private static LocalPlayerCharacter.Settings settings;
    private static cn.minerealms.rigworks.RigWorksConfig.CharacterAnimationMode mode;
    private static boolean physics,pause,hideGui,debugF3;
    private static CameraType camera;
    private static int scale;
    private static String controller;
    private static final Map<ForgeConfigSpec.ConfigValue<?>,Object> CONFIG=new LinkedHashMap<>();
    private static Map<String,Object> nativeChecks=Map.of();
    private LookDevSmokeTest(){}

    @SubscribeEvent public static void tick(TickEvent.ClientTickEvent event) {
        if(!ENABLED || done || event.phase!=TickEvent.Phase.END)return;
        var mc=Minecraft.getInstance();
        try {
            require(System.currentTimeMillis()-changed<180_000,"timeout");
            if(stage==-2) {
                if(mc.screen instanceof TitleScreen){stage=-1;changed=System.currentTimeMillis();mc.createWorldOpenFlows().loadLevel(new TitleScreen(),System.getProperty("lookdevtest.world"));}
                return;
            }
            if(mc.player==null || mc.level==null || mc.getOverlay()!=null)return;
            if(stage==-1) {if(mc.screen==null){save(mc);stage=0;}else return;}
            if(!prepared){prepare(mc);prepared=true;changed=System.currentTimeMillis();return;}
            if(pending || System.currentTimeMillis()-changed<10_500)return;
            var diagnostics=LocalPlayerCharacter.diagnostics();
            require(diagnostics.status()!=LocalPlayerCharacter.Status.FAILED,diagnostics.toString());
            if(diagnostics.status()!=LocalPlayerCharacter.Status.READY)return;
            verify(mc);
            pending=true;
            String name="assimp-lookdev-"+RUN+"-"+STAGES[stage]+".png";
            Screenshot.grab(mc.gameDirectory,name,mc.getMainRenderTarget(),message->mc.execute(()->{
                try {
                    require(Files.isRegularFile(mc.gameDirectory.toPath().resolve("screenshots").resolve(name)),"capture missing");
                    AssimpLoader.LOGGER.info("[LookDevSmoke] PASS {} {} {}x{}",RUN,STAGES[stage],mc.getWindow().getGuiScaledWidth(),mc.getWindow().getGuiScaledHeight());
                    oldOwner=LocalPlayerCharacter.currentAnimation();oldPhysics=LocalPlayerCharacter.currentPhysics();
                    stage++;pending=false;prepared=false;
                    if(stage==STAGES.length)finish(mc,true,"");
                }catch(Exception|LinkageError error){finish(mc,false,error.toString());}
            }));
        }catch(Exception|LinkageError error){AssimpLoader.LOGGER.error("[LookDevSmoke] stage="+stage,error);finish(mc,false,error.toString());}
    }
    private static void save(Minecraft mc) {
        settings=LocalPlayerCharacter.settings();mode=LocalPlayerCharacter.animationMode();physics=LocalPlayerCharacter.physicsEnabled();
        camera=mc.options.getCameraType();pause=mc.options.pauseOnLostFocus;hideGui=mc.options.hideGui;debugF3=mc.options.renderDebug;scale=mc.options.guiScale().get();
        controller=CharacterScriptRuntime.active();
        var d=AssimpLoaderConfig.CLIENT.debugView;
        for(var value:new ForgeConfigSpec.ConfigValue<?>[]{d.enabled,d.hud,d.bounds,d.skeleton,d.collision,d.worldCollision,d.axes,d.attachments,d.material})CONFIG.put(value,value.get());
        var character=cn.minerealms.rigworks.RigWorksConfig.CLIENT;
        CONFIG.put(character.enabled,character.enabled.get());CONFIG.put(character.profile,character.profile.get());
        character.enabled.set(true);character.profile.set(LocalPlayerCharacter.DEFAULT_PROFILE);character.enabled.save();
        mc.options.pauseOnLostFocus=false;mc.options.hideGui=false;mc.options.renderDebug=false;mc.options.setCameraType(CameraType.THIRD_PERSON_FRONT);
        mc.options.guiScale().set(2);mc.resizeDisplay();
        LocalPlayerCharacter.configure(new LocalPlayerCharacter.Settings(true,LocalPlayerCharacter.DEFAULT_PROFILE,true,false));
    }
    private static void prepare(Minecraft mc) {
        passes=LocalPlayerCharacter.diagnostics().renderedPasses();
        var d=AssimpLoaderConfig.CLIENT.debugView;
        switch(stage) {
            case 0 -> {LocalPlayerCharacter.setAnimationMode(cn.minerealms.rigworks.RigWorksConfig.CharacterAnimationMode.VANILLA);LocalPlayerCharacter.setPhysicsEnabled(true);}
            case 1 -> LocalPlayerCharacter.setPhysicsEnabled(false);
            case 2 -> {
                CharacterScriptRuntime.register("assimploader:acceptance",frame->{scriptTicks++;frame.defaultMotion();});
                LocalPlayerCharacter.setAnimationMode(cn.minerealms.rigworks.RigWorksConfig.CharacterAnimationMode.SCRIPTED);
            }
            case 3 -> CharacterScriptRuntime.register("assimploader:acceptance",frame->{throw new IllegalStateException("acceptance failure");});
            case 4 -> LocalPlayerCharacter.setAnimationMode(cn.minerealms.rigworks.RigWorksConfig.CharacterAnimationMode.CLIPS);
            case 5 -> {
                LocalPlayerCharacter.setAnimationMode(cn.minerealms.rigworks.RigWorksConfig.CharacterAnimationMode.VANILLA);LocalPlayerCharacter.setPhysicsEnabled(true);
                d.enabled.set(true);d.hud.set(true);d.bounds.set(true);d.skeleton.set(true);d.collision.set(true);d.worldCollision.set(true);d.axes.set(true);d.attachments.set(true);
            }
            case 6 -> d.material.set(AssimpLoaderConfig.MaterialView.NORMAL);
            case 7 -> d.material.set(AssimpLoaderConfig.MaterialView.AO);
            case 8 -> {
                d.material.set(AssimpLoaderConfig.MaterialView.DEFAULT);
                LookDevNavigation.open(LookDevNavigation.Page.MODELS);modelScreen=(LoadedModelsScreen)mc.screen;
                require(modelScreen.selectModel(LocalPlayerCharacter.currentProfile().model()),"model absent from catalog");
            }
            case 9,16 -> LookDevNavigation.open(LookDevNavigation.Page.NODES);
            case 10,17 -> LookDevNavigation.open(LookDevNavigation.Page.PLAYER);
            case 11,18 -> {LookDevNavigation.open(LookDevNavigation.Page.DEBUG);press(mc,"Geometry");}
            case 12,19 -> press(mc,"Materials");
            case 13,20 -> press(mc,"Rendering");
            case 14,21 -> press(mc,"HUD");
            case 15 -> {mc.options.guiScale().set(3);mc.resizeDisplay();LookDevNavigation.open(LookDevNavigation.Page.MODELS);}
            default -> throw new IllegalStateException("unknown stage");
        }
    }
    private static void verify(Minecraft mc) {
        require(GL11.glGetError()==GL11.GL_NO_ERROR,"OpenGL state error");
        var animation=LocalPlayerCharacter.currentAnimation();
        require(animation!=null && !animation.isClosed(),"closed active owner");
        if(stage<8)require(LocalPlayerCharacter.diagnostics().renderedPasses()>passes,"player replacement not rendered");
        if(stage==0 || stage==5) {
            var p=LocalPlayerCharacter.currentPhysics();
            require(p!=null && p.statistics().bodies()==17 && p.statistics().joints()==16,"physics not installed");
            require(!p.debugShapes(true).isEmpty(),"debug physical shapes empty");
            if(Boolean.getBoolean("lookdevtest.poseOnly"))nativeChecks=Map.of(
                    "player",cn.minerealms.rigworks.character.client.VanillaRuntimeProbe.verify(mc.player,LocalPlayerCharacter.currentProfile(),animation.getScene()),
                    "shaderParameters",ShaderParameterGpuChecks.verify());
        }
        if(stage==1)require(LocalPlayerCharacter.currentPhysics()==null && oldPhysics.statistics().closed() && oldOwner.isClosed(),"physics toggle leaked old owner");
        if(stage==2)require(scriptTicks>100,"script callback not invoked");
        if(stage==3)require(CharacterScriptRuntime.active()==null && CharacterScriptRuntime.failure().contains("acceptance failure"),"script did not fall back");
        if(stage==6 || stage==7) {
            require(MaterialDebugViews.active(),"material view disabled");
            for(var material:animation.getScene().getMaterials())require(ModelTextureCache.status(animation.getScene(),material).ready(),"texture pending");
        }
        if(stage==8 || stage==15)require(mc.screen==modelScreen && modelScreen.isPreviewRendered() && !modelScreen.isPreviewFailed(),"preview failed or workspace lost state");
        if(stage==9 || stage==16)require(mc.screen==modelScreen,"Nodes did not reuse model workspace");
        if(stage>=8) {
            var widgets=mc.screen.children().stream().filter(child->child instanceof AbstractWidget).map(child->(AbstractWidget)child).filter(w->w.visible).toList();
            for(var a:widgets) {
                require(a.getX()>=0 && a.getY()>=0 && a.getX()+a.getWidth()<=mc.screen.width && a.getY()+a.getHeight()<=mc.screen.height,"widget outside screen: "+a.getMessage().getString());
                for(var b:widgets)if(a!=b)require(a.getX()>=b.getX()+b.getWidth() || b.getX()>=a.getX()+a.getWidth() || a.getY()>=b.getY()+b.getHeight() || b.getY()>=a.getY()+a.getHeight(),"widgets overlap: "+a.getMessage().getString()+" / "+b.getMessage().getString());
            }
        }
    }
    private static void press(Minecraft mc,String label) {
        for(var child:mc.screen.children())if(child instanceof net.minecraft.client.gui.components.Button button && button.getMessage().equals(Component.literal(label))){button.onPress();return;}
        throw new IllegalStateException("missing tab "+label);
    }
    @SuppressWarnings({"rawtypes","unchecked"}) private static void restoreConfig(ForgeConfigSpec.ConfigValue value,Object old){value.set(old);value.save();}
    private static void finish(Minecraft mc,boolean success,String failure) {
        if(done)return;done=true;
        try {
            if(settings!=null) {
                CharacterScriptRuntime.unregister("assimploader:acceptance");
                if(controller!=null)CharacterScriptRuntime.use(controller);else CharacterScriptRuntime.useDefault();
                CONFIG.forEach(LookDevSmokeTest::restoreConfig);
                LocalPlayerCharacter.configure(settings);LocalPlayerCharacter.setAnimationMode(mode);LocalPlayerCharacter.setPhysicsEnabled(physics);
                mc.options.setCameraType(camera);mc.options.pauseOnLostFocus=pause;mc.options.hideGui=hideGui;mc.options.renderDebug=debugF3;mc.options.guiScale().set(scale);mc.resizeDisplay();
            }
            var result=new com.google.gson.JsonObject();result.addProperty("run",RUN);result.addProperty("success",success);result.addProperty("stage",stage);result.addProperty("stages",STAGES.length);result.addProperty("failure",failure);
            result.add("nativeChecks",new com.google.gson.Gson().toJsonTree(nativeChecks));
            Files.writeString(mc.gameDirectory.toPath().resolve("lookdev-smoke-result.json"),result.toString());
        }catch(Exception error){AssimpLoader.LOGGER.error("[LookDevSmoke] cleanup",error);}
        AssimpLoader.LOGGER.info("[LookDevSmoke] finished success={} stage={} failure={}",success,stage,failure);mc.stop();
    }
    private static void require(boolean value,String failure){if(!value)throw new IllegalStateException(failure);}
}
