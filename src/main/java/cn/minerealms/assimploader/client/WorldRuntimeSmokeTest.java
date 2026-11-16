package cn.minerealms.assimploader.client;

import cn.minerealms.assimploader.AssimpLoader;
import cn.minerealms.assimploader.animation.ClientAnimationManager;
import cn.minerealms.assimploader.animation.ModelInstance;
import cn.minerealms.assimploader.core.AssimpModelLoader;
import cn.minerealms.assimploader.core.ModelImportMode;
import cn.minerealms.assimploader.deer.DeerShowcaseEntity;
import cn.minerealms.assimploader.deer.DeerShowcaseRegistry;
import cn.minerealms.assimploader.deer.client.DeerShowcaseRenderer;
import cn.minerealms.assimploader.render.ModelTextureCache;
import cn.minerealms.assimploader.rendering.CpuSkinning;
import cn.minerealms.assimploader.showcase.ShowcaseBlockEntity;
import cn.minerealms.assimploader.showcase.ShowcaseModel;
import cn.minerealms.assimploader.showcase.ShowcaseRegistry;
import net.minecraft.client.Minecraft;
import net.minecraft.client.Screenshot;
import net.minecraft.client.gui.screens.TitleScreen;
import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.SnowLayerBlock;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import org.joml.Matrix4f;
import org.joml.Vector3f;
import org.lwjgl.opengl.GL11;

import java.nio.file.Files;
import java.util.List;
import java.util.UUID;

/** Opt-in mutable local-world regression; screenshots always wait at least ten seconds. */
@Mod.EventBusSubscriber(modid=AssimpLoader.MODID,value=Dist.CLIENT)
public final class WorldRuntimeSmokeTest {
    private static final boolean ENABLED=Boolean.getBoolean("worldruntimetest.enabled");
    private static final String RUN=Long.toString(System.currentTimeMillis());
    private static final String[] STAGES={"terrain-off","terrain-on","world-effects","moved","teleported","reloaded","saved-recreated","ai","world-reopened","cache-budget","removed"};
    private static volatile int stage=-2;
    private static long changed=System.currentTimeMillis();
    private static boolean pending;
    private static UUID deerId;
    private static double movementStartX;
    private static net.minecraft.world.phys.Vec3 aiOrigin;
    private static double aiDistance;
    private static boolean aiWalk;
    private static volatile net.minecraft.world.phys.Vec3 aiTarget;
    private static int aiServerTicks;
    private static ModelInstance lastInstance,retiredInstance;
    private static ModelInstance probeInstance;
    private static BlockPos probePosition;
    private static float probeWeight=Float.NaN;
    private static long probeMainBaseline,probeShadowBaseline;
    private static boolean oldGui,oldPause;
    private static int oldFov;
    private static int oldCacheMiB,oldCacheInstances;
    private static cn.minerealms.assimploader.config.GpuBatchPolicy.Mode oldBatchMode;
    private static boolean oldBatchHiZ;

    private WorldRuntimeSmokeTest() {}
    @SubscribeEvent public static void serverTick(TickEvent.ServerTickEvent event) {
        if(!ENABLED || (stage!=3 && stage!=7) || event.phase!=TickEvent.Phase.END) return;
        var server=Minecraft.getInstance().getSingleplayerServer();
        if(server==null || deerId==null) return;
        for(var level:server.getAllLevels()) {
            var entity=level.getEntity(deerId);
            if(entity!=null && stage==3) entity.setPos(entity.getX()+.0125,entity.getY(),entity.getZ());
            if(entity instanceof DeerShowcaseEntity deer && stage==7 && aiTarget!=null && ++aiServerTicks%20==0) {
                boolean path=deer.getNavigation().moveTo(aiTarget.x,aiTarget.y,aiTarget.z,1);
                AssimpLoader.LOGGER.info("[WorldRuntime] AI path={} done={} ground={} noAI={} pos={} velocity={}",
                        path,deer.getNavigation().isDone(),deer.onGround(),deer.isNoAi(),deer.position(),deer.getDeltaMovement());
            }
        }
    }
    @SubscribeEvent public static void tick(TickEvent.ClientTickEvent event) {
        if(!ENABLED || stage==99 || event.phase!=TickEvent.Phase.END) return;
        var mc=Minecraft.getInstance();
        try {
            if(System.currentTimeMillis()-changed>180_000) throw new IllegalStateException("world runtime timeout "+stage);
            if(stage==-2) {
                if(mc.screen instanceof TitleScreen) { stage=-1; changed=System.currentTimeMillis(); mc.createWorldOpenFlows().loadLevel(new TitleScreen(),System.getProperty("worldruntimetest.world")); }
                return;
            }
            if(stage==-4) {
                if(mc.screen instanceof TitleScreen && mc.getSingleplayerServer()==null) {
                    stage=-5;changed=System.currentTimeMillis();
                    mc.createWorldOpenFlows().loadLevel(new TitleScreen(),System.getProperty("worldruntimetest.world"));
                }
                return;
            }
            if(stage==-5) {
                if(mc.level!=null && mc.player!=null && mc.screen==null && mc.getOverlay()==null) {
                    stage=8;pending=false;changed=System.currentTimeMillis();
                }
                return;
            }
            if(mc.level==null || mc.player==null) return;
            if(stage==-1) { prepare(mc);return; }
            if(stage<0) return;
            if(pending || mc.screen!=null || mc.getOverlay()!=null) return;
            if(stage==10) {
                if(System.currentTimeMillis()-changed<10_500) return;
                if(deer(mc)!=null || retiredInstance==null || !retiredInstance.animation().isClosed())
                    throw new IllegalStateException("Removed entity retained managed animation");
                if(cn.minerealms.assimploader.rendering.OculusEntityBufferCache.isPoseCached(retiredInstance.animation()))
                    throw new IllegalStateException("Removed entity retained posed GPU buffers");
                AssimpLoader.LOGGER.info("[WorldRuntime] Entity removal closed playback, effects and collision state");
                advance(mc);return;
            }
            var deer=deer(mc); if(deer==null) return;
            var scene=AssimpModelLoader.getCachedModel(DeerShowcaseRenderer.MODEL_ID,ModelImportMode.DYNAMIC);
            if(scene==null) return;
            var state=ClientAnimationManager.getInstance().getOrCreateAnimationState(deer,DeerShowcaseRenderer.MODEL_ID,scene);
            if(state==null || state.getModelInstance()==null) throw new IllegalStateException("configured deer missing");
            var instance=state.getModelInstance();
            lastInstance=instance;
            if(stage==7) {
                if(aiOrigin==null) aiOrigin=deer.position();
                aiDistance=Math.max(aiDistance,deer.position().distanceTo(aiOrigin));
                aiWalk|="walk".equals(state.getStateMachine().getCurrentStateName());
            }
            if(stage<=2 && !prepareProbe(mc)) return;
            if(System.currentTimeMillis()-changed<10_500) return;
            for(var material:scene.getMaterials()) {
                ModelTextureCache.getTexture(scene,material);
                var status=ModelTextureCache.status(scene,material);
                if(status.failed()) throw new IllegalStateException("texture failure");
                if(!status.ready()) return;
            }
            verify(mc,deer,instance);
            if(GL11.glGetError()!=GL11.GL_NO_ERROR) throw new IllegalStateException("GL error");
            String file="assimp-world-runtime-"+RUN+"-"+stage+"-"+STAGES[stage]+".png";
            pending=true;
            Screenshot.grab(mc.gameDirectory,file,mc.getMainRenderTarget(),message->mc.execute(()->{
                try {
                    if(!Files.isRegularFile(mc.gameDirectory.toPath().resolve("screenshots").resolve(file))) throw new IllegalStateException("capture missing");
                    AssimpLoader.LOGGER.info("[WorldRuntime] Captured {} emitted={} trail={} feet={} stats={}",file,instance.emittedParticles(),instance.trails().get("antlers").samples().size(),instance.feet().size(),ClientAnimationManager.getInstance().getPerformanceStatistics());
                    advance(mc);
                } catch(Exception|LinkageError error) { fail(mc,error); }
            }));
        } catch(Exception|LinkageError error) { fail(mc,error); }
    }

    private static void verify(Minecraft mc,DeerShowcaseEntity deer,ModelInstance instance) {
        if(Boolean.getBoolean("worldruntimetest.batches")) {
            var batches=WorldModelBatches.statistics();
            if(batches==null || batches.accepted()==0 || batches.frames()==0 || !batches.lastHiZ())
                throw new IllegalStateException("Expected actual world MDI and current-frame HiZ: "+batches);
            AssimpLoader.LOGGER.info("[WorldRuntime] Actual world MDI and HiZ={}",batches);
        }
        if(Boolean.getBoolean("worldruntimetest.shaders")) {
            var passes=instance.renderStatistics();
            if(!cn.minerealms.assimploader.compat.OculusCompat.isShadersActive() || passes.mainPasses()==0 || passes.shadowPasses()==0)
                throw new IllegalStateException("Expected active Oculus main and shadow passes: "+passes);
            AssimpLoader.LOGGER.info("[WorldRuntime] Active Oculus render passes={}",passes);
            var cache=cn.minerealms.assimploader.rendering.OculusEntityBufferCache.poseStatistics();
            if(cn.minerealms.assimploader.rendering.OculusEntityBufferCache.isPoseCacheFailed()
                    || cache.draws()==0 || cache.shadowDraws()==0 || cache.hits()==0 || cache.uploads()>=cache.draws())
                throw new IllegalStateException("Expected pose upload reuse across main/shadow passes: "+cache);
            AssimpLoader.LOGGER.info("[WorldRuntime] Oculus posed buffer statistics={}",cache);
            if(stage==9 && (cache.owners()>1 || cache.residentBytes()>1024*1024))
                throw new IllegalStateException("Pose cache exceeded configured budget: "+cache);
        }
        if(stage<=2) {
            var probePose=probeInstance.animation().getPoseSnapshot();
            var mesh=probePose.getScene().getMeshes().get(0);
            var passes=probeInstance.renderStatistics();
            if(probePose.getBoneCount(mesh)!=256 || probePose.getMorphWeights(mesh)[0]!=probeWeight
                    || passes.mainPasses()<=probeMainBaseline
                    || Boolean.getBoolean("worldruntimetest.shaders") && passes.shadowPasses()<=probeShadowBaseline)
                throw new IllegalStateException("256-bone world Morph passes missing: "+passes+" / "+probeWeight);
            AssimpLoader.LOGGER.info("[WorldRuntime] 256-bone world Morph={} render passes={}",probeWeight,passes);
        }
        var pose=instance.animation().getPoseSnapshot();
        var visual=instance.animation().getRenderPose(.375f);
        if((stage==2 || stage==3) && "walk".equals(instance.animation().getStateMachine().getCurrentStateName())) {
            verifySoleContact(deer,pose,.02f);
            verifySoleContact(deer,visual,.03f);
        }
        if(instance.animation().getPerformanceSettings().renderInterpolation()) {
            double clock=instance.animation().getSimulationTime();
            long emissions=instance.emittedParticles();
            if(visual!=instance.animation().getRenderPose(.375f)) throw new IllegalStateException("Render phase cache not reused");
            verifyVisualBounds(visual);
            if(instance.animation().getPoseSnapshot()!=pose || instance.animation().getSimulationTime()!=clock
                    || instance.emittedParticles()!=emissions) throw new IllegalStateException("Rendering advanced simulation");
            AssimpLoader.LOGGER.info("[WorldRuntime] Interpolated frame={} authoritative={} phase=.375 vertices bounded; simulation/effects unchanged",
                    visual.getFrameId(),pose.getFrameId());
        }
        var bounds=pose.getBounds();
        var minimum=new Vector3f(Float.POSITIVE_INFINITY);
        var maximumEdge=new Vector3f(Float.NEGATIVE_INFINITY);
        int vertexCount=0;
        for(var mesh:pose.copyMeshInstances()) {
            var positions=CpuSkinning.geometry(pose,mesh.mesh()).positions();
            var transform=mesh.mesh().hasBones()?new Matrix4f():mesh.transform();
            var point=new Vector3f();
            for(int i=0;i<positions.length;i+=3) {
                transform.transformPosition(point.set(positions[i],positions[i+1],positions[i+2]));
                if(!point.isFinite() || point.x<bounds.minX-1e-4 || point.x>bounds.maxX+1e-4
                        || point.y<bounds.minY-1e-4 || point.y>bounds.maxY+1e-4
                        || point.z<bounds.minZ-1e-4 || point.z>bounds.maxZ+1e-4)
                    throw new IllegalStateException("Deformed vertex escaped visual bounds: "+point+" / "+bounds);
                minimum.min(point);maximumEdge.max(point);vertexCount++;
            }
        }
        if(vertexCount==0) throw new IllegalStateException("Empty deer geometry");
        AssimpLoader.LOGGER.info("[WorldRuntime] stage={} vertices={} edgesMin={} edgesMax={} bounds={} bodyOffset={}",
                STAGES[stage],vertexCount,minimum,maximumEdge,bounds,instance.groundBodyOffset());
        if(stage==0) verifyLocalVisualInputs(deer,pose.getScene());
        if(stage==1) {
            int grounded=0; float maximum=0;
            for(var entry:instance.feet().entrySet()) {
                Vector3f target=entry.getValue().getLastTarget();
                if(target==null) continue;
                Vector3f actual=instance.animation().getPoseSnapshot().getNodeTransform("hoof"+entry.getKey()).getTranslation(new Vector3f());
                maximum=Math.max(maximum,actual.distance(target)); grounded++;
            }
            if(grounded!=4 || maximum>.025f) throw new IllegalStateException("ground feet="+grounded+" maximum error="+maximum);
            AssimpLoader.LOGGER.info("[WorldRuntime] Four terrain samples applied; maximum hoof error={}m",maximum);
        }
        if(stage==2 || stage==3) {
            if(instance.emittedParticles()<5 || instance.trails().get("antlers").samples().size()<3) throw new IllegalStateException("world effects did not advance");
            if(!instance.trailsInWorldSpace()) throw new IllegalStateException("trail remained in model space");
            for(var sample:instance.trails().get("antlers").samples()) if(sample.start().distance(new Vector3f((float)deer.getX(),(float)deer.getY(),(float)deer.getZ()))>4) throw new IllegalStateException("trail disconnected");
        }
        if(stage==3) {
            double displacement=deer.getX()-movementStartX;
            if(displacement<.3) throw new IllegalStateException("scripted movement missing: "+displacement);
            AssimpLoader.LOGGER.info("[WorldRuntime] Scripted entity displacement={} blocks",displacement);
        }
        if(stage==4) for(var sample:instance.trails().get("antlers").samples()) if(sample.start().x<deer.getX()-4) throw new IllegalStateException("teleport retained old trail");
        if(stage==6 && (!deer.effectsEnabled() || !deer.groundIkEnabled() || !deer.isNoAi() || !deer.getShowcaseState().equals("walk"))) throw new IllegalStateException("NBT recreation did not preserve settings");
        if(stage==6 && (retiredInstance==null || !retiredInstance.animation().isClosed())) throw new IllegalStateException("NBT replacement retained old animation");
        if(stage==8 && (retiredInstance==null || !retiredInstance.animation().isClosed()
                || !deer.effectsEnabled() || !deer.groundIkEnabled() || !deer.isNoAi() || !deer.getShowcaseState().equals("idle")))
            throw new IllegalStateException("Disk world reopen lost settings or retained old playback");
        if(stage==7) {
            if(aiDistance<.3 || !aiWalk) throw new IllegalStateException("AI did not move or select walk: "+aiDistance+"/"+aiWalk);
            AssimpLoader.LOGGER.info("[WorldRuntime] AI moved {} blocks; observed walk=true",aiDistance);
        }
    }

    private static void verifyLocalVisualInputs(DeerShowcaseEntity deer,cn.minerealms.assimploader.model.AssimpScene scene) {
        var config=cn.minerealms.assimploader.config.AssimpLoaderConfig.CLIENT;
        var oldMode=config.visualMode.get();boolean oldGround=config.localGroundPlacement.get(),oldEffects=config.localEffects.get();
        var box=deer.getBoundingBox();boolean hostGround=deer.groundIkEnabled(),hostEffects=deer.effectsEnabled();
        ModelInstance local=null;
        try {
            config.visualMode.set(cn.minerealms.assimploader.config.ClientVisualPolicy.Mode.CLIENT_LOCAL);
            config.localGroundPlacement.set(true);config.localEffects.set(false);
            cn.minerealms.assimploader.config.ClientVisualPolicy.refreshFromConfig();
            local=cn.minerealms.assimploader.animation.InstanceDefinitionResources.forModel(DeerShowcaseRenderer.MODEL_RESOURCE).orElseThrow().instantiate(scene);
            local.requestState("idle");
            MinecraftInstanceWorldBinding.bind(local,deer,current->new Matrix4f().rotateY((float)Math.toRadians(-current.getYRot())),
                    current->{throw new IllegalStateException("Local visual mode read host ground input");},
                    current->{throw new IllegalStateException("Local visual mode read host effects input");});
            for(int tick=0;tick<16;tick++) local.animation().update(.05f);
            long grounded=local.feet().values().stream().filter(foot->foot.getLastTarget()!=null).count();
            if(grounded!=4 || local.trailsEnabled() || !box.equals(deer.getBoundingBox())
                    || deer.groundIkEnabled()!=hostGround || deer.effectsEnabled()!=hostEffects)
                throw new IllegalStateException("Client visual independence failed: feet="+grounded);
            AssimpLoader.LOGGER.info("[WorldRuntime] CLIENT_LOCAL sampled four feet with host suppliers forbidden; client entity AABB and synchronized switches unchanged");
        } finally {
            if(local!=null) local.animation().close();
            config.visualMode.set(oldMode);config.localGroundPlacement.set(oldGround);config.localEffects.set(oldEffects);
            cn.minerealms.assimploader.config.ClientVisualPolicy.refreshFromConfig();
        }
    }

    private static void verifyVisualBounds(cn.minerealms.assimploader.animation.PoseSnapshot pose) {
        var bounds=pose.getBounds().inflate(1e-4);
        var point=new Vector3f();
        for(var instance:pose.copyMeshInstances()) {
            var positions=CpuSkinning.geometry(pose,instance.mesh()).positions();
            var transform=instance.mesh().hasBones()?new Matrix4f():instance.transform();
            for(int index=0;index<positions.length;index+=3) {
                transform.transformPosition(point.set(positions[index],positions[index+1],positions[index+2]));
                if(!point.isFinite() || !bounds.contains(point.x,point.y,point.z))
                    throw new IllegalStateException("Interpolated vertex escaped bounds: "+point+" / "+bounds);
            }
        }
    }

    private static boolean prepareProbe(Minecraft mc) {
        if(!(mc.level.getBlockEntity(probePosition) instanceof ShowcaseBlockEntity owner)) return false;
        var scene=AssimpModelLoader.getCachedModel("highbone_probe");
        if(scene==null) return false;
        var state=ClientAnimationManager.getInstance().getOrCreateAnimationState(owner,"highbone_probe",scene);
        if(state==null || state.getModelInstance()==null) throw new IllegalStateException("Missing highbone showcase definition");
        for(var material:scene.getMaterials()) {
            ModelTextureCache.getTexture(scene,material);
            var status=ModelTextureCache.status(scene,material);
            if(status.failed()) throw new IllegalStateException("Highbone world texture failed");
            if(!status.ready()) return false;
        }
        float weight=stage==1?1:0;
        if(probeInstance!=state.getModelInstance() || probeWeight!=weight) {
            probeInstance=state.getModelInstance();probeWeight=weight;
            var passes=probeInstance.renderStatistics();
            probeMainBaseline=passes.mainPasses();probeShadowBaseline=passes.shadowPasses();
            state.setMorphWeights(scene.getMeshes().get(0),new float[]{weight});
        }
        return true;
    }

    private static void verifySoleContact(DeerShowcaseEntity deer,cn.minerealms.assimploader.animation.PoseSnapshot pose,float tolerance) {
        var world=new Matrix4f().translation((float)deer.getX(),(float)deer.getY(),(float)deer.getZ())
                .rotateY((float)Math.toRadians(-deer.getYRot()));
        var sampler=new MinecraftGroundSampler(deer.level());
        float support=Float.POSITIVE_INFINITY;
        for(String leg:List.of("FL","FR","BL","BR")) {
            var lowest=new Vector3f(0,Float.POSITIVE_INFINITY,0);
            for(var mesh:pose.getScene().getMeshes()) {
                var positions=CpuSkinning.geometry(pose,mesh).positions();var skin=mesh.getSkinnedData();
                var indices=skin.getBoneIndices();var weights=skin.getBoneWeights();var names=mesh.getBoneNames();
                int stride=weights.length/mesh.getVertexCount();
                for(int vertex=0;vertex<mesh.getVertexCount();vertex++) if(weights[vertex*stride]>.99f
                        && names.get(indices[vertex*stride]).equals("leg"+leg+"2")) {
                    var point=world.transformPosition(new Vector3f(positions[vertex*3],positions[vertex*3+1],positions[vertex*3+2]));
                    if(point.y<lowest.y) lowest.set(point);
                }
            }
            var ground=sampler.ground(new Vector3f(lowest).add(0,1,0),2).orElseThrow();
            support=Math.min(support,lowest.y-ground.y);
        }
        if(!Float.isFinite(support) || Math.abs(support-.005f)>tolerance)
            throw new IllegalStateException("Actual walking mesh support clearance="+support+" frame="+pose.getFrameId());
        AssimpLoader.LOGGER.info("[WorldRuntime] Actual mesh sole support clearance={}m frame={}",support,pose.getFrameId());
    }

    private static void prepare(Minecraft mc) {
        oldGui=mc.options.hideGui;oldPause=mc.options.pauseOnLostFocus;oldFov=mc.options.fov().get();
        var config=cn.minerealms.assimploader.config.AssimpLoaderConfig.CLIENT;
        if(Boolean.getBoolean("worldruntimetest.batches")) {
            oldBatchMode=config.batch.mode.get();oldBatchHiZ=config.batch.hiZEnabled.get();
            config.batch.mode.set(cn.minerealms.assimploader.config.GpuBatchPolicy.Mode.MDI);config.batch.hiZEnabled.set(true);
            cn.minerealms.assimploader.config.GpuBatchPolicy.refreshFromConfig();
        }
        oldCacheMiB=config.oculusPoseCacheMiB.get();oldCacheInstances=config.oculusPoseCacheInstances.get();
        mc.options.hideGui=true;mc.options.pauseOnLostFocus=false;mc.options.fov().set(50);
        pending=true;
        stage=-3;
        var server=mc.getSingleplayerServer();var playerId=mc.player.getUUID();
        server.execute(()->{
            try {
                var player=server.getPlayerList().getPlayer(playerId);var level=player.serverLevel();
                var base=player.blockPosition().above(12);
                for(int x=-12;x<=12;x++) for(int z=-12;z<=12;z++) {
                    level.getChunk(base.offset(x,0,z));
                    level.setBlock(base.offset(x,-1,z),Blocks.SMOOTH_STONE.defaultBlockState(),3);
                }
                for(int z=-2;z<=2;z++) level.setBlock(base.offset(1,0,z),Blocks.SNOW.defaultBlockState().setValue(SnowLayerBlock.LAYERS,3),3);
                var probe=base.offset(-2,0,-2);
                level.setBlock(probe,ShowcaseRegistry.block(ShowcaseModel.HIGHBONE_PROBE).defaultBlockState(),3);
                var deer=DeerShowcaseRegistry.DEER.get().create(level);
                deer.moveTo(base.getX()+.98,base.getY()+.12,base.getZ()+.5,0,0);
                deer.setNoAi(true);deer.setNoGravity(true);deer.setPersistenceRequired();deer.setShowcaseState("idle");deer.setGroundIkEnabled(false);
                if(Boolean.getBoolean("worldruntimetest.kubejs")) deer.addTag("assimp_kubejs_acceptance");
                if(!level.addFreshEntity(deer)) throw new IllegalStateException("Showcase deer spawn failed");
                if(Boolean.getBoolean("worldruntimetest.kubejs") && !deer.getTags().contains("assimp_kubejs_verified"))
                    throw new IllegalStateException("KubeJS server entity event did not complete its assertions");
                level.setDayTime(6000);level.setWeatherParameters(12000,0,false,false);
                player.setGameMode(net.minecraft.world.level.GameType.SPECTATOR);player.stopRiding();
                player.teleportTo(level,deer.getX()+4,deer.getY()+.2,deer.getZ()+5,141.34f,5);
                mc.execute(()->{probePosition=probe;deerId=deer.getUUID();stage=0;pending=false;changed=System.currentTimeMillis();});
            } catch(Exception|LinkageError error) { mc.execute(()->fail(mc,error)); }
        });
    }

    private static void advance(Minecraft mc) {
        if(++stage==STAGES.length) {
            stage=99;mc.options.hideGui=oldGui;mc.options.pauseOnLostFocus=oldPause;mc.options.fov().set(oldFov);
            restoreCacheConfig();
            AssimpLoader.LOGGER.info("[WorldRuntime] SUCCESS run={} terrain/effects/movement/teleport/reload/NBT/AI/disk-reopen/cache-budget/removal",RUN);mc.stop();return;
        }
        if(stage==5) {
            mc.reloadResourcePacks().whenComplete((v,error)->mc.execute(()->{if(error!=null) fail(mc,error);else {pending=false;changed=System.currentTimeMillis();}}));return;
        }
        if(stage==9) {
            var config=cn.minerealms.assimploader.config.AssimpLoaderConfig.CLIENT;
            config.oculusPoseCacheMiB.set(1);config.oculusPoseCacheInstances.set(1);
            pending=false;changed=System.currentTimeMillis();return;
        }
        if(stage==10) restoreCacheConfig();
        var server=mc.getSingleplayerServer();var playerId=mc.player.getUUID();
        if(stage==6 || stage==8 || stage==10) retiredInstance=lastInstance;
        server.execute(()->{
            try {
                var player=server.getPlayerList().getPlayer(playerId);var level=player.serverLevel();
                var deer=(DeerShowcaseEntity)level.getEntity(deerId);
                if(stage==1) deer.setGroundIkEnabled(true);
                if(stage==2) {deer.setNoGravity(false);deer.setEffectsEnabled(true);deer.setShowcaseState("walk");}
                if(stage==3) {movementStartX=deer.getX();deer.setDeltaMovement(0,0,0);}
                if(stage==4) {
                    deer.setDeltaMovement(0,0,0);deer.teleportTo(deer.getX()+7,deer.getY(),deer.getZ());
                    player.teleportTo(level,deer.getX()+4,deer.getY()+.2,deer.getZ()+5,141.34f,5);
                }
                if(stage==6) {
                    CompoundTag saved=new CompoundTag();deer.save(saved);deer.remove(Entity.RemovalReason.DISCARDED);
                    var restored=DeerShowcaseRegistry.DEER.get().create(level);restored.load(saved);
                    if(!level.addFreshEntity(restored)) throw new IllegalStateException("NBT entity recreation failed");
                    deerId=restored.getUUID();
                }
                if(stage==7) {
                    deer.setNoAi(false);deer.setNoGravity(false);deer.setShowcaseState("auto");
                    aiTarget=new net.minecraft.world.phys.Vec3(deer.getX()+2,Math.floor(deer.getY()),deer.getZ());
                }
                if(stage==8) {
                    deer.setNoAi(true);deer.setNoGravity(false);deer.setShowcaseState("idle");deer.setDeltaMovement(0,0,0);
                    mc.execute(()->{
                        stage=-4;changed=System.currentTimeMillis();
                        mc.level.disconnect();mc.clearLevel(new TitleScreen());
                    });
                    return;
                }
                if(stage==10) deer.remove(Entity.RemovalReason.DISCARDED);
                mc.execute(()->{pending=false;changed=System.currentTimeMillis();});
            } catch(Exception|LinkageError error) {mc.execute(()->fail(mc,error));}
        });
    }
    private static DeerShowcaseEntity deer(Minecraft mc) {
        for(var entity:mc.level.entitiesForRendering()) if(entity.getUUID().equals(deerId)&&entity instanceof DeerShowcaseEntity deer) return deer;
        return null;
    }
    private static void restoreCacheConfig() {
        if(oldBatchMode!=null) {
            var config=cn.minerealms.assimploader.config.AssimpLoaderConfig.CLIENT;
            config.batch.mode.set(oldBatchMode);config.batch.hiZEnabled.set(oldBatchHiZ);
            cn.minerealms.assimploader.config.GpuBatchPolicy.refreshFromConfig();oldBatchMode=null;
        }
        if(oldCacheMiB>0) {
            var config=cn.minerealms.assimploader.config.AssimpLoaderConfig.CLIENT;
            config.oculusPoseCacheMiB.set(oldCacheMiB);config.oculusPoseCacheInstances.set(oldCacheInstances);
        }
    }
    private static void fail(Minecraft mc,Throwable error) {stage=99;restoreCacheConfig();AssimpLoader.LOGGER.error("[WorldRuntime] FAILED",error);mc.stop();}
}
