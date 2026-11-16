package cn.minerealms.assimploader.mixin.visibility;

import cn.minerealms.assimploader.client.WorldModelBatches;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** Preserve opaque-before-translucent order when vanilla changes its dynamic buffer's render type. */
@Mixin(MultiBufferSource.BufferSource.class)
public abstract class WorldBatchFlushMixin {
    @Inject(method="endBatch(Lnet/minecraft/client/renderer/RenderType;)V",at=@At("HEAD"))
    private void assimploader$flushOpaqueBatch(RenderType type,CallbackInfo callback) {
        WorldModelBatches.beforeBufferFlush();
    }
}
