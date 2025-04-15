package cn.minerealms.iic.scanner.network;

import cn.minerealms.iic.scanner.ScanData;
import cn.minerealms.iic.scanner.client.ScannerScreen;
import net.minecraft.client.Minecraft;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.server.level.ServerPlayer;
import net.minecraftforge.network.NetworkDirection;
import net.minecraftforge.network.NetworkEvent;
import net.minecraftforge.network.PacketDistributor;

import java.util.function.Supplier;

public class PacketScannerResponse {

    private final ScanData scanData;

    public PacketScannerResponse(ScanData scanData) {
        this.scanData = scanData;
    }

    public static void toNetwork(PacketScannerResponse pkt, FriendlyByteBuf buf) {
        pkt.scanData.toNetwork(buf);
    }

    public static PacketScannerResponse fromNetwork(FriendlyByteBuf buf) {
        return new PacketScannerResponse(ScanData.fromNetwork(buf));
    }

    public static void handle(PacketScannerResponse pkt, Supplier<NetworkEvent.Context> ctx) {
        ctx.get().enqueueWork(() -> {
            Minecraft mc = Minecraft.getInstance();
            if (mc.screen instanceof ScannerScreen screen) {
                screen.onScanDataReceived(pkt.scanData);
            }
        });
        ctx.get().setPacketHandled(true);
    }

    public static void send(ServerPlayer player, ScanData data) {
        cn.minerealms.iic.network.PacketHandler.INSTANCE.sendTo(
                new PacketScannerResponse(data),
                player.connection.connection,
                NetworkDirection.PLAY_TO_CLIENT
        );
    }
}