package cn.minerealms.assimpshowcase.mixin.vehicle;

import cn.minerealms.assimpshowcase.vehicle.ArmoredCarEntity;
import net.minecraft.client.Camera;
import net.minecraft.world.entity.Entity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.ModifyArg;

@Mixin(Camera.class)
public abstract class ArmoredCarCameraMixin {
    @Shadow private Entity entity;

    //? if forge {
    @ModifyArg(method = "setup", at = @At(value = "INVOKE",
            target = "Lnet/minecraft/client/Camera;getMaxZoom(D)D"), index = 0)
    private double assimploader$moveCameraBehindArmoredCar(double distance) {
        if (entity != null && entity.getVehicle() instanceof ArmoredCarEntity)
            return Math.max(distance, ArmoredCarEntity.CAMERA_DISTANCE);
        return distance;
    }
    //?}
    //? if neoforge {
    /*
    // 1.21 feeds getMaxZoom a float, so the replacement argument has to follow.
    @ModifyArg(method = "setup", at = @At(value = "INVOKE",
            target = "Lnet/minecraft/client/Camera;getMaxZoom(F)F"), index = 0)
    private float assimploader$moveCameraBehindArmoredCar(float distance) {
        if (entity != null && entity.getVehicle() instanceof ArmoredCarEntity)
            return Math.max(distance, (float) ArmoredCarEntity.CAMERA_DISTANCE);
        return distance;
    }
    *///?}
}
