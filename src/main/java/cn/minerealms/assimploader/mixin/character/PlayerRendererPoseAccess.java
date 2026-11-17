package cn.minerealms.assimploader.mixin.character;

import com.mojang.blaze3d.vertex.PoseStack;
import net.minecraft.client.player.AbstractClientPlayer;
import net.minecraft.client.renderer.entity.player.PlayerRenderer;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Invoker;

/** Calls the vanilla renderer's pose decisions, including Forge arm-pose extensions. */
@Mixin(PlayerRenderer.class)
public interface PlayerRendererPoseAccess {
    @Invoker("setModelProperties")
    void assimploader$setModelProperties(AbstractClientPlayer player);

    @Invoker("setupRotations")
    void assimploader$setupRotations(AbstractClientPlayer player, PoseStack poses, float age, float bodyYaw, float partialTick);
}
