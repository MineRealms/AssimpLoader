package io.github.flemmli97.improvedmobs.forge.network;

import io.github.flemmli97.improvedmobs.client.DebugLineRenderer;
import net.minecraft.core.BlockPos;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraftforge.network.NetworkEvent;

import java.util.function.Supplier;

public record PacketDebugLine(int entityId, BlockPos pos) {

    public static PacketDebugLine read(FriendlyByteBuf buf) {
        return new PacketDebugLine(buf.readInt(), buf.readBlockPos());
    }

    public static void write(PacketDebugLine pkt, FriendlyByteBuf buf) {
        buf.writeInt(pkt.entityId);
        buf.writeBlockPos(pkt.pos);
    }

    public static void handle(PacketDebugLine pkt, Supplier<NetworkEvent.Context> ctx) {
        ctx.get().enqueueWork(() -> DebugLineRenderer.updateTarget(pkt.entityId, pkt.pos));
        ctx.get().setPacketHandled(true);
    }
}
