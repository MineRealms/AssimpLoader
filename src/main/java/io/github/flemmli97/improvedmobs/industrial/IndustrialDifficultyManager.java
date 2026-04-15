package io.github.flemmli97.improvedmobs.industrial;

import java.util.Map;
import java.util.HashMap;
import java.util.UUID;
import net.minecraft.world.entity.player.Player;

/**
 * Manages per-player industrial difficulty bonuses based on nearby machines and pollution.
 * <p>
 * This manager tracks each player's industrial progression by scanning nearby machines
 * and pollution levels, then calculating a difficulty bonus that reflects their
 * technological advancement and environmental impact.
 * <p>
 * The calculation process:
 * <ol>
 *   <li>Scan nearby machines to determine median voltage tier (tech score)</li>
 *   <li>Scan pollution levels using exponential moving average (pollution score)</li>
 *   <li>Combine scores using configured weights to get target difficulty</li>
 *   <li>Apply smoothing to prevent sudden difficulty spikes</li>
 * </ol>
 * <p>
 * Scanning occurs every {@value #SCAN_INTERVAL} ticks (1 second) for performance.
 * Debug logging outputs every 100 ticks (5 seconds) when enabled.
 *
 * @see MachineScanner
 * @see HazardScanner
 * @see DifficultySmoother
 * @see TriAxisConfig
 * @author ImprovedMobs Team
 */
public class IndustrialDifficultyManager {

    /**
     * Per-player industrial difficulty bonus values.
     */
    private static final Map<UUID, Float> playerIndustrialDifficulty = new HashMap<>();

    /**
     * Per-player pollution exponential moving average values.
     */
    private static final Map<UUID, Float> pollutionEMA = new HashMap<>();

    /**
     * Exponential moving average smoothing factor (0.0-1.0).
     * Higher values respond faster to changes.
     */
    private static final float ALPHA = 0.15f;

    /**
     * Maximum difficulty change per tick to prevent sudden spikes.
     */
    private static final float MAX_DELTA_PER_TICK = 0.005f;

    /**
     * Scan interval in ticks (20 ticks = 1 second).
     */
    private static final int SCAN_INTERVAL = 20;

    /**
     * Updates the industrial difficulty for a player.
     * <p>
     * This method should be called every tick for each player. It performs
     * scanning and calculation only every {@value #SCAN_INTERVAL} ticks for performance.
     *
     * @param player the player to update
     */
    public static void tick(Player player) {
        if (player.level().getGameTime() % SCAN_INTERVAL != 0) return;

        UUID uuid = player.getUUID();

        // 1. Machine Scan - use configured max voltage tier
        MachineScanner.ScanResult machineResult = MachineScanner.scanNearbyMachines(player, 32);
        float medianTier = DifficultySmoother.weightedMedian(machineResult.tiers(), machineResult.weights());

        // Normalize using configured max voltage tier
        int effectiveMaxTier = TriAxisConfig.getEffectiveMaxTier();
        float techScore = Math.min(1.0f, medianTier / effectiveMaxTier);

        // 2. Pollution Scan
        double currentPollution = HazardScanner.getPollutionLevel(player);
        float prevEMA = pollutionEMA.getOrDefault(uuid, 0f);
        float nextEMA = ALPHA * (float)currentPollution + (1 - ALPHA) * prevEMA;
        pollutionEMA.put(uuid, nextEMA);

        // Use configured pollution denominator
        float pollutionScore = (float) (1.0 - Math.exp(-nextEMA / TriAxisConfig.pollutionDenominator));

        // 3. Target Difficulty - use configured weights
        float industrialBonus = (float)(TriAxisConfig.techWeight * techScore) +
                               (float)(TriAxisConfig.pollutionWeight * pollutionScore);

        // 4. Smoothing
        float prevDiff = playerIndustrialDifficulty.getOrDefault(uuid, 0f);
        DifficultySmoother smoother = new DifficultySmoother(ALPHA, MAX_DELTA_PER_TICK * SCAN_INTERVAL);
        float finalDiff = smoother.smooth(prevDiff, industrialBonus);

        playerIndustrialDifficulty.put(uuid, finalDiff);

        // 5. Debug Logging - output every 5 seconds (100 ticks)
        if (IndustrialLogger.isDebugEnabled() && player.level().getGameTime() % 100 == 0) {
            IndustrialLogger.debugDifficulty(String.format(
                    "Player: %s | TechScore: %.2f | PollutionScore: %.2f | Difficulty: %.2f",
                    player.getName().getString(), techScore, pollutionScore, finalDiff));
        }
    }

    /**
     * Gets the current industrial difficulty bonus for a player.
     *
     * @param player the player to query
     * @return the player's industrial difficulty bonus (0.0 if not tracked)
     */
    public static float getDifficultyFor(Player player) {
        return playerIndustrialDifficulty.getOrDefault(player.getUUID(), 0f);
    }
}
