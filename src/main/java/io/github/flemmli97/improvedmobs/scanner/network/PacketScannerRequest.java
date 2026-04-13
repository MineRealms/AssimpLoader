package io.github.flemmli97.improvedmobs.scanner.network;

import io.github.flemmli97.improvedmobs.scanner.ScannerService;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.server.level.ServerPlayer;
import net.minecraftforge.network.NetworkEvent;
import net.minecraftforge.network.NetworkDirection;
import net.minecraftforge.network.PacketDistributor;

import java.util.function.Supplier;

public class PacketScannerRequest {

    public PacketScannerRequest() {
    }

    public static void toNetwork(PacketScannerRequest pkt, FriendlyByteBuf buf) {
    }

    public static PacketScannerRequest fromNetwork(FriendlyByteBuf buf) {
        return new PacketScannerRequest();
    }

    public static void handle(PacketScannerRequest pkt, Supplier<NetworkEvent.Context> ctx) {
        ServerPlayer player = ctx.get().getSender();
        if (player == null) return;

        ScannerService.requestScan(player, scanData -> {
            PacketScannerResponse.send(player, scanData);
        });
        ctx.get().setPacketHandled(true);
    }

    public static void sendToServer(LocalPlayer player) {
        io.github.flemmli97.improvedmobs.forge.network.PacketHandler.dispatcher.sendToServer(new PacketScannerRequest());
    }
}