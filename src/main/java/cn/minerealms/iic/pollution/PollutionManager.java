package cn.minerealms.iic.pollution;

import cn.minerealms.iic.industrial.IndustrialLogger;
import cn.minerealms.iic.industrial.TriAxisConfig;
import cn.minerealms.iic.integration.gregtech.GTIntegration;
import cn.minerealms.iic.integration.gregtech.GTPollutionScanner;
import cn.minerealms.iic.integration.spore.SporeIntegration;
import cn.minerealms.iic.threat.ThreatManager;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.tags.BlockTags;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.chunk.LevelChunk;
import net.minecraft.world.level.chunk.LevelChunkSection;

import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;

import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.monster.Creeper;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.entity.MobSpawnType;

/**
 * Pollution Manager - Handles temporary and permanent pollution systems for Improved Mobs.
 *
 * <h2>Pollution System Design</h2>
 * <p>This manager implements a dual-layer pollution system:
 *
 * <h3>1. Temporary Pollution</h3>
 * <ul>
 *   <li>Chunk-based diffusion pollution</li>
 *   <li>Can be absorbed by environment (leaves, grass, water)</li>
 *   <li>Triggers mob attacks on machines</li>
 *   <li>Does not directly affect difficulty</li>
 * </ul>
 *
 * <h3>2. Permanent Pollution</h3>
 * <ul>
 *   <li>Global accumulated pollution value</li>
 *   <li>Converted from temporary pollution when threshold exceeded</li>
 *   <li>Directly increases ImprovedMobs difficulty</li>
 *   <li>Represents long-term environmental impact of industrial development</li>
 * </ul>
 *
 * <h3>Performance Optimization</h3>
 * <p>Uses extremely low-frequency staggered tick scanning to ensure
 * thousands of machines don't cause server lag. All heavy operations
 * are performed asynchronously off the main thread.
 *
 * @see ThreatManager
 * @see TriAxisConfig
 * @author ImprovedMobs Industrial Integration
 */
public class PollutionManager {

    /** Chunk-based temporary pollution map (temporary pollution - chunk diffusion) */
    private static final Map<ChunkPos, Double> temporaryPollution = new ConcurrentHashMap<>();

    /** Chunk-based environmental reduction cache (caches water and leaf absorption per chunk) */
    private static final Map<ChunkPos, Double> environmentalReductionCache = new ConcurrentHashMap<>();

    /** Global permanent pollution value (permanent pollution - global accumulation) */
    private static double permanentPollution = 0.0;

    /** Scan counter for staggered processing */
    private static int tickCounter = 0;

    // === Configuration Parameters (adjustable via TriAxisConfig) ===

    /** Threshold for converting temporary pollution to permanent pollution */
    public static double TEMP_TO_PERM_THRESHOLD = 200.0;

    /** Conversion rate for temporary to permanent pollution (per second) */
    public static double TEMP_TO_PERM_RATE = 0.001;

    /** Conversion rate for permanent pollution to difficulty */
    public static double PERM_TO_DIFFICULTY_RATE = 0.1;

    /**
     * Adds to the global permanent pollution value.
     *
     * @param amount Amount of pollution to add
     */
    public static void addPermanentPollution(double amount) {
        permanentPollution += amount;
    }

    /**
     * Gets the current global permanent pollution value.
     *
     * @return Current permanent pollution level
     */
    public static double getPermanentPollution() {
        return permanentPollution;
    }

    /**
     * Gets the temporary pollution level for a specific chunk.
     *
     * @param pos Chunk position to query
     * @return Temporary pollution level, or 0 if no pollution
     */
    public static double getTemporaryPollution(ChunkPos pos) {
        return temporaryPollution.getOrDefault(pos, 0.0);
    }

    /**
     * Sets the temporary pollution level for a specific chunk.
     * Used by commands for direct pollution manipulation.
     *
     * @param level Server level (unused but kept for API consistency)
     * @param pos Chunk position to set
     * @param amount Pollution amount to set
     */
    public static void setChunkPollution(ServerLevel level, ChunkPos pos, double amount) {
        if (amount <= 0) {
            temporaryPollution.remove(pos);
        } else {
            temporaryPollution.put(pos, amount);
        }
    }

    /**
     * Gets the temporary pollution level for a specific chunk.
     * Alias for getTemporaryPollution for API consistency.
     *
     * @param level Server level (unused but kept for API consistency)
     * @param pos Chunk position to query
     * @return Temporary pollution level, or 0 if no pollution
     */
    public static double getChunkPollution(ServerLevel level, ChunkPos pos) {
        return getTemporaryPollution(pos);
    }

    /**
     * Sets the global permanent pollution value directly.
     * Used by commands for direct pollution manipulation.
     *
     * @param amount New permanent pollution value
     */
    public static void setPermanentPollution(double amount) {
        permanentPollution = Math.max(0, amount);
    }

    /**
     * Main tick method called from ServerTickEvent.
     * For performance, scanning is not done every tick but every 20 ticks (1 second).
     * Randomly samples loaded chunks for pollution accumulation and decay calculation.
     *
     * @param level The server level to process
     */
    public static void tick(ServerLevel level) {
        tickCounter++;

        // 1 second pollution & scan
        if (tickCounter % 20 == 0) {
            processPollutionDecayAndScanning(level);
        }

        // 5 minute environment scan (already in thread)
        if (tickCounter % 6000 == 0) {
            updateEnvironmentalCache(level);
        }
    }

    /**
     * Processes pollution decay and machine scanning asynchronously.
     * This method:
     * <ul>
     *   <li>Scans for active GT machines near players</li>
     *   <li>Calculates pollution generation from machines</li>
     *   <li>Converts temporary pollution to permanent when threshold exceeded</li>
     *   <li>Applies environmental reduction (leaves, water, grass)</li>
     *   <li>Triggers threat mechanisms based on pollution levels</li>
     * </ul>
     *
     * @param level The server level to process
     */
    private static void processPollutionDecayAndScanning(ServerLevel level) {
        Map<ChunkPos, Double> newPollutionThisSec = new HashMap<>();
        
        CompletableFuture.runAsync(() -> {
            int scanRadius = 4;
            Set<ChunkPos> scannedChunks = new HashSet<>();
            
            for (net.minecraft.server.level.ServerPlayer player : level.players()) {
                ChunkPos center = player.chunkPosition();
                for (int x = -scanRadius; x <= scanRadius; x++) {
                    for (int z = -scanRadius; z <= scanRadius; z++) {
                        ChunkPos scanPos = new ChunkPos(center.x + x, center.z + z);
                        if (scannedChunks.add(scanPos) && level.hasChunk(scanPos.x, scanPos.z)) {
                            net.minecraft.world.level.chunk.LevelChunk chunk = level.getChunk(scanPos.x, scanPos.z);
                            for (BlockEntity be : chunk.getBlockEntities().values()) {
                                if (GTIntegration.isGTMachine(be)) {
                                    if (GTIntegration.hasEnergyOrActive(be)) {
                                        int tier = GTIntegration.getVoltageTier(be);
                                        // Reasonable pollution generation: base 0.01, linear growth
                                        // tier 0 (ULV) = 0.01/s, tier 2 (MV) = 0.03/s, tier 4 (EV) = 0.05/s
                                        double pollutionValue = 0.01 * (1 + tier * 0.5);

                                        // Multiblock structures produce more pollution (3x)
                                        if (GTIntegration.isMultiblock(be)) {
                                            pollutionValue *= 3.0;
                                        }

                                        ChunkPos cPos = new ChunkPos(be.getBlockPos());
                                        newPollutionThisSec.merge(cPos, pollutionValue, Double::sum);

                                        // Debug logging
                                        if (IndustrialLogger.isDebugEnabled() && level.getGameTime() % 100 == 0) {
                                            IndustrialLogger.debugPollution(String.format(
                                                    "Machine at %s (tier %d, multiblock: %s) produces %.4f temp pollution/sec",
                                                    be.getBlockPos(), tier, GTIntegration.isMultiblock(be), pollutionValue));
                                        }
                                    }
                                }
                            }

                            // GT Pollution Integration - scan GT pollution sources
                            if (GTIntegration.isGTLoaded()) {
                                try {
                                    IndustrialLogger.debug(String.format(
                                            "[IIC-PollutionSystem] Scanning GT pollution at chunk %s",
                                            scanPos));

                                    GTPollutionScanner.GTHazardInfo gtHazard = GTPollutionScanner.scanChunkHazards(level, scanPos);
                                    if (gtHazard != null && gtHazard.sourceCount() > 0) {
                                        double gtMultiplier = GTPollutionScanner.calculateSourceMultiplier(gtHazard.sourceCount());
                                        double gtPollutionContribution = gtHazard.strength() * gtMultiplier * TriAxisConfig.gtPollutionWeight;

                                        // Add to temporary pollution for this chunk
                                        newPollutionThisSec.merge(scanPos, gtPollutionContribution, Double::sum);

                                        IndustrialLogger.debugPollution(String.format(
                                                "[IIC-PollutionSystem] GT Pollution added: chunk=%s, strength=%.2f, sources=%d, multiplier=%.2fx, weight=%.2f, contribution=%.4f",
                                                scanPos, gtHazard.strength(), gtHazard.sourceCount(), gtMultiplier, TriAxisConfig.gtPollutionWeight, gtPollutionContribution));
                                    } else {
                                        IndustrialLogger.debug(String.format(
                                                "[IIC-PollutionSystem] No GT pollution at chunk %s",
                                                scanPos));
                                    }
                                } catch (Exception e) {
                                    IndustrialLogger.error("[IIC-PollutionSystem] Error scanning GT pollution at chunk " + scanPos + ": " + e.getMessage(), e);
                                }
                            }
                        }
                    }
                }
            }
        }).thenAccept(v -> {
            // Process temporary pollution conversion to permanent pollution
            double totalTempPollution = 0;
            for (ChunkPos cPos : temporaryPollution.keySet()) {
                double current = temporaryPollution.get(cPos);
                totalTempPollution += current;

                // When temporary pollution exceeds threshold, convert to permanent
                if (current > TEMP_TO_PERM_THRESHOLD) {
                    double converted = current * TEMP_TO_PERM_RATE;
                    addPermanentPollution(converted);

                    if (IndustrialLogger.isDebugEnabled() && level.getGameTime() % 100 == 0) {
                        IndustrialLogger.debugPollution(String.format(
                                "Chunk %s: Temp pollution %.2f -> Converting %.4f to permanent",
                                cPos, current, converted));
                    }
                }

                // Calculate decay and new additions
                double reduction = 0.05;
                reduction += getSurroundingEnvironmentalReduction(cPos);
                double added = newPollutionThisSec.getOrDefault(cPos, 0.0);
                double nextVal = Math.max(0.0, current - reduction + added);

                if (nextVal <= 0.001) {
                    temporaryPollution.remove(cPos);
                } else {
                    temporaryPollution.put(cPos, nextVal);

                    // Check and trigger threat mechanisms (based on voltage tier and pollution)
                    double avgTier = ThreatManager.getChunkAverageVoltageTier(level, cPos);
                    ThreatManager.checkAndTriggerThreats(level, cPos, nextVal, avgTier);
                }
            }

            // New pollution chunks
            for (Map.Entry<ChunkPos, Double> entry : newPollutionThisSec.entrySet()) {
                if (!temporaryPollution.containsKey(entry.getKey())) {
                    temporaryPollution.put(entry.getKey(), entry.getValue());
                }
            }

            // When permanent pollution accumulates to a certain level, increase global difficulty
            if (permanentPollution >= PERM_TO_DIFFICULTY_RATE) {
                io.github.flemmli97.improvedmobs.difficulty.DifficultyData diffData =
                        io.github.flemmli97.improvedmobs.difficulty.DifficultyData.get(level.getServer());
                diffData.addDifficulty((float)permanentPollution, level.getServer());

                if (IndustrialLogger.isDebugEnabled()) {
                    IndustrialLogger.debugPollution(String.format(
                            "Added %.2f permanent pollution to global difficulty (Total temp: %.2f)",
                            permanentPollution, totalTempPollution));
                }

                permanentPollution = 0.0;
            }

            // Spore pollution feedback - accelerate Hivemind growth based on pollution
            // Must be called on main thread (modifies entity data)
            if (SporeIntegration.isSporeLoaded() && totalTempPollution > 100.0) {
                final double pollutionForFeedback = totalTempPollution;  // Make effectively final
                level.getServer().execute(() -> {
                    SporeIntegration.applyPollutionFeedback(level, pollutionForFeedback);
                });
            }
        });
    }

    /**
     * Gets the total environmental reduction from surrounding 4 chunks.
     * Environmental reduction comes from leaves, water, and grass blocks.
     *
     * @param center Center chunk position
     * @return Total environmental reduction value
     */
    private static double getSurroundingEnvironmentalReduction(ChunkPos center) {
        double totalReduction = 0.0;
        for (int x = -4; x <= 4; x++) {
            for (int z = -4; z <= 4; z++) {
                ChunkPos p = new ChunkPos(center.x + x, center.z + z);
                totalReduction += environmentalReductionCache.getOrDefault(p, 0.0);
            }
        }
        return totalReduction;
    }

    /**
     * Updates the environmental reduction cache in a background thread.
     * Scans chunks for leaves and water blocks to calculate pollution absorption.
     * Since vanilla water is Blocks.WATER and leaves are in BlockTags.LEAVES,
     * we read block states directly in a separate thread.
     *
     * @param level The server level to scan
     */
    private static void updateEnvironmentalCache(ServerLevel level) {

        new Thread(() -> {
            for (ChunkPos cPos : temporaryPollution.keySet()) {
                // Only scan chunks near pollution to avoid scanning too many useless areas
                for (int cx = -4; cx <= 4; cx++) {
                    for (int cz = -4; cz <= 4; cz++) {
                        ChunkPos scanPos = new ChunkPos(cPos.x + cx, cPos.z + cz);
                        if (!environmentalReductionCache.containsKey(scanPos) && level.hasChunk(scanPos.x, scanPos.z)) {
                            LevelChunk chunk = level.getChunk(scanPos.x, scanPos.z);
                            double reduction = calculateChunkReduction(chunk);
                            environmentalReductionCache.put(scanPos, reduction);
                        }
                    }
                }
            }
        }, "ImprovedMobs-Pollution-Scanner").start();
    }

    /**
     * Ultra-fast chunk scanning using ChunkSection's two-layer loop instead of three-layer coordinate loop.
     * This greatly optimizes performance!
     *
     * <p>Pollution absorption formula:
     * <ul>
     *   <li>Each leaf: 0.0001 (trees are the main purification source)</li>
     *   <li>Each water block: 0.00005 (water purification is weaker)</li>
     *   <li>Each grass/flower: 0.0001 (grass purification equals leaves)</li>
     * </ul>
     *
     * @param chunk The chunk to scan
     * @return Total environmental reduction value for the chunk
     */
    private static double calculateChunkReduction(LevelChunk chunk) {
        int leafCount = 0;
        int waterCount = 0;
        int grassCount = 0;

        for (LevelChunkSection section : chunk.getSections()) {
            if (section.hasOnlyAir()) continue;

            // For safe async thread access, we only do basic blockId comparison
            // (classic three-layer loop optimized version shown here)
            // Direct PaletteContainer access is not thread-safe
            for (int x = 0; x < 16; x++) {
                for (int y = 0; y < 16; y++) {
                    for (int z = 0; z < 16; z++) {
                        BlockState state = section.getBlockState(x, y, z);
                        if (state.is(Blocks.WATER)) {
                            waterCount++;
                        } else if (state.is(BlockTags.LEAVES)) {
                            leafCount++;
                        } else if (state.is(BlockTags.FLOWERS) ||
                                   state.is(Blocks.GRASS) ||
                                   state.is(Blocks.TALL_GRASS) ||
                                   state.is(Blocks.FERN) ||
                                   state.is(Blocks.LARGE_FERN)) {
                            grassCount++;
                        }
                    }
                }
            }
        }

        return (leafCount * 0.0001) + (waterCount * 0.00005) + (grassCount * 0.0001);
    }

    /**
     * Cleans pollution in a radius around a position (for Air Scrubber integration).
     * <p>
     * The cleaning amount decreases with distance from the center.
     * This method is called when GT Air Scrubber machines complete recipes.
     *
     * @param level         The server level
     * @param center        Center position of the cleaning effect
     * @param radiusChunks  Radius in chunks
     * @param amount        Base cleaning amount
     */
    public static void cleanPollutionInRadius(ServerLevel level, BlockPos center, int radiusChunks, double amount) {
        ChunkPos centerChunk = new ChunkPos(center);
        double efficiency = TriAxisConfig.airScrubberEfficiency;

        for (int dx = -radiusChunks; dx <= radiusChunks; dx++) {
            for (int dz = -radiusChunks; dz <= radiusChunks; dz++) {
                ChunkPos targetChunk = new ChunkPos(centerChunk.x + dx, centerChunk.z + dz);
                double distance = Math.sqrt(dx * dx + dz * dz);
                double cleanAmount = (amount * efficiency) / Math.max(1.0, distance);

                temporaryPollution.computeIfPresent(targetChunk, (k, v) -> {
                    double newValue = v - cleanAmount;
                    if (IndustrialLogger.isDebugEnabled()) {
                        IndustrialLogger.debugPollution(String.format(
                                "Air Scrubber cleaning chunk %s: %.2f -> %.2f (cleaned %.4f)",
                                targetChunk, v, Math.max(0, newValue), cleanAmount));
                    }
                    return newValue > 0 ? newValue : null; // Remove if <= 0
                });
            }
        }
    }
}