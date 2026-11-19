package cn.minerealms.assimploader.mixin.visibility;

import cn.minerealms.assimploader.render.ModelVisibility;
import net.minecraft.world.phys.AABB;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

//? if forge {
/** Embeddium uses its own viewport instead of Minecraft's Frustum for block entities. */
@Pseudo
@Mixin(targets = "me.jellysquid.mods.sodium.client.render.viewport.Viewport", remap = false)
public abstract class ShowcaseViewportMixin {
    @Inject(method = "isBoxVisible(Lnet/minecraft/world/phys/AABB;)Z", at = @At("HEAD"),
            cancellable = true, remap = false)
    private void assimploader$allowForcedShowcase(AABB bounds, CallbackInfoReturnable<Boolean> callback) {
        if (ModelVisibility.bypassFrustum(bounds)) callback.setReturnValue(true);
    }
}
//?}
//? if neoforge {
/*
// Sodium 0.6+ moved the cull test onto the `Frustum` interface and dropped the AABB overload,
// so the forced-showcase bypass hooks the single `testAab` implementation instead.
@Pseudo
@Mixin(targets = "net.caffeinemc.mods.sodium.client.render.viewport.frustum.SimpleFrustum", remap = false)
public abstract class ShowcaseViewportMixin {
    @Inject(method = "testAab(FFFFFF)Z", at = @At("HEAD"),
            cancellable = true, remap = false)
    private void assimploader$allowForcedShowcase(float minX, float minY, float minZ,
                                                   float maxX, float maxY, float maxZ,
                                                   CallbackInfoReturnable<Boolean> callback) {
        if (ModelVisibility.bypassFrustum(new AABB(minX, minY, minZ, maxX, maxY, maxZ))) callback.setReturnValue(true);
    }
}
*///?}
