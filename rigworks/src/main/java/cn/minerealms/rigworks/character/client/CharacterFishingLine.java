package cn.minerealms.rigworks.character.client;

import com.mojang.blaze3d.vertex.PoseStack;
import net.minecraft.client.Minecraft;
import net.minecraft.world.entity.HumanoidArm;
import net.minecraft.world.entity.projectile.FishingHook;
import net.minecraft.world.item.ItemDisplayContext;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.client.ForgeHooksClient;
import net.minecraftforge.common.ToolActions;
import org.joml.Matrix4f;
import org.joml.Matrix4fc;
import org.joml.Vector3f;

/** Visual-only fishing endpoint; the hook and fishing gameplay stay server-authoritative. */
public final class CharacterFishingLine {
    private CharacterFishingLine() {}

    /** Null preserves the original line for first-person, other players and an unavailable character. */
    public static Vec3 endpoint(FishingHook hook, float partialTick) {
        Minecraft minecraft = Minecraft.getInstance();
        var player = minecraft.player;
        if (player == null || hook.getPlayerOwner() != player || minecraft.options.getCameraType().isFirstPerson()) return null;
        boolean mainRod = player.getMainHandItem().canPerformAction(ToolActions.FISHING_ROD_CAST);
        var rod = mainRod ? player.getMainHandItem() : player.getOffhandItem();
        if (!rod.canPerformAction(ToolActions.FISHING_ROD_CAST)) return null;
        HumanoidArm arm = physicalArm(player.getMainArm(), mainRod);
        Matrix4f worldMount = LocalPlayerCharacter.equipmentTransform(arm, partialTick);
        if (worldMount == null) return null;
        boolean left = arm == HumanoidArm.LEFT;
        var context = left ? ItemDisplayContext.THIRD_PERSON_LEFT_HAND : ItemDisplayContext.THIRD_PERSON_RIGHT_HAND;
        var model = minecraft.getItemRenderer().getModel(rod, player.level(), player, player.getId() + context.ordinal());
        // Match ItemRenderer: resource-pack/Forge display transform, then centered generated-item coordinates.
        PoseStack itemPose = new PoseStack();
        ForgeHooksClient.handleCameraTransforms(itemPose, model, context, left);
        Vector3f point = transformTip(worldMount, itemPose.last().pose());
        return new Vec3(net.minecraft.util.Mth.lerp(partialTick, player.xo, player.getX()) + point.x,
                net.minecraft.util.Mth.lerp(partialTick, player.yo, player.getY()) + point.y,
                net.minecraft.util.Mth.lerp(partialTick, player.zo, player.getZ()) + point.z);
    }

    static HumanoidArm physicalArm(HumanoidArm mainArm, boolean mainHandRod) {
        return mainHandRod ? mainArm : mainArm.getOpposite();
    }

    /** Vanilla cast-rod tip is the opaque pixel (14,1) on its 16x16 generated item plane. */
    static Vector3f transformTip(Matrix4fc worldMount, Matrix4fc itemDisplay) {
        return new Matrix4f(worldMount).mul(itemDisplay).transformPosition(new Vector3f(6.5f / 16, 6.5f / 16, 0));
    }

    public static Vector3f hookDelta(Vec3 endpoint, double hookX, double hookY, double hookZ) {
        // stringVertex adds +.25 Y itself, so subtract it once when replacing its end delta.
        return new Vector3f((float)(endpoint.x - hookX), (float)(endpoint.y - hookY - .25), (float)(endpoint.z - hookZ));
    }
}
