package cn.minerealms.assimploader.mixin.camera;

import net.minecraft.client.Camera;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Invoker;

@Mixin(Camera.class)
public interface WorldEditCameraAccess {
    //? if forge {
    @Invoker("setRotation") void assimploader$setRotation(float yaw, float pitch);
    @Invoker("setPosition") void assimploader$setPosition(double x, double y, double z);
    @Invoker("getMaxZoom") double assimploader$getMaxZoom(double distance);
    @Invoker("move") void assimploader$move(double forward, double up, double left);
    //?}
    //? if neoforge {
    /*
    // 1.21 narrowed Camera's boom and move arguments from double to float, so the
    // accessor descriptors have to follow or mixin cannot locate the targets.
    @Invoker("setRotation") void assimploader$setRotation(float yaw, float pitch);
    @Invoker("setPosition") void assimploader$setPosition(double x, double y, double z);
    @Invoker("getMaxZoom") float assimploader$getMaxZoom(float distance);
    @Invoker("move") void assimploader$move(float forward, float up, float left);
    *///?}
}
