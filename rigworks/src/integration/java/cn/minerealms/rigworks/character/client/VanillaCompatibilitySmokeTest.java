package cn.minerealms.rigworks.character.client;

import cn.minerealms.assimploader.AssimpLoader;
import cn.minerealms.assimploader.animation.AnimationState;
import cn.minerealms.assimploader.animation.PoseSnapshot;
import cn.minerealms.rigworks.RigWorks;
import cn.minerealms.rigworks.character.core.CharacterProfile;
import cn.minerealms.rigworks.character.core.PlayerActionState;
import cn.minerealms.rigworks.character.core.PlayerVisualInput;
import cn.minerealms.rigworks.mixin.character.PlayerRendererPoseAccess;
import com.google.gson.GsonBuilder;
import net.minecraft.client.CameraType;
import net.minecraft.client.ClientRecipeBook;
import net.minecraft.client.Minecraft;
import net.minecraft.client.Screenshot;
import net.minecraft.client.gui.screens.TitleScreen;
import net.minecraft.client.player.Input;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.client.renderer.entity.player.PlayerRenderer;
import net.minecraft.core.Direction;
import net.minecraft.stats.StatsCounter;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.HumanoidArm;
import net.minecraft.world.entity.Pose;
import net.minecraft.world.entity.vehicle.Boat;
import net.minecraft.world.item.CrossbowItem;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.eventbus.api.EventPriority;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

import java.nio.file.Files;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/** Explicit disposable-world acceptance. Synthetic players are never registered, ticked or networked. */
@Mod.EventBusSubscriber(modid = RigWorks.MODID, value = Dist.CLIENT)
public final class VanillaCompatibilitySmokeTest {
    private static final boolean ENABLED = Boolean.getBoolean("vanillaCompatibilityTest.enabled");
    private static final long START = System.currentTimeMillis();
    private static final String RUN = Long.toString(START);
    private static final Map<String, Object> RESULT = new LinkedHashMap<>();
    private static final List<String> CAPTURES = new ArrayList<>();
    private static boolean finished, opening, pending, prepared, itemsChanged, restoring;
    private static int stage;
    private static long stageStarted;
    private static CameraType camera;
    private static boolean hideGui, pause;
    private static float yaw, pitch;
    private static UUID playerId;
    private static ItemStack main = ItemStack.EMPTY, off = ItemStack.EMPTY;
    private static Throwable terminalFailure;
    private static String selectedProfile, selectedScript;
    private static long firstPersonDraws;

    private VanillaCompatibilitySmokeTest() {}

    @SubscribeEvent(priority = EventPriority.LOWEST)
    public static void tick(TickEvent.ClientTickEvent event) {
        if (!ENABLED || finished || event.phase != TickEvent.Phase.END) return;
        var mc = Minecraft.getInstance();
        try {
            if (restoring && System.currentTimeMillis() - START >= 180_000) {
                terminalFailure = new IllegalStateException("Timed out while restoring the server hand fixture"); finish(mc); return;
            }
            require(System.currentTimeMillis() - START < 180_000, "Vanilla compatibility acceptance timed out");
            if (pending || restoring) return;
            if (mc.player == null || mc.level == null) {
                String world = System.getProperty("vanillaCompatibilityTest.world", "");
                if (!opening && !world.isBlank() && mc.screen instanceof TitleScreen && mc.getOverlay() == null) {
                    opening = true;
                    mc.createWorldOpenFlows().loadLevel(new TitleScreen(), world);
                }
                return;
            }
            if (mc.screen != null || mc.getOverlay() != null || mc.isPaused()) return;
            var diagnostics = LocalPlayerCharacter.diagnostics();
            require(diagnostics.status() != LocalPlayerCharacter.Status.FAILED, diagnostics.toString());
            if (!prepared) {
                if (diagnostics.status() != LocalPlayerCharacter.Status.READY) return;
                require(!LocalPlayerCharacter.DEFAULT_PROFILE.equals(LocalPlayerCharacter.settings().profile()),
                        "Select the real imported character profile before running compatibility acceptance");
                require(mc.getSingleplayerServer() != null, "Screenshot item preservation requires a disposable integrated-server world");
                selectedProfile = LocalPlayerCharacter.settings().profile(); selectedScript = CharacterScriptRuntime.active();
                camera = mc.options.getCameraType(); hideGui = mc.options.hideGui; pause = mc.options.pauseOnLostFocus;
                yaw = mc.player.getYRot(); pitch = mc.player.getXRot(); playerId = mc.player.getUUID();
                prepared = true;
                RESULT.put("run", RUN); RESULT.put("profile", selectedProfile); RESULT.put("script", selectedScript);
                RESULT.put("observedRealPlayerBefore", observation(new MinecraftPlayerInputSampler().sample(mc.player)));
                RESULT.put("syntheticProbe", verify(mc));
                mc.options.pauseOnLostFocus = false; mc.options.hideGui = true;
                mc.options.setCameraType(CameraType.THIRD_PERSON_FRONT);
                mc.player.setXRot(0); mc.player.xRotO = 0;
                stage = 0; stageStarted = System.currentTimeMillis();
                AssimpLoader.LOGGER.info("[VanillaCompatibilitySmoke] Probes passed; capturing imported character {}", selectedProfile);
                return;
            }
            require(selectedProfile.equals(LocalPlayerCharacter.settings().profile()), "Character profile changed during acceptance");
            require(java.util.Objects.equals(selectedScript, CharacterScriptRuntime.active()), "Character script changed during acceptance");
            if (System.currentTimeMillis() - stageStarted < 10_000) return;
            if (stage == 0) capture(mc, "third-person", () -> clearHands(mc));
            else if (stage == 1) {
                require(mc.player.getMainHandItem().isEmpty() && mc.player.getOffhandItem().isEmpty(),
                        "Server-authorized empty hands did not reach the client");
                require(LocalPlayerCharacter.view(mc.player, mc.getFrameTime()) != null, "First-person view lost the imported character binding");
                var armDraws=CharacterFirstPersonArms.diagnostics();
                require(((Number)armDraws.get("leftDraws")).longValue()+((Number)armDraws.get("rightDraws")).longValue()>firstPersonDraws,
                        "Custom first-person arm renderer was not invoked");
                RESULT.put("firstPersonDraws",armDraws);
                RESULT.put("firstPersonObservation", Map.of("camera", mc.options.getCameraType().name(),
                        "mainEmpty", true, "offEmpty", true,
                        "evidence", "Actual empty-hand first-person screenshot and completed custom arm draw counters"));
                capture(mc, "first-person", () -> complete(mc, null));
            }
        } catch (Exception | LinkageError failure) { complete(mc, failure); }
    }

    private static void clearHands(Minecraft mc) {
        pending = true;
        mc.getSingleplayerServer().execute(() -> {
            try {
                var player = mc.getSingleplayerServer().getPlayerList().getPlayer(playerId);
                require(player != null, "Player left before hand fixture preparation");
                main = player.getMainHandItem().copy(); off = player.getOffhandItem().copy(); itemsChanged = true;
                player.setItemInHand(InteractionHand.MAIN_HAND, ItemStack.EMPTY);
                player.setItemInHand(InteractionHand.OFF_HAND, ItemStack.EMPTY);
                player.inventoryMenu.broadcastChanges();
                mc.execute(() -> { var draws=CharacterFirstPersonArms.diagnostics();
                    firstPersonDraws=((Number)draws.get("leftDraws")).longValue()+((Number)draws.get("rightDraws")).longValue();
                    // F1 hides native hand rendering too; keep it enabled for this acceptance stage.
                    mc.options.hideGui=false;mc.options.setCameraType(CameraType.FIRST_PERSON); stage = 1;
                    stageStarted = System.currentTimeMillis(); pending = false; });
            } catch (Exception failure) { mc.execute(() -> complete(mc, failure)); }
        });
    }

    private static void capture(Minecraft mc, String label, Runnable continuation) {
        pending = true;
        String name = "vanilla-compatibility-" + RUN + "-" + label + ".png";
        Screenshot.grab(mc.gameDirectory, name, mc.getMainRenderTarget(), message -> mc.execute(() -> {
            try {
                require(Files.isRegularFile(mc.gameDirectory.toPath().resolve("screenshots").resolve(name)), "Screenshot missing: " + name);
                CAPTURES.add(name); pending = false; continuation.run();
            } catch (Exception failure) { complete(mc, failure); }
        }));
    }

    private static void complete(Minecraft mc, Throwable failure) {
        if (finished || restoring) return;
        restoring = true; terminalFailure = failure;
        if (prepared) {
            mc.options.setCameraType(camera); mc.options.hideGui = hideGui; mc.options.pauseOnLostFocus = pause;
            if (mc.player != null && mc.player.getUUID().equals(playerId)) { mc.player.setYRot(yaw); mc.player.yRotO = yaw;
                mc.player.setXRot(pitch); mc.player.xRotO = pitch; }
        }
        if (itemsChanged && mc.getSingleplayerServer() != null) {
            mc.getSingleplayerServer().execute(() -> {
                try {
                    var player = mc.getSingleplayerServer().getPlayerList().getPlayer(playerId);
                    require(player != null, "Cannot restore held items after player left");
                    player.setItemInHand(InteractionHand.MAIN_HAND, main.copy());
                    player.setItemInHand(InteractionHand.OFF_HAND, off.copy());
                    player.inventoryMenu.broadcastChanges();
                    mc.execute(() -> { itemsChanged = false; finish(mc); });
                } catch (Exception restoreFailure) { mc.execute(() -> { terminalFailure = restoreFailure; finish(mc); }); }
            });
        } else finish(mc);
    }

    private static void finish(Minecraft mc) {
        finished = true;
        RESULT.put("passed", terminalFailure == null); RESULT.put("screenshots", CAPTURES);
        RESULT.put("failure", terminalFailure == null ? "" : terminalFailure.toString());
        RESULT.put("cameraRestored", !prepared || mc.options.getCameraType() == camera);
        RESULT.put("heldItemsRestoredOnServer", !itemsChanged);
        RESULT.put("clientLeftOpen", true);
        RESULT.put("notProven", List.of("Every movement combination from live keyboard input", "Multiplayer and packet delivery",
                "Pixel-perfect item contact", "All equipment and shader variants", "First-person pixels require screenshot inspection"));
        try {
            Files.writeString(mc.gameDirectory.toPath().resolve("vanilla-compatibility-smoke-result.json"),
                    new GsonBuilder().setPrettyPrinting().create().toJson(RESULT));
        } catch (Exception writeFailure) { AssimpLoader.LOGGER.error("[VanillaCompatibilitySmoke] Cannot write result", writeFailure); }
        if (terminalFailure == null) AssimpLoader.LOGGER.info("[VanillaCompatibilitySmoke] PASS run={} screenshots={}", RUN, CAPTURES);
        else AssimpLoader.LOGGER.error("[VanillaCompatibilitySmoke] FAIL; client remains open", terminalFailure);
    }

    private static Map<String, Object> verify(Minecraft mc) {
        var real = mc.player; var profile = LocalPlayerCharacter.currentProfile();
        var scene = LocalPlayerCharacter.currentAnimation().getScene();
        require(profile != null && profile.bone("hips") != null && profile.bone("head") != null, "Imported humanoid mapping required");
        var renderer = mc.getEntityRenderDispatcher().getRenderer(real);
        require(renderer instanceof PlayerRenderer && renderer instanceof PlayerRendererPoseAccess, "Native player pose mixin unavailable");
        var vanilla = (PlayerRenderer) renderer; var access = (PlayerRendererPoseAccess) renderer;
        var before = real.position(); var realPose = real.getPose(); var realMain = real.getMainHandItem().copy();
        var checks = new LinkedHashMap<String, Object>();
        RESULT.put("syntheticChecks", checks);
        var player = new ProbePlayer(mc, real);
        try (var animation = new AnimationState("compatibility-probe:" + RUN, scene);
             var driver = new VanillaPlayerPoseDriver(profile, animation)) {
            var neutral = sample(player, driver, animation, profile);
            player.reset(); player.crouching = true; player.setPose(Pose.CROUCHING);
            var crouch = sample(player, driver, animation, profile);
            require(delta(neutral, crouch, profile.bone("head").node()) > .01f, "Crouch did not alter the imported head pose");
            checks.put("crouch", poseEvidence(player, crouch, profile));
            checks.put("airSemantics", airSemantics(player));

            for (String name : List.of("swim", "crawl", "climb_up", "climb_down", "climb_idle", "water_idle", "lava",
                    "creative_flight", "ride", "sleep", "riptide", "elytra", "frozen", "hurt", "death")) {
                player.reset();
                switch (name) {
                    case "swim" -> { player.swimming = player.inWater = true; player.setPose(Pose.SWIMMING); player.setWalk(7, 1); }
                    case "crawl" -> { player.setPose(Pose.SWIMMING); player.setWalk(7, 1); }
                    case "water_idle" -> { player.inWater = true; player.setOnGround(false); }
                    case "lava" -> { player.inLava = true; player.setOnGround(false); }
                    case "creative_flight" -> { player.getAbilities().flying = true; player.setOnGround(false); }
                    case "climb_up", "climb_down", "climb_idle" -> { player.climbing = true;
                        player.setDeltaMovement(0, name.equals("climb_up") ? .15 : name.equals("climb_down") ? -.15 : 0, 0); }
                    case "ride" -> player.vehicle = new Boat(EntityType.BOAT, mc.level);
                    case "sleep" -> player.setPose(Pose.SLEEPING);
                    case "riptide" -> { player.spin = true; player.setPose(Pose.SPIN_ATTACK); }
                    case "elytra" -> { player.fallFlying = true; player.setPose(Pose.FALL_FLYING); player.setDeltaMovement(0,-.3,1); }
                    case "frozen" -> player.frozen = true;
                    case "hurt" -> { player.hurtTime = 6; player.hurtDuration = 10; }
                    case "death" -> { player.setHealth(0); player.deathTime = 10; }
                }
                var pose = sample(player, driver, animation, profile);
                var entry = new LinkedHashMap<String, Object>(poseEvidence(player, pose, profile));
                if (name.equals("sleep")) {
                    var offset = VanillaPlayerPoseDriver.renderOffset(player);
                    require(offset.x < 0 && offset.z == 0, "East-facing bed world offset missing or reversed");
                    entry.put("bedOffset", List.of(offset.x, offset.y, offset.z));
                }
                if (name.equals("crawl")) require(new MinecraftPlayerInputSampler().sample(player).crawling(), "Dry crawl was classified as ordinary standing");
                if (!List.of("climb_idle", "lava", "creative_flight").contains(name)) require(delta(neutral, pose, profile.bone("hips").node()) > .00001f
                                || delta(neutral, pose, profile.bone("head").node()) > .00001f
                                || changedLimb(neutral, pose, profile), "Special state produced no skeletal change: " + name);
                checks.put(name, entry);
            }

            var uses = new ArrayList<Map<String, Object>>();
            for (Item item : List.of(Items.APPLE, Items.BOW, Items.CROSSBOW, Items.SHIELD, Items.SPYGLASS, Items.TRIDENT,
                    Items.GOAT_HORN, Items.BRUSH)) for (HumanoidArm side : HumanoidArm.values()) {
                player.reset(); player.primary = side; player.main = new ItemStack(item); player.using = true;
                var pose = sample(player, driver, animation, profile);
                String armPose = (side == HumanoidArm.LEFT ? vanilla.getModel().leftArmPose : vanilla.getModel().rightArmPose).name();
                String expected = item == Items.BOW ? "BOW_AND_ARROW" : item == Items.CROSSBOW ? "CROSSBOW_CHARGE"
                        : item == Items.SHIELD ? "BLOCK" : item == Items.SPYGLASS ? "SPYGLASS"
                        : item == Items.TRIDENT ? "THROW_SPEAR" : item == Items.GOAT_HORN ? "TOOT_HORN" : item == Items.BRUSH ? "BRUSH" : "ITEM";
                require(armPose.equals(expected), "Native use dispatch: " + item + " / " + side + " expected " + expected + " got " + armPose);
                uses.add(Map.of("item", item.toString(), "physicalArm", side.name(), "armPose", armPose,
                        "state", observation(new MinecraftPlayerInputSampler().sample(player))));
            }
            player.reset(); player.main = new ItemStack(Items.CROSSBOW); CrossbowItem.setCharged(player.main, true);
            sample(player, driver, animation, profile);
            require(vanilla.getModel().rightArmPose.name().equals("CROSSBOW_HOLD"), "Charged crossbow hold dispatch missing");
            checks.put("itemUse", uses); checks.put("chargedCrossbowHold", true);

            var attacks = new ArrayList<Map<String, Object>>(); PoseSnapshot previous = null;
            for (float phase : new float[]{.05f,.35f,.7f,.95f}) {
                player.reset(); player.main = new ItemStack(Items.DIAMOND_SWORD); player.attack = phase;
                var pose = sample(player, driver, animation, profile);
                if (previous != null) require(changedLimb(previous, pose, profile), "Attack phase did not reach the arm skeleton");
                attacks.add(Map.of("phase", phase, "pose", poseEvidence(player, pose, profile))); previous = pose;
            }
            checks.put("attack", attacks);
        } finally { access.assimploader$setModelProperties(real); }
        require(real.position().equals(before) && real.getPose() == realPose && ItemStack.matches(real.getMainHandItem(), realMain),
                "Synthetic probe modified real player gameplay state");
        return Map.of("passed", true, "source", "Synthetic unregistered LocalPlayer on actual Minecraft classes; not live key input",
                "realPlayerUnchanged", true, "checks", checks);
    }

    private static List<Map<String, Object>> airSemantics(ProbePlayer player) {
        player.reset(); var sampler = new MinecraftPlayerInputSampler(); var entries = new ArrayList<Map<String, Object>>();
        player.input.jumping = true;
        var grounded = sampler.sample(player);
        require(grounded.actionState().airPhase() == PlayerActionState.AirPhase.GROUNDED && !grounded.jumping(),
                "Held jump key invented an airborne phase"); entries.add(observation(grounded));
        for (int hop = 0; hop < 2; hop++) {
            for (int step = 0; step < 4; step++) {
                double dy = step == 0 ? .3 : step == 1 ? .1 : step == 2 ? -.1 : 0;
                player.tickCount++; player.setPos(player.getX(), player.getY() + dy, player.getZ());
                player.setDeltaMovement(0,dy,0); player.setOnGround(step == 3);
                var input = sampler.sample(player); entries.add(observation(input));
                var expected = step == 0 ? PlayerActionState.AirPhase.TAKEOFF : step == 1 ? PlayerActionState.AirPhase.RISING
                        : step == 2 ? PlayerActionState.AirPhase.FALLING : PlayerActionState.AirPhase.LANDING;
                require(input.actionState().airPhase() == expected, "Actual observation sampler air transition mismatch: " + input.actionState());
            }
        }
        return entries;
    }

    private static PoseSnapshot sample(ProbePlayer player, VanillaPlayerPoseDriver driver, AnimationState animation, CharacterProfile profile) {
        player.tickCount++; driver.update(player,true); animation.update(.05f); var result = animation.getPoseSnapshot();
        for (var entry : profile.humanoid().entrySet()) require(result.getNodeTransform(entry.getValue().node()).isFinite(),
                "Non-finite imported bone during " + player.getPose() + ": " + entry.getKey());
        return result;
    }

    private static boolean changedLimb(PoseSnapshot a, PoseSnapshot b, CharacterProfile profile) {
        for (String role : List.of("left_upper_arm","right_upper_arm","left_thigh","right_thigh")) {
            var bone = profile.bone(role); if (bone != null && delta(a,b,bone.node()) > .00001f) return true;
        }
        return false;
    }

    private static float delta(PoseSnapshot a, PoseSnapshot b, String node) {
        float[] first = a.getNodeTransform(node).get(new float[16]), second = b.getNodeTransform(node).get(new float[16]);
        float maximum = 0; for (int i=0;i<16;i++) maximum = Math.max(maximum,Math.abs(first[i]-second[i])); return maximum;
    }

    private static Map<String, Object> poseEvidence(ProbePlayer player, PoseSnapshot pose, CharacterProfile profile) {
        float[] hips = pose.getNodeTransform(profile.bone("hips").node()).get(new float[16]);
        float[] head = pose.getNodeTransform(profile.bone("head").node()).get(new float[16]);
        return Map.of("input", observation(new MinecraftPlayerInputSampler().sample(player)), "hips", hips, "head", head);
    }

    private static Map<String, Object> observation(PlayerVisualInput input) {
        var out = new LinkedHashMap<String, Object>();
        out.put("sequence",input.sequence());out.put("pose",input.actionState().pose());out.put("airPhase",input.actionState().airPhaseName());
        out.put("onGround",input.onGround());out.put("verticalVelocity",input.verticalVelocity());out.put("crawling",input.crawling());
        out.put("climbing",input.actionState().climbing());out.put("swimming",input.swimming());out.put("riptide",input.actionState().riptide());
        out.put("frozen",input.actionState().frozen());out.put("useAnimation",input.actionState().useAnimation());return out;
    }

    private static void require(boolean condition,String message) { if(!condition)throw new IllegalStateException(message); }

    private static final class ProbePlayer extends LocalPlayer {
        boolean crouching, swimming, inWater, inLava, fallFlying, using, climbing, spin, frozen;
        float attack; HumanoidArm primary = HumanoidArm.RIGHT;
        ItemStack main = ItemStack.EMPTY, off = ItemStack.EMPTY; Entity vehicle;
        final String skin;
        ProbePlayer(Minecraft mc, LocalPlayer real) {
            super(mc,mc.level,real.connection,new StatsCounter(),new ClientRecipeBook(),false,false);
            skin=real.getModelName();input=new Input();tickCount=100;reset();
        }
        void reset() {
            crouching=swimming=inWater=inLava=fallFlying=using=climbing=spin=frozen=false;attack=0;
            primary=HumanoidArm.RIGHT;main=off=ItemStack.EMPTY;vehicle=null;setPose(Pose.STANDING);
            setHealth(20);deathTime=hurtTime=0;getAbilities().flying=false;
            setOnGround(true);setXRot(0);setYRot(0);yBodyRot=yBodyRotO=yHeadRot=yHeadRotO=0;
            setDeltaMovement(Vec3.ZERO);input.jumping=false;swingingArm=InteractionHand.MAIN_HAND;setWalk(0,0);
        }
        void setWalk(float phase,float speed) {walkAnimation.update(phase-walkAnimation.position(1),1);walkAnimation.setSpeed(speed);}
        @Override public String getModelName(){return skin==null?"default":skin;}
        @Override public boolean isSpectator(){return false;}
        @Override public boolean isCrouching(){return crouching;}
        @Override public boolean isSwimming(){return swimming;}
        @Override public boolean isVisuallySwimming(){return swimming||getPose()==Pose.SWIMMING;}
        @Override public boolean isInWater(){return inWater;}
        @Override public boolean isInLava(){return inLava;}
        @Override public float getSwimAmount(float partialTick){return isVisuallySwimming()?1:0;}
        @Override public boolean isFallFlying(){return fallFlying;}
        @Override public int getFallFlyingTicks(){return fallFlying?20:0;}
        @Override public boolean onClimbable(){return climbing;}
        @Override public boolean isAutoSpinAttack(){return spin;}
        @Override public boolean isFullyFrozen(){return frozen;}
        @Override public boolean isPassenger(){return vehicle!=null;}
        @Override public Entity getVehicle(){return vehicle;}
        @Override public Direction getBedOrientation(){return Direction.EAST;}
        @Override public boolean isSleeping(){return getPose()==Pose.SLEEPING;}
        @Override public HumanoidArm getMainArm(){return primary==null?HumanoidArm.RIGHT:primary;}
        @Override public ItemStack getItemInHand(InteractionHand hand){return hand==InteractionHand.MAIN_HAND?main:off;}
        @Override public ItemStack getMainHandItem(){return main;}
        @Override public ItemStack getOffhandItem(){return off;}
        @Override public boolean isUsingItem(){return using;}
        @Override public InteractionHand getUsedItemHand(){return InteractionHand.MAIN_HAND;}
        @Override public ItemStack getUseItem(){return using?main:ItemStack.EMPTY;}
        @Override public int getTicksUsingItem(){return using?15:0;}
        @Override public int getUseItemRemainingTicks(){return using?main.getUseDuration()-15:0;}
        @Override public float getAttackAnim(float partialTick){return attack;}
    }
}
