package cn.minerealms.rigworks.character.client;

import cn.minerealms.assimploader.animation.AnimationState;
import cn.minerealms.rigworks.character.core.AirborneLegPose;
import cn.minerealms.rigworks.character.core.CharacterProfile;
import cn.minerealms.rigworks.character.core.VanillaPoseSelection;
import cn.minerealms.rigworks.mixin.character.PlayerRendererPoseAccess;
import com.mojang.blaze3d.vertex.PoseStack;
import net.minecraft.client.Minecraft;
import net.minecraft.client.model.HumanoidModel;
import net.minecraft.client.model.PlayerModel;
import net.minecraft.client.model.geom.ModelPart;
import net.minecraft.client.model.geom.builders.CubeDeformation;
import net.minecraft.client.model.geom.builders.LayerDefinition;
import net.minecraft.client.player.AbstractClientPlayer;
import net.minecraft.client.renderer.entity.player.PlayerRenderer;
import net.minecraft.util.Mth;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.HumanoidArm;
import net.minecraft.world.entity.Pose;
import net.minecraft.world.item.UseAnim;
import net.minecraft.world.phys.Vec3;
import org.joml.Matrix4f;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;

/** Samples actual vanilla player animation; only the resulting matrices enter the character solver. */
public final class VanillaPlayerPoseDriver implements AutoCloseable {
    public static final String MODIFIER = "character:vanilla-pose";
    private final CharacterProfile profile;
    private final AnimationState animation;
    private final VanillaPoseRetargeter retargeter;
    private PlayerModel<AbstractClientPlayer> model;
    private PlayerModel<?> sampledSource;
    private boolean closed;
    private final AirborneLegPose airborne = new AirborneLegPose();
    private final Map<String, VanillaPoseSelection> selections = new LinkedHashMap<>();

    public VanillaPlayerPoseDriver(CharacterProfile profile, AnimationState animation) {
        this.profile = Objects.requireNonNull(profile);
        this.animation = Objects.requireNonNull(animation);
        animation.initialize();
        if (animation.getModifier(MODIFIER) != null) throw new IllegalArgumentException("Vanilla player driver already installed");
        retargeter = new VanillaPoseRetargeter(profile, animation.getScene());
        animation.addModifier(MODIFIER, retargeter);
    }

    /** actions=false leaves swing and held-item transforms to the script's upper-body layer. */
    public void update(AbstractClientPlayer player, boolean actions) {
        update(player, actions ? VanillaPoseSelection.ALL : VanillaPoseSelection.MOTION);
    }

    public void update(AbstractClientPlayer player, VanillaPoseSelection selection) {
        update(player,selection,player.getAbilities().flying);
    }

    public void update(AbstractClientPlayer player, VanillaPoseSelection selection,boolean flying) {
        if (closed || animation.isClosed()) throw new IllegalStateException("Vanilla player driver is closed");
        Objects.requireNonNull(selection);
        if (!selection.enabled()) { disable(); airborne.reset(); return; }
        var renderer = Minecraft.getInstance().getEntityRenderDispatcher().getRenderer(player);
        if (!(renderer instanceof PlayerRenderer vanilla) || !(vanilla instanceof PlayerRendererPoseAccess access))
            throw new IllegalStateException("Vanilla player renderer pose access is unavailable");
        access.assimploader$setModelProperties(player);
        var source = vanilla.getModel();
        if (source != sampledSource) {
            model = createPoseModel(source, "slim".equals(player.getModelName()));
            sampledSource = source;
        }
        model.leftArmPose = selection.items() ? source.leftArmPose : HumanoidModel.ArmPose.EMPTY;
        model.rightArmPose = selection.items() ? source.rightArmPose : HumanoidModel.ArmPose.EMPTY;
        model.crouching = source.crouching;
        model.attackTime = selection.attack() ? player.getAttackAnim(1) : 0;
        model.riding = player.isPassenger() && player.getVehicle() != null && player.getVehicle().shouldRiderSit();
        model.young = player.isBaby();

        float bodyYaw = player.yBodyRot;
        float headYaw = player.yHeadRot;
        float relativeYaw = headYaw - bodyYaw;
        if (model.riding && player.getVehicle() instanceof LivingEntity mount) {
            bodyYaw = mount.yBodyRot;
            relativeYaw = Mth.wrapDegrees(headYaw - bodyYaw);
            float clamped = Mth.clamp(relativeYaw, -85, 85);
            bodyYaw = headYaw - clamped;
            if (clamped * clamped > 2500) bodyYaw += clamped * .2f;
            relativeYaw = headYaw - bodyYaw;
        }
        float pitch = player.getXRot();
        if (net.minecraft.client.renderer.entity.LivingEntityRenderer.isEntityUpsideDown(player)) {
            pitch *= -1;
            relativeYaw *= -1;
        }
        float walkSpeed = 0, walkPosition = 0;
        if (!model.riding && player.isAlive()) {
            walkSpeed = Math.min(1, player.walkAnimation.speed(1));
            walkPosition = player.walkAnimation.position(1) * (player.isBaby() ? 3 : 1);
        }
        float age = player.tickCount + 1;
        model.prepareMobModel(player, walkPosition, walkSpeed, 1);
        model.setupAnim(player, walkPosition, walkSpeed, age, relativeYaw, pitch);
        float leftElbow = 0, rightElbow = 0;
        boolean ladder = player.onClimbable() && !player.isPassenger() && !player.isSwimming();
        if (ladder) {
            float cycle = (float) (player.getY() * 5);
            float movement = Mth.clamp((float) Math.abs(player.getDeltaMovement().y) * 8, 0, 1);
            boolean leftFree = !selection.items() || physicalHand(player, HumanoidArm.LEFT).isEmpty();
            boolean rightFree = !selection.items() || physicalHand(player, HumanoidArm.RIGHT).isEmpty();
            if (leftFree) model.leftArm.xRot = -2.2f + Mth.sin(cycle) * .45f * movement;
            if (rightFree) model.rightArm.xRot = -2.2f - Mth.sin(cycle) * .45f * movement;
            model.leftLeg.xRot = -.35f - Mth.sin(cycle) * .45f * movement;
            model.rightLeg.xRot = -.35f + Mth.sin(cycle) * .45f * movement;
            leftElbow = leftFree ? -.3f : 0;
            rightElbow = rightFree ? -.3f : 0;
        } else if (player.isInWater() && !player.isSwimming() && !player.onGround()) {
            model.leftLeg.xRot += Mth.sin(age * .13f) * .15f;
            model.rightLeg.xRot -= Mth.sin(age * .13f) * .15f;
            model.leftArm.zRot -= .2f;
            model.rightArm.zRot += .2f;
        }
        if (selection.items() && player.isUsingItem()
                && (player.getUseItem().getUseAnimation() == UseAnim.EAT || player.getUseItem().getUseAnimation() == UseAnim.DRINK)) {
            HumanoidArm arm = player.getUsedItemHand() == net.minecraft.world.InteractionHand.MAIN_HAND
                    ? player.getMainArm() : player.getMainArm().getOpposite();
            var part = arm == HumanoidArm.LEFT ? model.leftArm : model.rightArm;
            part.xRot = -.9f + Mth.sin(age * 1.2f) * .04f;
            part.yRot = arm == HumanoidArm.LEFT ? .2f : -.2f;
            if (arm == HumanoidArm.LEFT) leftElbow = -.9f; else rightElbow = -.9f;
        }

        boolean articulated = supportsArticulatedLegs(profile);
        boolean ordinaryJump = articulated && !model.riding && !flying && !player.isFallFlying()
                && !player.isInWater() && !player.isInLava() && !player.onClimbable() && !player.isAutoSpinAttack()
                && player.getPose() != Pose.SWIMMING && player.isAlive() && !player.isSleeping();
        float airWeight = airborne.update(player.tickCount, player.onGround(), ordinaryJump);
        float knee = AirborneLegPose.knee(player.getDeltaMovement().y);
        model.leftLeg.xRot = Mth.lerp(airWeight, model.leftLeg.xRot, -knee * .5f);
        model.rightLeg.xRot = Mth.lerp(airWeight, model.rightLeg.xRot, -knee * .5f);

        var poses = new PoseStack();
        access.assimploader$setupRotations(player, poses, age, bodyYaw, 1);
        // Remove the yaw already supplied by LocalPlayerCharacter, then convert vanilla's model axes.
        Matrix4f root = new Matrix4f().rotateY((float)Math.toRadians(player.yBodyRot - profile.modelYawDegrees()))
                .mul(poses.last().pose()).rotateZ((float)Math.PI).translate(0, -.001f, 0)
                .rotateX((float)Math.PI).rotateY((float)Math.toRadians(profile.modelYawDegrees()));
        if (player.hurtTime > 0 && player.isAlive() && !player.isSleeping()) {
            float progress = (float) player.hurtTime / Math.max(1, player.hurtDuration);
            root.rotateX(Mth.sin(progress * (float) Math.PI) * .06f);
        }
        Map<String, VanillaPoseRetargeter.Part> parts = new LinkedHashMap<>();
        parts.put("hips", part(model.body));
        parts.put("head", part(model.head));
        parts.put("left_upper_arm", part(model.leftArm));
        parts.put("right_upper_arm", part(model.rightArm));
        parts.put("left_thigh", part(model.leftLeg));
        parts.put("right_thigh", part(model.rightLeg));
        parts.put("left_forearm", new VanillaPoseRetargeter.Part(leftElbow, 0, 0, 0, 0, 0));
        parts.put("right_forearm", new VanillaPoseRetargeter.Part(rightElbow, 0, 0, 0, 0, 0));
        if (articulated) {
            float leftKnee = ladder ? Math.max(.1f, -model.leftLeg.xRot) : knee * airWeight;
            float rightKnee = ladder ? Math.max(.1f, -model.rightLeg.xRot) : knee * airWeight;
            parts.put("left_shin", new VanillaPoseRetargeter.Part(leftKnee, 0, 0, 0, 0, 0));
            parts.put("right_shin", new VanillaPoseRetargeter.Part(rightKnee, 0, 0, 0, 0, 0));
        }
        retargeter.update(root, parts, selection);
        animation.resume();
    }

    static PlayerModel<AbstractClientPlayer> createPoseModel(PlayerModel<?> source, boolean slim) {
        var copy = new PlayerModel<AbstractClientPlayer>(
                LayerDefinition.create(PlayerModel.createMesh(CubeDeformation.NONE, slim), 64, 64).bakeRoot(), slim);
        // Slim shoulders start at y=2.5, while setupAnim writes y=2. Reference deltas must use the actual renderer.
        for (ModelPart[] pair : new ModelPart[][]{{copy.body, source.body}, {copy.head, source.head},
                {copy.leftArm, source.leftArm}, {copy.rightArm, source.rightArm},
                {copy.leftLeg, source.leftLeg}, {copy.rightLeg, source.rightLeg}}) {
            pair[0].setInitialPose(pair[1].getInitialPose());
            pair[0].resetPose();
        }
        return copy;
    }

    static VanillaPoseRetargeter.Part part(ModelPart value) {
        var neutral = value.getInitialPose();
        return new VanillaPoseRetargeter.Part(value.xRot, value.yRot, value.zRot,
                value.x - neutral.x, value.y - neutral.y, value.z - neutral.z);
    }

    static boolean supportsArticulatedLegs(CharacterProfile profile) {
        return profile.bone("left_thigh") != null && profile.bone("right_thigh") != null
                && profile.bone("left_shin") != null && profile.bone("right_shin") != null;
    }

    /** World-space offset from vanilla LivingEntityRenderer.render, applied before model yaw and scale. */
    public static Vec3 renderOffset(AbstractClientPlayer player) {
        var bed = player.getBedOrientation();
        if (!player.isSleeping() || bed == null) return Vec3.ZERO;
        float offset = player.getEyeHeight(Pose.STANDING) - .1f;
        return new Vec3(-bed.getStepX() * offset, 0, -bed.getStepZ() * offset);
    }

    private static net.minecraft.world.item.ItemStack physicalHand(AbstractClientPlayer player, HumanoidArm arm) {
        return player.getItemInHand(player.getMainArm() == arm ? net.minecraft.world.InteractionHand.MAIN_HAND
                : net.minecraft.world.InteractionHand.OFF_HAND);
    }

    /** Scripted frames disable the previous modifier before resampling; contact history survives that boundary. */
    public void disable() { retargeter.disable(); }

    /** Resource definitions are compiled once per player binding; explicit reload replaces only on success. */
    public VanillaPoseSelection selection(String resource, boolean reload) {
        if (closed || animation.isClosed()) throw new IllegalStateException("Vanilla player driver is closed");
        if (!reload && selections.containsKey(resource)) return selections.get(resource);
        if (!selections.containsKey(resource) && selections.size() >= 32) throw new IllegalArgumentException("At most 32 native pose definitions per binding");
        ResourceLocation id = new ResourceLocation(resource);
        try (var reader = Minecraft.getInstance().getResourceManager().openAsReader(id)) {
            VanillaPoseSelection compiled = VanillaPoseSelection.read(new java.io.StringReader(readBounded(reader)));
            selections.put(resource, compiled);
            return compiled;
        } catch (java.io.IOException error) { throw new IllegalArgumentException("Cannot read native pose: " + resource, error); }
    }

    private static String readBounded(java.io.Reader reader) throws java.io.IOException {
        char[] data = new char[4097];
        int total = 0, count;
        while (total < data.length && (count = reader.read(data, total, data.length - total)) != -1) total += count;
        if (total > 4096) throw new IllegalArgumentException("Native pose JSON exceeds 4096 characters");
        return new String(data, 0, total);
    }

    @Override public void close() {
        if (closed) return;
        closed = true;
        selections.clear();
        retargeter.disable();
        airborne.reset();
        if (!animation.isClosed() && animation.getModifier(MODIFIER) == retargeter) animation.removeModifier(MODIFIER);
    }
}
