package cn.minerealms.assimploader.character.client;

import cn.minerealms.assimploader.animation.AnimationState;
import cn.minerealms.assimploader.animation.PoseGraph;
import cn.minerealms.assimploader.animation.PoseSnapshot;
import cn.minerealms.assimploader.animation.SkinBindPose;
import cn.minerealms.assimploader.character.core.CharacterProfile;
import cn.minerealms.assimploader.mixin.character.PlayerRendererPoseAccess;
import cn.minerealms.assimploader.model.AssimpScene;
import net.minecraft.client.ClientRecipeBook;
import net.minecraft.client.Minecraft;
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
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.phys.Vec3;
import org.joml.Vector3f;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/** Explicit client-world probe. Its unregistered player never sends input, ticks, joins a level or touches a save. */
public final class VanillaRuntimeProbe {
    private VanillaRuntimeProbe() {}

    public static Map<String, Object> verify(LocalPlayer real, CharacterProfile profile, AssimpScene scene) {
        Minecraft minecraft = Minecraft.getInstance();
        if (!minecraft.isSameThread() || real != minecraft.player || minecraft.level == null)
            throw new IllegalStateException("Vanilla runtime probe requires the active player on the client thread");
        var renderer = minecraft.getEntityRenderDispatcher().getRenderer(real);
        if (!(renderer instanceof PlayerRenderer vanilla) || !(vanilla instanceof PlayerRendererPoseAccess access))
            throw new IllegalStateException("PlayerRendererPoseAccess mixin did not load");
        if (!profile.humanoid().keySet().containsAll(List.of("hips", "head", "left_upper_arm", "right_upper_arm", "left_thigh", "right_thigh")))
            throw new IllegalArgumentException("Vanilla probe requires the complete humanoid fixture mapping");
        var before = real.position(); float beforePitch = real.getXRot(); float beforeYaw = real.getYRot();
        var beforePose = real.getPose(); var beforeItem = real.getMainHandItem().copy();
        Map<String, Object> checks = new LinkedHashMap<>();
        checks.put("mixin", vanilla.getClass().getName());
        var bind = new PoseGraph(scene.getRootNode(), SkinBindPose.locals(scene));
        Vector3f faceLocal = bind.global(profile.bone("head").node()).invert()
                .transformDirection(new Vector3f(0, 0, 1)).normalize();
        ProbePlayer player = new ProbePlayer(minecraft, real);
        try (var animation = new AnimationState("vanilla-runtime-probe", scene);
             var driver = new VanillaPlayerPoseDriver(profile, animation)) {
            PoseSnapshot neutral = sample(player, driver, animation);
            List<Object> headSamples = new ArrayList<>();
            for (float pitch : new float[]{-60, 60}) {
                player.reset(); player.setXRot(pitch);
                PoseSnapshot pose = sample(player, driver, animation);
                Vector3f face = pose.getNodeTransform(profile.bone("head").node()).transformDirection(new Vector3f(faceLocal)).normalize();
                float expectedY = -(float)Math.sin(Math.toRadians(pitch));
                require(Math.abs(face.y - expectedY) < .002f, "Visible head pitch is reversed or missing: " + face);
                headSamples.add(Map.of("pitch", pitch, "face", vector(face)));
            }
            checks.put("headPitch", headSamples);

            player.reset(); player.crouching = true;
            PoseSnapshot crouch = sample(player, driver, animation);
            float headDrop = position(neutral, profile, "head").y - position(crouch, profile, "head").y;
            require(headDrop > .1f, "Original crouch head pivot was not retargeted: " + headDrop);
            checks.put("crouch", Map.of("headDrop", headDrop, "hips", vector(position(crouch, profile, "hips"))));

            player.reset(); player.setWalk(2, 1);
            PoseSnapshot walkA = sample(player, driver, animation);
            player.setWalk(7, 1);
            PoseSnapshot walkB = sample(player, driver, animation);
            float walkChange = limbAxis(walkA, profile, "left_thigh", "left_shin")
                    .distance(limbAxis(walkB, profile, "left_thigh", "left_shin"));
            require(walkChange > .1f, "Actual walk animation did not reach the leg skeleton");
            checks.put("walk", Map.of("legDirectionChange", walkChange));

            List<Vector3f> armStroke = new ArrayList<>(), legKick = new ArrayList<>();
            List<Object> swimSamples = new ArrayList<>();
            for (int phase : new int[]{2, 10, 20}) {
                player.reset(); player.swimming = player.inWater = true; player.setWalk(phase, 1);
                PoseSnapshot pose = sample(player, driver, animation);
                Vector3f arm = limbAxis(pose, profile, "right_upper_arm", "right_forearm");
                Vector3f leg = limbAxis(pose, profile, "left_thigh", "left_shin");
                Vector3f otherLeg = limbAxis(pose, profile, "right_thigh", "right_shin");
                require(Math.abs(bodyAxis(pose, profile).y) < .002f, "Swimming root stayed upright");
                armStroke.add(arm); legKick.add(leg);
                swimSamples.add(Map.of("phase", phase, "arm", vector(arm), "leftLeg", vector(leg), "rightLeg", vector(otherLeg)));
            }
            require(spread(armStroke) > .3f, "Original swimming arm stroke did not reach the rig");
            require(spread(legKick) > .1f, "Original swimming leg kick did not reach the rig");
            checks.put("swim", Map.of("samples", swimSamples, "armDirectionChange", spread(armStroke), "legDirectionChange", spread(legKick)));

            List<Object> flightSamples = new ArrayList<>();
            for (float pitch : new float[]{-60, 0, 60}) {
                player.reset(); player.fallFlying = true; player.setXRot(pitch); player.setDeltaMovement(0, -.3, 1);
                PoseSnapshot pose = sample(player, driver, animation);
                Vector3f body = bodyAxis(pose, profile);
                Vector3f expected = new Vector3f(0, -(float)Math.sin(Math.toRadians(pitch)), (float)Math.cos(Math.toRadians(pitch)));
                require(body.distance(expected) < .002f, "Elytra root did not follow the actual dive pitch: " + body);
                flightSamples.add(Map.of("pitch", pitch, "body", vector(body)));
            }
            checks.put("elytra", flightSamples);

            player.reset(); player.vehicle = new Boat(EntityType.BOAT, minecraft.level);
            PoseSnapshot ride = sample(player, driver, animation);
            Vector3f rideLeg = limbAxis(ride, profile, "right_thigh", "right_shin");
            require(rideLeg.z > .75f, "Original mounted leg pose was not selected");
            checks.put("ride", Map.of("rightLeg", vector(rideLeg)));

            player.reset(); player.setPose(Pose.SLEEPING);
            PoseSnapshot sleep = sample(player, driver, animation);
            Vector3f sleepBody = bodyAxis(sleep, profile);
            require(Math.abs(sleepBody.y) < .002f, "Original sleeping renderer rotation was not applied");
            checks.put("sleep", Map.of("body", vector(sleepBody), "bedDirection", Direction.EAST.getName()));

            player.reset(); player.main = new ItemStack(Items.DIAMOND_SWORD);
            PoseSnapshot held = sample(player, driver, animation);
            Vector3f idleArm = limbAxis(neutral, profile, "right_upper_arm", "right_forearm");
            Vector3f heldArm = limbAxis(held, profile, "right_upper_arm", "right_forearm");
            require(idleArm.distance(heldArm) > .15f, "Original ITEM arm pose was not selected");
            checks.put("heldItem", Map.of("rightArm", vector(heldArm), "armPose", vanilla.getModel().rightArmPose.name()));

            List<Object> uses = new ArrayList<>();
            for (var entry : Map.of(Items.BOW, "BOW_AND_ARROW", Items.SHIELD, "BLOCK", Items.CROSSBOW, "CROSSBOW_CHARGE").entrySet()) {
                player.reset(); player.main = new ItemStack(entry.getKey()); player.using = true;
                PoseSnapshot pose = sample(player, driver, animation);
                String actual = vanilla.getModel().rightArmPose.name();
                require(actual.equals(entry.getValue()), "Original item action dispatch failed: " + actual + " expected " + entry.getValue());
                Vector3f arm = limbAxis(pose, profile, "right_upper_arm", "right_forearm");
                require(arm.isFinite() && arm.z > .1f, "Item-use arm is not forward or finite: " + arm);
                uses.add(Map.of("armPose", actual, "rightArm", vector(arm)));
            }
            checks.put("itemUse", uses);

            List<Vector3f> swings = new ArrayList<>();
            List<Object> attackSamples = new ArrayList<>();
            for (float phase : new float[]{.05f, .35f, .7f, .95f}) {
                player.reset(); player.main = new ItemStack(Items.DIAMOND_SWORD); player.attack = phase;
                PoseSnapshot pose = sample(player, driver, animation);
                Vector3f arm = limbAxis(pose, profile, "right_upper_arm", "right_forearm");
                swings.add(arm); attackSamples.add(Map.of("phase", phase, "rightArm", vector(arm)));
            }
            require(spread(swings) > .5f, "Accepted vanilla attack phases did not change the final arm pose");
            checks.put("attack", Map.of("samples", attackSamples, "directionChange", spread(swings)));
        } finally {
            access.assimploader$setModelProperties(real);
        }
        require(real.position().equals(before) && real.getXRot() == beforePitch && real.getYRot() == beforeYaw
                        && real.getPose() == beforePose && ItemStack.matches(real.getMainHandItem(), beforeItem),
                "Runtime probe changed the real player's state");
        return Map.of("passed", true, "checks", checks, "realPlayerUnchanged", true,
                "input", "Unregistered LocalPlayer subclass; original PlayerRenderer and PlayerModel; independent FBX AnimationState",
                "notCovered", List.of("Live key input and accepted swing detection", "Server authority", "Render pixels", "Armor/cape layers", "Physics and terrain solver"));
    }

    private static PoseSnapshot sample(ProbePlayer player, VanillaPlayerPoseDriver driver, AnimationState animation) {
        driver.update(player, true); animation.update(.05f); return animation.getPoseSnapshot();
    }
    private static Vector3f position(PoseSnapshot pose, CharacterProfile profile, String role) {
        return pose.getNodeTransform(profile.bone(role).node()).getTranslation(new Vector3f());
    }
    private static Vector3f limbAxis(PoseSnapshot pose, CharacterProfile profile, String upper, String lower) {
        return position(pose, profile, lower).sub(position(pose, profile, upper)).normalize();
    }
    private static Vector3f bodyAxis(PoseSnapshot pose, CharacterProfile profile) { return limbAxis(pose, profile, "hips", "head"); }
    private static List<Float> vector(Vector3f value) { return List.of(value.x, value.y, value.z); }
    private static float spread(List<Vector3f> values) {
        float max = 0;
        for (Vector3f a : values) for (Vector3f b : values) max = Math.max(max, a.distance(b));
        return max;
    }
    private static void require(boolean condition, String message) { if (!condition) throw new IllegalStateException(message); }

    private static final class ProbePlayer extends LocalPlayer {
        boolean crouching, swimming, inWater, fallFlying, using;
        float attack;
        ItemStack main = ItemStack.EMPTY, off = ItemStack.EMPTY;
        Entity vehicle;
        final String modelName;
        ProbePlayer(Minecraft minecraft, LocalPlayer real) {
            super(minecraft, minecraft.level, real.connection, new StatsCounter(), new ClientRecipeBook(), false, false);
            modelName = real.getModelName();
            reset();
        }
        void reset() {
            crouching = swimming = inWater = fallFlying = using = false; attack = 0;
            main = off = ItemStack.EMPTY; vehicle = null; setPose(Pose.STANDING);
            setXRot(0); setYRot(0); yBodyRot = yBodyRotO = yHeadRot = yHeadRotO = 0;
            tickCount = 100; swingingArm = InteractionHand.MAIN_HAND;
            setDeltaMovement(Vec3.ZERO); setWalk(0, 0);
        }
        void setWalk(float phase, float speed) {
            walkAnimation.update(phase - walkAnimation.position(1), 1); walkAnimation.setSpeed(speed);
        }
        @Override public String getModelName() { return modelName == null ? "default" : modelName; }
        @Override public boolean isSpectator() { return false; }
        @Override public boolean isCrouching() { return crouching; }
        @Override public boolean isSwimming() { return swimming; }
        @Override public boolean isVisuallySwimming() { return swimming; }
        @Override public boolean isInWater() { return inWater; }
        @Override public float getSwimAmount(float partialTick) { return swimming ? 1 : 0; }
        @Override public boolean isFallFlying() { return fallFlying; }
        @Override public int getFallFlyingTicks() { return fallFlying ? 20 : 0; }
        @Override public boolean isPassenger() { return vehicle != null; }
        @Override public Entity getVehicle() { return vehicle; }
        @Override public Direction getBedOrientation() { return Direction.EAST; }
        @Override public HumanoidArm getMainArm() { return HumanoidArm.RIGHT; }
        @Override public ItemStack getItemInHand(InteractionHand hand) { return hand == InteractionHand.MAIN_HAND ? main : off; }
        @Override public ItemStack getMainHandItem() { return main; }
        @Override public ItemStack getOffhandItem() { return off; }
        @Override public boolean isUsingItem() { return using; }
        @Override public InteractionHand getUsedItemHand() { return InteractionHand.MAIN_HAND; }
        @Override public ItemStack getUseItem() { return using ? main : ItemStack.EMPTY; }
        @Override public int getTicksUsingItem() { return using ? 15 : 0; }
        @Override public int getUseItemRemainingTicks() { return using ? main.getUseDuration() - 15 : 0; }
        @Override public float getAttackAnim(float partialTick) { return attack; }
    }
}
