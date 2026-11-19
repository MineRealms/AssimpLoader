package cn.minerealms.assimpshowcase.vehicle;

import cn.minerealms.assimploader.AssimpLoader;
//? if forge {
import net.minecraft.resources.ResourceLocation;
import net.minecraftforge.network.NetworkDirection;
import net.minecraftforge.network.NetworkRegistry;
import net.minecraftforge.network.simple.SimpleChannel;
import cn.minerealms.assimploader.util.ResourceLocations;
//?}
//? if neoforge {
/*
import net.neoforged.neoforge.network.PacketDistributor;
import net.neoforged.neoforge.network.event.RegisterPayloadHandlersEvent;
import net.neoforged.neoforge.network.registration.PayloadRegistrar;
*///?}
public final class VehicleNetworking {
    private static final String PROTOCOL = "1";
    //? if forge {
    private static final SimpleChannel CHANNEL = NetworkRegistry.ChannelBuilder
            .named(ResourceLocations.of(AssimpLoader.MODID, "vehicle"))
            .networkProtocolVersion(() -> PROTOCOL)
            .clientAcceptedVersions(PROTOCOL::equals)
            .serverAcceptedVersions(PROTOCOL::equals)
            .simpleChannel();
    //?}
    private static boolean registered;

    //? if forge {
    public static synchronized void register() {
        if (registered) return;
        CHANNEL.messageBuilder(VehicleInputPacket.class, 0, NetworkDirection.PLAY_TO_SERVER)
                .encoder(VehicleInputPacket::encode)
                .decoder(VehicleInputPacket::decode)
                .consumerMainThread(VehicleInputPacket::handle)
                .add();
        registered = true;
    }
    //?}
    //? if neoforge {
    /*
    public static synchronized void registerPayloads(RegisterPayloadHandlersEvent event) {
        if (registered) return;
        PayloadRegistrar registrar = event.registrar(PROTOCOL);
        registrar.playToServer(VehicleInputPacket.TYPE, VehicleInputPacket.STREAM_CODEC, VehicleInputPacket::handle);
        registered = true;
    }
    *///?}

    public static void sendInput(int entityId, int sequence, float throttle, float steering, boolean handbrake) {
        byte throttleValue = (byte) Math.round(Math.max(-1.0f, Math.min(1.0f, throttle)) * 100.0f);
        byte steeringValue = (byte) Math.round(Math.max(-1.0f, Math.min(1.0f, steering)) * 100.0f);
        //? if forge {
        CHANNEL.sendToServer(new VehicleInputPacket(entityId, sequence, throttleValue, steeringValue, handbrake));
        //?}
        //? if neoforge {
        /*
        PacketDistributor.sendToServer(
                new VehicleInputPacket(entityId, sequence, throttleValue, steeringValue, handbrake));
        *///?}
    }

    private VehicleNetworking() {}
}
