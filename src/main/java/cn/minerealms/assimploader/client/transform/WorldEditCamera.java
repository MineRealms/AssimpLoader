package cn.minerealms.assimploader.client.transform;

import cn.minerealms.assimploader.AssimpLoader;
import cn.minerealms.assimploader.mixin.camera.WorldEditCameraAccess;
import com.mojang.blaze3d.systems.RenderSystem;
import net.minecraft.client.Camera;
import net.minecraft.client.CameraType;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.client.event.ClientPlayerNetworkEvent;
import net.minecraftforge.event.GameShuttingDownEvent;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import org.joml.Vector3d;

import java.util.Objects;
import java.util.function.Function;

/**
 * A temporary camera for an in-world editor, using the normal world/shader rendering path.
 * Open from Screen.init and close from removed. The focus callback is sampled once per setup.
 * Vanilla VISUAL-shape collision ignores fluids and uses eight offset rays along the boom;
 * the pan/framing pivot also clips against visual block shapes. Collision and loaded chunks
 * take precedence over exact panel framing. This does not move or rotate the player.
 */
@Mod.EventBusSubscriber(modid = AssimpLoader.MODID, value = Dist.CLIENT)
public final class WorldEditCamera {
    private static WorldEditCamera current;
    private final WorldEditCameraOrbit orbit = new WorldEditCameraOrbit();
    private final Vector3d pivot = new Vector3d();
    private Function<Float, Vec3> focusProvider;
    private ClientLevel level;
    private LocalPlayer player;
    private Screen screen;
    private CameraType previousType;
    private double verticalTangent = Math.tan(Math.toRadians(35));

    public WorldEditCamera() {}

    public void open(Function<Float, Vec3> focus) {
        Objects.requireNonNull(focus, "Camera focus callback");
        Minecraft minecraft = Minecraft.getInstance();
        if (minecraft.level == null || minecraft.player == null || minecraft.getCameraEntity() != minecraft.player)
            throw new IllegalStateException("The world edit camera requires a local player in a loaded world");
        if (!finite(focus.apply(minecraft.getFrameTime())))
            throw new IllegalStateException("The world edit camera focus must be a finite world position");
        if (current != null) current.close();
        level = minecraft.level;
        player = minecraft.player;
        screen = minecraft.screen;
        previousType = minecraft.options.getCameraType();
        focusProvider = focus;
        orbit.reset(player.yBodyRot);
        minecraft.options.setCameraType(CameraType.THIRD_PERSON_BACK);
        current = this;
    }

    public void close() {
        if (current == this) {
            current = null;
            if (previousType != null) Minecraft.getInstance().options.setCameraType(previousType);
        }
        focusProvider = null;
        previousType = null;
        level = null;
        player = null;
        screen = null;
    }

    public boolean active() {
        if (current != this) return false;
        Minecraft minecraft = Minecraft.getInstance();
        if (minecraft.level != level || minecraft.player != player || player.isRemoved()
                || minecraft.getCameraEntity() != player || minecraft.screen != screen) {
            close();
            return false;
        }
        return true;
    }

    public void orbit(double dx, double dy) { orbit.orbit(dx, dy); }

    public void pan(double dx, double dy) {
        int height = Math.max(1, Minecraft.getInstance().getWindow().getGuiScaledHeight());
        orbit.pan(dx, dy, 2 * orbit.distance() * verticalTangent / height);
    }

    public void zoom(double wheel) { orbit.zoom(wheel); }
    public void lift(double wheel) { orbit.lift(wheel); }
    public void focus() { orbit.focus(); }
    public void frame(double viewportCenterX) { orbit.frame(viewportCenterX); }
    public double distance() { return orbit.distance(); }

    /** Called only by Camera.setup TAIL, after the renderer installed the world projection. */
    public static void apply(Camera camera, BlockGetter world, Entity entity, float partialTick) {
        WorldEditCamera editor = current;
        if (editor == null || !editor.active()) return;
        Minecraft minecraft = Minecraft.getInstance();
        if (camera != minecraft.gameRenderer.getMainCamera() || world != editor.level || entity != editor.player) return;
        editor.update(camera, partialTick);
    }

    private void update(Camera camera, float partialTick) {
        Vec3 focus;
        try {
            float alpha = Float.isFinite(partialTick) ? Math.max(0, Math.min(partialTick, 1)) : 0;
            focus = focusProvider.apply(alpha);
        } catch (RuntimeException error) {
            AssimpLoader.LOGGER.warn("Closing world edit camera after its focus callback failed", error);
            close();
            return;
        }
        if (!finite(focus) || !loaded(focus.x, focus.y, focus.z)) {
            close();
            return;
        }
        double horizontalTangent = tangent(RenderSystem.getProjectionMatrix().m00(), 1);
        verticalTangent = tangent(RenderSystem.getProjectionMatrix().m11(), verticalTangent);
        orbit.pivot(focus.x, focus.y, focus.z, horizontalTangent, pivot);
        // The complete focus-to-pivot segment covers both panning and panel framing.
        Vec3 framed = clip(focus, new Vec3(pivot.x, pivot.y, pivot.z));
        framed = loadedSegment(focus, framed);
        WorldEditCameraAccess access = (WorldEditCameraAccess) camera;
        access.assimploader$setRotation(orbit.yaw(), orbit.pitch());
        access.assimploader$setPosition(framed.x, framed.y, framed.z);
        //? if forge {
        double boom = access.assimploader$getMaxZoom(orbit.distance());
        if (!Double.isFinite(boom)) boom = 0;
        access.assimploader$move(-Math.max(0, Math.min(boom, orbit.distance())), 0, 0);
        //?}
        //? if neoforge {
        /*
        double boom = access.assimploader$getMaxZoom((float) orbit.distance());
        if (!Double.isFinite(boom)) boom = 0;
        access.assimploader$move(-(float) Math.max(0, Math.min(boom, orbit.distance())), 0, 0);
        *///?}
        Vec3 finalPosition = loadedSegment(framed, camera.getPosition());
        access.assimploader$setPosition(finalPosition.x, finalPosition.y, finalPosition.z);
    }

    private Vec3 clip(Vec3 start, Vec3 end) {
        double length = start.distanceTo(end);
        if (length < 1e-6) return end;
        var hit = level.clip(new ClipContext(start, end, ClipContext.Block.VISUAL, ClipContext.Fluid.NONE, player));
        if (hit.getType() == HitResult.Type.MISS) return end;
        return start.lerp(end, Math.max(0, (start.distanceTo(hit.getLocation()) - .12) / length));
    }

    private Vec3 loadedSegment(Vec3 start, Vec3 end) {
        double dx = end.x - start.x, dy = end.y - start.y, dz = end.z - start.z;
        int steps = Math.max(1, (int) Math.ceil(start.distanceTo(end)));
        double previous = 0;
        for (int step = 1; step <= steps; step++) {
            double fraction = (double) step / steps;
            if (!loaded(start.x + dx * fraction, start.y + dy * fraction, start.z + dz * fraction))
                return start.lerp(end, previous);
            previous = fraction;
        }
        return end;
    }

    private boolean loaded(double x, double y, double z) {
        return level.getChunkSource().hasChunk((int) Math.floor(x) >> 4, (int) Math.floor(z) >> 4);
    }

    private static boolean finite(Vec3 value) {
        return value != null && Double.isFinite(value.x) && Double.isFinite(value.y) && Double.isFinite(value.z)
                && Math.abs(value.x) <= 30_000_000 && Math.abs(value.z) <= 30_000_000 && Math.abs(value.y) <= 30_000_000;
    }

    private static double tangent(float coefficient, double fallback) {
        double tangent = 1 / Math.abs(coefficient);
        return Double.isFinite(tangent) ? Math.max(.01, Math.min(tangent, 8)) : fallback;
    }

    @SubscribeEvent public static void tick(TickEvent.ClientTickEvent event) {
        if (event.phase == TickEvent.Phase.END && current != null) current.active();
    }

    @SubscribeEvent public static void logout(ClientPlayerNetworkEvent.LoggingOut event) { release(); }
    @SubscribeEvent public static void shutdown(GameShuttingDownEvent event) { release(); }

    private static void release() { if (current != null) current.close(); }
}
