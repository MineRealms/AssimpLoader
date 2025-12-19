package cn.minerealms.iic.network;

import cn.minerealms.iic.client.hud.DifficultyHudData;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.client.Minecraft;
import net.minecraftforge.network.NetworkEvent;

import java.util.function.Supplier;

/**
 * 同步HUD数据到客户端
 */
public class SyncHudDataPacket {

    private final double totalDifficulty;
    private final double timeFactor;
    private final double voltageFactor;
    private final double pollutionFactor;
    private final int nearbyMachines;
    private final double medianTier;
    private final double industrialBonus;
    private final double localPollution;
    private final double globalPollution;
    private final int activeHiveminds;
    private final int evolutionPhase;
    private final float infectionLevel;
    private final int totalBiomass;
    private final int totalHosts;
    private final int infectedChunks;
    private final double sporeMultiplier;
    private final double localPollutionProgress;
    private final String pollutionLevelText;
    private final double globalGameStage;
    private final boolean showHud;

    public SyncHudDataPacket(
            double totalDifficulty, double timeFactor, double voltageFactor, double pollutionFactor,
            int nearbyMachines, double medianTier, double industrialBonus,
            double localPollution, double globalPollution,
            int activeHiveminds, int evolutionPhase, float infectionLevel,
            int totalBiomass, int totalHosts, int infectedChunks, double sporeMultiplier,
            double localPollutionProgress, String pollutionLevelText, double globalGameStage,
            boolean showHud) {
        this.totalDifficulty = totalDifficulty;
        this.timeFactor = timeFactor;
        this.voltageFactor = voltageFactor;
        this.pollutionFactor = pollutionFactor;
        this.nearbyMachines = nearbyMachines;
        this.medianTier = medianTier;
        this.industrialBonus = industrialBonus;
        this.localPollution = localPollution;
        this.globalPollution = globalPollution;
        this.activeHiveminds = activeHiveminds;
        this.evolutionPhase = evolutionPhase;
        this.infectionLevel = infectionLevel;
        this.totalBiomass = totalBiomass;
        this.totalHosts = totalHosts;
        this.infectedChunks = infectedChunks;
        this.sporeMultiplier = sporeMultiplier;
        this.localPollutionProgress = localPollutionProgress;
        this.pollutionLevelText = pollutionLevelText;
        this.globalGameStage = globalGameStage;
        this.showHud = showHud;
    }

    public static void encode(SyncHudDataPacket packet, FriendlyByteBuf buf) {
        buf.writeDouble(packet.totalDifficulty);
        buf.writeDouble(packet.timeFactor);
        buf.writeDouble(packet.voltageFactor);
        buf.writeDouble(packet.pollutionFactor);
        buf.writeInt(packet.nearbyMachines);
        buf.writeDouble(packet.medianTier);
        buf.writeDouble(packet.industrialBonus);
        buf.writeDouble(packet.localPollution);
        buf.writeDouble(packet.globalPollution);
        buf.writeInt(packet.activeHiveminds);
        buf.writeInt(packet.evolutionPhase);
        buf.writeFloat(packet.infectionLevel);
        buf.writeInt(packet.totalBiomass);
        buf.writeInt(packet.totalHosts);
        buf.writeInt(packet.infectedChunks);
        buf.writeDouble(packet.sporeMultiplier);
        buf.writeDouble(packet.localPollutionProgress);
        buf.writeUtf(packet.pollutionLevelText);
        buf.writeDouble(packet.globalGameStage);
        buf.writeBoolean(packet.showHud);
    }

    public static SyncHudDataPacket decode(FriendlyByteBuf buf) {
        return new SyncHudDataPacket(
                buf.readDouble(), buf.readDouble(), buf.readDouble(), buf.readDouble(),
                buf.readInt(), buf.readDouble(), buf.readDouble(),
                buf.readDouble(), buf.readDouble(),
                buf.readInt(), buf.readInt(), buf.readFloat(),
                buf.readInt(), buf.readInt(), buf.readInt(), buf.readDouble(),
                buf.readDouble(), buf.readUtf(), buf.readDouble(),
                buf.readBoolean()
        );
    }

    public static void handle(SyncHudDataPacket packet, Supplier<NetworkEvent.Context> ctx) {
        ctx.get().enqueueWork(() -> {
            Minecraft mc = Minecraft.getInstance();
            if (mc.player != null) {
                DifficultyHudData.PlayerHudData data = DifficultyHudData.getOrCreate(mc.player.getUUID());
                data.totalDifficulty = packet.totalDifficulty;
                data.timeFactor = packet.timeFactor;
                data.voltageFactor = packet.voltageFactor;
                data.pollutionFactor = packet.pollutionFactor;
                data.nearbyMachines = packet.nearbyMachines;
                data.medianTier = packet.medianTier;
                data.industrialBonus = packet.industrialBonus;
                data.localPollution = packet.localPollution;
                data.globalPollution = packet.globalPollution;
                data.activeHiveminds = packet.activeHiveminds;
                data.evolutionPhase = packet.evolutionPhase;
                data.infectionLevel = packet.infectionLevel;
                data.totalBiomass = packet.totalBiomass;
                data.totalHosts = packet.totalHosts;
                data.infectedChunks = packet.infectedChunks;
                data.sporeMultiplier = packet.sporeMultiplier;
                data.localPollutionProgress = packet.localPollutionProgress;
                data.pollutionLevel = packet.pollutionLevelText;
                data.globalGameStage = packet.globalGameStage;
                data.showHud = packet.showHud;
                data.lastUpdate = System.currentTimeMillis();

                // Debug logging on client side
                if (cn.minerealms.iic.industrial.IndustrialLogger.isDebugEnabled()) {
                    cn.minerealms.iic.industrial.IndustrialLogger.info(String.format(
                            "[CLIENT] Received HUD data: Machines=%d, MedianTier=%.2f, Total=%.4f, Time=%.4f, Voltage=%.4f, Pollution=%.4f",
                            data.nearbyMachines, data.medianTier, data.totalDifficulty, data.timeFactor, data.voltageFactor, data.pollutionFactor));
                }
            }
        });
        ctx.get().setPacketHandled(true);
    }
}
