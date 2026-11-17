package cn.minerealms.assimploader.mixin.visibility;

import cn.minerealms.assimploader.render.ModelVisibility;
import net.minecraft.client.renderer.culling.Frustum;
import net.minecraft.world.phys.AABB;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(Frustum.class)
public abstract class ShowcaseFrustumMixin {
    @Inject(method = "isVisible", at = @At("HEAD"), cancellable = true)
    private void assimploader$allowForcedShowcase(AABB bounds, CallbackInfoReturnable<Boolean> callback) {
        if (ModelVisibility.bypassFrustum(bounds)) callback.setReturnValue(true);
    }
}
