package cn.minerealms.assimpshowcase.vehicle;

/** Client input is accepted only for the sender's current driver seat. */
//? if forge {
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;
import net.minecraftforge.network.NetworkEvent;

import java.util.function.Supplier;

public record VehicleInputPacket(int entityId, int sequence, byte throttle, byte steering, boolean handbrake) {
    public static void encode(VehicleInputPacket packet, FriendlyByteBuf buffer) {
        buffer.writeVarInt(packet.entityId());
        buffer.writeInt(packet.sequence());
        buffer.writeByte(packet.throttle());
        buffer.writeByte(packet.steering());
        buffer.writeBoolean(packet.handbrake());
    }

    public static VehicleInputPacket decode(FriendlyByteBuf buffer) {
        return new VehicleInputPacket(buffer.readVarInt(), buffer.readInt(), buffer.readByte(),
                buffer.readByte(), buffer.readBoolean());
    }

    public static void handle(VehicleInputPacket packet, Supplier<NetworkEvent.Context> contextSupplier) {
        NetworkEvent.Context context = contextSupplier.get();
        ServerPlayer sender = context.getSender();
        if (sender != null) {
            Entity ridden = sender.getVehicle();
            if (ridden instanceof ArmoredCarEntity car && ridden.getId() == packet.entityId()) {
                car.applyInput(sender, packet.sequence(),
                        clamp(packet.throttle()) / 100.0f, clamp(packet.steering()) / 100.0f,
                        packet.handbrake());
            }
        }
        context.setPacketHandled(true);
    }

    private static int clamp(byte value) { return Math.max(-100, Math.min(100, value)); }
}
//?}
//? if neoforge {
/*
import cn.minerealms.assimploader.AssimpLoader;
import cn.minerealms.assimploader.util.ResourceLocations;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;
import net.neoforged.neoforge.network.handling.IPayloadContext;

// Client input is accepted only for the sender's current driver seat.
public record VehicleInputPacket(int entityId, int sequence, byte throttle, byte steering, boolean handbrake)
        implements CustomPacketPayload {
    // 1.21 replaced Forge's SimpleChannel with the vanilla typed-payload registry.
    public static final Type<VehicleInputPacket> TYPE =
            new Type<>(ResourceLocations.of(AssimpLoader.MODID, "vehicle_input"));
    public static final StreamCodec<FriendlyByteBuf, VehicleInputPacket> STREAM_CODEC =
            CustomPacketPayload.codec(VehicleInputPacket::encode, VehicleInputPacket::decode);

    @Override public Type<? extends CustomPacketPayload> type() { return TYPE; }

    public static void encode(VehicleInputPacket packet, FriendlyByteBuf buffer) {
        buffer.writeVarInt(packet.entityId());
        buffer.writeInt(packet.sequence());
        buffer.writeByte(packet.throttle());
        buffer.writeByte(packet.steering());
        buffer.writeBoolean(packet.handbrake());
    }

    public static VehicleInputPacket decode(FriendlyByteBuf buffer) {
        return new VehicleInputPacket(buffer.readVarInt(), buffer.readInt(), buffer.readByte(),
                buffer.readByte(), buffer.readBoolean());
    }

    public static void handle(VehicleInputPacket packet, IPayloadContext context) {
        if (context.player() instanceof ServerPlayer sender) {
            Entity ridden = sender.getVehicle();
            if (ridden instanceof ArmoredCarEntity car && ridden.getId() == packet.entityId()) {
                car.applyInput(sender, packet.sequence(),
                        clamp(packet.throttle()) / 100.0f, clamp(packet.steering()) / 100.0f,
                        packet.handbrake());
            }
        }
    }

    private static int clamp(byte value) { return Math.max(-100, Math.min(100, value)); }
}
*///?}
