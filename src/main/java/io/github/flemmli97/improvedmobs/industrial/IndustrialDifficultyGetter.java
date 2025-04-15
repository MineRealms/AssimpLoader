package io.github.flemmli97.improvedmobs.industrial;

import io.github.flemmli97.improvedmobs.api.difficulty.DifficultyGetter;
import io.github.flemmli97.improvedmobs.config.Config;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.phys.Vec3;

/**
 * Industrial difficulty provider that calculates difficulty from multiple sources.
 * <p>
 * This difficulty getter implements a multi-factor difficulty calculation system that
 * considers various aspects of industrial progression and environmental pollution.
 * <p>
 * Contributing factors:
 * <ol>
 *   <li>Player industrial bonus (based on nearby machines and pollution)</li>
 *   <li>Local temporary pollution (chunk-based pollution spread)</li>
 *   <li>Global permanent pollution (long-term accumulation)</li>
 *   <li>Time factor (game days elapsed)</li>
 * </ol>
 * <p>
 * Each factor is weighted according to {@link TriAxisConfig} settings and combined
 * to produce the final difficulty value. This value is added to the base difficulty
 * from other sources.
 *
 * @see IndustrialDifficultyManager
 * @see PollutionManager
 * @see TriAxisConfig
 * @author ImprovedMobs Team
 */
public class IndustrialDifficultyGetter implements DifficultyGetter {

    @Override
    public float getDifficulty(ServerLevel level, Vec3 pos) {
        float totalDifficulty = 0;

        // 1. Player industrial bonus (real-time calculation based on nearby players)
        float maxPlayerBonus = 0;
        for (Player player : level.players()) {
            if (player.position().closerThan(pos, 64)) {
                float playerBonus = IndustrialDifficultyManager.getDifficultyFor(player);
                if (playerBonus > maxPlayerBonus) {
                    maxPlayerBonus = playerBonus;
                }
            }
        }
        totalDifficulty += maxPlayerBonus * TriAxisConfig.playerBonusWeight;

        // 2. Local temporary pollution (chunk-based pollution spread)
        ChunkPos chunkPos = new ChunkPos((int) pos.x >> 4, (int) pos.z >> 4);
        double localPollution = PollutionManager.getTemporaryPollution(chunkPos);
        // Normalization: 100 pollution = 1.0 difficulty
        totalDifficulty += (float) (localPollution / 100.0) * TriAxisConfig.localPollutionWeight;

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
