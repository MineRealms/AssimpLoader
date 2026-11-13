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
import cn.minerealms.assimploader.showcase.ShowcaseModel;
import cn.minerealms.assimploader.showcase.ShowcaseRegistry;
import net.minecraft.client.Minecraft;
import net.minecraft.client.Screenshot;
import net.minecraft.client.CameraType;
import net.minecraft.client.gui.screens.TitleScreen;
import net.minecraft.core.BlockPos;
import net.minecraft.network.protocol.game.ClientboundGameEventPacket;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.GameRules;
import net.minecraft.world.level.storage.ServerLevelData;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import net.minecraft.resources.ResourceLocation;
import org.lwjgl.opengl.GL11;

import java.nio.file.Files;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/** Explicit client-only PBR integration smoke run. */
@Mod.EventBusSubscriber(modid = "assimploader", value = Dist.CLIENT)
public final class PbrSmokeTest {
    private static final boolean ENABLED = Boolean.getBoolean("autotest.enabled") && Boolean.getBoolean("autotest.pbr");
    private static final boolean KEEP_OPEN = Boolean.getBoolean("autotest.pbrKeepOpen");
    private static final String RUN = Long.toString(System.currentTimeMillis());
    private static final List<BlockPos> CHANGED = new ArrayList<>();
    private static final List<BlockState> ORIGINAL = new ArrayList<>();
    private enum Phase {
        TITLE, WORLD, PREPARE, PREPARING, TEXTURES, CAPTURE_ONE, CAPTURE_NO_PBR,
        RELOAD, TEXTURES_RELOADED, CAPTURE_TWO, CAPTURE_NO_SHADERS, TEXTURES_REENABLED,
        CAPTURE_REENABLED, SHOWCASE_TEXTURES, CAPTURE_CIWS, CAPTURE_KNIFE, CAPTURE_PORTAL,
        CAPTURE_PORTAL_NO_PBR, CAPTURE_PORTAL_RELOADED, RESTORE, RESTORING, DONE
    }
    private static Phase phase = Phase.TITLE;
    private static long changedAt = System.currentTimeMillis();
    private static BlockPos origin;
    private static GameType oldMode;
    private static double oldX, oldY, oldZ;
    private static float oldYaw, oldPitch;
    private static long oldTime;
    private static int oldClearTime, oldRainTime, oldThunderTime;
    private static float oldRain, oldThunder;
    private static boolean oldRaining, oldThundering, oldDaylight, oldPbr;
    private static boolean shadersDisabled;
    private static boolean saved;
    private static boolean captureDone;
    private static boolean captureRequested;
    private static boolean oldHideGui, oldPauseOnLostFocus;
    private static CameraType oldCamera;
    private static UUID playerId;
    private static ServerLevel testLevel;
    private static long captureFrame;
    private static long renderedFrames;
    private static ShowcaseModel currentModel = ShowcaseModel.PBR_PROBE;
    private static Phase showcaseCapture;
    private static final List<AssimpPbrTexture> FIRST_TEXTURES = new ArrayList<>();
    private static final List<AssimpPbrTexture> PORTAL_TEXTURES = new ArrayList<>();
    private static Throwable failure;

    private PbrSmokeTest() {}

    @SubscribeEvent
    public static void tick(TickEvent.ClientTickEvent event) {
        if (!ENABLED || event.phase != TickEvent.Phase.END || phase == Phase.DONE) return;
        Minecraft mc = Minecraft.getInstance();
        try {
            if (System.currentTimeMillis() - changedAt > 120_000) throw new IllegalStateException("timeout in " + phase);
            switch (phase) {
                case TITLE -> {
                    if (mc.screen instanceof TitleScreen && elapsed(3000)) {
                        String world = System.getProperty("autotest.world");
                        if (world == null || world.isBlank()) throw new IllegalStateException("autotest.world is required");
                        mc.createWorldOpenFlows().loadLevel(new TitleScreen(), world);
                        move(Phase.WORLD);
                    }
                }
                case WORLD -> { if (mc.level != null && mc.player != null) move(Phase.PREPARE); }
                case PREPARE -> prepare(mc);
                case TEXTURES, TEXTURES_RELOADED, TEXTURES_REENABLED -> pollTextures(mc);
                case SHOWCASE_TEXTURES -> pollShowcase(mc);
                case CAPTURE_ONE, CAPTURE_NO_PBR, CAPTURE_TWO, CAPTURE_NO_SHADERS,
                        CAPTURE_REENABLED, CAPTURE_CIWS, CAPTURE_KNIFE, CAPTURE_PORTAL,
                        CAPTURE_PORTAL_NO_PBR, CAPTURE_PORTAL_RELOADED -> {
                    if (captureDone) {
                        captureDone = false;
                        switch (phase) {
                            case CAPTURE_ONE -> {
                                AssimpLoaderConfig.CLIENT.pbrEnabled.set(false);
                                beginCapture(Phase.CAPTURE_NO_PBR);
                            }
                            case CAPTURE_NO_PBR -> {
                                AssimpLoaderConfig.CLIENT.pbrEnabled.set(true);
                                move(Phase.RELOAD);
                                mc.reloadResourcePacks().whenComplete((ignored, error) -> mc.execute(() -> {
                                    if (error != null) fail(mc, "resource reload failed", error);
                                    else move(Phase.TEXTURES_RELOADED);
                                }));
                            }
                            case CAPTURE_TWO -> {
                                setShaders(mc, false);
                                beginCapture(Phase.CAPTURE_NO_SHADERS);
                            }
                            case CAPTURE_NO_SHADERS -> { setShaders(mc, true); move(Phase.TEXTURES_REENABLED); }
                            case CAPTURE_REENABLED -> showcase(mc, ShowcaseModel.CIWS_TEXTURED, Phase.CAPTURE_CIWS);
                            case CAPTURE_CIWS -> showcase(mc, ShowcaseModel.KNIFE_TEXTURED, Phase.CAPTURE_KNIFE);
                            case CAPTURE_KNIFE -> showcase(mc, ShowcaseModel.PORTAL_PBR, Phase.CAPTURE_PORTAL);
                            case CAPTURE_PORTAL -> {
                                AssimpLoaderConfig.CLIENT.pbrEnabled.set(false);
                                beginCapture(Phase.CAPTURE_PORTAL_NO_PBR);
                            }
                            case CAPTURE_PORTAL_NO_PBR -> {
                                AssimpLoaderConfig.CLIENT.pbrEnabled.set(true);
                                showcaseCapture = Phase.CAPTURE_PORTAL_RELOADED;
                                move(Phase.RELOAD);
                                mc.reloadResourcePacks().whenComplete((ignored, error) -> mc.execute(() -> {
                                    if (error != null) fail(mc, "portal resource reload failed", error);
                                    else move(Phase.SHOWCASE_TEXTURES);
                                }));
                            }
                            case CAPTURE_PORTAL_RELOADED -> {
                                if (KEEP_OPEN) finish(mc);
                                else move(Phase.RESTORE);
                            }
                            default -> throw new IllegalStateException("unexpected capture phase " + phase);
                        }
                    }
                }
                case PREPARING, RELOAD, RESTORING -> { }
                case RESTORE -> restore(mc);
                case DONE -> { }
            }
        } catch (Exception | LinkageError error) { fail(mc, "failure in " + phase, error); }
    }

    @SubscribeEvent
    public static void render(TickEvent.RenderTickEvent event) {
        if (!ENABLED || event.phase != TickEvent.Phase.END || phase == Phase.DONE) return;
        renderedFrames++;
        if (!isCapturePhase() || captureRequested || renderedFrames - captureFrame < 60 || !elapsed(5000)) return;
        Minecraft mc = Minecraft.getInstance();
        if (mc.screen != null || mc.getOverlay() != null) return;
        try { capture(mc); }
        catch (Exception | LinkageError error) { fail(mc, "screenshot failed", error); }
    }

    private static void prepare(Minecraft mc) {
        MinecraftServer server = mc.getSingleplayerServer();
        if (server == null || mc.player == null) throw new IllegalStateException("integrated server unavailable");
        playerId = mc.player.getUUID();
        oldHideGui = mc.options.hideGui;
        oldPauseOnLostFocus = mc.options.pauseOnLostFocus;
        oldCamera = mc.options.getCameraType();
        oldPbr = AssimpLoaderConfig.CLIENT.pbrEnabled.get();
        AssimpLoaderConfig.CLIENT.pbrEnabled.set(true);
        mc.options.hideGui = true;
        mc.options.pauseOnLostFocus = false;
        mc.options.setCameraType(CameraType.FIRST_PERSON);
        move(Phase.PREPARING);
        server.execute(() -> {
            try { prepareWorld(mc, server); }
            catch (Exception | LinkageError error) { mc.execute(() -> fail(mc, "world preparation failed", error)); }
        });
    }

    private static void prepareWorld(Minecraft mc, MinecraftServer server) {
        ServerPlayer player = server.getPlayerList().getPlayer(playerId);
        if (player == null) throw new IllegalStateException("integrated player unavailable");
        oldX = player.getX(); oldY = player.getY(); oldZ = player.getZ();
        oldYaw = player.getYRot(); oldPitch = player.getXRot(); oldMode = player.gameMode.getGameModeForPlayer();
        testLevel = player.serverLevel();
        oldTime = testLevel.getDayTime();
        ServerLevelData weather = (ServerLevelData) player.serverLevel().getLevelData();
        oldClearTime = weather.getClearWeatherTime(); oldRainTime = weather.getRainTime();
        oldThunderTime = weather.getThunderTime(); oldRaining = weather.isRaining(); oldThundering = weather.isThundering();
        oldRain = player.serverLevel().getRainLevel(1); oldThunder = player.serverLevel().getThunderLevel(1);
        oldDaylight = player.serverLevel().getGameRules().getBoolean(GameRules.RULE_DAYLIGHT);
        saved = true;
        BlockPos base = player.blockPosition().above(3);
        BlockPos chosen = null;
        for (int dy = 0; dy < 16 && chosen == null; dy++) {
            BlockPos candidate = base.above(dy);
            boolean clear = true;
            for (int x = -2; x <= 2 && clear; x++) for (int y = -1; y < 5 && clear; y++) for (int z = -6; z <= 6; z++) {
                BlockPos pos = candidate.offset(x, y, z);
                if (!player.serverLevel().isInWorldBounds(pos) || !player.serverLevel().hasChunkAt(pos)
                        || !player.serverLevel().getBlockState(pos).isAir()) { clear = false; break; }
            }
            if (clear) chosen = candidate;
        }
        if (chosen == null) throw new IllegalStateException("no loaded air volume above player for probe and camera");
        origin = chosen;
        for (int x = -2; x <= 2; x++) for (int z = -1; z <= 1; z++) {
            BlockPos pos = origin.offset(x, -1, z);
            ORIGINAL.add(player.serverLevel().getBlockState(pos));
            CHANGED.add(pos);
            player.serverLevel().setBlock(pos, Blocks.SMOOTH_STONE.defaultBlockState(), 3);
        }
        ORIGINAL.add(player.serverLevel().getBlockState(origin));
        CHANGED.add(origin);
        player.serverLevel().setBlock(origin, ShowcaseRegistry.block(ShowcaseModel.PBR_PROBE).defaultBlockState(), 3);
        player.setGameMode(GameType.SPECTATOR);
        player.serverLevel().setWeatherParameters(12000, 0, false, false);
        player.serverLevel().setRainLevel(0); player.serverLevel().setThunderLevel(0);
        syncWeather(player, false, 0, 0);
        player.serverLevel().getGameRules().getRule(GameRules.RULE_DAYLIGHT).set(false, server);
        player.serverLevel().setDayTime(6000);
        player.connection.teleport(origin.getX() + 0.5, origin.getY() - 0.1, origin.getZ() - 2.4, 0, 22);
        AssimpLoader.LOGGER.info("[PbrSmoke] Prepared probe at {}", origin);
        mc.execute(() -> move(Phase.TEXTURES));
    }

    private static void pollTextures(Minecraft mc) {
        if (!OculusCompat.isShadersActive()) throw new IllegalStateException("Oculus shader pack is not active");
        AssimpScene scene = AssimpModelLoader.getCachedModel(ShowcaseModel.PBR_PROBE.modelName());
        if (scene == null) return;
        if (scene.getMaterials().size() != 3) throw new IllegalStateException("expected 3 PBR materials, got " + scene.getMaterials().size());
        List<Integer> bases = new ArrayList<>();
        List<Integer> normals = new ArrayList<>();
        List<Integer> specs = new ArrayList<>();
        int minSmoothness = 255, maxSmoothness = 0, minReflectance = 255, maxReflectance = 0;
        List<AssimpPbrTexture> readyTextures = new ArrayList<>();
        List<String> descriptions = new ArrayList<>();
        for (AssimpMaterial material : scene.getMaterials()) {
            ResourceLocation id = ModelTextureCache.getTexture(scene, material);
            var status = ModelTextureCache.status(scene, material);
            if (status.failed()) throw new IllegalStateException("texture preparation failed for " + material.getName());
            if (!status.ready() || !status.normalImage() || !status.specularImage()) return;
            OculusPBRSupport.prepareTexture(id);
            var texture = Minecraft.getInstance().getTextureManager().getTexture(id);
            if (!(texture instanceof AssimpPbrTexture pbr) || !pbr.isReady() || !pbr.hasNormalMap() || !pbr.hasSpecularMap()) return;
            if (pbr.normalTextureId() <= 0 || pbr.specularTextureId() <= 0) return;
            if (phase == Phase.TEXTURES_RELOADED && FIRST_TEXTURES.contains(pbr)) return;
            readyTextures.add(pbr);
            bases.add(texture.getId()); normals.add(pbr.normalTextureId()); specs.add(pbr.specularTextureId());
            int packed = pbr.maps().specular().pixel(0, 0);
            minSmoothness = Math.min(minSmoothness, packed & 255);
            maxSmoothness = Math.max(maxSmoothness, packed & 255);
            minReflectance = Math.min(minReflectance, (packed >>> 8) & 255);
            maxReflectance = Math.max(maxReflectance, (packed >>> 8) & 255);
            descriptions.add(material.getName() + " normal=" + status.normalSource() + " specular=" + status.specularSource()
                    + " smoothness=" + (packed & 255) + " reflectance=" + ((packed >>> 8) & 255));
        }
        if (bases.stream().distinct().count() != 3 || normals.stream().distinct().count() != 3 || specs.stream().distinct().count() != 3)
            throw new IllegalStateException("material GL texture IDs are not independent: base=" + bases + " normal=" + normals + " specular=" + specs);
        if (maxSmoothness - minSmoothness < 25) throw new IllegalStateException("packed images do not distinguish matte and polished materials");
        if (minReflectance >= 230 || maxReflectance < 230) throw new IllegalStateException("packed images do not distinguish dielectric and metal materials");
        if (phase == Phase.TEXTURES) FIRST_TEXTURES.addAll(readyTextures);
        descriptions.forEach(description -> AssimpLoader.LOGGER.info("[PbrSmoke] {} {}", phase, description));
        AssimpLoader.LOGGER.info("[PbrSmoke] {} textures ready; shaderActive={} base={} normal={} specular={}", phase, OculusCompat.isShadersActive(), bases, normals, specs);
        beginCapture(phase == Phase.TEXTURES ? Phase.CAPTURE_ONE
                : phase == Phase.TEXTURES_RELOADED ? Phase.CAPTURE_TWO : Phase.CAPTURE_REENABLED);
    }

    private static void beginCapture(Phase next) {
        captureFrame = renderedFrames;
        captureRequested = false;
        move(next);
    }

    private static boolean isCapturePhase() {
        return switch (phase) {
            case CAPTURE_ONE, CAPTURE_NO_PBR, CAPTURE_TWO, CAPTURE_NO_SHADERS,
                    CAPTURE_REENABLED, CAPTURE_CIWS, CAPTURE_KNIFE, CAPTURE_PORTAL,
                    CAPTURE_PORTAL_NO_PBR, CAPTURE_PORTAL_RELOADED -> true;
            default -> false;
        };
    }

    private static void capture(Minecraft mc) {
        boolean expectedShaders = phase != Phase.CAPTURE_NO_SHADERS;
        if (OculusCompat.isShadersActive() != expectedShaders) throw new IllegalStateException("unexpected shader state before screenshot");
        if (phase == Phase.CAPTURE_NO_PBR || phase == Phase.CAPTURE_PORTAL_NO_PBR) {
            var scene = AssimpModelLoader.getCachedModel(currentModel.modelName());
            for (var material : scene.getMaterials()) {
                ModelTextureCache.getTexture(scene, material);
                var status = ModelTextureCache.status(scene, material);
                if (status.normalTextureId() != 0 || status.specularTextureId() != 0)
                    throw new IllegalStateException("PBR disabled but authored channels are still bound");
            }
        }
        int error = GL11.glGetError();
        if (error != GL11.GL_NO_ERROR) throw new IllegalStateException("OpenGL error before screenshot: " + error);
        String suffix = switch (phase) {
            case CAPTURE_ONE -> "world";
            case CAPTURE_NO_PBR -> "pbr-off";
            case CAPTURE_TWO -> "reload";
            case CAPTURE_NO_SHADERS -> "shaders-off";
            case CAPTURE_REENABLED -> "shaders-on";
            case CAPTURE_CIWS -> "ciws-textured";
            case CAPTURE_KNIFE -> "knife-textured";
            case CAPTURE_PORTAL -> "portal-pbr";
            case CAPTURE_PORTAL_NO_PBR -> "portal-pbr-off";
            case CAPTURE_PORTAL_RELOADED -> "portal-reload";
            default -> throw new IllegalStateException("not capturing");
        };
        String name = "assimp-pbr-smoke-" + RUN + "-" + suffix + ".png";
        captureRequested = true;
        Screenshot.grab(mc.gameDirectory, name, mc.getMainRenderTarget(), message -> mc.execute(() -> {
            if (!Files.isRegularFile(mc.gameDirectory.toPath().resolve("screenshots").resolve(name))) fail(mc, "screenshot failed: " + message.getString(), null);
            else { AssimpLoader.LOGGER.info("[PbrSmoke] Captured {} shaderActive={}", name, OculusCompat.isShadersActive()); captureDone = true; }
        }));
    }

    private static void setShaders(Minecraft mc, boolean enabled) throws ReflectiveOperationException {
        Class<?> iris = Class.forName("net.irisshaders.iris.Iris");
        iris.getMethod("toggleShaders", Minecraft.class, boolean.class).invoke(null, mc, enabled);
        shadersDisabled = !enabled;
        OculusCompat.reset();
    }

    private static void showcase(Minecraft mc, ShowcaseModel model, Phase capture) {
        var scene = AssimpModelLoader.getCachedModel(model.modelName());
        if (scene == null) throw new IllegalStateException("showcase model missing: " + model);
        var bounds = SceneTransforms.bounds(scene);
        double framing = model == ShowcaseModel.PORTAL_PBR ? 1.15 : 1.6;
        double distance = Math.max(2, Math.max(bounds.getXsize(), Math.max(bounds.getYsize(), bounds.getZsize())) * framing);
        var center = bounds.getCenter();
        var server = mc.getSingleplayerServer();
        if (server == null) throw new IllegalStateException("integrated server unavailable");
        move(Phase.PREPARING);
        server.execute(() -> {
            try {
                ServerPlayer player = server.getPlayerList().getPlayer(playerId);
                if (player == null || !player.serverLevel().getBlockState(origin).is(ShowcaseRegistry.block(currentModel)))
                    throw new IllegalStateException("showcase block changed during test");
                player.serverLevel().setBlock(origin, ShowcaseRegistry.block(model).defaultBlockState(), 3);
                player.connection.teleport(origin.getX() + 0.5 + center.x + distance * 0.6,
                        origin.getY() + center.y + distance * 0.25 - player.getEyeHeight(),
                        origin.getZ() + 0.5 + center.z + distance * 0.8, 143.13f, 14.04f);
                mc.execute(() -> { currentModel = model; showcaseCapture = capture; move(Phase.SHOWCASE_TEXTURES); });
            } catch (Exception | LinkageError error) { mc.execute(() -> fail(mc, "showcase preparation failed", error)); }
        });
    }

    private static void pollShowcase(Minecraft mc) {
        if (mc.level == null || !mc.level.getBlockState(origin).is(ShowcaseRegistry.block(currentModel))) return;
        var scene = AssimpModelLoader.getCachedModel(currentModel.modelName());
        if (scene == null) return;
        int transparent = 0, normals = 0, roughness = 0, metallic = 0;
        List<AssimpPbrTexture> portalReady = new ArrayList<>();
        for (var material : scene.getMaterials()) {
            ResourceLocation location = ModelTextureCache.getTexture(scene, material);
            var status = ModelTextureCache.status(scene, material);
            if (status.failed()) throw new IllegalStateException("showcase texture preparation failed");
            if (!status.ready()) return;
            if (currentModel == ShowcaseModel.PORTAL_PBR) {
                if (material.isTransparent()) transparent++;
                var inputs = material.getPbrInputs();
                if (inputs.normal() != null) normals++;
                if (inputs.roughness() != null) roughness++;
                if (inputs.metallic() != null) metallic++;
                OculusPBRSupport.prepareTexture(location);
                var texture = mc.getTextureManager().getTexture(location);
                if (!(texture instanceof AssimpPbrTexture pbr) || !pbr.isReady()) return;
                if (inputs.normal() != null && pbr.normalTextureId() <= 0) return;
                if (pbr.hasSpecularMap() && pbr.specularTextureId() <= 0) return;
                if (showcaseCapture == Phase.CAPTURE_PORTAL_RELOADED && PORTAL_TEXTURES.contains(pbr)) return;
                portalReady.add(pbr);
            }
        }
        if (currentModel == ShowcaseModel.PORTAL_PBR) {
            if (scene.getMeshes().size() < 5 || scene.getMaterials().size() < 5 || transparent < 1
                    || normals < 4 || roughness < 4 || metallic < 4)
                throw new IllegalStateException("portal lost multi-part/PBR/glass bindings");
            if (showcaseCapture == Phase.CAPTURE_PORTAL) PORTAL_TEXTURES.addAll(portalReady);
            AssimpLoader.LOGGER.info("[PbrSmoke] Portal ready; meshes={} materials={} transparent={} normals={} roughness={} metallic={} reload={}",
                    scene.getMeshes().size(), scene.getMaterials().size(), transparent, normals, roughness, metallic,
                    showcaseCapture == Phase.CAPTURE_PORTAL_RELOADED);
        }
        beginCapture(showcaseCapture);
    }

    private static void restore(Minecraft mc) {
        if (phase == Phase.RESTORING) return;
        move(Phase.RESTORING);
        if (oldCamera != null) AssimpLoaderConfig.CLIENT.pbrEnabled.set(oldPbr);
        if (shadersDisabled) {
            try { setShaders(mc, true); }
            catch (ReflectiveOperationException error) { failure = error; AssimpLoader.LOGGER.error("[PbrSmoke] FAILED: shader restoration", error); }
        }
        MinecraftServer server = mc.getSingleplayerServer();
        if (server != null && saved) server.execute(() -> {
            try {
                for (int i = 0; i < CHANGED.size(); i++) {
                    BlockPos pos = CHANGED.get(i);
                    BlockState current = testLevel.getBlockState(pos);
                    if ((pos.equals(origin) && current.is(ShowcaseRegistry.block(currentModel)))
                            || (!pos.equals(origin) && current.is(Blocks.SMOOTH_STONE)))
                        testLevel.setBlock(pos, ORIGINAL.get(i), 3);
                }
                testLevel.setDayTime(oldTime);
                testLevel.getGameRules().getRule(GameRules.RULE_DAYLIGHT).set(oldDaylight, server);
                ServerLevelData weather = (ServerLevelData) testLevel.getLevelData();
                weather.setClearWeatherTime(oldClearTime); weather.setRainTime(oldRainTime); weather.setThunderTime(oldThunderTime);
                weather.setRaining(oldRaining); weather.setThundering(oldThundering);
                testLevel.setRainLevel(oldRain); testLevel.setThunderLevel(oldThunder);
                ServerPlayer player = server.getPlayerList().getPlayer(playerId);
                if (player != null && player.serverLevel() == testLevel) {
                    syncWeather(player, oldRaining, oldRain, oldThunder);
                    player.setGameMode(oldMode);
                    player.connection.teleport(oldX, oldY, oldZ, oldYaw, oldPitch);
                }
                mc.execute(() -> finish(mc));
            } catch (Exception | LinkageError error) {
                mc.execute(() -> fail(mc, "world restoration failed", error));
            }
        });
        else finish(mc);
    }

    private static void syncWeather(ServerPlayer player, boolean raining, float rain, float thunder) {
        // Direct level changes bypass the server tick's weather-delta detection.
        player.connection.send(new ClientboundGameEventPacket(raining
                ? ClientboundGameEventPacket.START_RAINING : ClientboundGameEventPacket.STOP_RAINING, 0));
        player.connection.send(new ClientboundGameEventPacket(ClientboundGameEventPacket.RAIN_LEVEL_CHANGE, rain));
        player.connection.send(new ClientboundGameEventPacket(ClientboundGameEventPacket.THUNDER_LEVEL_CHANGE, thunder));
    }

    private static void fail(Minecraft mc, String message, Throwable error) {
        if (phase == Phase.DONE) return;
        if (failure == null) failure = new IllegalStateException(message, error);
        AssimpLoader.LOGGER.error("[PbrSmoke] FAILED: " + message, error);
        if (phase == Phase.RESTORING) { finish(mc); return; }
        move(Phase.RESTORE);
        restore(mc);
    }

    private static void finish(Minecraft mc) {
        if (oldCamera != null) {
            mc.options.hideGui = oldHideGui;
            mc.options.pauseOnLostFocus = oldPauseOnLostFocus;
            mc.options.setCameraType(oldCamera);
        }
        if (failure == null) AssimpLoader.LOGGER.info("[PbrSmoke] SUCCESS: authored normal/specular children, independent material IDs, PBR toggle, resource reload, shader toggle, textured CIWS/knife, multi-part PBR/glass portal, ten world screenshots; run={} keepOpen={} origin={}", RUN, KEEP_OPEN, origin);
        phase = Phase.DONE;
        if (!KEEP_OPEN) mc.stop();
    }

    private static boolean elapsed(long millis) { return System.currentTimeMillis() - changedAt > millis; }
    private static void move(Phase next) { phase = next; changedAt = System.currentTimeMillis(); }
}
