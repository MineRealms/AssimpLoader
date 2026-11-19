package cn.minerealms.assimploader.mixin.lifecycle;

import cn.minerealms.assimploader.render.ClientRenderResources;
import cn.minerealms.assimploader.resource.ModelCacheLifecycle;
import net.minecraft.client.Minecraft;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(Minecraft.class)
public abstract class MinecraftShutdownMixin {
    @Inject(method = "close", at = @At("HEAD"))
    private void assimploader$closeModelResources(CallbackInfo callback) {
        // stop() fires GameShuttingDownEvent while the current frame can still tick.
        try {
            ModelCacheLifecycle.shutdown();
        } finally {
            ClientRenderResources.shutdown();
        }
    }
}
