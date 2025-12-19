package cn.minerealms.iic.client;

import cn.minerealms.iic.industrial.PollutionManager;
import net.minecraft.client.Minecraft;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.ChunkPos;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.api.distmarker.OnlyIn;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

/**
 * Client-side event handler for pollution visual effects.
 *
 * <p>This is a backup solution if Mixin doesn't work.
 * It uses Forge's event system to trigger effects every client tick.
 */
@OnlyIn(Dist.CLIENT)
@Mod.EventBusSubscriber(modid = "integratedindustrialcraft", value = Dist.CLIENT)
public class PollutionVisualEventHandler {

    private static final Logger LOGGER = LogManager.getLogger("PollutionVisualEvents");
    private static int tickCounter = 0;

    /**
     * Called every client tick.
     * Checks pollution levels and triggers visual effects.
     */
    @SubscribeEvent
    public static void onClientTick(TickEvent.ClientTickEvent event) {
        // Only process at the end of the tick
        if (event.phase != TickEvent.Phase.END) {
            return;
        }

        tickCounter++;

        // Log every 100 ticks to verify event is working
        if (tickCounter % 100 == 0) {
            LOGGER.info("[PollutionVisualEvents] Event handler is active! Tick: {}", tickCounter);
        }

        // Get the player
        Minecraft mc = Minecraft.getInstance();
        Player player = mc.player;

        if (player == null || !player.isAlive()) {
            return;
        }

        // Get client-side pollution data (synced from server)
        ChunkPos chunkPos = player.chunkPosition();
        double pollution = PollutionManager.getTemporaryPollution(chunkPos);

        // Trigger effects based on pollution level
        try {
            cn.minerealms.iic.industrial.PollutionVisualEffects.checkAndTriggerEffects(player, pollution);
        } catch (Exception e) {
            LOGGER.error("[PollutionVisualEvents] Error triggering effects", e);
        }
    }
}
