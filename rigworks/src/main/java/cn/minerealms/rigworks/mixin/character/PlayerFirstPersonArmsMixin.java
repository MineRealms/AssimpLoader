package cn.minerealms.rigworks.mixin.character;

import cn.minerealms.rigworks.character.client.CharacterFirstPersonArms;
import com.mojang.blaze3d.vertex.PoseStack;
import net.minecraft.client.player.AbstractClientPlayer;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.entity.player.PlayerRenderer;
import net.minecraft.world.entity.HumanoidArm;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(PlayerRenderer.class)
public abstract class PlayerFirstPersonArmsMixin {
    @Inject(method="renderRightHand",at=@At("HEAD"),cancellable=true)
    private void rigworks$right(PoseStack poses,MultiBufferSource buffers,int light,AbstractClientPlayer player,CallbackInfo ci) {
        if(CharacterFirstPersonArms.render(player,HumanoidArm.RIGHT,poses,buffers,light))ci.cancel();
    }
    @Inject(method="renderLeftHand",at=@At("HEAD"),cancellable=true)
    private void rigworks$left(PoseStack poses,MultiBufferSource buffers,int light,AbstractClientPlayer player,CallbackInfo ci) {
        if(CharacterFirstPersonArms.render(player,HumanoidArm.LEFT,poses,buffers,light))ci.cancel();
    }
}
