package cn.minerealms.rigworks.character.client;

import cn.minerealms.assimploader.animation.AnimationState;
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
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.client.renderer.entity.player.PlayerRenderer;
import net.minecraft.util.Mth;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.LivingEntity;
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
    public void update(LocalPlayer player, boolean actions) {
        update(player, actions ? VanillaPoseSelection.ALL : VanillaPoseSelection.MOTION);
    }

    public void update(LocalPlayer player, VanillaPoseSelection selection) {
        if (closed || animation.isClosed()) throw new IllegalStateException("Vanilla player driver is closed");
        Objects.requireNonNull(selection);
        if (!selection.enabled()) { disable(); return; }
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

        var poses = new PoseStack();
        access.assimploader$setupRotations(player, poses, age, bodyYaw, 1);
        // Remove the yaw already supplied by LocalPlayerCharacter, then convert vanilla's model axes.
        Matrix4f root = new Matrix4f().rotateY((float)Math.toRadians(player.yBodyRot - profile.modelYawDegrees()))
                .mul(poses.last().pose()).rotateZ((float)Math.PI).translate(0, -.001f, 0)
                .rotateX((float)Math.PI).rotateY((float)Math.toRadians(profile.modelYawDegrees()));
        Map<String, VanillaPoseRetargeter.Part> parts = new LinkedHashMap<>();
        parts.put("hips", part(model.body));
        parts.put("head", part(model.head));
        parts.put("left_upper_arm", part(model.leftArm));
        parts.put("right_upper_arm", part(model.rightArm));
        parts.put("left_thigh", part(model.leftLeg));
        parts.put("right_thigh", part(model.rightLeg));
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
        if (!animation.isClosed() && animation.getModifier(MODIFIER) == retargeter) animation.removeModifier(MODIFIER);
    }
}
