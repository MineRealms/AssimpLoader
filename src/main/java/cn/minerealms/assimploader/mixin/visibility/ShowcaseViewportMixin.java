package cn.minerealms.assimploader.mixin.visibility;

import cn.minerealms.assimploader.showcase.ShowcaseVisibility;
import net.minecraft.world.phys.AABB;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/** Embeddium uses its own viewport instead of Minecraft's Frustum for block entities. */
@Pseudo
@Mixin(targets = "me.jellysquid.mods.sodium.client.render.viewport.Viewport", remap = false)
public abstract class ShowcaseViewportMixin {
    @Inject(method = "isBoxVisible(Lnet/minecraft/world/phys/AABB;)Z", at = @At("HEAD"),
            cancellable = true, remap = false)
    private void assimploader$allowForcedShowcase(AABB bounds, CallbackInfoReturnable<Boolean> callback) {
        if (ShowcaseVisibility.bypassFrustum(bounds)) callback.setReturnValue(true);
    }
}
