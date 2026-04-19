package cn.minerealms.iic.mixin.enhancedvisuals;

import cn.minerealms.iic.pollution.PollutionManager;
import cn.minerealms.iic.pollution.visual.PollutionVisualEffects;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.ChunkPos;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.api.distmarker.OnlyIn;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.jetbrains.annotations.Nullable;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import team.creative.enhancedvisuals.client.VisualManager;

/**
 * Mixin to inject pollution visual effects into EnhancedVisuals tick loop.
 *
 * <p>This mixin is only loaded if EnhancedVisuals is present (checked by {@link cn.minerealms.iic.mixin.MixinPlugin}).
 */
@OnlyIn(Dist.CLIENT)
@Mixin(value = VisualManager.class, remap = false, priority = 1100)
public class VisualManagerMixin {

    private static final Logger LOGGER = LogManager.getLogger("VisualManagerMixin");
    private static int tickCounter = 0;
    private static boolean firstTickLogged = false;

    /**
     * Inject pollution visual effects into the EnhancedVisuals tick loop.
     *
     * <p>This is called every client tick, so we check pollution levels and trigger effects accordingly.
     */
    @Inject(
        method = "onTick(Lnet/minecraft/world/entity/player/Player;)V",
        at = @At("HEAD"),
        remap = false,
        require = 0,
        allow = 1
    )
    private static void iic$onTick(@Nullable Player player, CallbackInfo ci) {
        // Mark mixin as applied on first tick
        if (!firstTickLogged) {
            firstTickLogged = true;
            cn.minerealms.iic.util.MixinLoadTracker.markApplied("VisualManagerMixin");
            LOGGER.error("========================================");
            LOGGER.error("MIXIN IS WORKING! First tick detected!");
            LOGGER.error("Player: {}", player != null ? player.getName().getString() : "null");
            LOGGER.error("========================================");
        }

        // Log every 100 ticks to verify mixin is working
        tickCounter++;
        if (tickCounter % 100 == 0) {
            LOGGER.info("[VisualManagerMixin] Tick: {}, Player: {}", tickCounter, player != null ? player.getName().getString() : "null");
        }

        if (player == null || !player.isAlive()) {
            return;
        }

        // Get client-side pollution data (synced from server)
        ChunkPos chunkPos = player.chunkPosition();
        double pollution = PollutionManager.getTemporaryPollution(chunkPos);

        // Trigger effects based on pollution level (handled by PollutionVisualEffects)
        cn.minerealms.iic.pollution.visual.PollutionVisualEffects.checkAndTriggerEffects(player, pollution);
    }
}
