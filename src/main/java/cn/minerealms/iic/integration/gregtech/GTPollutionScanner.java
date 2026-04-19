package cn.minerealms.iic.integration.gregtech;

import cn.minerealms.iic.integration.gregtech.GTIntegration;
import cn.minerealms.iic.industrial.IndustrialLogger;
import cn.minerealms.iic.industrial.TriAxisConfig;

import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.ChunkPos;

import java.lang.reflect.Method;
import java.util.Map;

/**
 * GregTech Pollution Scanner - Detects and analyzes GT pollution sources.
 * <p>
 * This scanner integrates with GregTech Modern's EnvironmentalHazardSavedData
 * to detect pollution zones (primarily CO from Muffler machines) and calculate
 * multipliers based on source density.
 * <p>
 * When multiple pollution sources exist in a chunk, the multiplier increases:
 * <ul>
 *   <li>1-10 sources: 1.0x (no change)</li>
 *   <li>11-20 sources: 1.5x</li>
 *   <li>21-30 sources: 2.0x</li>
 *   <li>31-50 sources: 3.0x</li>
 *   <li>51+ sources: 4.0x</li>
 * </ul>
 *
 * @see PollutionManager
 * @see GTIntegration
 */
public class GTPollutionScanner {

    private static boolean initialized = false;
    private static boolean gtHazardAvailable = false;

    // Cached reflection classes and methods
    private static Class<?> hazardSavedDataClass;
    private static Class<?> hazardZoneClass;
    private static Method getOrCreateMethod;
    private static Method getHazardZonesMethod;
    private static Method getZoneByPosMethod;
    private static Method strengthMethod;
    private static Method canSpreadMethod;

    /**
     * Data class for GT hazard information.
     *
     * @param strength     Pollution strength in the chunk
     * @param sourceCount  Number of pollution sources detected
     * @param canSpread    Whether pollution can spread to adjacent chunks
     */
    public record GTHazardInfo(float strength, int sourceCount, boolean canSpread) {
        public static final GTHazardInfo NONE = new GTHazardInfo(0, 0, false);
    }

    /**
     * Initialize GT pollution system integration.
     */
    private static void initialize() {
        if (initialized) return;
        initialized = true;

        IndustrialLogger.info("[IIC-PollutionSystem] Initializing GT Pollution Scanner...");

        if (!GTIntegration.isGTLoaded()) {
            IndustrialLogger.info("[IIC-PollutionSystem] GT not loaded, pollution scanner disabled");
            return;
        }

        try {
            IndustrialLogger.debug("[IIC-PollutionSystem] Loading GT pollution classes via reflection...");

            // Load EnvironmentalHazardSavedData class
            hazardSavedDataClass = Class.forName("com.gregtechceu.gtceu.common.capability.EnvironmentalHazardSavedData");
            IndustrialLogger.debug("[IIC-PollutionSystem] ✓ Loaded EnvironmentalHazardSavedData");

            hazardZoneClass = Class.forName("com.gregtechceu.gtceu.common.capability.EnvironmentalHazardSavedData$HazardZone");
            IndustrialLogger.debug("[IIC-PollutionSystem] ✓ Loaded HazardZone");

            // Cache methods
            getOrCreateMethod = hazardSavedDataClass.getMethod("getOrCreate", ServerLevel.class);
            IndustrialLogger.debug("[IIC-PollutionSystem] ✓ Cached getOrCreate method");

            getHazardZonesMethod = hazardSavedDataClass.getMethod("getHazardZones");
            IndustrialLogger.debug("[IIC-PollutionSystem] ✓ Cached getHazardZones method");

            getZoneByPosMethod = hazardSavedDataClass.getMethod("getZoneByPos", ChunkPos.class);
            IndustrialLogger.debug("[IIC-PollutionSystem] ✓ Cached getZoneByPos method");

            strengthMethod = hazardZoneClass.getMethod("strength");
            IndustrialLogger.debug("[IIC-PollutionSystem] ✓ Cached strength method");

            canSpreadMethod = hazardZoneClass.getMethod("canSpread");
            IndustrialLogger.debug("[IIC-PollutionSystem] ✓ Cached canSpread method");

            gtHazardAvailable = true;
            IndustrialLogger.info("[IIC-PollutionSystem] ✓ GT Pollution Scanner initialized successfully");
        } catch (ClassNotFoundException e) {
            IndustrialLogger.warn("[IIC-PollutionSystem] GT EnvironmentalHazardSavedData not found - pollution integration disabled");
            IndustrialLogger.error("[IIC-PollutionSystem] ClassNotFoundException: " + e.getMessage(), e);
        } catch (NoSuchMethodException e) {
            IndustrialLogger.error("[IIC-PollutionSystem] GT API method not found: " + e.getMessage(), e);
        } catch (Exception e) {
            IndustrialLogger.error("[IIC-PollutionSystem] Unexpected error during initialization: " + e.getMessage(), e);
        }
    }

    /**
     * Scan GT pollution hazards in a specific chunk.
     *
     * @param level    The server level
     * @param chunkPos The chunk position to scan
     * @return GTHazardInfo containing pollution data, or NONE if no pollution
     */
    public static GTHazardInfo scanChunkHazards(ServerLevel level, ChunkPos chunkPos) {
        if (!initialized) initialize();
        if (!gtHazardAvailable) {
            IndustrialLogger.debug("[IIC-PollutionSystem] GT hazard system not available, skipping scan");
            return GTHazardInfo.NONE;
        }

        try {
            IndustrialLogger.debug("[IIC-PollutionSystem] Scanning GT hazards at chunk " + chunkPos);

            // Get EnvironmentalHazardSavedData instance
            Object hazardData = getOrCreateMethod.invoke(null, level);
            if (hazardData == null) {
                IndustrialLogger.debug("[IIC-PollutionSystem] HazardData is null for chunk " + chunkPos);
                return GTHazardInfo.NONE;
            }

            // Get hazard zone for this chunk
            Object hazardZone = getZoneByPosMethod.invoke(hazardData, chunkPos);
            if (hazardZone == null) {
                IndustrialLogger.debug("[IIC-PollutionSystem] No hazard zone at chunk " + chunkPos);
                return GTHazardInfo.NONE;
            }

            // Extract zone data
            float strength = (float) strengthMethod.invoke(hazardZone);
            boolean canSpread = (boolean) canSpreadMethod.invoke(hazardZone);

            IndustrialLogger.debug(String.format(
                    "[IIC-PollutionSystem] Found hazard zone at %s: strength=%.2f, canSpread=%s",
                    chunkPos, strength, canSpread));

            // Count sources in nearby area (3x3 chunks)
            int sourceCount = countNearbyPollutionSources(hazardData, chunkPos);

            IndustrialLogger.debug(String.format(
                    "[IIC-PollutionSystem] Counted %d pollution sources near %s",
                    sourceCount, chunkPos));

            GTHazardInfo result = new GTHazardInfo(strength, sourceCount, canSpread);

            if (sourceCount > 0) {
                IndustrialLogger.debugPollution(String.format(
                        "[IIC-PollutionSystem] GT Hazard detected: chunk=%s, strength=%.2f, sources=%d, canSpread=%s",
                        chunkPos, strength, sourceCount, canSpread));
            }

            return result;

        } catch (IllegalAccessException e) {
            IndustrialLogger.error("[IIC-PollutionSystem] IllegalAccessException scanning GT hazards: " + e.getMessage(), e);
            return GTHazardInfo.NONE;
        } catch (java.lang.reflect.InvocationTargetException e) {
            IndustrialLogger.error("[IIC-PollutionSystem] InvocationTargetException scanning GT hazards: " + e.getMessage(), e);
            if (e.getCause() != null) {
                IndustrialLogger.error("[IIC-PollutionSystem] Caused by: " + e.getCause().getMessage(), e.getCause());
            }
            return GTHazardInfo.NONE;
        } catch (Exception e) {
            IndustrialLogger.error("[IIC-PollutionSystem] Unexpected error scanning GT hazards: " + e.getMessage(), e);
            return GTHazardInfo.NONE;
        }
    }

    /**
     * Count pollution sources in a 3x3 chunk area around the center.
     *
     * @param hazardData The EnvironmentalHazardSavedData instance
     * @param center     Center chunk position
     * @return Number of pollution sources detected
     */
    @SuppressWarnings("unchecked")
    private static int countNearbyPollutionSources(Object hazardData, ChunkPos center) {
        try {
            IndustrialLogger.debug("[IIC-PollutionSystem] Counting pollution sources around " + center);

            Map<ChunkPos, Object> hazardZones = (Map<ChunkPos, Object>) getHazardZonesMethod.invoke(hazardData);
            if (hazardZones == null || hazardZones.isEmpty()) {
                IndustrialLogger.debug("[IIC-PollutionSystem] No hazard zones found in world");
                return 0;
            }

            IndustrialLogger.debug(String.format(
                    "[IIC-PollutionSystem] Total hazard zones in world: %d",
                    hazardZones.size()));

            int count = 0;
            int zonesChecked = 0;

            // Check 3x3 chunk area
            for (int dx = -1; dx <= 1; dx++) {
                for (int dz = -1; dz <= 1; dz++) {
                    ChunkPos checkPos = new ChunkPos(center.x + dx, center.z + dz);
                    if (hazardZones.containsKey(checkPos)) {
                        zonesChecked++;
                        Object zone = hazardZones.get(checkPos);
                        float strength = (float) strengthMethod.invoke(zone);
                        // Each 100 strength ≈ 1 source (Muffler emits ~2.5/tick)
                        int sources = Math.max(1, (int) (strength / 100.0));
                        count += sources;

                        IndustrialLogger.debug(String.format(
                                "[IIC-PollutionSystem] Chunk %s: strength=%.2f, estimated sources=%d",
                                checkPos, strength, sources));
                    }
                }
            }

            IndustrialLogger.debug(String.format(
                    "[IIC-PollutionSystem] Checked %d zones in 3x3 area, total sources: %d",
                    zonesChecked, count));

            return count;

        } catch (IllegalAccessException e) {
            IndustrialLogger.error("[IIC-PollutionSystem] IllegalAccessException counting sources: " + e.getMessage(), e);
            return 0;
        } catch (java.lang.reflect.InvocationTargetException e) {
            IndustrialLogger.error("[IIC-PollutionSystem] InvocationTargetException counting sources: " + e.getMessage(), e);
            return 0;
        } catch (ClassCastException e) {
            IndustrialLogger.error("[IIC-PollutionSystem] ClassCastException counting sources (API mismatch?): " + e.getMessage(), e);
            return 0;
        } catch (Exception e) {
            IndustrialLogger.error("[IIC-PollutionSystem] Unexpected error counting sources: " + e.getMessage(), e);
            return 0;
        }
    }

    /**
     * Calculate pollution multiplier based on source count.
     * <p>
     * Multiplier tiers:
     * <ul>
     *   <li>1-10 sources: 1.0x</li>
     *   <li>11-20 sources: 1.5x</li>
     *   <li>21-30 sources: 2.0x</li>
     *   <li>31-50 sources: 3.0x</li>
     *   <li>51+ sources: 4.0x</li>
     * </ul>
     *
     * @param sourceCount Number of pollution sources
     * @return Multiplier value (1.0 to 4.0)
     */
    public static double calculateSourceMultiplier(int sourceCount) {
        double multiplier;

        if (sourceCount <= TriAxisConfig.gtSourceThreshold) {
            multiplier = 1.0;
        } else if (sourceCount <= 20) {
            multiplier = 1.0 + (sourceCount - TriAxisConfig.gtSourceThreshold) * TriAxisConfig.gtSourceMultiplierBase / 10.0;
        } else if (sourceCount <= 30) {
            multiplier = 2.0;
        } else if (sourceCount <= 50) {
            multiplier = 3.0;
        } else {
            multiplier = 4.0;
        }

        IndustrialLogger.debug(String.format(
                "[IIC-PollutionSystem] Source multiplier calculation: sources=%d, threshold=%d, base=%.2f, result=%.2fx",
                sourceCount, TriAxisConfig.gtSourceThreshold, TriAxisConfig.gtSourceMultiplierBase, multiplier));

        return multiplier;
    }

    /**
     * Check if GT pollution system is available.
     *
     * @return true if GT pollution can be scanned
     */
    public static boolean isAvailable() {
        if (!initialized) initialize();
        return gtHazardAvailable;
    }
}
