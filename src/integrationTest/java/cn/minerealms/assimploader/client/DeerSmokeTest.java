package cn.minerealms.assimploader.client;

import cn.minerealms.assimploader.AssimpLoader;
import cn.minerealms.assimploader.animation.ClientAnimationManager;
import cn.minerealms.assimploader.core.AssimpModelLoader;
import cn.minerealms.assimploader.core.ModelImportMode;
import cn.minerealms.assimpshowcase.deer.DeerShowcaseEntity;
import cn.minerealms.assimpshowcase.deer.DeerShowcaseRegistry;
import cn.minerealms.assimpshowcase.deer.client.DeerShowcaseRenderer;
import cn.minerealms.assimploader.render.ModelTextureCache;
import net.minecraft.client.Minecraft;
import net.minecraft.client.Screenshot;
import net.minecraft.client.gui.screens.TitleScreen;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import org.lwjgl.opengl.GL11;

import java.nio.file.Files;
import java.util.UUID;

/** Explicit local test world only. Runs after the offscreen CPU/GPU comparisons. */
@Mod.EventBusSubscriber(modid = AssimpLoader.MODID, value = Dist.CLIENT)
public final class DeerSmokeTest {
    private static final boolean ENABLED = Boolean.getBoolean("deertest.enabled");
    private static final String RUN = Long.toString(System.currentTimeMillis());
    private static final String[] STATES = {"idle", "walk", "gallop", "ik_probe", "idle"};
    private static int phase;
    private static int stateIndex;
    private static long changedAt = System.currentTimeMillis();
    private static UUID entityId;
    private static boolean pending;
    private static boolean oldHideGui, oldPause;
    private static int oldFov;
    private static cn.minerealms.assimploader.model.AssimpScene beforeReload;

    private DeerSmokeTest() {}

    @SubscribeEvent public static void tick(TickEvent.ClientTickEvent event) {
        if (!ENABLED || phase == 9 || event.phase != TickEvent.Phase.END || !AnimationSmokeTest.deerComplete()) return;
        Minecraft mc = Minecraft.getInstance();
        try {
            if (phase == 0) {
                if (!(mc.screen instanceof TitleScreen)) return;
                phase = 1; changedAt = System.currentTimeMillis();
                //? if forge {
                mc.createWorldOpenFlows().loadLevel(new TitleScreen(), System.getProperty("deertest.world"));
                //?}
                //? if neoforge {
                /*
                // 1.21 replaced WorldOpenFlows#loadLevel(TitleScreen,String) with openWorld(String,Runnable).
                mc.createWorldOpenFlows().openWorld(System.getProperty("deertest.world"), () -> {});
                *///?}
                return;
            }
            if (System.currentTimeMillis() - changedAt > 180_000) throw new IllegalStateException("deer world timeout " + phase);
            if (mc.player == null || mc.level == null) return;
            if (phase == 1) { prepare(mc); return; }
            if (phase != 3 || pending || System.currentTimeMillis() - changedAt < 10_000 || mc.screen != null || mc.getOverlay() != null) return;
            var deer = deer(mc);
            if (deer == null) return;
            var scene = AssimpModelLoader.getCachedModel(DeerShowcaseRenderer.MODEL_ID, ModelImportMode.DYNAMIC);
            if (scene == null) return;
            for (var material : scene.getMaterials()) {
                ModelTextureCache.getTexture(scene, material);
                var status = ModelTextureCache.status(scene, material);
                if (status.failed()) throw new IllegalStateException("deer texture failed");
                if (!status.ready()) return;
            }
            var state = ClientAnimationManager.getInstance().getOrCreateAnimationState(deer, DeerShowcaseRenderer.MODEL_ID, scene);
            if (state == null || state.getModelInstance() == null) throw new IllegalStateException("deer sidecar missing");
            if (!STATES[stateIndex].equals(state.getStateMachine().getCurrentStateName())) throw new IllegalStateException("deer state not applied");
            if (stateIndex == 4 && scene == beforeReload) throw new IllegalStateException("deer resource reload reused scene");
            if (GL11.glGetError() != GL11.GL_NO_ERROR) throw new IllegalStateException("deer GL error");
            String name = "assimp-deer-" + RUN + "-" + stateIndex + "-" + STATES[stateIndex] + ".png";
            pending = true;
            Screenshot.grab(mc.gameDirectory, name, mc.getMainRenderTarget(), message -> mc.execute(() -> {
                try {
                    if (!Files.isRegularFile(mc.gameDirectory.toPath().resolve("screenshots").resolve(name))) throw new IllegalStateException("missing " + name);
                    AssimpLoader.LOGGER.info("[DeerSmoke] Captured {} bounds={} performance={}", name, state.getPoseSnapshot().getBounds(), ClientAnimationManager.getInstance().getPerformanceStatistics());
                    if (++stateIndex == STATES.length) { finish(mc); return; }
                    if (stateIndex == 4) {
                        beforeReload = scene;
                        mc.reloadResourcePacks().whenComplete((ignored, error) -> mc.execute(() -> {
                            if (error != null) fail(mc, error); else changeState(mc);
                        }));
                    } else changeState(mc);
                } catch (Exception | LinkageError error) { fail(mc, error); }
            }));
        } catch (Exception | LinkageError error) { fail(mc, error); }
    }

    private static void prepare(Minecraft mc) {
        var server = mc.getSingleplayerServer();
        if (server == null) throw new IllegalStateException("integrated server required");
        var playerId = mc.player.getUUID();
        oldHideGui = mc.options.hideGui; oldPause = mc.options.pauseOnLostFocus; oldFov = mc.options.fov().get();
        mc.options.hideGui = true; mc.options.pauseOnLostFocus = false; mc.options.fov().set(50);
        phase = 2;
        server.execute(() -> {
            try {
                var player = server.getPlayerList().getPlayer(playerId);
                if (player == null) throw new IllegalStateException("missing player");
                var level = player.serverLevel();
                BlockPos base = player.blockPosition().above(12);
                for (int x = -10; x <= 10; x++) for (int z = -10; z <= 10; z++) {
                    level.getChunk(base.offset(x, 0, z));
                    level.setBlock(base.offset(x, -1, z), Blocks.SMOOTH_STONE.defaultBlockState(), 3);
                }
                var deer = DeerShowcaseRegistry.DEER.get().create(level);
                if (deer == null) throw new IllegalStateException("deer factory failed");
                deer.moveTo(base.getX() + .5, base.getY(), base.getZ() + .5, 0, 0);
                deer.setNoAi(true); deer.setPersistenceRequired(); deer.setShowcaseState("idle");
                level.addFreshEntity(deer);
                Vec3 eye = deer.position().add(4, .2, 5);
                player.stopRiding();
                player.setGameMode(net.minecraft.world.level.GameType.SPECTATOR);
                player.teleportTo(level, eye.x, eye.y, eye.z, 141.34f, 5);
                level.setDayTime(6000);
                level.setWeatherParameters(12000, 0, false, false);
                mc.execute(() -> { entityId = deer.getUUID(); phase = 3; changedAt = System.currentTimeMillis(); });
            } catch (Exception | LinkageError error) { mc.execute(() -> fail(mc, error)); }
        });
    }

    private static void changeState(Minecraft mc) {
        var server = mc.getSingleplayerServer();
        server.execute(() -> {
            try {
                var player = server.getPlayerList().getPlayer(mc.player.getUUID());
                var deer = (DeerShowcaseEntity) player.serverLevel().getEntity(entityId);
                if (deer == null) throw new IllegalStateException("server deer missing");
                deer.setShowcaseState(STATES[stateIndex]);
                mc.execute(() -> { pending = false; changedAt = System.currentTimeMillis(); });
            } catch (Exception | LinkageError error) { mc.execute(() -> fail(mc, error)); }
        });
    }

    private static DeerShowcaseEntity deer(Minecraft mc) {
        for (var entity : mc.level.entitiesForRendering()) if (entity.getUUID().equals(entityId) && entity instanceof DeerShowcaseEntity deer) return deer;
        return null;
    }

    private static void finish(Minecraft mc) {
        phase = 9;
        mc.options.hideGui = oldHideGui; mc.options.pauseOnLostFocus = oldPause; mc.options.fov().set(oldFov);
        AssimpLoader.LOGGER.info("[DeerSmoke] SUCCESS run={} world entity, five delayed captures, states and resource reload", RUN);
        mc.stop();
    }

    private static void fail(Minecraft mc, Throwable failure) {
        phase = 9;
        AssimpLoader.LOGGER.error("[DeerSmoke] FAILED", failure);
        mc.stop();
    }
}
