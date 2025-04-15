package cn.minerealms.iic.turrets.common.packet;

import cn.minerealms.iic.network.PacketHandler;
import net.minecraft.server.level.ServerPlayer;
import net.minecraftforge.network.PacketDistributor;

public final class MekanismTurretsPacketHandler {

    public static void registerPackets() {
        // Packets are now registered in the main PacketHandler
        // This method is kept for compatibility but does nothing
    }

    public static void sendToClient(Object msg, ServerPlayer player) {
        PacketHandler.sendToClient(msg, player);
    }

    public static void sendToServer(Object msg) {
        PacketHandler.sendToServer(msg);
    }
}
