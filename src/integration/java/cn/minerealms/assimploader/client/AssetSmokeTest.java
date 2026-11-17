package cn.minerealms.assimploader.client;

import cn.minerealms.assimploader.AssimpLoader;
import cn.minerealms.assimploader.compat.OculusCompat;
import cn.minerealms.assimploader.compat.OculusPBRSupport;
import cn.minerealms.assimploader.config.AssimpLoaderConfig;
import cn.minerealms.assimploader.core.AssimpModelLoader;
import cn.minerealms.assimploader.model.AssimpMaterial;
import cn.minerealms.assimploader.model.AssimpScene;
import cn.minerealms.assimploader.render.AssimpPbrTexture;
import cn.minerealms.assimploader.render.ModelTextureCache;
import cn.minerealms.assimploader.render.SceneTransforms;
import cn.minerealms.assimpshowcase.showcase.ShowcaseBlockEntity;
import cn.minerealms.assimpshowcase.showcase.ShowcaseModel;
import cn.minerealms.assimpshowcase.showcase.ShowcaseRegistry;
import cn.minerealms.assimpshowcase.showcase.ShowcaseRenderer;
import cn.minerealms.assimploader.render.ModelVisibility;
import net.minecraft.client.CameraType;
import net.minecraft.client.Minecraft;
import net.minecraft.client.Screenshot;
import net.minecraft.client.gui.screens.TitleScreen;
import net.minecraft.core.BlockPos;
import net.minecraft.network.protocol.game.ClientboundGameEventPacket;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.GameRules;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import org.lwjgl.opengl.GL11;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.UUID;

/** Opt-in world screenshots for any registered Showcase model, including local resource overrides. */
@Mod.EventBusSubscriber(modid = AssimpLoader.MODID, value = Dist.CLIENT)
public final class AssetSmokeTest {
    private static final boolean ENABLED = Boolean.getBoolean("assettest.enabled");
    private static final String RUN = Long.toString(System.currentTimeMillis());
    private enum Phase { TITLE, WORLD, PREPARE, PREPARING, TEXTURES, POSITIONING, CAPTURE, RELOAD, DONE }
    private record View(String name, Vec3 eye, Vec3 target) {}
    private static Phase phase = Phase.TITLE;
    private static long changedAt = System.currentTimeMillis(), renderedFrames, captureFrame;
    private static ShowcaseModel model;
    private static UUID playerId;
    private static BlockPos origin;
    private static List<View> views;
    private static int viewIndex, screenshots;
    private static boolean reloaded, captureRequested, captureDone, optionsSaved;
    private static boolean oldHideGui, oldPauseOnLostFocus, oldBobView;
    private static int oldFov;
    private static CameraType oldCamera;
    private static AssimpScene firstScene;
    private static final List<AssimpPbrTexture> FIRST_TEXTURES = new ArrayList<>();

    private AssetSmokeTest() {}

    @SubscribeEvent
    public static void tick(TickEvent.ClientTickEvent event) {
        if (!ENABLED || phase == Phase.DONE || event.phase != TickEvent.Phase.END) return;
        Minecraft mc = Minecraft.getInstance();
        try {
            if (Boolean.getBoolean("autotest.enabled")) throw new IllegalStateException("assettest cannot share a run with autotest");
            if (elapsed(180_000)) throw new IllegalStateException("timeout in " + phase);
            switch (phase) {
                case TITLE -> {
                    if (mc.screen instanceof TitleScreen && elapsed(3000)) {
                        String world = System.getProperty("assettest.world", "");
                        if (world.isBlank()) throw new IllegalStateException("assettest.world is required");
                        model = ShowcaseModel.valueOf(System.getProperty("assettest.model", "PROBE_TEXTURED").toUpperCase(Locale.ROOT));
                        move(Phase.WORLD);
                        mc.createWorldOpenFlows().loadLevel(new TitleScreen(), world);
                    }
                }
                case WORLD -> { if (mc.level != null && mc.player != null) move(Phase.PREPARE); }
                case PREPARE -> prepare(mc);
                case TEXTURES -> pollTextures(mc);
                case CAPTURE -> {
                    if (captureDone) {
                        captureDone = false;
                        if (++viewIndex < views.size()) position(mc);
                        else if (!reloaded) reload(mc);
                        else finish(mc);
                    }
                }
                case PREPARING, POSITIONING, RELOAD, DONE -> { }
            }
        } catch (Exception | LinkageError error) { fail(mc, "failure in " + phase, error); }
    }

    @SubscribeEvent
    public static void render(TickEvent.RenderTickEvent event) {
        if (!ENABLED || phase == Phase.DONE || event.phase != TickEvent.Phase.END) return;
        renderedFrames++;
        Minecraft mc = Minecraft.getInstance();
        if (phase != Phase.CAPTURE || captureRequested || renderedFrames - captureFrame < 60
                || !elapsed(5000) || mc.screen != null || mc.getOverlay() != null) return;
        try {
            View view = views.get(viewIndex);
            if (mc.player == null || mc.player.getEyePosition(1).distanceToSqr(view.eye()) > 0.25)
                throw new IllegalStateException("camera did not reach " + view.name());
            verifyBounds(mc);
            if (view.name().endsWith("overview")) {
                AABB bounds = ModelVisibility.worldBounds(SceneTransforms.bounds(scene()), origin);
                for (double x : new double[]{bounds.minX, bounds.maxX})
                    for (double y : new double[]{bounds.minY, bounds.maxY})
                        for (double z : new double[]{bounds.minZ, bounds.maxZ})
                            if (!inFrame(mc, view, new Vec3(x, y, z)))
                                throw new IllegalStateException("overview does not frame the entire model");
            }
            int error = GL11.glGetError();
            if (error != GL11.GL_NO_ERROR) throw new IllegalStateException("OpenGL error before screenshot: " + error);
            String name = "assimp-asset-smoke-" + RUN + "-" + model.modelName() + "-" + view.name() + ".png";
            captureRequested = true;
            Screenshot.grab(mc.gameDirectory, name, mc.getMainRenderTarget(), message -> mc.execute(() -> {
                if (phase == Phase.DONE) return;
                if (!Files.isRegularFile(mc.gameDirectory.toPath().resolve("screenshots").resolve(name))) {
                    fail(mc, "screenshot failed: " + message.getString(), null);
                } else {
                    screenshots++;
                    captureDone = true;
                    AssimpLoader.LOGGER.info("[AssetSmoke] Captured {} GL_NO_ERROR shaderActive={} eye={} anchorInFrame={}",
                            name, OculusCompat.isShadersActive(), view.eye(), inFrame(mc, view, Vec3.atCenterOf(origin)));
                }
            }));
        } catch (Exception | LinkageError error) { fail(mc, "capture failed", error); }
    }

    private static void prepare(Minecraft mc) {
        AssimpScene scene = scene();
        if (scene == null) return;
        AABB bounds = SceneTransforms.bounds(scene);
        if (scene.getMeshCount() == 0 || !Double.isFinite(bounds.getSize()) || bounds.getSize() <= 0)
            throw new IllegalStateException("model has no finite geometry");
        // This harness creates a temporary display platform, so reject impractically large scans.
        if (Math.max(bounds.getXsize(), Math.max(bounds.getYsize(), bounds.getZsize())) > 128)
            throw new IllegalStateException("asset smoke platform is limited to 128 blocks per axis");
        MinecraftServer server = mc.getSingleplayerServer();
        if (server == null || mc.player == null) throw new IllegalStateException("integrated server unavailable");
        playerId = mc.player.getUUID();
        oldHideGui = mc.options.hideGui; oldPauseOnLostFocus = mc.options.pauseOnLostFocus;
        oldCamera = mc.options.getCameraType(); oldFov = mc.options.fov().get(); oldBobView = mc.options.bobView().get();
        optionsSaved = true;
        mc.options.hideGui = true;
        mc.options.pauseOnLostFocus = false;
        mc.options.setCameraType(CameraType.FIRST_PERSON);
        mc.options.fov().set(60);
        mc.options.bobView().set(false);
        move(Phase.PREPARING);
        server.execute(() -> {
            try {
                ServerPlayer player = requirePlayer(server);
                ServerLevel level = player.serverLevel();
                BlockPos chosen = findAirVolume(level, player.blockPosition().above(4), bounds);
                AABB world = ModelVisibility.worldBounds(bounds, chosen);
                int floor = (int) Math.floor(world.minY) - 1;
                for (int x = (int) Math.floor(world.minX) - 1; x <= (int) Math.ceil(world.maxX) + 1; x++)
                    for (int z = (int) Math.floor(world.minZ) - 1; z <= (int) Math.ceil(world.maxZ) + 1; z++)
                        level.setBlock(new BlockPos(x, floor, z), Blocks.SMOOTH_STONE.defaultBlockState(), 3);
                if (!level.setBlock(chosen, ShowcaseRegistry.block(model).defaultBlockState(), 3))
                    throw new IllegalStateException("could not place Showcase block");
                player.setGameMode(GameType.SPECTATOR);
                level.setWeatherParameters(12000, 0, false, false);
                level.setRainLevel(0); level.setThunderLevel(0);
                player.connection.send(new ClientboundGameEventPacket(ClientboundGameEventPacket.STOP_RAINING, 0));
                player.connection.send(new ClientboundGameEventPacket(ClientboundGameEventPacket.RAIN_LEVEL_CHANGE, 0));
                player.connection.send(new ClientboundGameEventPacket(ClientboundGameEventPacket.THUNDER_LEVEL_CHANGE, 0));
                level.getGameRules().getRule(GameRules.RULE_DAYLIGHT).set(false, server);
                level.setDayTime(6000);
                mc.execute(() -> {
                    origin = chosen;
                    AssimpLoader.LOGGER.info("[AssetSmoke] Prepared model={} source={} format={} generator={} meshes={} bounds={} anchor={}",
                            model, scene.getSourceResource() == null ? scene.getSourcePath() : scene.getSourceResource(),
                            scene.getSourceFormat(), scene.getSourceGenerator(), scene.getMeshCount(), bounds, origin);
                    move(Phase.TEXTURES);
                });
            } catch (Exception | LinkageError error) { mc.execute(() -> fail(mc, "world preparation failed", error)); }
        });
    }

    private static BlockPos findAirVolume(ServerLevel level, BlockPos base, AABB bounds) {
        for (int dy = 0; dy <= 96; dy += 4) {
            BlockPos candidate = base.above(dy);
            AABB world = ModelVisibility.worldBounds(bounds, candidate);
            int minY = Math.min(candidate.getY(), (int) Math.floor(world.minY) - 1);
            int maxY = Math.max(candidate.getY(), (int) Math.ceil(world.maxY) + 1);
            boolean clear = true;
            for (BlockPos pos : BlockPos.betweenClosed((int) Math.floor(world.minX) - 2, minY,
                    (int) Math.floor(world.minZ) - 2, (int) Math.ceil(world.maxX) + 2, maxY,
                    (int) Math.ceil(world.maxZ) + 2)) {
                if (!level.isInWorldBounds(pos) || !level.getWorldBorder().isWithinBounds(pos)
                        || !level.hasChunkAt(pos) || !level.getBlockState(pos).isAir()) { clear = false; break; }
            }
            if (clear && level.getBlockState(candidate).isAir()) return candidate;
        }
        throw new IllegalStateException("no loaded, empty display volume above player; use a flat test world");
    }

    private static void pollTextures(Minecraft mc) {
        AssimpScene scene = scene();
        if (scene == null || mc.level == null || !(mc.level.getBlockEntity(origin) instanceof ShowcaseBlockEntity)) return;
        if (reloaded && scene == firstScene) return;
        List<AssimpPbrTexture> textures = new ArrayList<>();
        List<String> descriptions = new ArrayList<>();
        boolean pbrActive = OculusCompat.isShadersActive() && AssimpLoaderConfig.CLIENT.pbrEnabled.get();
        for (AssimpMaterial material : scene.getMeshes().stream().map(mesh -> ModelTextureCache.material(scene, mesh)).distinct().toList()) {
            if (material == null) throw new IllegalStateException("mesh refers to a missing material");
            var location = ModelTextureCache.getTexture(scene, material);
            var status = ModelTextureCache.status(scene, material);
            if (status.failed()) throw new IllegalStateException("texture preparation failed: " + material.getName());
            if (!status.ready()) return;
            OculusPBRSupport.prepareTexture(location);
            if (!(mc.getTextureManager().getTexture(location) instanceof AssimpPbrTexture texture) || !texture.isReady()) return;
            var inputs = material.getPbrInputs();
            verifyReference(mc, scene, material.getDiffuseTexture());
            if (inputs.normal() != null) verifyReference(mc, scene, inputs.normal().path());
            if (inputs.roughness() != null) verifyReference(mc, scene, inputs.roughness().path());
            if (inputs.metallic() != null) verifyReference(mc, scene, inputs.metallic().path());
            if (inputs.normal() != null && !status.normalImage()) throw new IllegalStateException("normal map missing: " + material.getName());
            if ((inputs.roughness() != null || inputs.metallic() != null) && !status.specularImage())
                throw new IllegalStateException("roughness/metallic packing missing: " + material.getName());
            if (pbrActive && ((status.normalImage() && texture.normalTextureId() <= 0)
                    || (status.specularImage() && texture.specularTextureId() <= 0))) return;
            if (reloaded && FIRST_TEXTURES.contains(texture)) return;
            textures.add(texture);
            descriptions.add(material.getName() + " ready=true albedo=" + material.getDiffuseTexture() + " workflow=" + inputs.workflow()
                    + " normal=" + inputs.normal() + " roughness=" + inputs.roughness() + " metallic=" + inputs.metallic()
                    + " factors=" + inputs.roughnessFactor() + "/" + inputs.metallicFactor()
                    + " normalSource=" + status.normalSource() + " specularSource=" + status.specularSource()
                    + " GL=" + texture.getId() + "/" + texture.normalTextureId() + "/" + texture.specularTextureId());
        }
        verifyBounds(mc);
        if (!reloaded) { firstScene = scene; FIRST_TEXTURES.addAll(textures); }
        descriptions.forEach(line -> AssimpLoader.LOGGER.info("[AssetSmoke] reload={} {}", reloaded, line));
        AssimpLoader.LOGGER.info("[AssetSmoke] All {} used materials ready; reload={} shaderActive={} pbrBindingsRequired={}",
                textures.size(), reloaded, OculusCompat.isShadersActive(), pbrActive);
        List<View> initial = cameraViews(mc, ModelVisibility.worldBounds(SceneTransforms.bounds(scene), origin));
        views = reloaded ? List.of(new View("reload-overview", initial.get(0).eye(), initial.get(0).target()),
                new View("reload-edge-far", initial.get(2).eye(), initial.get(2).target())) : initial;
        viewIndex = 0;
        position(mc);
    }

    private static List<View> cameraViews(Minecraft mc, AABB bounds) {
        Vec3 center = bounds.getCenter();
        double aspect = (double) mc.getWindow().getWidth() / Math.max(1, mc.getWindow().getHeight());
        double halfFov = Math.min(Math.toRadians(30), Math.atan(Math.tan(Math.toRadians(30)) * aspect));
        double distance = Math.max(3, Math.sqrt(bounds.getXsize() * bounds.getXsize() + bounds.getYsize() * bounds.getYsize()
                + bounds.getZsize() * bounds.getZsize()) * 0.5 / Math.sin(halfFov) * 1.2);
        View overview = new View("overview", center.add(new Vec3(1, 0.48, 1.25).normalize().scale(distance)), center);
        double y = bounds.minY + Math.min(2, bounds.getYsize() * 0.45);
        List<View> edges = new ArrayList<>();
        if (bounds.getXsize() >= bounds.getZsize()) {
            for (double x : new double[]{bounds.minX + bounds.getXsize() * 0.05, bounds.maxX - bounds.getXsize() * 0.05})
                edges.add(new View("edge", new Vec3(x, y + 1, bounds.minZ - 2), new Vec3(x, y, bounds.minZ + 0.5)));
        } else {
            for (double z : new double[]{bounds.minZ + bounds.getZsize() * 0.05, bounds.maxZ - bounds.getZsize() * 0.05})
                edges.add(new View("edge", new Vec3(bounds.minX - 2, y + 1, z), new Vec3(bounds.minX + 0.5, y, z)));
        }
        edges.sort(Comparator.comparingDouble(view -> view.eye().distanceToSqr(Vec3.atCenterOf(origin))));
        return List.of(overview, new View("edge-near", edges.get(0).eye(), edges.get(0).target()),
                new View("edge-far", edges.get(1).eye(), edges.get(1).target()));
    }

    private static void position(Minecraft mc) {
        View view = views.get(viewIndex);
        MinecraftServer server = mc.getSingleplayerServer();
        if (server == null) throw new IllegalStateException("integrated server unavailable");
        move(Phase.POSITIONING);
        server.execute(() -> {
            try {
                ServerPlayer player = requirePlayer(server);
                if (!player.serverLevel().getBlockState(origin).is(ShowcaseRegistry.block(model)))
                    throw new IllegalStateException("Showcase anchor changed during test");
                teleport(player, view);
                mc.execute(() -> {
                    captureRequested = false; captureDone = false; captureFrame = renderedFrames;
                    move(Phase.CAPTURE);
                });
            } catch (Exception | LinkageError error) { mc.execute(() -> fail(mc, "camera placement failed", error)); }
        });
    }

    private static void teleport(ServerPlayer player, View view) {
        Vec3 aim = view.target().subtract(view.eye());
        float yaw = (float) Math.toDegrees(Math.atan2(aim.z, aim.x)) - 90;
        float pitch = (float) -Math.toDegrees(Math.atan2(aim.y, Math.sqrt(aim.x * aim.x + aim.z * aim.z)));
        player.setDeltaMovement(Vec3.ZERO);
        player.connection.teleport(view.eye().x, view.eye().y - player.getEyeHeight(), view.eye().z, yaw, pitch);
    }

    private static void verifyBounds(Minecraft mc) {
        if (mc.level == null || !(mc.level.getBlockEntity(origin) instanceof ShowcaseBlockEntity entity))
            throw new IllegalStateException("client Showcase block entity unavailable");
        if (entity.getModel() != model) throw new IllegalStateException("client Showcase model changed");
        AABB geometry = ModelVisibility.worldBounds(SceneTransforms.bounds(scene()), origin);
        AABB rendered = entity.getRenderBoundingBox();
        if (rendered.minX > geometry.minX || rendered.minY > geometry.minY || rendered.minZ > geometry.minZ
                || rendered.maxX < geometry.maxX || rendered.maxY < geometry.maxY || rendered.maxZ < geometry.maxZ)
            throw new IllegalStateException("render bounds do not contain geometry: " + rendered + " geometry=" + geometry);
        Vec3 camera = mc.player == null ? geometry.getCenter() : mc.player.getEyePosition(1);
        if (phase == Phase.CAPTURE && !new ShowcaseRenderer().shouldRender(entity, camera))
            throw new IllegalStateException("distance culling rejected screenshot camera");
        AssimpLoader.LOGGER.info("[AssetSmoke] Render bounds contain geometry: bounds={} geometry={} anchorDistance={}",
                rendered, geometry, camera.distanceTo(Vec3.atCenterOf(origin)));
    }

    private static void verifyReference(Minecraft mc, AssimpScene scene, String path) {
        if (path == null || path.isBlank() || scene.findEmbeddedTextureIndex(path) >= 0) return;
        if (scene.getSourceResource() != null) {
            var location = ModelTextureCache.resolveResource(scene.getSourceResource(), path);
            if (location != null && mc.getResourceManager().getResource(location).isPresent()) return;
        } else if (scene.getSourcePath() != null) {
            Path parent = scene.getSourcePath().toAbsolutePath().normalize().getParent();
            Path file = parent == null ? null : parent.resolve(path.replace('\\', '/')).normalize();
            if (file != null && file.startsWith(parent) && Files.isRegularFile(file)) return;
        }
        throw new IllegalStateException("referenced texture is unavailable: " + path);
    }

    private static boolean inFrame(Minecraft mc, View view, Vec3 point) {
        Vec3 forward = view.target().subtract(view.eye()).normalize();
        Vec3 right = forward.cross(new Vec3(0, 1, 0)).normalize();
        Vec3 up = right.cross(forward);
        Vec3 relative = point.subtract(view.eye());
        double depth = relative.dot(forward), tan = Math.tan(Math.toRadians(30));
        double aspect = (double) mc.getWindow().getWidth() / Math.max(1, mc.getWindow().getHeight());
        return depth > 0 && Math.abs(relative.dot(right)) <= depth * tan * aspect && Math.abs(relative.dot(up)) <= depth * tan;
    }

    private static void reload(Minecraft mc) {
        move(Phase.RELOAD);
        AssimpLoader.LOGGER.info("[AssetSmoke] Requesting resource reload through Minecraft (same reload as F3+T)");
        mc.reloadResourcePacks().whenComplete((ignored, error) -> mc.execute(() -> {
            if (phase == Phase.DONE) return;
            if (error != null) fail(mc, "resource reload failed", error);
            else { reloaded = true; move(Phase.TEXTURES); }
        }));
    }

    private static void finish(Minecraft mc) {
        View overview = views.get(0);
        MinecraftServer server = mc.getSingleplayerServer();
        if (server != null) server.execute(() -> {
            try { teleport(requirePlayer(server), overview); }
            catch (Exception error) { AssimpLoader.LOGGER.warn("[AssetSmoke] Could not return to overview", error); }
        });
        restoreOptions(mc);
        move(Phase.DONE);
        FIRST_TEXTURES.clear(); firstScene = null;
        AssimpLoader.LOGGER.info("[AssetSmoke] SUCCESS model={} screenshots={} anchor={} reload=true; client and display remain open",
                model, screenshots, origin);
    }

    private static void fail(Minecraft mc, String message, Throwable error) {
        AssimpLoader.LOGGER.error("[AssetSmoke] FAILED: " + message, error);
        restoreOptions(mc);
        move(Phase.DONE);
        FIRST_TEXTURES.clear(); firstScene = null;
    }

    private static void restoreOptions(Minecraft mc) {
        if (!optionsSaved) return;
        mc.options.hideGui = oldHideGui; mc.options.pauseOnLostFocus = oldPauseOnLostFocus;
        mc.options.setCameraType(oldCamera); mc.options.fov().set(oldFov); mc.options.bobView().set(oldBobView);
        optionsSaved = false;
    }

    private static ServerPlayer requirePlayer(MinecraftServer server) {
        ServerPlayer player = server.getPlayerList().getPlayer(playerId);
        if (player == null) throw new IllegalStateException("integrated player unavailable");
        return player;
    }

    private static AssimpScene scene() { return model == null ? null : AssimpModelLoader.getCachedModel(model.modelName()); }
    private static void move(Phase next) { phase = next; changedAt = System.currentTimeMillis(); }
    private static boolean elapsed(long millis) { return System.currentTimeMillis() - changedAt >= millis; }
}
