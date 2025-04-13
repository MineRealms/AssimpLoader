package io.github.flemmli97.improvedmobs.forge.network;

import io.github.flemmli97.improvedmobs.client.DebugLineRenderer;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraftforge.network.NetworkEvent;

import java.util.function.Supplier;

public record PacketDebugLinesSync(boolean enabled) {

    public static PacketDebugLinesSync read(FriendlyByteBuf buf) {
        return new PacketDebugLinesSync(buf.readBoolean());
    }

    public static void write(PacketDebugLinesSync pkt, FriendlyByteBuf buf) {
        buf.writeBoolean(pkt.enabled());
    }

    public static void handle(PacketDebugLinesSync pkt, Supplier<NetworkEvent.Context> ctx) {
        ctx.get().enqueueWork(() -> DebugLineRenderer.renderLines = pkt.enabled());
        ctx.get().setPacketHandled(true);
    }
}