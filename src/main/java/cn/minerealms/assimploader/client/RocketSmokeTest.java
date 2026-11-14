package cn.minerealms.assimploader.client;

import cn.minerealms.assimploader.AssimpLoader;
import cn.minerealms.assimploader.compat.OculusCompat;
import cn.minerealms.assimploader.core.AssimpModelLoader;
import cn.minerealms.assimploader.rocket.client.ExhaustProfiles;
import cn.minerealms.assimploader.rocket.client.RocketExhausts;
import cn.minerealms.assimploader.rocket.client.VanillaExhaustRenderer;
import cn.minerealms.assimploader.rocket.common.RocketEngineState;
import cn.minerealms.assimploader.showcase.ShowcaseBlockEntity;
import net.minecraft.client.CameraType;
import net.minecraft.client.Minecraft;
import net.minecraft.client.Screenshot;
import net.minecraft.client.gui.screens.TitleScreen;
import net.minecraft.core.BlockPos;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.GameRules;
import net.minecraft.world.level.GameType;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import org.lwjgl.BufferUtils;

import java.util.UUID;

import static org.lwjgl.opengl.GL33C.*;

/** Opt-in verification in a disposable world. Leaves the final test stand and client open. */
@Mod.EventBusSubscriber(modid = AssimpLoader.MODID, value = Dist.CLIENT)
public final class RocketSmokeTest {
    private static final boolean ENABLED = Boolean.getBoolean("rockettest.enabled");
    private static final String RUN = Long.toString(System.currentTimeMillis());
    private enum Phase { TITLE, WORLD, SETUP, PREPARING, FLOW_LOW, NOMINAL, CLOSEUP, NIGHT, OFF_AXIS,
        SHUTDOWN, COOLED, HYDROLOX, SOLID, VANILLA, RELOAD, RESTORED, DONE }
    private static Phase phase = Phase.TITLE;
    private static long changedAt = System.currentTimeMillis(), frames, captureFrame;
    private static boolean captureRequested, captureDone;
    private static BlockPos anchor;
    private static UUID player;
    private static double nominal;
    private static int captures;

    private RocketSmokeTest() {}

    @SubscribeEvent public static void tick(TickEvent.ClientTickEvent event) {
        if (!ENABLED || event.phase != TickEvent.Phase.END || phase == Phase.DONE) return;
        Minecraft mc = Minecraft.getInstance();
        try {
            if (System.currentTimeMillis()-changedAt>180_000) throw new IllegalStateException("timeout: "+phase);
            switch (phase) {
                case TITLE -> {
                    if (mc.screen instanceof TitleScreen && elapsed(3000)) {
                        String name=System.getProperty("rockettest.world");
                        if(name==null || name.isBlank()) throw new IllegalStateException("rockettest.world required");
                        mc.createWorldOpenFlows().loadLevel(new TitleScreen(),name); move(Phase.WORLD);
                    }
                }
                case WORLD -> { if(mc.level!=null && mc.player!=null) move(Phase.SETUP); }
                case SETUP -> setup(mc);
                case FLOW_LOW,NOMINAL,CLOSEUP,NIGHT,OFF_AXIS,SHUTDOWN,COOLED,HYDROLOX,SOLID,VANILLA,RESTORED -> {
                    if(!captureDone) return;
                    captureDone=false;
                    switch(phase) {
                        case FLOW_LOW -> { commandFlow(mc,-1,null,true); move(Phase.NOMINAL); }
                        case NOMINAL -> { camera(mc,true,false); move(Phase.CLOSEUP); }
                        case CLOSEUP -> {
                            var server=mc.getSingleplayerServer();
                            server.execute(()->server.getPlayerList().getPlayer(player).serverLevel().setDayTime(14000));
                            move(Phase.NIGHT);
                        }
                        case NIGHT -> { camera(mc,true,true); move(Phase.OFF_AXIS); }
                        case OFF_AXIS -> { commandFlow(mc,-1,null,false); camera(mc,false,false); move(Phase.SHUTDOWN); }
                        case SHUTDOWN -> move(Phase.COOLED);
                        case COOLED -> {
                            var server=mc.getSingleplayerServer();
                            server.execute(()->server.getPlayerList().getPlayer(player).serverLevel().setDayTime(11500));
                            commandFlow(mc,-1,"hydrolox",true); move(Phase.HYDROLOX);
                        }
                        case HYDROLOX -> { commandFlow(mc,-1,"solid",true); move(Phase.SOLID); }
                        case SOLID -> { commandFlow(mc,-1,"kerolox",true); shaders(mc,false); move(Phase.VANILLA); }
                        case VANILLA -> {
                            shaders(mc,true); move(Phase.RELOAD);
                            mc.reloadResourcePacks().whenComplete((ignored,error)->mc.execute(()-> {
                                if(error!=null) fail("resource reload",error); else move(Phase.RESTORED);
                            }));
                        }
                        case RESTORED -> {
                            camera(mc,true,false);
                            var server=mc.getSingleplayerServer();
                            server.execute(()->server.getPlayerList().getPlayer(player).serverLevel().setDayTime(14000));
                            mc.options.hideGui=false; move(Phase.DONE);
                            AssimpLoader.LOGGER.info("[RocketSmoke] SUCCESS anchor={} captures={} shaderUploads={} vanillaDraws={} prefix=rocket-{}; client remains open",
                                    anchor,captures,RocketExhausts.bridgeDraws(),VanillaExhaustRenderer.draws(),RUN);
                        }
                        default -> { }
                    }
                }
                default -> { }
            }
        } catch(Exception | LinkageError error) { fail("phase "+phase,error); }
    }

    @SubscribeEvent public static void frame(TickEvent.RenderTickEvent event) {
        if(!ENABLED || event.phase!=TickEvent.Phase.END || phase==Phase.DONE) return;
        frames++;
        if(phase.ordinal()<Phase.FLOW_LOW.ordinal() || phase==Phase.RELOAD || captureRequested) return;
        long delay=phase==Phase.SHUTDOWN?1100:phase==Phase.COOLED?7500:phase==Phase.CLOSEUP?12000:6500;
        if(!elapsed(delay) || frames-captureFrame<30) return;
        Minecraft mc=Minecraft.getInstance();
        if(mc.screen!=null || mc.getOverlay()!=null) return;
        try {
            if(!(mc.level.getBlockEntity(anchor) instanceof ShowcaseBlockEntity engine)) throw new IllegalStateException("engine missing");
            var flow=engine.getEngineState().interpolate(1);
            if(phase==Phase.FLOW_LOW && Math.abs(flow.massFlowKgS()/nominal-.35)>.06) throw new IllegalStateException("low-flow response");
            if(phase==Phase.NOMINAL && Math.abs(flow.massFlowKgS()/nominal-1)>.06) throw new IllegalStateException("nominal response");
            if(phase==Phase.COOLED && flow.massFlowKgS()>.001) throw new IllegalStateException("shutdown did not stop flow");
            if((phase==Phase.SHUTDOWN || phase==Phase.COOLED) && flow.nozzleWallTemperatureK()<=flow.ambientTemperatureK()+10)
                throw new IllegalStateException("no thermal inertia");
            boolean vanilla=phase==Phase.VANILLA;
            if(!vanilla!=OculusCompat.isShadersActive()) throw new IllegalStateException("unexpected shader state");
            if(!vanilla && RocketExhausts.bridgeDraws()==0) throw new IllegalStateException("uniform hook missing");
            if(vanilla && VanillaExhaustRenderer.draws()==0) throw new IllegalStateException("vanilla fallback missing");
            AssimpLoader.LOGGER.info("[RocketSmoke] CPU {} sources={} hasAnchor={} puffs={} camera={} anchor={} paused={} flow={}",
                    phase,RocketExhausts.packet().count(),RocketExhausts.hasSource(anchor),RocketExhausts.packet().puffCount(),
                    mc.gameRenderer.getMainCamera().getPosition(),anchor,mc.isPaused(),flow.massFlowKgS());
            if(!vanilla) probeTargets();
            int error=glGetError();
            if(error!=GL_NO_ERROR) throw new IllegalStateException("GL error: "+error);
            AssimpLoader.LOGGER.info("[RocketSmoke] {} mdot={} kg/s target={} exitT={} K wallT={} K velocity={} m/s pressure={} Pa thrust={} N energy={} W puffs={}",
                    phase,flow.massFlowKgS(),flow.targetMassFlowKgS(),flow.exitTemperatureK(),flow.nozzleWallTemperatureK(),
                    flow.exitVelocityMps(),flow.exitPressurePa(),flow.thrustN(),flow.exhaustEnergyWatts(),RocketExhausts.packet().puffCount());
            String name="rocket-"+RUN+"-"+phase.name().toLowerCase(java.util.Locale.ROOT)+".png";
            captureRequested=true;
            Screenshot.grab(mc.gameDirectory,name,mc.getMainRenderTarget(),message->mc.execute(()-> {
                captureDone=true; captures++;
                AssimpLoader.LOGGER.info("[RocketSmoke] Captured {} GL_NO_ERROR",name);
            }));
        } catch(Exception | LinkageError error) { fail("capture",error); }
    }

    private static void setup(Minecraft mc) {
        if(AssimpModelLoader.getCachedModel("rocket_engine")==null) return;
        var server=mc.getSingleplayerServer();
        if(server==null || mc.player==null) throw new IllegalStateException("integrated player required");
        player=mc.player.getUUID();
        mc.options.pauseOnLostFocus=false; mc.options.hideGui=true;
        mc.options.setCameraType(CameraType.FIRST_PERSON); mc.options.fov().set(60); mc.options.bobView().set(false);
        move(Phase.PREPARING);
        server.execute(()-> {
            try {
                ServerPlayer entity=server.getPlayerList().getPlayer(player);
                if(entity==null) throw new IllegalStateException("player lost");
                int standX=entity.getBlockX()+60, standZ=entity.getBlockZ()+60;
                int standY=entity.serverLevel().getHeight(net.minecraft.world.level.levelgen.Heightmap.Types.MOTION_BLOCKING_NO_LEAVES,standX,standZ);
                BlockPos ground=new BlockPos(standX,standY,standZ);
                int result=server.getCommands().performPrefixedCommand(entity.createCommandSourceStack().withPermission(4),
                        "assimpdemo rocket "+ground.getX()+" "+ground.getY()+" "+ground.getZ());
                if(result!=1) throw new IllegalStateException("could not create stand");
                anchor=ground.above(12);
                var level=entity.serverLevel();
                level.getGameRules().getRule(GameRules.RULE_DAYLIGHT).set(false,server);
                level.setDayTime(11500); level.setWeatherParameters(12000,0,false,false);
                entity.setGameMode(GameType.SPECTATOR);
                mc.execute(()-> {
                    var profile=ExhaustProfiles.get(new ResourceLocation("assimploader","kerolox"));
                    nominal=RocketEngineState.nominalMassFlowKgS(profile,1.03425);
                    commandFlow(mc,nominal*.35,null,true); camera(mc,false,false); move(Phase.FLOW_LOW);
                });
            } catch(Exception | LinkageError error) { mc.execute(()->fail("stand setup",error)); }
        });
    }

    private static void commandFlow(Minecraft mc,double flow,String profile,boolean burning) {
        var server=mc.getSingleplayerServer();
        server.execute(()-> {
            var entity=server.getPlayerList().getPlayer(player);
            if(entity!=null && entity.serverLevel().getBlockEntity(anchor) instanceof ShowcaseBlockEntity engine) {
                if(profile!=null) engine.setExhaustProfile(new ResourceLocation("assimploader",profile));
                engine.setTargetMassFlowKgS(flow); engine.setRocketBurning(burning);
            }
        });
    }

    private static void camera(Minecraft mc,boolean close,boolean offAxis) {
        var server=mc.getSingleplayerServer();
        server.execute(()-> {
            var entity=server.getPlayerList().getPlayer(player);
            if(entity==null) return;
            Vec3 target=Vec3.atBottomCenterOf(anchor).add(0,close?-2:-3,0);
            Vec3 eye=target.add(close?8:14,close?1:3,close?-10:-18);
            Vec3 aim=target.subtract(eye);
            float yaw=(float)Math.toDegrees(Math.atan2(aim.z,aim.x))-90;
            float pitch=(float)-Math.toDegrees(Math.atan2(aim.y,Math.sqrt(aim.x*aim.x+aim.z*aim.z)));
            entity.connection.teleport(eye.x,eye.y-entity.getEyeHeight(),eye.z,yaw+(offAxis?18:0),pitch+(offAxis?-10:0));
        });
    }

    /** Read-only GPU evidence: verifies dedicated volume history rather than merely pack scene TAA. */
    private static void probeTargets() throws ReflectiveOperationException {
        Class<?> iris=Class.forName("net.irisshaders.iris.Iris");
        Object manager=iris.getMethod("getPipelineManager").invoke(null);
        Object pipeline=manager.getClass().getMethod("getPipelineNullable").invoke(manager);
        var field=pipeline.getClass().getDeclaredField("renderTargets"); field.setAccessible(true);
        Object targets=field.get(pipeline);
        Object target=targets.getClass().getMethod("get",int.class).invoke(targets,10);
        int texture=(int)target.getClass().getMethod("getMainTexture").invoke(target);
        int active=glGetInteger(GL_ACTIVE_TEXTURE), binding=glGetInteger(GL_TEXTURE_BINDING_2D);
        try {
            glBindTexture(GL_TEXTURE_2D,texture);
            int width=glGetTexLevelParameteri(GL_TEXTURE_2D,0,GL_TEXTURE_WIDTH), height=glGetTexLevelParameteri(GL_TEXTURE_2D,0,GL_TEXTURE_HEIGHT);
            var pixels=BufferUtils.createFloatBuffer(width*height*4);
            glGetTexImage(GL_TEXTURE_2D,0,GL_RGBA,GL_FLOAT,pixels);
            int covered=0, accumulated=0; float maxFrames=0;
            for(int i=0;i<pixels.limit();i+=4) {
                if(pixels.get(i)>0) covered++;
                if(pixels.get(i)>0 && pixels.get(i+1)>1.5) accumulated++;
                maxFrames=Math.max(maxFrames,pixels.get(i+1));
            }
            AssimpLoader.LOGGER.info("[RocketSmoke] GPU history {}x{} covered={} accumulated={} maxFrames={}",width,height,covered,accumulated,maxFrames);
            if(phase==Phase.NOMINAL && covered==0) throw new IllegalStateException("blank volume GPU target");
            if(phase==Phase.NOMINAL || phase==Phase.NIGHT || phase==Phase.RESTORED) {
                Object radianceTarget=targets.getClass().getMethod("get",int.class).invoke(targets,8);
                int radianceTexture=(int)radianceTarget.getClass().getMethod("getMainTexture").invoke(radianceTarget);
                glBindTexture(GL_TEXTURE_2D,radianceTexture); pixels.clear();
                glGetTexImage(GL_TEXTURE_2D,0,GL_RGBA,GL_FLOAT,pixels);
                float maxRadiance=0,minTransmission=1;
                int hdrPixels=0;
                for(int i=0;i<pixels.limit();i+=4) {
                    float peak=Math.max(pixels.get(i),Math.max(pixels.get(i+1),pixels.get(i+2)));
                    if(!Float.isFinite(peak)) throw new IllegalStateException("non-finite volume radiance");
                    maxRadiance=Math.max(maxRadiance,peak); minTransmission=Math.min(minTransmission,pixels.get(i+3));
                    if(peak>1) hdrPixels++;
                }
                AssimpLoader.LOGGER.info("[RocketSmoke] GPU volume maxHDR={} hdrPixels={} minTransmission={}",maxRadiance,hdrPixels,minTransmission);
                if(maxRadiance<=0) throw new IllegalStateException("empty radiance buffer");
            }
        } finally { glBindTexture(GL_TEXTURE_2D,binding); glActiveTexture(active); }
    }

    private static void shaders(Minecraft mc,boolean enabled) throws ReflectiveOperationException {
        Class.forName("net.irisshaders.iris.Iris").getMethod("toggleShaders",Minecraft.class,boolean.class).invoke(null,mc,enabled);
        OculusCompat.reset();
    }
    private static void move(Phase next) { phase=next; changedAt=System.currentTimeMillis(); captureFrame=frames; captureRequested=false; }
    private static boolean elapsed(long millis) { return System.currentTimeMillis()-changedAt>=millis; }
    private static void fail(String message,Throwable error) {
        AssimpLoader.LOGGER.error("[RocketSmoke] FAILED: "+message,error);
        Minecraft.getInstance().options.hideGui=false; move(Phase.DONE);
    }
}
