package cn.minerealms.assimploader.client;

import cn.minerealms.assimploader.AssimpLoader;
import cn.minerealms.assimploader.compat.OculusCompat;
import cn.minerealms.assimploader.compat.OculusPBRSupport;
import cn.minerealms.assimploader.core.AssimpModelLoader;
import cn.minerealms.assimploader.core.ModelImportMode;
import cn.minerealms.assimploader.model.AssimpScene;
import cn.minerealms.assimploader.render.AssimpPbrTexture;
import cn.minerealms.assimploader.render.ModelTextureCache;
import cn.minerealms.assimploader.rendering.OculusEntityBufferCache;
import cn.minerealms.assimploader.vehicle.ArmoredCarEntity;
import cn.minerealms.assimploader.vehicle.VehicleRegistry;
import cn.minerealms.assimploader.vehicle.client.ArmoredCarRenderer;
import net.minecraft.client.CameraType;
import net.minecraft.client.Minecraft;
import net.minecraft.client.Screenshot;
import net.minecraft.client.gui.screens.TitleScreen;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.GameRules;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import org.lwjgl.opengl.GL11;

import java.nio.file.Files;
import java.util.UUID;

/** Explicit developer-only drive test; creates a display in the selected local test world. */
@Mod.EventBusSubscriber(modid = AssimpLoader.MODID, value = Dist.CLIENT)
public final class VehicleSmokeTest {
    public record Controls(float throttle, float steering, boolean brake) {}
    private enum Phase { TITLE, WORLD, PREPARING, READY, FIRST_IMAGE, ACCELERATE, COAST, BRAKE, REVERSE, STOP, LAST_IMAGE, DONE }
    private static final boolean ENABLED = Boolean.getBoolean("vehicletest.enabled");
    private static final String RUN = Long.toString(System.currentTimeMillis());
    private static Phase phase = Phase.TITLE;
    private static long changedAt = System.currentTimeMillis();
    private static int ticks;
    private static UUID carId;
    private static Vec3 previousPosition;
    private static float firstWheel, earlySpeed, lateSpeed;
    private static double speed;
    private static boolean capturePending, captureDone, oldHideGui, oldPause;
    private static CameraType oldCamera;
    private static int oldFov;
    private static AssimpScene originalScene;
    private static boolean reloaded;

    private VehicleSmokeTest() {}

    /** The normal client input sender still sends and validates these inputs through its packet. */
    public static Controls controls() {
        if (!ENABLED || phase == Phase.TITLE || phase == Phase.WORLD || phase == Phase.DONE) return null;
        return switch (phase) {
            case ACCELERATE -> new Controls(1, ticks >= 50 ? 0.7f : 0, false);
            case COAST -> new Controls(0, 0, false);
            case REVERSE -> new Controls(-1, -0.5f, false);
            default -> new Controls(0, 0, true);
        };
    }

    @SubscribeEvent
    public static void tick(TickEvent.ClientTickEvent event) {
        if (!ENABLED || phase == Phase.DONE || event.phase != TickEvent.Phase.END) return;
        Minecraft mc = Minecraft.getInstance();
        try {
            if (System.currentTimeMillis() - changedAt > 180_000) throw new IllegalStateException("timeout in " + phase);
            ticks++;
            if (phase == Phase.TITLE) {
                if (mc.screen instanceof TitleScreen && ticks > 60) {
                    String world = System.getProperty("vehicletest.world", "");
                    if (world.isBlank()) throw new IllegalStateException("vehicletest.world is required");
                    move(Phase.WORLD);
                    mc.createWorldOpenFlows().loadLevel(new TitleScreen(), world);
                }
                return;
            }
            if (mc.player == null || mc.level == null) return;
            if (phase == Phase.WORLD) { prepare(mc); return; }
            ArmoredCarEntity car = car(mc);
            if (phase == Phase.PREPARING || car == null) return;
            Vec3 position = car.position();
            speed = previousPosition == null ? 0 : position.subtract(previousPosition).horizontalDistance();
            previousPosition = position;
            switch (phase) {
                case READY -> {
                    if (mc.player.getVehicle() != car || ticks < 40 || !texturesReady(mc)) return;
                    if (mc.options.getCameraType() != CameraType.THIRD_PERSON_BACK)
                        throw new IllegalStateException("mount did not select third person");
                    firstWheel = car.getWheelAngle(1);
                    mc.player.setYRot(-32); mc.player.setXRot(14);
                    move(Phase.FIRST_IMAGE);
                }
                case FIRST_IMAGE -> {
                    captureView(mc, -32, 14);
                    if (captureDone) {
                        captureDone = false;
                        if (reloaded) finish(mc);
                        else move(Phase.ACCELERATE);
                    }
                }
                case ACCELERATE -> {
                    if (ticks == 25) earlySpeed = (float) speed;
                    if (ticks >= 70) {
                        lateSpeed = (float) speed;
                        if (lateSpeed < earlySpeed + 0.04f || Math.abs(car.getWheelAngle(1) - firstWheel) < 1)
                            throw new IllegalStateException("vehicle did not accelerate / roll: " + earlySpeed + " -> " + lateSpeed);
                        if (Math.abs(car.getSteeringAngle(1)) < 0.05)
                            throw new IllegalStateException("front wheel steering did not update");
                        AssimpLoader.LOGGER.info("[VehicleSmoke] Acceleration {} -> {} blocks/tick wheel={} steering={}",
                                earlySpeed, lateSpeed, car.getWheelAngle(1), car.getSteeringAngle(1));
                        move(Phase.COAST);
                    }
                }
                case COAST -> {
                    if (ticks >= 20) {
                        if (speed <= 0.05 || speed >= lateSpeed + 0.03) throw new IllegalStateException("coasting inertia invalid: " + speed);
                        AssimpLoader.LOGGER.info("[VehicleSmoke] Coasting speed={}", speed);
                        move(Phase.BRAKE);
                    }
                }
                case BRAKE -> { if (ticks >= 35) { requireStopped(); move(Phase.REVERSE); } }
                case REVERSE -> {
                    if (ticks >= 35) {
                        if (speed < 0.03) throw new IllegalStateException("reverse did not move");
                        AssimpLoader.LOGGER.info("[VehicleSmoke] Reverse speed={} wheel={}", speed, car.getWheelAngle(1));
                        move(Phase.STOP);
                    }
                }
                case STOP -> {
                    if (ticks >= 35) {
                        requireStopped();
                        mc.player.setYRot(car.getYRot() - 145); mc.player.setXRot(15);
                        move(Phase.LAST_IMAGE);
                    }
                }
                case LAST_IMAGE -> {
                    captureView(mc, car.getYRot() - 145, 15);
                    if (captureDone) {
                        captureDone = false;
                        if (!reloaded) {
                            originalScene = scene();
                            move(Phase.PREPARING);
                            mc.reloadResourcePacks().whenComplete((ignored, error) -> mc.execute(() -> {
                                if (error != null) fail(mc, error);
                                else { reloaded = true; move(Phase.READY); }
                            }));
                        } else finish(mc);
                    }
                }
                default -> { }
            }
        } catch (Exception | LinkageError error) { fail(mc, error); }
    }

    private static void prepare(Minecraft mc) {
        if (scene() == null) throw new IllegalStateException("armored car asset missing; enable its local resource pack");
        var server = mc.getSingleplayerServer();
        if (server == null) throw new IllegalStateException("integrated server required");
        UUID playerId = mc.player.getUUID();
        oldCamera = mc.options.getCameraType(); oldHideGui = mc.options.hideGui;
        oldPause = mc.options.pauseOnLostFocus; oldFov = mc.options.fov().get();
        mc.options.hideGui = true; mc.options.pauseOnLostFocus = false; mc.options.fov().set(60);
        move(Phase.PREPARING);
        server.execute(() -> {
            try {
                ServerPlayer player = server.getPlayerList().getPlayer(playerId);
                if (player == null) throw new IllegalStateException("player disappeared");
                var level = player.serverLevel();
                BlockPos base = player.blockPosition().above(8);
                for (int x = -25; x <= 25; x++) for (int z = -25; z <= 25; z++) {
                    level.getChunk(base.offset(x, 0, z));
                    level.setBlock(base.offset(x, -1, z), Blocks.SMOOTH_STONE.defaultBlockState(), 3);
                }
                ArmoredCarEntity car = VehicleRegistry.ARMORED_CAR.get().create(level);
                if (car == null) throw new IllegalStateException("entity factory failed");
                car.moveTo(base.getX() + 0.5, base.getY() + 0.01, base.getZ() + 0.5, 0, 0);
                level.addFreshEntity(car);
                player.stopRiding(); player.setGameMode(GameType.CREATIVE);
                if (!player.startRiding(car)) throw new IllegalStateException("mount failed");
                level.setWeatherParameters(12000, 0, false, false);
                level.getGameRules().getRule(GameRules.RULE_DAYLIGHT).set(false, server);
                level.setDayTime(6000);
                UUID id = car.getUUID();
                mc.execute(() -> { carId = id; move(Phase.READY); });
            } catch (Exception | LinkageError error) { mc.execute(() -> fail(mc, error)); }
        });
    }

    private static boolean texturesReady(Minecraft mc) {
        AssimpScene scene = scene();
        if (scene == null || reloaded && scene == originalScene) return false;
        for (var material : scene.getMaterials()) {
            var texture = ModelTextureCache.getTexture(scene, material);
            var status = ModelTextureCache.status(scene, material);
            if (status.failed()) throw new IllegalStateException("PBR texture failure: " + material.getName());
            if (!status.ready()) return false;
            OculusPBRSupport.prepareTexture(texture);
            if (OculusCompat.isShadersActive() && (!(mc.getTextureManager().getTexture(texture) instanceof AssimpPbrTexture pbr)
                    || pbr.normalTextureId() <= 0 || pbr.specularTextureId() <= 0)) return false;
        }
        AssimpLoader.LOGGER.info("[VehicleSmoke] PBR ready reload={} cache={}", reloaded, OculusEntityBufferCache.statistics());
        return true;
    }

    @SubscribeEvent
    public static void render(TickEvent.RenderTickEvent event) {
        if (!ENABLED) return;
        if (event.phase == TickEvent.Phase.START) {
            Minecraft mc = Minecraft.getInstance();
            ArmoredCarEntity car = car(mc);
            if (mc.player != null && car != null) {
                if (phase == Phase.FIRST_IMAGE) captureView(mc, -32, 14);
                else if (phase == Phase.LAST_IMAGE) captureView(mc, car.getYRot() - 145, 15);
            }
            return;
        }
        if (capturePending || ticks < 25
                || phase != Phase.FIRST_IMAGE && phase != Phase.LAST_IMAGE) return;
        Minecraft mc = Minecraft.getInstance();
        if (mc.screen != null || mc.getOverlay() != null) return;
        try {
            ArmoredCarEntity car = car(mc);
            if (car == null) return;
            Vec3 eye = mc.gameRenderer.getMainCamera().getPosition();
            if (car.getBoundingBox().inflate(0.15).contains(eye) || eye.y < car.getY() + 2.5)
                throw new IllegalStateException("camera clips into vehicle: " + eye + " bounds=" + car.getBoundingBox()
                        + " player=" + mc.player.position() + " pitch=" + mc.player.getXRot());
            int error = GL11.glGetError();
            if (error != GL11.GL_NO_ERROR) throw new IllegalStateException("GL error: " + error);
            if (OculusCompat.isShadersActive() && OculusEntityBufferCache.statistics().draws() == 0)
                throw new IllegalStateException("Oculus expanded vertex cache was not exercised");
            String name = "assimp-vehicle-" + RUN + "-" + phase.name().toLowerCase(java.util.Locale.ROOT)
                    + (reloaded ? "-reload" : "") + ".png";
            capturePending = true;
            Screenshot.grab(mc.gameDirectory, name, mc.getMainRenderTarget(), message -> mc.execute(() -> {
                if (!Files.isRegularFile(mc.gameDirectory.toPath().resolve("screenshots").resolve(name)))
                    fail(mc, new IllegalStateException("screenshot missing: " + message.getString()));
                else {
                    captureDone = true;
                    AssimpLoader.LOGGER.info("[VehicleSmoke] Captured {} eye={} car={} cache={}", name, eye, car.position(),
                            OculusEntityBufferCache.statistics());
                }
            }));
        } catch (Exception | LinkageError error) { fail(mc, error); }
    }

    private static void requireStopped() {
        if (speed > 0.008) throw new IllegalStateException("handbrake did not stop vehicle: " + speed);
    }

    private static void captureView(Minecraft mc, float yaw, float pitch) {
        mc.player.setYRot(yaw);
        mc.player.yRotO = yaw;
        mc.player.setXRot(pitch);
        mc.player.xRotO = pitch;
    }

    private static ArmoredCarEntity car(Minecraft mc) {
        if (mc.level == null || carId == null) return null;
        for (var entity : mc.level.entitiesForRendering())
            if (entity.getUUID().equals(carId) && entity instanceof ArmoredCarEntity car) return car;
        return null;
    }

    private static AssimpScene scene() {
        return AssimpModelLoader.getCachedModel(ArmoredCarRenderer.MODEL_ID, ModelImportMode.DYNAMIC);
    }

    private static void finish(Minecraft mc) {
        mc.options.hideGui = oldHideGui; mc.options.pauseOnLostFocus = oldPause; mc.options.fov().set(oldFov);
        move(Phase.DONE);
        AssimpLoader.LOGGER.info("[VehicleSmoke] SUCCESS car={} acceleration/coast/brake/reverse/camera/PBR/reload; client remains open", carId);
    }

    private static void fail(Minecraft mc, Throwable error) {
        AssimpLoader.LOGGER.error("[VehicleSmoke] FAILED phase=" + phase, error);
        if (oldCamera != null) {
            mc.options.hideGui = oldHideGui; mc.options.pauseOnLostFocus = oldPause; mc.options.fov().set(oldFov);
        }
        move(Phase.DONE);
    }

    private static void move(Phase next) {
        phase = next; ticks = 0; changedAt = System.currentTimeMillis(); capturePending = false;
    }
}
