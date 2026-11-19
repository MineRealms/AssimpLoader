package cn.minerealms.assimploader.mixin.camera;

import net.minecraft.client.Camera;
import net.minecraft.client.renderer.GameRenderer;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Invoker;

@Mixin(GameRenderer.class)
public interface WorldEditProjectionAccess {
    @Invoker("getFov") double assimploader$getFov(Camera camera, float partialTick, boolean changingFov);
}
