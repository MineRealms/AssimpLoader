package cn.minerealms.iic.network;

import cn.minerealms.iic.IntegratedIndustrialCraft;
import cn.minerealms.iic.scanner.network.PacketScannerRequest;
import cn.minerealms.iic.scanner.network.PacketScannerResponse;
import cn.minerealms.iic.turrets.common.packet.ModifyTurretTargetPacket;
import net.minecraft.core.BlockPos;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraftforge.network.ConnectionData;
import net.minecraftforge.network.NetworkDirection;
import net.minecraftforge.network.NetworkHooks;
import net.minecraftforge.network.NetworkRegistry;
import net.minecraftforge.network.PacketDistributor;
import net.minecraftforge.network.simple.SimpleChannel;

public class PacketHandler {

    private static final String PROTOCOL_VERSION = "1";
    private static final ResourceLocation channelID = new ResourceLocation(IntegratedIndustrialCraft.MODID, "main");

    public static final SimpleChannel INSTANCE = NetworkRegistry.newSimpleChannel(
            channelID,
            () -> PROTOCOL_VERSION,
            PROTOCOL_VERSION::equals,
            PROTOCOL_VERSION::equals
    );

    private static int packetId = 0;

    private static int id() {
        return packetId++;
    }

    public static void register() {
        // HUD sync packet
        INSTANCE.messageBuilder(SyncHudDataPacket.class, id(), NetworkDirection.PLAY_TO_CLIENT)
                .decoder(SyncHudDataPacket::decode)
                .encoder(SyncHudDataPacket::encode)
                .consumerMainThread(SyncHudDataPacket::handle)
                .add();

        // Scanner packets
        INSTANCE.messageBuilder(PacketScannerRequest.class, id(), NetworkDirection.PLAY_TO_SERVER)
                .decoder(PacketScannerRequest::fromNetwork)
                .encoder(PacketScannerRequest::toNetwork)
                .consumerMainThread(PacketScannerRequest::handle)
                .add();

        INSTANCE.messageBuilder(PacketScannerResponse.class, id(), NetworkDirection.PLAY_TO_CLIENT)
                .decoder(PacketScannerResponse::fromNetwork)
                .encoder(PacketScannerResponse::toNetwork)
                .consumerMainThread(PacketScannerResponse::handle)
                .add();

        // Turret packets
        INSTANCE.registerMessage(id(), ModifyTurretTargetPacket.class,
                ModifyTurretTargetPacket::encode,
                ModifyTurretTargetPacket::decode,
                ModifyTurretTargetPacket::receivePacket);
    }

    public static void sendHudDataToPlayer(SyncHudDataPacket packet, ServerPlayer player) {
        if (hasChannel(player)) {
            INSTANCE.sendTo(packet, player.connection.connection, NetworkDirection.PLAY_TO_CLIENT);
        }
    }

    public static void sendDebugLineToAll(int entityId, BlockPos pos, MinecraftServer server) {
        // Debug line rendering is handled by ImprovedMobs, this is a no-op for IIC
        // We keep this method for compatibility with ZombieDestroyMachineGoal
    }

    public static void sendToClient(Object msg, ServerPlayer player) {
        INSTANCE.send(PacketDistributor.PLAYER.with(() -> player), msg);
    }

    public static void sendToServer(Object msg) {
        INSTANCE.send(PacketDistributor.SERVER.noArg(), msg);
    }

    private static boolean hasChannel(ServerPlayer player) {
        ConnectionData data = NetworkHooks.getConnectionData(player.connection.connection);
        return data != null && data.getChannels().containsKey(channelID);
    }
}
