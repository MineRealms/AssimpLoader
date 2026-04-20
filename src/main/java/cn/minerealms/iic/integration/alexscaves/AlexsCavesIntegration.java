package cn.minerealms.iic.integration.alexscaves;

import cn.minerealms.iic.industrial.IndustrialLogger;

/**
 * Main integration class for AlexsCaves mod.
 *
 * <p>This class provides integration with AlexsCaves, specifically for
 * spawning Nucleepers based on industrial progression and Hivemind infestation.
 *
 * <h2>Features</h2>
 * <ul>
 *   <li>Automatic Nucleeper spawning based on voltage tier and pollution</li>
 *   <li>Hivemind-based Nucleeper spawning with biomass scaling</li>
 *   <li>Horde event integration for factory raids</li>
 *   <li>Custom AI for Nucleepers to target machines</li>
 * </ul>
 *
 * <h2>Configuration</h2>
 * <p>All parameters are configurable via {@code triaxis-difficulty.properties}.
 * See {@link NucleeperSpawnConfig} for details.
 *
 * @author I3C Team
 * @since 1.0.0
 * @see NucleeperSpawnManager
 * @see NucleeperSpawnConfig
 * @see NucleeperTargetMachineGoal
 */
public class AlexsCavesIntegration {

    private static boolean initialized = false;

    /**
     * Checks if AlexsCaves mod is loaded.
     *
     * @return true if AlexsCaves is loaded
     */
    public static boolean isAlexsCavesLoaded() {
        return NucleeperSpawnManager.isAlexsCavesLoaded();
    }

    /**
     * Initializes AlexsCaves integration.
     * Called during mod initialization.
     */
    public static void initialize() {
        if (initialized) {
            return;
        }

        IndustrialLogger.info("=== Initializing AlexsCaves Integration ===");

        // Load configuration
        NucleeperSpawnConfig.load();

        // Check if AlexsCaves is loaded
        if (isAlexsCavesLoaded()) {
            IndustrialLogger.info("✓ AlexsCaves integration enabled");
            IndustrialLogger.info("  - Nucleeper spawning: Voltage + Pollution = " +
                NucleeperSpawnConfig.enableVoltagePollutionSpawn);
            IndustrialLogger.info("  - Nucleeper spawning: Hivemind = " +
                NucleeperSpawnConfig.enableHivemindSpawn);
            IndustrialLogger.info("  - Nucleeper spawning: Horde Events = " +
                NucleeperSpawnConfig.enableHordeSpawn);
        } else {
            IndustrialLogger.info("✗ AlexsCaves not found, integration disabled");
        }

        initialized = true;
        IndustrialLogger.info("=== AlexsCaves Integration: " +
            (isAlexsCavesLoaded() ? "ENABLED" : "DISABLED") + " ===");
    }

    /**
     * Ticks the AlexsCaves integration.
     * Called from ServerTickEvent.
     *
     * @param level the server level
     */
    public static void tick(net.minecraft.server.level.ServerLevel level) {
        if (!isAlexsCavesLoaded()) {
            return;
        }

        NucleeperSpawnManager.tick(level);
    }

    /**
     * Called when a Horde event is triggered.
     * Attempts to spawn a Nucleeper if conditions are met.
     *
     * @param level the server level
     * @param player the player triggering the Horde
     * @return true if a Nucleeper was spawned
     */
    public static boolean onHordeEvent(net.minecraft.server.level.ServerLevel level,
                                       net.minecraft.server.level.ServerPlayer player) {
        if (!isAlexsCavesLoaded()) {
            return false;
        }

        return NucleeperSpawnManager.spawnNucleeperForHorde(level, player);
    }

    /**
     * Reloads configuration from disk.
     */
    public static void reloadConfig() {
        NucleeperSpawnConfig.load();
        IndustrialLogger.info("[AlexsCaves] Configuration reloaded");
    }

    /**
     * Saves configuration to disk.
     */
    public static void saveConfig() {
        NucleeperSpawnConfig.save();
        IndustrialLogger.info("[AlexsCaves] Configuration saved");
    }
}
