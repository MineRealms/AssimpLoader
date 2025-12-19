package cn.minerealms.iic.difficulty;

import cn.minerealms.iic.industrial.IndustrialLogger;
import cn.minerealms.iic.industrial.TriAxisConfig;
import cn.minerealms.iic.integration.spore.HivemindProximityManager;
import cn.minerealms.iic.integration.spore.SporeIntegration;
import cn.minerealms.iic.pollution.PollutionManager;
import io.github.flemmli97.improvedmobs.api.difficulty.DifficultyGetter;
import io.github.flemmli97.improvedmobs.config.Config;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.phys.Vec3;

/**
 * ImprovedMobs API difficulty provider for location-based difficulty calculation.
 * <p>
 * <b>Responsibility:</b> Implements {@link DifficultyGetter} to provide location-based
 * difficulty values to the ImprovedMobs system. This is the external API integration
 * point that combines multiple difficulty sources.
 * <p>
 * <b>Key Differences from {@link DifficultyManager}:</b>
 * <ul>
 *   <li><b>DifficultyProvider</b> - Provides location-based difficulty to ImprovedMobs API</li>
 *   <li><b>DifficultyManager</b> - Manages per-player state, calculates player-specific bonuses</li>
 * </ul>
 * <p>
 * Contributing factors:
 * <ol>
 *   <li>Player industrial bonus (from {@link DifficultyManager}, based on nearby players)</li>
 *   <li>Local temporary pollution (chunk-based pollution spread)</li>
 *   <li>Global permanent pollution (long-term accumulation)</li>
 *   <li>Time factor (game days elapsed)</li>
 * </ol>
 * <p>
 * Each factor is weighted according to {@link TriAxisConfig} settings and combined
 * to produce the final difficulty value. This value is added to the base difficulty
 * from other sources.
 *
 * @see DifficultyManager
 * @see PollutionManager
 * @see TriAxisConfig
 * @author ImprovedMobs Team
 * @since 1.0.0
 */
public class DifficultyProvider implements DifficultyGetter {

    @Override
    public float getDifficulty(ServerLevel level, Vec3 pos) {
        float totalDifficulty = 0;

        // 1. Player industrial bonus (real-time calculation based on nearby players)
        float maxPlayerBonus = 0;
        for (Player player : level.players()) {
            if (player.position().closerThan(pos, 64)) {
                float playerBonus = DifficultyManager.getDifficultyFor(player);
                if (playerBonus > maxPlayerBonus) {
                    maxPlayerBonus = playerBonus;
                }
            }
        }
        totalDifficulty += maxPlayerBonus * TriAxisConfig.playerBonusWeight;

        // 2. Local temporary pollution (chunk-based pollution spread)
        ChunkPos chunkPos = new ChunkPos((int) pos.x >> 4, (int) pos.z >> 4);
        double localPollution = PollutionManager.getTemporaryPollution(chunkPos);

        // Hivemind Proximity Acceleration - check if pollution is near Hiveminds
        double acceleratedPollution = localPollution;
        if (SporeIntegration.isSporeLoaded() && TriAxisConfig.enableHivemindAcceleration) {
            try {
                IndustrialLogger.debug(String.format(
                        "[IIC-PollutionSystem] Checking Hivemind acceleration for chunk %s (pollution=%.2f)",
                        chunkPos, localPollution));

                // Update Hivemind cache periodically
                HivemindProximityManager.updateHivemindCache(level);

                // Check proximity and apply acceleration
                HivemindProximityManager.HivemindProximityInfo proximity =
                        HivemindProximityManager.checkProximity(chunkPos, localPollution);

                if (proximity.isNear()) {
                    double accelerationMultiplier = proximity.calculateAccelerationMultiplier();
                    acceleratedPollution = localPollution * accelerationMultiplier;

                    IndustrialLogger.debugPollution(String.format(
                            "[IIC-PollutionSystem] ✓ Hivemind acceleration applied! chunk=%s, original=%.2f, multiplier=%.2fx, accelerated=%.2f, distance=%.1f chunks, biomass=%d",
                            chunkPos, localPollution, accelerationMultiplier, acceleratedPollution, proximity.distance(), proximity.biomass()));
                } else {
                    IndustrialLogger.debug(String.format(
                            "[IIC-PollutionSystem] No Hivemind acceleration for chunk %s",
                            chunkPos));
                }
            } catch (Exception e) {
                IndustrialLogger.error("[IIC-PollutionSystem] Error applying Hivemind acceleration: " + e.getMessage(), e);
            }
        }

        // Normalization: 100 pollution = 1.0 difficulty
        totalDifficulty += (float) (acceleratedPollution / 100.0) * TriAxisConfig.localPollutionWeight;

        // 3. Global permanent pollution (long-term accumulation)
        double globalPollution = PollutionManager.getPermanentPollution();
        // Normalization: 50 permanent pollution = 1.0 difficulty
        totalDifficulty += (float) (globalPollution / 50.0) * TriAxisConfig.globalPollutionWeight;

        // 4. Time factor (game days)
        long mcDays = level.getDayTime() / 24000L;
        // Normalization: 100 days = 1.0 difficulty
        float timeFactor = (float) Math.min(mcDays / 100.0, 2.0); // Max 2.0
        totalDifficulty += timeFactor * TriAxisConfig.timeFactorWeight;

        return totalDifficulty;
    }

    @Override
    public Config.IntegrationType getType() {
        return Config.IntegrationType.ADD;  // Add to base difficulty
    }
}
