package cn.minerealms.assimpshowcase.vehicle;

import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;
import net.minecraftforge.network.NetworkEvent;

import java.util.function.Supplier;

/** Client input is accepted only for the sender's current driver seat. */
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
