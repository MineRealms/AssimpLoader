package cn.minerealms.iic.api;

import cn.minerealms.iic.difficulty.DifficultyManager;
import cn.minerealms.iic.pollution.PollutionManager;
import cn.minerealms.iic.threat.ThreatManager;
import cn.minerealms.iic.industrial.TriAxisConfig;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.ChunkPos;

/**
 * <h1>I3C API - Improved Integrated Industrial Craft</h1>
 *
 * <p>This is the main API entry point for extending I3C (Improved Integrated Industrial Craft)
 * with custom difficulty providers, pollution sources, threat handlers, and integration hooks.
 *
 * <p>Like the I3C bus (an advanced version of I2C), this API acts as a "bus" that allows
 * various mods to integrate and connect with the industrial difficulty system.
 *
 * <h2>Core Systems</h2>
 *
 * <h3>1. Difficulty System</h3>
 * <p>The difficulty system provides dynamic difficulty scaling based on player
 * progression and environmental factors. You can:
 * <ul>
 *   <li>Query player-specific difficulty bonuses</li>
 *   <li>Add custom difficulty providers via ImprovedMobs API</li>
 *   <li>Calculate location-based difficulty</li>
 * </ul>
 *
 * <h3>2. Pollution System</h3>
 * <p>The pollution system implements a dual-layer pollution model:
 * <ul>
 *   <li><b>Temporary Pollution</b>: Chunk-based, can be absorbed by environment</li>
 *   <li><b>Permanent Pollution</b>: Global accumulation, directly affects difficulty</li>
 * </ul>
 *
 * <h3>3. Threat System</h3>
 * <p>The threat system spawns hostile mobs based on pollution levels and voltage tiers:
 * <ul>
 *   <li>MV (Tier 2): Zombies attack machines when pollution ≥ 50</li>
 *   <li>HV (Tier 3+): Active zombie spawning when pollution ≥ 80</li>
 *   <li>HV (Tier 3+): Active creeper spawning when pollution ≥ 120</li>
 *   <li>Extreme: Charged creeper spawning when pollution ≥ 200</li>
 * </ul>
 *
 * <h2>Usage Examples</h2>
 *
 * <h3>Example 1: Query Player Difficulty</h3>
 * <pre>{@code
 * // Get the current difficulty bonus for a player (read-only)
 * float playerDifficulty = I3CAPI.getPlayerDifficulty(player);
 *
 * // To add custom difficulty, implement DifficultyGetter (see Example 5)
 * }</pre>
 *
 * <h3>Example 2: Add Pollution Source</h3>
 * <pre>{@code
 * // Add temporary pollution to a chunk (e.g., from custom machines)
 * ChunkPos chunkPos = new ChunkPos(blockPos);
 * I3CAPI.addTemporaryPollution(chunkPos, 10.0);
 *
 * // Add permanent pollution (long-term environmental impact)
 * I3CAPI.addPermanentPollution(5.0);
 * }</pre>
 *
 * <h3>Example 3: Query Pollution Levels</h3>
 * <pre>{@code
 * // Get temporary pollution in a chunk
 * double tempPollution = I3CAPI.getTemporaryPollution(chunkPos);
 *
 * // Get global permanent pollution
 * double permPollution = I3CAPI.getPermanentPollution();
 * }</pre>
 *
 * <h3>Example 4: Clean Pollution (Air Scrubber Integration)</h3>
 * <pre>{@code
 * // Clean pollution in a radius (e.g., from air scrubber machines)
 * I3CAPI.cleanPollutionInRadius(level, centerPos, radiusChunks, cleanAmount);
 * }</pre>
 *
 * <h3>Example 5: Custom Difficulty Provider</h3>
 * <pre>{@code
 * // Implement DifficultyGetter interface from ImprovedMobs API
 * public class MyDifficultyProvider implements DifficultyGetter {
 *     @Override
 *     public float getDifficulty(ServerLevel level, Vec3 pos) {
 *         // Your custom difficulty calculation
 *         return customDifficultyValue;
 *     }
 *
 *     @Override
 *     public Config.IntegrationType getType() {
 *         return Config.IntegrationType.ADD;  // or MULTIPLY, OVERRIDE
 *     }
 * }
 *
 * // Register in your mod's constructor
 * DifficultyFetcher.add(new MyDifficultyProvider());
 * }</pre>
 *
 * <h2>Configuration</h2>
 * <p>All system parameters are configurable via {@link TriAxisConfig}:
 * <ul>
 *   <li>Pollution generation rates and thresholds</li>
 *   <li>Threat trigger conditions and spawn probabilities</li>
 *   <li>Difficulty weights and calculation formulas</li>
 *   <li>Voltage tier thresholds for threat escalation</li>
 * </ul>
 *
 * <h2>Thread Safety</h2>
 * <p>All API methods are thread-safe and can be called from any thread. Heavy
 * operations are automatically offloaded to background threads to prevent server lag.
 *
 * <h2>Performance Considerations</h2>
 * <ul>
 *   <li>Pollution scanning: Async, every 1 second</li>
 *   <li>Environment caching: Background thread, every 5 minutes</li>
 *   <li>Threat detection: Probability-based, every 1 second</li>
 *   <li>All operations are optimized for large modpacks with hundreds of machines</li>
 * </ul>
 *
 * @author ImprovedMobs Team
 * @version 1.0.0
 * @since 1.0.0
 *
 * @see DifficultyManager
 * @see PollutionManager
 * @see ThreatManager
 * @see TriAxisConfig
 */
public final class I3CAPI {

    private I3CAPI() {
        throw new UnsupportedOperationException("This is a utility class and cannot be instantiated");
    }

    // ========================================
    // Difficulty System API
    // ========================================

    /**
     * Gets the current difficulty bonus for a player.
     *
     * <p>This value represents the player's industrial progression bonus,
     * calculated automatically from nearby machines, voltage tiers, and pollution levels.
     *
     * <p><b>Note:</b> This value is read-only and automatically calculated by the system.
     * To add custom difficulty, implement a {@code DifficultyGetter} and register it
     * with {@code DifficultyFetcher.add()}.
     *
     * @param player the player to query
     * @return the difficulty bonus value (0.0 = no bonus, higher = more difficult)
     * @throws NullPointerException if player is null
     *
     * @see io.github.flemmli97.improvedmobs.api.difficulty.DifficultyGetter
     * @see io.github.flemmli97.improvedmobs.api.difficulty.DifficultyFetcher
     */
    public static float getPlayerDifficulty(Player player) {
        if (player == null) {
            throw new NullPointerException("Player cannot be null");
        }
        return DifficultyManager.getDifficultyFor(player);
    }

    // ========================================
    // Pollution System API
    // ========================================

    /**
     * Gets the temporary pollution level for a specific chunk.
     *
     * <p>Temporary pollution is chunk-based and can be absorbed by the environment
     * (leaves, water, grass). It triggers mob attacks on machines but does not
     * directly affect difficulty.
     *
     * @param chunkPos the chunk position to query
     * @return the temporary pollution level (0.0 = no pollution)
     * @throws NullPointerException if chunkPos is null
     */
    public static double getTemporaryPollution(ChunkPos chunkPos) {
        if (chunkPos == null) {
            throw new NullPointerException("ChunkPos cannot be null");
        }
        return PollutionManager.getTemporaryPollution(chunkPos);
    }

    /**
     * Adds temporary pollution to a specific chunk.
     *
     * <p>This is useful for custom machines or pollution sources that want to
     * contribute to the temporary pollution system. The pollution will naturally
     * decay over time and can be absorbed by the environment.
     *
     * @param chunkPos the chunk position to add pollution to
     * @param amount the pollution amount to add (must be positive)
     * @throws NullPointerException if chunkPos is null
     * @throws IllegalArgumentException if amount is negative
     */
    public static void addTemporaryPollution(ChunkPos chunkPos, double amount) {
        if (chunkPos == null) {
            throw new NullPointerException("ChunkPos cannot be null");
        }
        if (amount < 0) {
            throw new IllegalArgumentException("Pollution amount cannot be negative");
        }
        double current = PollutionManager.getTemporaryPollution(chunkPos);
        PollutionManager.setChunkPollution(null, chunkPos, current + amount);
    }

    /**
     * Sets the temporary pollution level for a specific chunk directly.
     *
     * <p>This overwrites the chunk's current pollution value. Use with caution,
     * as it may interfere with the automatic pollution calculation system.
     *
     * @param level the server level (can be null)
     * @param chunkPos the chunk position to modify
     * @param amount the new pollution value (negative values are clamped to 0)
     * @throws NullPointerException if chunkPos is null
     */
    public static void setTemporaryPollution(ServerLevel level, ChunkPos chunkPos, double amount) {
        if (chunkPos == null) {
            throw new NullPointerException("ChunkPos cannot be null");
        }
        PollutionManager.setChunkPollution(level, chunkPos, amount);
    }

    /**
     * Gets the current global permanent pollution value.
     *
     * <p>Permanent pollution is a global accumulation that directly increases
     * ImprovedMobs difficulty. It represents long-term environmental impact
     * and cannot be reversed.
     *
     * @return the permanent pollution level (0.0 = no pollution)
     */
    public static double getPermanentPollution() {
        return PollutionManager.getPermanentPollution();
    }

    /**
     * Adds to the global permanent pollution value.
     *
     * <p>This is useful for custom systems that want to contribute to long-term
     * environmental impact. Permanent pollution directly increases difficulty
     * and cannot be removed.
     *
     * @param amount the pollution amount to add (must be positive)
     * @throws IllegalArgumentException if amount is negative
     */
    public static void addPermanentPollution(double amount) {
        if (amount < 0) {
            throw new IllegalArgumentException("Pollution amount cannot be negative");
        }
        PollutionManager.addPermanentPollution(amount);
    }

    /**
     * Sets the global permanent pollution value directly.
     *
     * <p>This overwrites the current permanent pollution value. Use with caution,
     * as it may interfere with the automatic pollution accumulation system.
     *
     * @param amount the new permanent pollution value (negative values are clamped to 0)
     */
    public static void setPermanentPollution(double amount) {
        PollutionManager.setPermanentPollution(amount);
    }

    /**
     * Cleans pollution in a radius around a position.
     *
     * <p>This is useful for air scrubber machines or other pollution cleaning
     * systems. The cleaning amount decreases with distance from the center.
     *
     * <p>Example usage for an air scrubber machine:
     * <pre>{@code
     * // Clean 10 units of pollution in a 3-chunk radius
     * ImprovedMobsAPI.cleanPollutionInRadius(level, machinePos, 3, 10.0);
     * }</pre>
     *
     * @param level the server level
     * @param center the center position of the cleaning effect
     * @param radiusChunks the radius in chunks
     * @param amount the base cleaning amount
     * @throws NullPointerException if level or center is null
     * @throws IllegalArgumentException if radiusChunks is negative or amount is negative
     */
    public static void cleanPollutionInRadius(ServerLevel level, BlockPos center, int radiusChunks, double amount) {
        if (level == null) {
            throw new NullPointerException("ServerLevel cannot be null");
        }
        if (center == null) {
            throw new NullPointerException("BlockPos cannot be null");
        }
        if (radiusChunks < 0) {
            throw new IllegalArgumentException("Radius cannot be negative");
        }
        if (amount < 0) {
            throw new IllegalArgumentException("Clean amount cannot be negative");
        }
        PollutionManager.cleanPollutionInRadius(level, center, radiusChunks, amount);
    }

    // ========================================
    // Threat System API
    // ========================================

    /**
     * Gets the average voltage tier of machines in a chunk.
     *
     * <p>This is useful for determining the threat level in an area. Higher
     * voltage tiers trigger more dangerous threats.
     *
     * @param level the server level
     * @param chunkPos the chunk position to scan
     * @return the average voltage tier (0=ULV, 1=LV, 2=MV, 3=HV, etc.), or 0 if no machines
     * @throws NullPointerException if level or chunkPos is null
     */
    public static double getChunkAverageVoltageTier(ServerLevel level, ChunkPos chunkPos) {
        if (level == null) {
            throw new NullPointerException("ServerLevel cannot be null");
        }
        if (chunkPos == null) {
            throw new NullPointerException("ChunkPos cannot be null");
        }
        return ThreatManager.getChunkAverageVoltageTier(level, chunkPos);
    }

    /**
     * Manually triggers threat checks for a chunk.
     *
     * <p>This is useful for custom systems that want to trigger threats based on
     * their own conditions. The threat system will check pollution levels and
     * voltage tiers to determine which threats to spawn.
     *
     * <p><b>Note:</b> This method is called automatically by the pollution system
     * every second. Only use this if you need to trigger threats manually.
     *
     * @param level the server level
     * @param chunkPos the chunk position to check
     * @param pollution the current pollution level in the chunk
     * @param avgVoltageTier the average voltage tier of machines in the chunk
     * @throws NullPointerException if level or chunkPos is null
     */
    public static void triggerThreatCheck(ServerLevel level, ChunkPos chunkPos, double pollution, double avgVoltageTier) {
        if (level == null) {
            throw new NullPointerException("ServerLevel cannot be null");
        }
        if (chunkPos == null) {
            throw new NullPointerException("ChunkPos cannot be null");
        }
        ThreatManager.checkAndTriggerThreats(level, chunkPos, pollution, avgVoltageTier);
    }

    // ========================================
    // Configuration API
    // ========================================

    /**
     * Gets the current configuration instance.
     *
     * <p>This provides access to all configurable parameters for the pollution,
     * threat, and difficulty systems. You can read configuration values but
     * should not modify them directly (use the config file instead).
     *
     * @return the configuration instance
     */
    public static TriAxisConfig getConfig() {
        return new TriAxisConfig(); // Returns a reference to the config class
    }

    /**
     * Reloads the configuration from disk.
     *
     * <p>This is useful for custom commands or systems that want to reload
     * configuration without restarting the server.
     */
    public static void reloadConfig() {
        TriAxisConfig.load();
    }

    /**
     * Saves the current configuration to disk.
     *
     * <p>This is useful for custom systems that modify configuration values
     * programmatically and want to persist them.
     */
    public static void saveConfig() {
        TriAxisConfig.save();
    }

    // ========================================
    // Utility Methods
    // ========================================

    /**
     * Gets the mod version.
     *
     * @return the mod version string
     */
    public static String getVersion() {
        return "1.0.0";
    }

    /**
     * Gets the API version.
     *
     * @return the API version string
     */
    public static String getAPIVersion() {
        return "1.0.0";
    }
}
