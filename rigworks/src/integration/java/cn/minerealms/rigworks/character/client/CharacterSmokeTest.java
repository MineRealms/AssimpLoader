package cn.minerealms.rigworks.character.client;

import cn.minerealms.assimploader.AssimpLoader;
import cn.minerealms.assimploader.animation.AnimationState;
import cn.minerealms.assimploader.character.physics.BulletCharacterPhysics;
import cn.minerealms.assimploader.render.ModelTextureCache;
import cn.minerealms.assimploader.rendering.CpuSkinning;
import net.minecraft.client.CameraType;
import net.minecraft.client.Minecraft;
import net.minecraft.client.KeyMapping;
import net.minecraft.client.Screenshot;
import net.minecraft.client.gui.screens.TitleScreen;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.HumanoidArm;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.eventbus.api.EventPriority;
import net.minecraftforge.fml.common.Mod;
import org.joml.Vector3f;
import org.lwjgl.opengl.GL11;

import java.nio.file.Files;

/** Explicit local-world acceptance. Every capture waits at least ten seconds after changing a stage. */
@Mod.EventBusSubscriber(modid=cn.minerealms.rigworks.RigWorks.MODID,value=Dist.CLIENT)
public final class CharacterSmokeTest {
    private static final boolean ENABLED=Boolean.getBoolean("charactertest.enabled");
    private static final String RUN=Long.toString(System.currentTimeMillis());
    private static final String[] STAGES={"idle","ik","expression","ragdoll","restored","reloaded",
            "equipment_right","equipment_left","motion","menu","crouch","rapid_swing","fallback"};
    private static int stage=-2;
    private static long changed=System.currentTimeMillis(),passes;
    private static boolean pending,prepared;
    private static AnimationState previous;
    private static BulletCharacterPhysics previousPhysics;
    private static LocalPlayerCharacter.Settings oldSettings;
    private static cn.minerealms.rigworks.RigWorksConfig.CharacterAnimationMode oldMode;
    private static CameraType oldCamera;
    private static boolean oldPause,oldGui;
    private static Vector3f standingHead;
    private static ItemStack oldMain,oldOff;
    private static HumanoidArm oldArm;
    private static int swingSamples,swingRestarts,lastSwingTime=-1;
    private CharacterSmokeTest(){}

    @SubscribeEvent(priority=EventPriority.HIGHEST)
    public static void controlInputs(TickEvent.ClientTickEvent event) {
        if(!ENABLED || stage<-1 || stage==99 || event.phase!=TickEvent.Phase.START)return;
        // END runs after Minecraft consumes attack clicks and the character samples the resulting swing.
        KeyMapping.releaseAll();
        var mc=Minecraft.getInstance();
        if(stage==10 && mc.player!=null)mc.options.keyShift.setDown(true);
        if(stage==11 && mc.player!=null)mc.player.swing(net.minecraft.world.InteractionHand.MAIN_HAND);
    }

    @SubscribeEvent public static void tick(TickEvent.ClientTickEvent event) {
        if(!ENABLED || stage==99 || event.phase!=TickEvent.Phase.END)return;
        var mc=Minecraft.getInstance();
        try {
            require(System.currentTimeMillis()-changed<180_000,"Timeout at "+stage+" "+LocalPlayerCharacter.diagnostics());
            if(stage==-2) {
                if(mc.screen instanceof TitleScreen){stage=-1;changed=System.currentTimeMillis();mc.createWorldOpenFlows().loadLevel(new TitleScreen(),System.getProperty("charactertest.world"));}
                return;
            }
            if(mc.player==null || mc.level==null || mc.screen!=null && !(stage==9 && mc.screen instanceof CharacterSelectionScreen) || mc.getOverlay()!=null)return;
            KeyMapping.releaseAll();
            if(stage==-1) {if(!pending)prepare(mc);return;}
            if(stage<0 || pending)return;
            if(stage==10)mc.options.keyShift.setDown(true);
            var diagnostics=LocalPlayerCharacter.diagnostics();
            if(stage==STAGES.length-1) {
                require(diagnostics.status()==LocalPlayerCharacter.Status.FAILED,"Missing profile must fail and restore vanilla");
                require(previous.isClosed() && previousPhysics.statistics().closed(),"Old character not released on failure");
            } else {
                require(diagnostics.status()!=LocalPlayerCharacter.Status.FAILED,diagnostics.toString());
                if(diagnostics.status()!=LocalPlayerCharacter.Status.READY)return;
                if(!prepared){prepareStage();prepared=true;changed=System.currentTimeMillis();}
                if(stage==11)checkSwingClock(mc);
                if(System.currentTimeMillis()-changed<10_500)return;
                verify(mc);
            }
            if(System.currentTimeMillis()-changed<10_500)return;
            require(GL11.glGetError()==GL11.GL_NO_ERROR,"OpenGL error");
            String name="assimp-character-"+RUN+"-"+STAGES[stage]+".png";
            pending=true;
            Screenshot.grab(mc.gameDirectory,name,mc.getMainRenderTarget(),message->mc.execute(()->{
                try {require(Files.isRegularFile(mc.gameDirectory.toPath().resolve("screenshots").resolve(name)),"Missing capture");advance(mc);}
                catch(Exception|LinkageError failure){fail(mc,failure);}
            }));
        } catch(Exception|LinkageError failure){fail(mc,failure);}
    }
    private static void prepare(Minecraft mc) {
        oldSettings=LocalPlayerCharacter.settings();oldCamera=mc.options.getCameraType();
        oldMode=LocalPlayerCharacter.animationMode();
        LocalPlayerCharacter.setAnimationMode(cn.minerealms.rigworks.RigWorksConfig.CharacterAnimationMode.CLIPS);
        oldPause=mc.options.pauseOnLostFocus;oldGui=mc.options.hideGui;
        mc.options.pauseOnLostFocus=false;mc.options.hideGui=true;mc.options.setCameraType(CameraType.THIRD_PERSON_FRONT);
        pending=true;var server=mc.getSingleplayerServer();require(server!=null,"Needs an integrated test world");
        var id=mc.player.getUUID();
        server.execute(()->{
            try {
                var player=server.getPlayerList().getPlayer(id);var level=player.serverLevel();
                var base=player.blockPosition().above(12);
                for(int x=-8;x<=8;x++)for(int z=-8;z<=8;z++) {
                    level.getChunk(base.offset(x,0,z));level.setBlock(base.offset(x,-1,z),Blocks.SMOOTH_STONE.defaultBlockState(),3);
                    for(int y=0;y<4;y++)level.setBlock(base.offset(x,y,z),Blocks.AIR.defaultBlockState(),3);
                }
                player.setGameMode(GameType.CREATIVE);player.stopRiding();player.getAbilities().flying=false;player.onUpdateAbilities();
                clearSwing(player);
                player.teleportTo(level,base.getX()+.5,base.getY(),base.getZ()+.5,0,0);
                level.setDayTime(6000);level.setWeatherParameters(12000,0,false,false);
                mc.execute(()->{
                    KeyMapping.releaseAll();clearSwing(mc.player);
                    LocalPlayerCharacter.configure(new LocalPlayerCharacter.Settings(true,LocalPlayerCharacter.DEFAULT_PROFILE,true,false));
                    stage=0;pending=false;changed=System.currentTimeMillis();
                });
            } catch(Exception|LinkageError failure){mc.execute(()->fail(mc,failure));}
        });
    }
    private static void prepareStage() {
        var state=LocalPlayerCharacter.currentAnimation();var physics=LocalPlayerCharacter.currentPhysics();
        require(state!=null && physics!=null,"Missing model/physics");
        passes=LocalPlayerCharacter.diagnostics().renderedPasses();
        if(stage==0) {
            var player=Minecraft.getInstance().player;
            standingHead=state.getPoseSnapshot().getNodeTransform("Head").getTranslation(new Vector3f());
        }
        if(stage==1)LocalPlayerCharacter.currentRig().ik().get("left_leg_probe").setEnabled(true);
        if(stage==2) {LocalPlayerCharacter.currentRig().ik().get("left_leg_probe").setEnabled(false);LocalPlayerCharacter.currentRig().morphs().setWeight("Expression",1);}
        if(stage==3) {
            var mc=Minecraft.getInstance();oldMain=mc.player.getMainHandItem().copy();oldOff=mc.player.getOffhandItem().copy();oldArm=mc.player.getMainArm();
            equip(mc,new ItemStack(Items.DIAMOND_SWORD),new ItemStack(Items.SHIELD));
            mc.player.setMainArm(HumanoidArm.RIGHT);
            LocalPlayerCharacter.currentRig().morphs().reset();LocalPlayerCharacter.setRagdollOverride(true);
        }
        if(stage==4)LocalPlayerCharacter.setRagdollOverride(false);
        if(stage==6) {
            Minecraft.getInstance().player.setMainArm(HumanoidArm.RIGHT);
            equip(Minecraft.getInstance(),new ItemStack(Items.DIAMOND_SWORD),new ItemStack(Items.SHIELD));
        }
        if(stage==7) {
            Minecraft.getInstance().player.setMainArm(HumanoidArm.LEFT);
            equip(Minecraft.getInstance(),new ItemStack(Items.STONE),new ItemStack(Items.SHIELD));
        }
        if(stage==8) {
            var motion=LocalPlayerCharacter.currentRig().motions();motion.play("solver_probe",false);motion.seek(1);motion.pause();
        }
        if(stage==9) {
            LocalPlayerCharacter.currentRig().motions().reset();
            Minecraft.getInstance().setScreen(new CharacterSelectionScreen(null));
        }
        if(stage==10) {
            Minecraft.getInstance().setScreen(null);
            Minecraft.getInstance().options.keyShift.setDown(true);
        }
        if(stage==11)Minecraft.getInstance().player.setMainArm(HumanoidArm.RIGHT);
    }
    private static void verify(Minecraft mc) {
        var state=LocalPlayerCharacter.currentAnimation();var physics=LocalPlayerCharacter.currentPhysics();var pose=state.getPoseSnapshot();
        require(LocalPlayerCharacter.diagnostics().renderedPasses()>passes,"Actual player renderer was not called");
        var dimensions=mc.player.getDimensions(mc.player.getPose());var physical=mc.player.getBoundingBox();
        require(Math.abs(physical.getXsize()-dimensions.width)<1e-6 && Math.abs(physical.getYsize()-dimensions.height)<1e-6,
                "Visual solver changed player dimensions: pose="+mc.player.getPose()+" expected="+dimensions+" actual="+physical);
        for(var material:state.getScene().getMaterials()) {
            var texture=ModelTextureCache.status(state.getScene(),material);
            require(texture.ready() && texture.normalImage() && texture.specularImage(),"PBR channels not ready: "+texture);
        }
        for(var mesh:state.getScene().getMeshes()) {
            var vertices=CpuSkinning.geometry(pose,mesh).positions();var bounds=pose.getBounds().inflate(.0001);
            for(int i=0;i<vertices.length;i+=3)require(Float.isFinite(vertices[i]) && bounds.contains(vertices[i],vertices[i+1],vertices[i+2]),"Deformed vertex escaped bounds");
        }
        require(physics.statistics().bodies()==17 && physics.statistics().joints()==16 && physics.statistics().worldBoxes()>0,"Incomplete physical fixture");
        if(stage==0)require("idle".equals(LocalPlayerCharacter.diagnostics().selectedAnimation()),
                "Idle fixture received a live action: "+LocalPlayerCharacter.diagnostics()+" swinging="+mc.player.swinging+" phase="+mc.player.attackAnim);
        if(stage==1)require(LocalPlayerCharacter.currentRig().ik().get("left_leg_probe").lastError()<.015,"IK error");
        if(stage==2)require(Math.abs(LocalPlayerCharacter.currentRig().morphs().expandedWeights().get("Smile")-.8)<1e-5,"Group expression weight");
        if(stage==3)require(physics.statistics().ragdoll() && pose.getNodeTransform("Head").m31()<standingHead.y-.3,"Ragdoll did not fall");
        if(stage==4)require(!physics.statistics().ragdoll() && Math.abs(pose.getNodeTransform("Hips").m31()-.85)<.08,"Animation restore failed");
        if(stage==5)require(previous.isClosed() && previousPhysics.statistics().closed(),"Reload leaked former character");
        if(stage==6)require(mc.player.getMainArm()==HumanoidArm.RIGHT && mc.player.getMainHandItem().is(Items.DIAMOND_SWORD),"Right-hand equipment fixture");
        if(stage==7)require(mc.player.getMainArm()==HumanoidArm.LEFT && mc.player.getMainHandItem().is(Items.STONE),"Left-hand equipment fixture");
        if(stage==8) {
            require(LocalPlayerCharacter.currentRig().motions().isPaused() && Math.abs(LocalPlayerCharacter.currentRig().motions().time()-1)<1e-6,"Motion seek/pause");
            require(Math.abs(LocalPlayerCharacter.currentRig().morphs().expandedWeights().get("Smile")-.8)<1e-5,"Motion Morph publication");
            require(LocalPlayerCharacter.currentRig().ik().get("left_leg_probe").isEnabled() && LocalPlayerCharacter.currentRig().ik().get("left_leg_probe").lastError()<.015,"Motion IK switch");
        }
        if(stage==9)require(mc.screen instanceof CharacterSelectionScreen,"Character menu did not open");
        if(stage==10) {
            require("crouch".equals(LocalPlayerCharacter.diagnostics().selectedAnimation()),"Crouch state not selected");
            for(String side:new String[]{"L","R"}) {
                var hip=pose.getNodeTransform("Thigh."+side).getTranslation(new Vector3f());
                var knee=pose.getNodeTransform("Shin."+side).getTranslation(new Vector3f());
                var ankle=pose.getNodeTransform("Foot."+side).getTranslation(new Vector3f());
                require(knee.z>hip.z+.08 && knee.z>ankle.z+.08,"Knee bent backward: "+side+" "+knee);
                require(Math.abs(ankle.y-.1)<.01,"Crouch foot not grounded: "+ankle);
            }
        }
        if(stage==11)require(swingSamples>=180 && swingRestarts>=20,"Insufficient rapid swing samples: "+swingSamples+" restarts="+swingRestarts);
        AssimpLoader.LOGGER.info("[CharacterSmoke] PASS run={} stage={} pose={} physics={} player={}",RUN,STAGES[stage],pose.getFrameId(),physics.statistics(),LocalPlayerCharacter.diagnostics());
    }
    private static void advance(Minecraft mc) {
        pending=false;prepared=false;
        if(stage==4 || stage==11){previous=LocalPlayerCharacter.currentAnimation();previousPhysics=LocalPlayerCharacter.currentPhysics();}
        stage++;changed=System.currentTimeMillis();
        if(stage==10)mc.setScreen(null);
        if(stage==5)LocalPlayerCharacter.reload();
        if(stage==STAGES.length-1) {
            mc.setScreen(null);
            LocalPlayerCharacter.configure(new LocalPlayerCharacter.Settings(true,"assimploader:missing-character-profile.json",true,false));
        }
        if(stage==STAGES.length) {
            restore(mc);writeResult(mc,true,"");stage=99;AssimpLoader.LOGGER.info("[CharacterSmoke] SUCCESS run={} {} actual-player stages with 10.5s settling",RUN,STAGES.length);mc.stop();
        }
    }
    private static void restore(Minecraft mc) {
        KeyMapping.releaseAll();
        if(oldMain!=null && mc.player!=null) {equip(mc,oldMain,oldOff);mc.player.setMainArm(oldArm);}
        if(oldSettings!=null)LocalPlayerCharacter.configure(oldSettings);
        if(oldMode!=null)LocalPlayerCharacter.setAnimationMode(oldMode);
        LocalPlayerCharacter.reload();
        if(oldCamera!=null)mc.options.setCameraType(oldCamera);
        mc.options.pauseOnLostFocus=oldPause;mc.options.hideGui=oldGui;
    }
    private static void equip(Minecraft mc,ItemStack main,ItemStack off) {
        var id=mc.player.getUUID();var server=mc.getSingleplayerServer();
        mc.player.setItemSlot(EquipmentSlot.MAINHAND,main.copy());mc.player.setItemSlot(EquipmentSlot.OFFHAND,off.copy());
        if(server!=null)server.execute(()->{
            var player=server.getPlayerList().getPlayer(id);
            if(player!=null) {player.setItemSlot(EquipmentSlot.MAINHAND,main.copy());player.setItemSlot(EquipmentSlot.OFFHAND,off.copy());}
        });
    }
    private static void clearSwing(LivingEntity player) {
        if(player==null)return;
        player.swinging=false;player.swingTime=0;player.attackAnim=0;player.oAttackAnim=0;
    }
    private static void checkSwingClock(Minecraft mc) {
        var clip=LocalPlayerCharacter.currentAnimation().getCurrentAnimation();
        require(mc.player.swinging && clip!=null && clip.hasSynchronizedPhase(),"Native swing not synchronized");
        float phase=mc.player.swingTime<=0?0:Math.max(0,Math.min(1,mc.player.attackAnim));
        double actual=clip.getAnimation().getDuration()>0?clip.getCurrentTime()/clip.getAnimation().getDuration():0;
        require(Math.abs(phase-actual)<1e-5,"Swing clock lag: player="+phase+" model="+actual);
        if(lastSwingTime<0 || mc.player.swingTime<lastSwingTime)swingRestarts++;
        lastSwingTime=mc.player.swingTime;swingSamples++;
    }
    private static void writeResult(Minecraft mc,boolean success,String failure) {
        var result=new com.google.gson.JsonObject();result.addProperty("run",RUN);result.addProperty("success",success);
        result.addProperty("stage",stage);result.addProperty("stages",STAGES.length);result.addProperty("failure",failure);
        try {Files.writeString(mc.gameDirectory.toPath().resolve("character-smoke-result.json"),result.toString());}
        catch(java.io.IOException exception){AssimpLoader.LOGGER.error("Cannot write character acceptance result",exception);}
    }
    private static void fail(Minecraft mc,Throwable failure) {writeResult(mc,false,failure.toString());stage=99;AssimpLoader.LOGGER.error("[CharacterSmoke] FAILED run="+RUN,failure);restore(mc);mc.stop();}
    private static void require(boolean condition,String message){if(!condition)throw new IllegalStateException(message);}
}
