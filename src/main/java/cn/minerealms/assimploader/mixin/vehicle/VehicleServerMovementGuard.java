package cn.minerealms.assimploader.mixin.vehicle;

import cn.minerealms.assimploader.vehicle.ArmoredCarEntity;

import net.minecraft.network.protocol.game.ServerboundMoveVehiclePacket;
import net.minecraft.server.network.ServerGamePacketListenerImpl;
import net.minecraft.server.level.ServerPlayer;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** This vehicle accepts controls only; vanilla client-authored vehicle positions are ignored. */
@Mixin(ServerGamePacketListenerImpl.class)
public abstract class VehicleServerMovementGuard {
    @Shadow public ServerPlayer player;

    @Inject(method = "handleMoveVehicle", at = @At(value = "INVOKE",
            target = "Lnet/minecraft/network/protocol/PacketUtils;ensureRunningOnSameThread(Lnet/minecraft/network/protocol/Packet;Lnet/minecraft/network/PacketListener;Lnet/minecraft/server/level/ServerLevel;)V",
            shift = At.Shift.AFTER), cancellable = true)
    private void assimploader$ignoreClientVehiclePosition(ServerboundMoveVehiclePacket packet, CallbackInfo callback) {
        if (player.getRootVehicle() instanceof ArmoredCarEntity) callback.cancel();
    }
}
