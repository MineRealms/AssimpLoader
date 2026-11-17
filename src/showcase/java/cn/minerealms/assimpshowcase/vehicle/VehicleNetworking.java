package cn.minerealms.assimpshowcase.vehicle;

import cn.minerealms.assimploader.AssimpLoader;
import net.minecraft.resources.ResourceLocation;
import net.minecraftforge.network.NetworkDirection;
import net.minecraftforge.network.NetworkRegistry;
import net.minecraftforge.network.simple.SimpleChannel;

public final class VehicleNetworking {
    private static final String PROTOCOL = "1";
    private static final SimpleChannel CHANNEL = NetworkRegistry.ChannelBuilder
            .named(new ResourceLocation(AssimpLoader.MODID, "vehicle"))
            .networkProtocolVersion(() -> PROTOCOL)
            .clientAcceptedVersions(PROTOCOL::equals)
            .serverAcceptedVersions(PROTOCOL::equals)
            .simpleChannel();
    private static boolean registered;

    public static synchronized void register() {
        if (registered) return;
        CHANNEL.messageBuilder(VehicleInputPacket.class, 0, NetworkDirection.PLAY_TO_SERVER)
                .encoder(VehicleInputPacket::encode)
                .decoder(VehicleInputPacket::decode)
                .consumerMainThread(VehicleInputPacket::handle)
                .add();
        registered = true;
    }

    public static void sendInput(int entityId, int sequence, float throttle, float steering, boolean handbrake) {
        byte throttleValue = (byte) Math.round(Math.max(-1.0f, Math.min(1.0f, throttle)) * 100.0f);
        byte steeringValue = (byte) Math.round(Math.max(-1.0f, Math.min(1.0f, steering)) * 100.0f);
        CHANNEL.sendToServer(new VehicleInputPacket(entityId, sequence, throttleValue, steeringValue, handbrake));
    }

    private VehicleNetworking() {}
}
