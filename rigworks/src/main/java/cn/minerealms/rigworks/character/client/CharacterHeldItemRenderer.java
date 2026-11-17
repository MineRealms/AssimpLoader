package cn.minerealms.rigworks.character.client;

import cn.minerealms.assimploader.animation.PoseSnapshot;
import cn.minerealms.rigworks.character.core.CharacterEquipmentDefinition;
import com.mojang.blaze3d.vertex.PoseStack;
import net.minecraft.client.Minecraft;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.world.entity.HumanoidArm;
import net.minecraft.world.item.ItemDisplayContext;
import org.joml.Quaternionf;
import org.joml.Vector3f;

/** Reads inventory and the exact rendered character pose; never changes inventory or gameplay state. */
final class CharacterHeldItemRenderer {
    private CharacterHeldItemRenderer() {}

    static void render(LocalPlayer player, CharacterEquipmentDefinition equipment, PoseSnapshot renderedPose,
                       PoseStack poses, MultiBufferSource buffers, int light, int overlay) {
        if (!equipment.enabled()) return;
        renderHand(player, equipment.rightHand(), false, renderedPose, poses, buffers, light, overlay);
        renderHand(player, equipment.leftHand(), true, renderedPose, poses, buffers, light, overlay);
    }

    private static void renderHand(LocalPlayer player, CharacterEquipmentDefinition.Mount mount, boolean left,
                                   PoseSnapshot renderedPose, PoseStack poses, MultiBufferSource buffers, int light, int overlay) {
        if (mount == null) return;
        boolean main = (player.getMainArm() == HumanoidArm.LEFT) == left;
        var stack = main ? player.getMainHandItem() : player.getOffhandItem();
        if (stack.isEmpty()) return;
        var transform = mount.transform(renderedPose);
        Vector3f position = transform.getTranslation(new Vector3f());
        poses.pushPose();
        try {
            poses.translate(position.x, position.y, position.z);
            poses.mulPose(transform.getUnnormalizedRotation(new Quaternionf()).normalize());
            poses.scale(mount.scale(), mount.scale(), mount.scale());
            Minecraft.getInstance().getItemRenderer().renderStatic(player, stack,
                    left ? ItemDisplayContext.THIRD_PERSON_LEFT_HAND : ItemDisplayContext.THIRD_PERSON_RIGHT_HAND,
                    left, poses, buffers, player.level(), light, OverlayTexture.NO_OVERLAY,
                    player.getId()+(left?ItemDisplayContext.THIRD_PERSON_LEFT_HAND:ItemDisplayContext.THIRD_PERSON_RIGHT_HAND).ordinal());
        } finally {
            poses.popPose();
        }
    }
}
