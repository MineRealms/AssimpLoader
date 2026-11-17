package cn.minerealms.rigworks.mixin.character;

import cn.minerealms.rigworks.character.client.CharacterFishingLine;
import com.mojang.blaze3d.vertex.PoseStack;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.entity.FishingHookRenderer;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.projectile.FishingHook;
import org.joml.Vector3f;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.ModifyArg;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** Replaces only the line delta, retaining vanilla hook geometry and its catenary strip. */
@Mixin(FishingHookRenderer.class)
public abstract class FishingHookRendererMixin {
    @Unique private Vector3f assimploader$lineDelta;

    @Inject(method = "render(Lnet/minecraft/world/entity/projectile/FishingHook;FFLcom/mojang/blaze3d/vertex/PoseStack;Lnet/minecraft/client/renderer/MultiBufferSource;I)V", at = @At("HEAD"))
    private void assimploader$resolveRodTip(FishingHook hook, float yaw, float partialTick, PoseStack poses,
                                          MultiBufferSource buffers, int light, CallbackInfo ci) {
        assimploader$lineDelta = null;
        var endpoint = CharacterFishingLine.endpoint(hook, partialTick);
        if (endpoint != null) assimploader$lineDelta = CharacterFishingLine.hookDelta(endpoint,
                Mth.lerp(partialTick, hook.xo, hook.getX()), Mth.lerp(partialTick, hook.yo, hook.getY()),
                Mth.lerp(partialTick, hook.zo, hook.getZ()));
    }

    @ModifyArg(method = "render(Lnet/minecraft/world/entity/projectile/FishingHook;FFLcom/mojang/blaze3d/vertex/PoseStack;Lnet/minecraft/client/renderer/MultiBufferSource;I)V", index=0,
            at = @At(value = "INVOKE", target = "Lnet/minecraft/client/renderer/entity/FishingHookRenderer;stringVertex(FFFLcom/mojang/blaze3d/vertex/VertexConsumer;Lcom/mojang/blaze3d/vertex/PoseStack$Pose;FF)V"))
    private float assimploader$lineX(float original) {return assimploader$lineDelta==null?original:assimploader$lineDelta.x;}

    @ModifyArg(method = "render(Lnet/minecraft/world/entity/projectile/FishingHook;FFLcom/mojang/blaze3d/vertex/PoseStack;Lnet/minecraft/client/renderer/MultiBufferSource;I)V", index=1,
            at = @At(value = "INVOKE", target = "Lnet/minecraft/client/renderer/entity/FishingHookRenderer;stringVertex(FFFLcom/mojang/blaze3d/vertex/VertexConsumer;Lcom/mojang/blaze3d/vertex/PoseStack$Pose;FF)V"))
    private float assimploader$lineY(float original) {return assimploader$lineDelta==null?original:assimploader$lineDelta.y;}

    @ModifyArg(method = "render(Lnet/minecraft/world/entity/projectile/FishingHook;FFLcom/mojang/blaze3d/vertex/PoseStack;Lnet/minecraft/client/renderer/MultiBufferSource;I)V", index=2,
            at = @At(value = "INVOKE", target = "Lnet/minecraft/client/renderer/entity/FishingHookRenderer;stringVertex(FFFLcom/mojang/blaze3d/vertex/VertexConsumer;Lcom/mojang/blaze3d/vertex/PoseStack$Pose;FF)V"))
    private float assimploader$lineZ(float original) {return assimploader$lineDelta==null?original:assimploader$lineDelta.z;}
}
