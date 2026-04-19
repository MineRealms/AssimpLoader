package cn.minerealms.iic.network;

import cn.minerealms.iic.pollution.visual.PollutionVisualEffects;
import net.minecraft.client.Minecraft;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.world.entity.player.Player;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.fml.DistExecutor;
import net.minecraftforge.network.NetworkEvent;

import java.util.function.Supplier;

/**
 * Packet to trigger pollution visual effects on the client.
 * Sent from server command to client for testing.
 */
public class TriggerPollutionEffectPacket {

    private final String effectType;

    public TriggerPollutionEffectPacket(String effectType) {
        this.effectType = effectType;
    }

    public TriggerPollutionEffectPacket(FriendlyByteBuf buf) {
        this.effectType = buf.readUtf();
    }

    public void encode(FriendlyByteBuf buf) {
        buf.writeUtf(this.effectType);
    }

    public void handle(Supplier<NetworkEvent.Context> ctx) {
        ctx.get().enqueueWork(() -> {
            // Execute on client side only
            DistExecutor.unsafeRunWhenOn(Dist.CLIENT, () -> () -> {
                Player player = Minecraft.getInstance().player;
                if (player != null) {
                    PollutionVisualEffects.triggerTestEffect(player, effectType);
                }
            });
        });
        ctx.get().setPacketHandled(true);
    }
}
