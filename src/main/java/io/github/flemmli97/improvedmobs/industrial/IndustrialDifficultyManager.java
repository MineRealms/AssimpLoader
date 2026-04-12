package io.github.flemmli97.improvedmobs.industrial;

import java.util.Map;
import java.util.HashMap;
import java.util.UUID;
import net.minecraft.world.entity.player.Player;

public class IndustrialDifficultyManager {

    private static final Map<UUID, Float> playerIndustrialDifficulty = new HashMap<>();
    private static final Map<UUID, Float> pollutionEMA = new HashMap<>();
    
    private static final float ALPHA = 0.15f;
    private static final float MAX_DELTA_PER_TICK = 0.005f;
    private static final int SCAN_INTERVAL = 20;
    
    private static final float TECH_WEIGHT = 50.0f; // Scale to IM difficulty range
    private static final float POLLUTION_WEIGHT = 30.0f;
    private static final int MAX_TIER = 14; // UHV+

    public static void tick(Player player) {
        if (player.level().getGameTime() % SCAN_INTERVAL != 0) return;

        UUID uuid = player.getUUID();
        
        // 1. Machine Scan
        MachineScanner.ScanResult machineResult = MachineScanner.scanNearbyMachines(player, 32);
        float medianTier = DifficultySmoother.weightedMedian(machineResult.tiers(), machineResult.weights());
        float techScore = Math.min(1.0f, medianTier / MAX_TIER);
        
        // 2. Pollution Scan
        double currentPollution = HazardScanner.getPollutionLevel(player);
        float prevEMA = pollutionEMA.getOrDefault(uuid, 0f);
        float nextEMA = ALPHA * (float)currentPollution + (1 - ALPHA) * prevEMA;
        pollutionEMA.put(uuid, nextEMA);
        
        // Exponential pollution score: 1 - exp(-k * EMA)
        float pollutionScore = (float) (1.0 - Math.exp(-0.00001 * nextEMA)); 

        // 3. Target Difficulty
        float industrialBonus = (TECH_WEIGHT * techScore) + (POLLUTION_WEIGHT * pollutionScore);
        
        // 4. Smoothing
        float prevDiff = playerIndustrialDifficulty.getOrDefault(uuid, 0f);
        DifficultySmoother smoother = new DifficultySmoother(ALPHA, MAX_DELTA_PER_TICK * SCAN_INTERVAL);
        float finalDiff = smoother.smooth(prevDiff, industrialBonus);
        
        playerIndustrialDifficulty.put(uuid, finalDiff);
    }

    public static float getDifficultyFor(Player player) {
        return playerIndustrialDifficulty.getOrDefault(player.getUUID(), 0f);
    }
}
