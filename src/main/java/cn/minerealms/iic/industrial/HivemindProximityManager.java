package cn.minerealms.iic.industrial;

import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.ChunkPos;

import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Hivemind Proximity Manager - Tracks Spore Hiveminds and calculates difficulty acceleration.
 * <p>
 * This manager creates synergy between industrial pollution and biological threats.
 * When pollution spreads near Spore Hiveminds (Proto entities), the difficulty
 * accelerates faster, making mobs stronger and increasing their HP.
 * <p>
 * The acceleration is based on three factors:
 * <ul>
 *   <li>Distance: Closer Hiveminds have stronger effect (inverse distance)</li>
 *   <li>Biomass: Larger Hiveminds (more biomass) have stronger effect (log scale)</li>
 *   <li>Pollution: Higher pollution amplifies the effect</li>
 * </ul>
 * <p>
 * Performance: Hivemind positions are cached and updated every 5 seconds (100 ticks).
 *
 * @see SporeIntegration
 * @see IndustrialDifficultyGetter
 */
public class HivemindProximityManager {

    /** Cache of Hivemind positions mapped to their biomass */
    private static final Map<BlockPos, Integer> hivemindPositions = new ConcurrentHashMap<>();

    /** Last update time in game ticks */
    private static long lastUpdate = 0;

    /** Update interval in ticks (100 ticks = 5 seconds) */
    private static final int UPDATE_INTERVAL = 100;

    /**
     * Data class for Hivemind proximity information.
     *
     * @param isNear    Whether a Hivemind is within proximity radius
     * @param distance  Distance to closest Hivemind in chunks
     * @param biomass   Biomass of the closest Hivemind
     * @param pollution Pollution level at the checked location
     */
    public record HivemindProximityInfo(boolean isNear, double distance, int biomass, double pollution) {
        public static final HivemindProximityInfo NONE = new HivemindProximityInfo(false, 0, 0, 0);

        /**
         * Calculate difficulty acceleration multiplier.
         * <p>
         * Formula: 1.0 + (distanceFactor × biomassFactor × pollutionFactor × config)
         * <p>
         * Result range: 1.0x (no effect) to 5.0x (maximum acceleration)
         *
         * @return Acceleration multiplier
         */
        public double calculateAccelerationMultiplier() {
            if (!isNear || !TriAxisConfig.enableHivemindAcceleration) {
                IndustrialLogger.debug("[IIC-PollutionSystem] No acceleration: isNear=" + isNear + ", enabled=" + TriAxisConfig.enableHivemindAcceleration);
                return 1.0;
            }

            try {
                // Closer = stronger effect (inverse distance, clamped to avoid division by zero)
                double distanceFactor = 1.0 / Math.max(1.0, distance);

                // More biomass = stronger effect (log scale to prevent extreme values)
                double biomassFactor = Math.log10(Math.max(10, biomass)) / 3.0;

                // More pollution = stronger effect (capped at 2.0x)
                double pollutionFactor = Math.min(2.0, pollution / 100.0);

                // Combined multiplier with configurable max
                double multiplier = 1.0 + (distanceFactor * biomassFactor * pollutionFactor * TriAxisConfig.hivemindAccelerationFactor);

                // Clamp to reasonable range
                double finalMultiplier = Math.min(5.0, Math.max(1.0, multiplier));

                IndustrialLogger.debugPollution(String.format(
                        "[IIC-PollutionSystem] Acceleration calculation: distance=%.2f->%.4f, biomass=%d->%.4f, pollution=%.2f->%.4f, config=%.2f, raw=%.4f, final=%.4fx",
                        distance, distanceFactor, biomass, biomassFactor, pollution, pollutionFactor,
                        TriAxisConfig.hivemindAccelerationFactor, multiplier, finalMultiplier));

                return finalMultiplier;

            } catch (Exception e) {
                IndustrialLogger.error("[IIC-PollutionSystem] Error calculating acceleration multiplier: " + e.getMessage(), e);
                return 1.0;
            }
        }
    }

    /**
     * Update Hivemind position cache from Spore mod.
     * <p>
     * This method queries all active Proto entities and caches their
     * node positions and biomass values. Called automatically every
     * 5 seconds to balance accuracy and performance.
     *
     * @param level The server level
     */
    public static void updateHivemindCache(ServerLevel level) {
        if (!SporeIntegration.isSporeLoaded()) {
            IndustrialLogger.debug("[IIC-PollutionSystem] Spore not loaded, skipping Hivemind cache update");
            return;
        }

        long currentTime = level.getGameTime();
        if (currentTime - lastUpdate < UPDATE_INTERVAL) {
            return;
        }

        lastUpdate = currentTime;

        try {
            IndustrialLogger.debug("[IIC-PollutionSystem] Updating Hivemind cache...");

            List<Object> hiveminds = SporeIntegration.getHiveminds();
            if (hiveminds == null || hiveminds.isEmpty()) {
                if (!hivemindPositions.isEmpty()) {
                    IndustrialLogger.debugPollution("[IIC-PollutionSystem] No Hiveminds found, clearing cache");
                }
                hivemindPositions.clear();
                return;
            }

            IndustrialLogger.debug(String.format(
                    "[IIC-PollutionSystem] Found %d Hiveminds to cache",
                    hiveminds.size()));

            // Clear old data
            hivemindPositions.clear();

            int validHiveminds = 0;
            int invalidHiveminds = 0;

            // Populate with current Hivemind data
            for (Object proto : hiveminds) {
                try {
                    BlockPos nodePos = SporeIntegration.getNodePosition(proto);
                    int biomass = SporeIntegration.getBiomass(proto);

                    if (nodePos != null && !nodePos.equals(BlockPos.ZERO)) {
                        hivemindPositions.put(nodePos, biomass);
                        validHiveminds++;

                        IndustrialLogger.debug(String.format(
                                "[IIC-PollutionSystem] Cached Hivemind: pos=%s, biomass=%d",
                                nodePos, biomass));
                    } else {
                        invalidHiveminds++;
                        IndustrialLogger.debug("[IIC-PollutionSystem] Skipped Hivemind with invalid position");
                    }
                } catch (Exception e) {
                    invalidHiveminds++;
                    IndustrialLogger.error("[IIC-PollutionSystem] Error caching individual Hivemind: " + e.getMessage(), e);
                }
            }

            IndustrialLogger.debugPollution(String.format(
                    "[IIC-PollutionSystem] Hivemind cache updated: %d valid, %d invalid, %d total cached",
                    validHiveminds, invalidHiveminds, hivemindPositions.size()));

        } catch (Exception e) {
            IndustrialLogger.error("[IIC-PollutionSystem] Error updating Hivemind cache: " + e.getMessage(), e);
        }
    }

    /**
     * Check if a pollution chunk is near any Hivemind.
     * <p>
     * Scans all cached Hivemind positions and finds the closest one.
     * If within the configured proximity radius, returns detailed info
     * for calculating difficulty acceleration.
     *
     * @param pollutionChunk The chunk with pollution
     * @param pollutionLevel The pollution level in that chunk
     * @return HivemindProximityInfo with distance and biomass data
     */
    public static HivemindProximityInfo checkProximity(ChunkPos pollutionChunk, double pollutionLevel) {
        if (!TriAxisConfig.enableHivemindAcceleration) {
            IndustrialLogger.debug("[IIC-PollutionSystem] Hivemind acceleration disabled in config");
            return HivemindProximityInfo.NONE;
        }

        if (hivemindPositions.isEmpty()) {
            IndustrialLogger.debug("[IIC-PollutionSystem] No Hiveminds in cache, skipping proximity check");
            return HivemindProximityInfo.NONE;
        }

        IndustrialLogger.debug(String.format(
                "[IIC-PollutionSystem] Checking proximity for chunk %s (pollution=%.2f) against %d Hiveminds",
                pollutionChunk, pollutionLevel, hivemindPositions.size()));

        double closestDistance = Double.MAX_VALUE;
        int strongestBiomass = 0;
        BlockPos closestHivemind = null;

        try {
            // Find closest Hivemind
            for (Map.Entry<BlockPos, Integer> entry : hivemindPositions.entrySet()) {
                ChunkPos hivemindChunk = new ChunkPos(entry.getKey());

                // Calculate chunk distance (Euclidean)
                double distance = Math.sqrt(
                        Math.pow(pollutionChunk.x - hivemindChunk.x, 2) +
                                Math.pow(pollutionChunk.z - hivemindChunk.z, 2)
                );

                if (distance < closestDistance) {
                    closestDistance = distance;
                    strongestBiomass = entry.getValue();
                    closestHivemind = entry.getKey();

                    IndustrialLogger.debug(String.format(
                            "[IIC-PollutionSystem] New closest Hivemind: pos=%s, distance=%.2f chunks, biomass=%d",
                            closestHivemind, distance, strongestBiomass));
                }
            }

            // Check if within proximity radius
            if (closestDistance <= TriAxisConfig.hivemindProximityRadius) {
                HivemindProximityInfo result = new HivemindProximityInfo(true, closestDistance, strongestBiomass, pollutionLevel);

                IndustrialLogger.debugPollution(String.format(
                        "[IIC-PollutionSystem] ✓ Hivemind proximity detected! chunk=%s, hivemind=%s, distance=%.2f chunks (radius=%.2f), biomass=%d, pollution=%.2f",
                        pollutionChunk, closestHivemind, closestDistance, TriAxisConfig.hivemindProximityRadius, strongestBiomass, pollutionLevel));

                return result;
            } else {
                IndustrialLogger.debug(String.format(
                        "[IIC-PollutionSystem] Closest Hivemind at %.2f chunks (outside radius %.2f)",
                        closestDistance, TriAxisConfig.hivemindProximityRadius));
            }

        } catch (Exception e) {
            IndustrialLogger.error("[IIC-PollutionSystem] Error checking Hivemind proximity: " + e.getMessage(), e);
        }

        return HivemindProximityInfo.NONE;
    }

    /**
     * Get the number of cached Hiveminds.
     *
     * @return Number of active Hiveminds in cache
     */
    public static int getCachedHivemindCount() {
        return hivemindPositions.size();
    }

    /**
     * Clear the Hivemind cache (for testing or reset).
     */
    public static void clearCache() {
        hivemindPositions.clear();
        lastUpdate = 0;
    }
}
