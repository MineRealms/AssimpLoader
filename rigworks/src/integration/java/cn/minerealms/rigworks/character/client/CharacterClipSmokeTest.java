package cn.minerealms.rigworks.character.client;

import cn.minerealms.assimploader.AssimpLoader;
import cn.minerealms.assimploader.animation.AnimationState;
import cn.minerealms.assimploader.animation.PoseSnapshot;
import cn.minerealms.assimploader.compat.OculusCompat;
import cn.minerealms.assimploader.compat.OculusPBRSupport;
import cn.minerealms.assimploader.model.AssimpNode;
import cn.minerealms.assimploader.render.ModelTextureCache;
import cn.minerealms.assimploader.rendering.OculusEntityBufferCache;
import cn.minerealms.rigworks.RigWorks;
import cn.minerealms.rigworks.RigWorksConfig;
import com.google.gson.GsonBuilder;
import net.minecraft.client.CameraType;
import net.minecraft.client.KeyMapping;
import net.minecraft.client.Minecraft;
import net.minecraft.client.Screenshot;
import net.minecraft.client.gui.screens.TitleScreen;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.eventbus.api.EventPriority;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

import java.io.IOException;
import java.nio.file.Files;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/** Opt-in acceptance of the configured imported character; leaves the client and selected profile open. */
@Mod.EventBusSubscriber(modid = RigWorks.MODID, value = Dist.CLIENT)
public final class CharacterClipSmokeTest {
    private static final boolean ENABLED = Boolean.getBoolean("charactercliptest.enabled");
    private static final boolean REQUIRE_OCULUS_GPU = Boolean.getBoolean("charactercliptest.requireOculusGpu");
    private static final String RUN = Long.toString(System.currentTimeMillis());
    private static final String[] STAGES = REQUIRE_OCULUS_GPU
            ? new String[]{"idle", "walk", "run", "walk_hops", "run_hops", "crouch"} : new String[]{"idle", "walk", "run"};
    private static final long TIMEOUT = Math.max(30L, Long.getLong("charactercliptest.timeoutSeconds", 300L)) * 1000;
    private static final List<Map<String, Object>> RESULTS = new ArrayList<>();
    private static final List<String> NODES = new ArrayList<>();
    private static final long STARTED = System.currentTimeMillis();
    private static int stage = -1, sampledTicks;
    private static long changed = STARTED, passes, firstFrame, firstGpuDispatches, firstUploads;
    private static boolean finished, openingWorld, prepared, pending, oldPause, oldGui, positioned, oldMouseGrabbed;
    private static String profile;
    private static RigWorksConfig.CharacterAnimationMode mode;
    private static AnimationState animation;
    private static Map<String, float[]> baseline;
    private static BlockPos fixture;
    private static float maximumPoseChange;
    private static boolean passed;
    private static int positionedStage = -1, hops, hopWarmup;
    private static boolean previouslyGrounded = true;

    private CharacterClipSmokeTest() {}

    static boolean succeeded() { return finished && passed; }

    @SubscribeEvent(priority = EventPriority.HIGHEST)
    public static void input(TickEvent.ClientTickEvent event) {
        if (!ENABLED || finished || !prepared || pending || event.phase != TickEvent.Phase.START) return;
        Minecraft mc = Minecraft.getInstance();
        if (mc.player == null || mc.level == null || mc.screen != null || mc.getOverlay() != null) return;
        KeyMapping.releaseAll();
        String current = STAGES[stage];
        boolean hopping = current.endsWith("_hops"), sprinting = current.startsWith("run");
        if (hopping && (positionedStage != stage || hopWarmup < 6)) { mc.player.setSprinting(false); return; }
        mc.options.keyUp.setDown(current.startsWith("walk") || sprinting);
        mc.options.keySprint.setDown(sprinting);
        mc.options.keyShift.setDown("crouch".equals(current));
        mc.options.keyJump.setDown(hopping && hopWarmup >= 10);
        mc.player.setSprinting(sprinting);
    }

    @SubscribeEvent(priority = EventPriority.LOWEST)
    public static void tick(TickEvent.ClientTickEvent event) {
        if (!ENABLED || finished || event.phase != TickEvent.Phase.END) return;
        Minecraft mc = Minecraft.getInstance();
        try {
            require(System.currentTimeMillis() - STARTED < TIMEOUT, "Acceptance timed out: " + LocalPlayerCharacter.diagnostics());
            if (mc.player == null || mc.level == null) {
                String world = System.getProperty("charactercliptest.world", "");
                if (!openingWorld && !world.isBlank() && mc.screen instanceof TitleScreen) {
                    openingWorld = true;
                    mc.createWorldOpenFlows().loadLevel(new TitleScreen(), world);
                }
                return;
            }
            if (mc.screen != null || mc.getOverlay() != null || mc.isPaused() || pending) return;
            var diagnostics = LocalPlayerCharacter.diagnostics();
            require(diagnostics.status() != LocalPlayerCharacter.Status.FAILED, diagnostics.toString());
            if (!prepared) {
                require(LocalPlayerCharacter.settings().enabled(), "Configured character is disabled");
                mode = LocalPlayerCharacter.animationMode();
                require(mode == RigWorksConfig.CharacterAnimationMode.CLIPS || mode == RigWorksConfig.CharacterAnimationMode.SCRIPTED,
                        "Select CLIPS or a SCRIPTED imported-clip controller before enabling this test");
                profile = LocalPlayerCharacter.settings().profile();
                require(!LocalPlayerCharacter.DEFAULT_PROFILE.equals(profile), "Select the imported player profile before testing");
                String expected = System.getProperty("charactercliptest.profile", profile);
                require(expected.equals(profile), "Configured profile differs from requested acceptance profile: " + profile);
                if (diagnostics.status() != LocalPlayerCharacter.Status.READY) return;
                if (REQUIRE_OCULUS_GPU && !positioned) {
                    var server = mc.getSingleplayerServer();
                    require(server != null, "GPU acceptance requires a disposable singleplayer world");
                    var playerId = mc.player.getUUID();
                    int originX = mc.player.getBlockX(), originZ = mc.player.getBlockZ();
                    pending = true;
                    server.execute(() -> {
                        try {
                            var player = server.getPlayerList().getPlayer(playerId);
                            require(player != null, "Test player left during fixture preparation");
                            BlockPos lane = findLane(player.serverLevel(), originX, originZ);
                            player.setItemInHand(InteractionHand.MAIN_HAND, new ItemStack(Items.DIAMOND_SWORD));
                            player.setItemInHand(InteractionHand.OFF_HAND, new ItemStack(Items.TORCH));
                            player.teleportTo(player.serverLevel(), lane.getX() + .5, lane.getY(), lane.getZ() + .5, 0, 0);
                            mc.execute(() -> { fixture = lane; positioned = true; pending = false; });
                        } catch (Exception failure) {
                            mc.execute(() -> complete(mc, failure));
                        }
                    });
                    return;
                }
                if (REQUIRE_OCULUS_GPU && !mc.player.onGround()) return;
                require(mc.player.onGround() && !mc.player.getAbilities().flying && !mc.player.isPassenger(),
                        "Stand on clear level ground before starting the locomotion acceptance");
                animation = LocalPlayerCharacter.currentAnimation();
                require(animation != null, "READY character has no animation");
                collect(animation.getScene().getRootNode());
                oldPause = mc.options.pauseOnLostFocus;
                oldGui = mc.options.hideGui;
                mc.options.pauseOnLostFocus = false;
                mc.options.hideGui = true;
                mc.options.setCameraType(CameraType.THIRD_PERSON_FRONT);
                if (REQUIRE_OCULUS_GPU) {
                    mc.getWindow().setTitle("Minecraft 1.20.1 - AssimpLoader GPU acceptance");
                    oldMouseGrabbed = mc.mouseHandler.isMouseGrabbed();
                    mc.mouseHandler.releaseMouse();
                    mc.player.setYRot(0); mc.player.yRotO = 0;
                    mc.player.setXRot(0); mc.player.xRotO = 0;
                    mc.player.yBodyRot = mc.player.yBodyRotO = 0;
                    mc.player.yHeadRot = mc.player.yHeadRotO = 0;
                }
                prepared = true;
                stage = 0;
                changed = System.currentTimeMillis();
                AssimpLoader.LOGGER.info("[CharacterClipSmoke] START run={} profile={} mode={} controller={}", RUN, profile, mode, CharacterScriptRuntime.active());
            }
            require(profile.equals(LocalPlayerCharacter.settings().profile()), "Selected profile changed during acceptance");
            require(LocalPlayerCharacter.animationMode() == mode, "Animation mode changed during acceptance");
            if (mode == RigWorksConfig.CharacterAnimationMode.SCRIPTED) {
                require(CharacterScriptRuntime.active() != null, "SCRIPTED mode has no active KubeJS controller");
                require(CharacterScriptRuntime.failure().isBlank(), "KubeJS controller failed: " + CharacterScriptRuntime.failure());
                require(CharacterScriptRuntime.reloadFailure().isBlank(), "KubeJS reload failed: " + CharacterScriptRuntime.reloadFailure());
            }
            require(LocalPlayerCharacter.currentAnimation() == animation && !animation.isClosed(), "Character binding changed during acceptance");
            require(System.currentTimeMillis() - changed < 60_000, "Stage did not settle: " + STAGES[stage] + " " + diagnostics);
            if (diagnostics.status() != LocalPlayerCharacter.Status.READY) return;

            String intent = STAGES[stage];
            boolean hopping = intent.endsWith("_hops");
            if (hopping && positionedStage != stage) {
                pending = true;
                KeyMapping.releaseAll();
                var server = mc.getSingleplayerServer();
                var id = mc.player.getUUID();
                server.execute(() -> {
                    try {
                        var player = server.getPlayerList().getPlayer(id);
                        require(player != null, "Player left during moving-hop setup");
                        player.setDeltaMovement(net.minecraft.world.phys.Vec3.ZERO);
                        player.fallDistance = 0;
                        player.teleportTo(player.serverLevel(), fixture.getX() + .5, fixture.getY(), fixture.getZ() + .5, 0, 0);
                        mc.execute(() -> { positionedStage = stage; pending = false; });
                    } catch (Exception failure) { mc.execute(() -> complete(mc, failure)); }
                });
                return;
            }
            if (hopping) {
                if (hopWarmup < 10) { hopWarmup++; return; }
                if (!mc.player.onGround()) {
                    if (previouslyGrounded) hops++;
                }
                previouslyGrounded = mc.player.onGround();
            }
            String clipIntent = "crouch".equals(intent) ? "idle" : intent.replace("_hops", "");
            if ("crouch".equals(intent) && !mc.player.isCrouching()) return;
            String binding = LocalPlayerCharacter.currentProfile().animation(clipIntent);
            if (mode == RigWorksConfig.CharacterAnimationMode.CLIPS) {
                if (!clipIntent.equals(diagnostics.requestedAnimation()) || !binding.equals(diagnostics.selectedAnimation())) return;
            } else if (!binding.equals(animation.getStateMachine().getCurrentStateName())) return;
            String expectedClip = animation.getStateMachine().getBindings().containsKey(binding)
                    ? animation.getStateMachine().getBindings().get(binding).clip() : binding;
            var playing = animation.getCurrentAnimation();
            if (playing == null || !expectedClip.equals(playing.getAnimation().getName())) return;
            var pose = animation.getPoseSnapshot();
            Map<String, float[]> current = matrices(pose);
            if (baseline == null) {
                baseline = current;
                passes = diagnostics.renderedPasses();
                firstFrame = pose.getFrameId();
                firstGpuDispatches = OculusEntityBufferCache.gpuPoseStatistics().dispatches();
                firstUploads = OculusEntityBufferCache.poseStatistics().uploads();
            } else maximumPoseChange = Math.max(maximumPoseChange, difference(baseline, current));
            sampledTicks++;
            List<Map<String, Object>> materials = materials();
            if (sampledTicks < 30 || System.currentTimeMillis() - changed < 2_000
                    || hopping && (hops < 3 || mc.player.onGround() || mc.player.getDeltaMovement().y <= .05)
                    || maximumPoseChange <= 1e-5f || pose.getFrameId() <= firstFrame
                    || diagnostics.renderedPasses() <= passes || !texturesReady(materials)) return;

            var poseCache = OculusEntityBufferCache.poseStatistics();
            var gpu = OculusEntityBufferCache.gpuPoseStatistics();
            if (REQUIRE_OCULUS_GPU) {
                require(OculusCompat.isShadersActive(), "GPU acceptance requires the selected Oculus shader pack");
                require(!OculusEntityBufferCache.isPoseCacheFailed() && !gpu.failed(), "Oculus GPU pose cache failed");
                require(OculusEntityBufferCache.isPoseCached(animation) && gpu.buffers() > 0, "No GPU-deformed character VBO");
                require(gpu.dispatches() > firstGpuDispatches, "GPU deformation did not advance with animation");
                require(poseCache.uploads() - firstUploads <= animation.getScene().getMeshes().size(),
                        "Animated VBO data was repeatedly uploaded from the CPU");
            }
            if (hopping) require(mc.options.keyJump.isDown() && mc.player.getDeltaMovement().horizontalDistance() > .05,
                    "Hop acceptance must move while holding jump");

            var result = new LinkedHashMap<String, Object>();
            result.put("stage", intent);
            result.put("requested", diagnostics.requestedAnimation());
            result.put("selected", diagnostics.selectedAnimation());
            result.put("clip", playing.getAnimation().getName());
            result.put("state", animation.getStateMachine().getCurrentStateName());
            result.put("playerPose", mc.player.getPose().name());
            result.put("crouching", mc.player.isCrouching());
            result.put("movingHops", hops);
            result.put("jumpHeld", mc.options.keyJump.isDown());
            result.put("horizontalSpeed", mc.player.getDeltaMovement().horizontalDistance() * 20);
            result.put("verticalVelocity", mc.player.getDeltaMovement().y * 20);
            result.put("controller", CharacterScriptRuntime.active());
            result.put("clipTimeTicks", playing.getCurrentTime());
            result.put("sampledTicks", sampledTicks);
            result.put("poseFrames", List.of(firstFrame, pose.getFrameId()));
            result.put("maxBoneMatrixDifference", maximumPoseChange);
            result.put("renderedPasses", diagnostics.renderedPasses() - passes);
            result.put("materials", materials);
            result.put("shadersActive", OculusCompat.isShadersActive());
            result.put("oculusPbrAvailable", OculusPBRSupport.isPBRAvailable());
            result.put("fpsAtCapture", mc.getFps());
            result.put("poseCache", poseCache);
            result.put("gpuPoseCache", gpu);
            result.put("gpuDispatchesDuringStage", gpu.dispatches() - firstGpuDispatches);
            result.put("cpuVboUploadsDuringStage", poseCache.uploads() - firstUploads);
            String capture = "assimp-character-clips-" + RUN + "-" + intent + ".png";
            result.put("screenshot", "screenshots/" + capture);
            pending = true;
            KeyMapping.releaseAll();
            Screenshot.grab(mc.gameDirectory, capture, mc.getMainRenderTarget(), message -> mc.execute(() -> {
                try {
                    require(Files.isRegularFile(mc.gameDirectory.toPath().resolve("screenshots").resolve(capture)), "Missing screenshot " + capture);
                    RESULTS.add(result);
                    AssimpLoader.LOGGER.info("[CharacterClipSmoke] PASS run={} stage={} clip={} poseChange={}", RUN, intent, expectedClip, maximumPoseChange);
                    stage++;
                    baseline = null;
                    maximumPoseChange = 0;
                    sampledTicks = 0;
                    hops = hopWarmup = 0;
                    previouslyGrounded = true;
                    pending = false;
                    changed = System.currentTimeMillis();
                    if (stage == STAGES.length) complete(mc, null);
                } catch (Exception | LinkageError failure) { complete(mc, failure); }
            }));
        } catch (Exception | LinkageError failure) { complete(mc, failure); }
    }

    private static BlockPos findLane(ServerLevel level, int originX, int originZ) {
        int[][] candidates = {{64, 0}, {-64, 0}, {0, 64}, {0, -64}, {64, 64}, {-64, -64}, {128, 0}, {0, 128}};
        for (int[] offset : candidates) {
            int x = originX + offset[0], z = originZ + offset[1];
            int y = level.getHeight(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, x, z);
            boolean clear = true;
            for (int dx = -3; dx <= 3 && clear; dx++) {
                for (int dz = -6; dz <= 48; dz++) {
                    BlockPos feet = new BlockPos(x + dx, y, z + dz);
                    if (level.getHeight(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, feet.getX(), feet.getZ()) != y
                            || !level.getBlockState(feet.below()).isFaceSturdy(level, feet.below(), Direction.UP)
                            || !level.isEmptyBlock(feet) || !level.isEmptyBlock(feet.above())
                            || !level.isEmptyBlock(feet.above(2)) || !level.isEmptyBlock(feet.above(3))) {
                        clear = false;
                        break;
                    }
                }
            }
            if (clear) return new BlockPos(x, y, z);
        }
        throw new IllegalStateException("No clear flat acceptance lane near the disposable world's player");
    }

    private static Map<String, float[]> matrices(PoseSnapshot pose) {
        Map<String, float[]> result = new LinkedHashMap<>();
        for (String node : NODES) {
            var matrix = pose.getNodeTransform(node);
            require(matrix.isFinite(), "Non-finite pose matrix for " + node);
            result.put(node, matrix.get(new float[16]));
        }
        return result;
    }

    private static float difference(Map<String, float[]> before, Map<String, float[]> after) {
        float maximum = 0;
        for (var entry : before.entrySet()) {
            float[] a = entry.getValue(), b = after.get(entry.getKey());
            for (int i = 0; i < a.length; i++) maximum = Math.max(maximum, Math.abs(a[i] - b[i]));
        }
        return maximum;
    }

    private static void collect(AssimpNode node) {
        if (node == null) return;
        NODES.add(node.getName());
        for (AssimpNode child : node.getChildren()) collect(child);
    }

    private static List<Map<String, Object>> materials() {
        List<Map<String, Object>> result = new ArrayList<>();
        if (animation == null || animation.isClosed()) return result;
        for (var material : animation.getScene().getMaterials()) {
            var texture = ModelTextureCache.status(animation.getScene(), material);
            var inputs = material.getPbrInputs();
            var item = new LinkedHashMap<String, Object>();
            item.put("material", material.getName());
            item.put("albedo", texture.albedo().toString());
            item.put("diffuseReference", material.getDiffuseTexture());
            item.put("ready", texture.ready());
            item.put("failed", texture.failed());
            item.put("normalImage", texture.normalImage());
            item.put("specularImage", texture.specularImage());
            item.put("normalTextureId", texture.normalTextureId());
            item.put("specularTextureId", texture.specularTextureId());
            item.put("normalSource", texture.normalSource());
            item.put("specularSource", texture.specularSource());
            item.put("normalRequired", inputs.normal() != null && inputs.normal().uvIndex() == 0);
            item.put("specularRequired", inputs.roughness() != null || inputs.metallic() != null);
            result.add(item);
        }
        return result;
    }

    private static boolean texturesReady(List<Map<String, Object>> materials) {
        if (materials.isEmpty()) return false;
        boolean shaders = OculusCompat.isShadersActive();
        for (var material : materials) {
            require(!Boolean.TRUE.equals(material.get("failed")), "Texture preparation failed: " + material);
            if (!Boolean.TRUE.equals(material.get("ready"))) return false;
            if (Boolean.TRUE.equals(material.get("normalRequired")) && !Boolean.TRUE.equals(material.get("normalImage"))) return false;
            if (Boolean.TRUE.equals(material.get("specularRequired")) && !Boolean.TRUE.equals(material.get("specularImage"))) return false;
            if (shaders && Boolean.TRUE.equals(material.get("normalImage")) && (int)material.get("normalTextureId") <= 0) return false;
            if (shaders && Boolean.TRUE.equals(material.get("specularImage")) && (int)material.get("specularTextureId") <= 0) return false;
        }
        return true;
    }

    private static void complete(Minecraft mc, Throwable failure) {
        if (finished) return;
        finished = true;
        passed = failure == null;
        KeyMapping.releaseAll();
        if (mc.player != null) mc.player.setSprinting(false);
        if (prepared) {
            mc.options.pauseOnLostFocus = oldPause;
            mc.options.hideGui = oldGui;
            if (REQUIRE_OCULUS_GPU && oldMouseGrabbed) mc.mouseHandler.grabMouse();
        }
        var result = new LinkedHashMap<String, Object>();
        result.put("run", RUN);
        result.put("success", failure == null);
        result.put("failure", failure == null ? "" : failure.toString());
        result.put("configuredProfile", profile);
        result.put("mode", LocalPlayerCharacter.animationMode().name());
        result.put("controller", CharacterScriptRuntime.active());
        result.put("fixture", fixture == null ? null : List.of(fixture.getX(), fixture.getY(), fixture.getZ()));
        result.put("scriptFailure", CharacterScriptRuntime.failure());
        result.put("scriptReloadFailure", CharacterScriptRuntime.reloadFailure());
        result.put("diagnostics", LocalPlayerCharacter.diagnostics());
        result.put("stages", RESULTS);
        result.put("lastMaterialStatus", materials());
        try {
            Files.writeString(mc.gameDirectory.toPath().resolve("character-clip-smoke-result.json"),
                    new GsonBuilder().setPrettyPrinting().serializeNulls().create().toJson(result));
        } catch (IOException exception) { AssimpLoader.LOGGER.error("Cannot write character clip acceptance result", exception); }
        if (failure == null) AssimpLoader.LOGGER.info("[CharacterClipSmoke] SUCCESS run={} profile={}; client remains open", RUN, profile);
        else AssimpLoader.LOGGER.error("[CharacterClipSmoke] FAILED run=" + RUN + "; client remains open", failure);
    }

    private static void require(boolean condition, String message) {
        if (!condition) throw new IllegalStateException(message);
    }
}
