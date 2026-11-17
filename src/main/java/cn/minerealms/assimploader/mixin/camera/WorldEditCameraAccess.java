package cn.minerealms.assimploader.mixin.camera;

import net.minecraft.client.Camera;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Invoker;

@Mixin(Camera.class)
public interface WorldEditCameraAccess {
    @Invoker("setRotation") void assimploader$setRotation(float yaw, float pitch);
    @Invoker("setPosition") void assimploader$setPosition(double x, double y, double z);
    @Invoker("getMaxZoom") double assimploader$getMaxZoom(double distance);
    @Invoker("move") void assimploader$move(double forward, double up, double left);
}
