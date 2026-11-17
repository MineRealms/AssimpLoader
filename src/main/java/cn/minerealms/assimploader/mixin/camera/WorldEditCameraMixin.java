package cn.minerealms.assimploader.mixin.camera;

import cn.minerealms.assimploader.client.transform.WorldEditCamera;
import net.minecraft.client.Camera;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.level.BlockGetter;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(Camera.class)
public abstract class WorldEditCameraMixin {
    @Inject(method = "setup", at = @At("TAIL"))
    private void assimploader$applyWorkbenchCamera(BlockGetter level, Entity entity, boolean detached,
                                                  boolean mirrored, float partialTick, CallbackInfo callback) {
        WorldEditCamera.apply((Camera) (Object) this, level, entity, partialTick);
    }
}
