package cn.minerealms.iic.integration.alexscaves;

import cn.minerealms.iic.industrial.TriAxisConfig;

/**
 * Configuration for Nucleeper spawning conditions and behavior.
 *
 * <p>Nucleeper can spawn under two different conditions:
 * <ol>
 *   <li><b>High Voltage + High Pollution</b>: UV~UHV tier with pollution ≥ 200</li>
 *   <li><b>Hivemind Infestation</b>: 5+ Hiveminds with 500+ total biomass</li>
 * </ol>
 *
 * <p>All parameters are configurable via {@code triaxis-difficulty.properties}.
 *
 * @author I3C Team
 * @since 1.0.0
 */
public class NucleeperSpawnConfig {

    // ========================================
    // Condition 1: High Voltage + High Pollution
    // ========================================

    /** Enable Nucleeper spawning based on voltage tier and pollution */
    public static boolean enableVoltagePollutionSpawn = true;

    /** Minimum average voltage tier for Nucleeper spawning (default: 8 = UV) */
    public static int minVoltageTierForNucleeper = 8;  // UV

    /** Maximum average voltage tier for Nucleeper spawning (default: 10 = UHV) */
    public static int maxVoltageTierForNucleeper = 10;  // UHV

    /** Minimum pollution level for Nucleeper spawning */
    public static double minPollutionForNucleeper = 200.0;

    /** Base spawn chance per second when conditions are met (default: 2%) */
    public static double baseSpawnChanceVoltage = 0.02;

    // ========================================
    // Condition 2: Hivemind Infestation
    // ========================================

    /** Enable Nucleeper spawning based on Hivemind count and biomass */
    public static boolean enableHivemindSpawn = true;

    /** Minimum number of Hiveminds required for Nucleeper spawning */
    public static int minHivemindCount = 5;

    /** Minimum total biomass required for Nucleeper spawning */
    public static int minBiomassForNucleeper = 500;

    /** Minimum voltage tier for Hivemind-based spawning (default: 7 = ZPM) */
    public static int minVoltageTierForHivemindSpawn = 7;  // ZPM

    /** Maximum voltage tier for Hivemind-based spawning (default: 10 = UHV) */
    public static int maxVoltageTierForHivemindSpawn = 10;  // UHV

    /** Base spawn chance per second when Hivemind conditions are met (default: 2%) */
    public static double baseSpawnChanceHivemind = 0.02;

    /** Additional spawn chance per 10 biomass above minimum (default: 0.75%) */
    public static double spawnChancePerBiomass = 0.0075;

    /** Biomass increment for spawn chance calculation (default: 10) */
    public static int biomassIncrement = 10;

    // ========================================
    // Horde Event Integration
    // ========================================

    /** Enable Nucleeper spawning during Horde events */
    public static boolean enableHordeSpawn = true;

    /** Minimum number of Hiveminds for Horde spawning */
    public static int minHivemindCountForHorde = 5;

    /** Minimum voltage tier for Horde spawning (default: 8 = UV) */
    public static int minVoltageTierForHorde = 8;  // UV

    /** Minimum pollution level for Horde spawning */
    public static double minPollutionForHorde = 200.0;

    /** Minimum difficulty for Horde spawning */
    public static double minDifficultyForHorde = 150.0;

    /** Spawn chance per Horde event when conditions are met (default: 10%) */
    public static double hordeSpawnChance = 0.10;

    // ========================================
    // General Settings
    // ========================================

    /** Spawn radius around player (in blocks) */
    public static int spawnRadius = 32;

    /** Minimum distance from player for spawning (in blocks) */
    public static int minSpawnDistance = 16;

    /** Maximum number of Nucleepers that can exist simultaneously */
    public static int maxNucleeperCount = 5;

    /** Cooldown between spawn attempts (in ticks, default: 20 = 1 second) */
    public static int spawnCooldown = 20;

    /** Enable debug logging for Nucleeper spawning */
    public static boolean debugLogging = false;

    /**
     * Loads configuration from TriAxisConfig.
     * Called during mod initialization and config reload.
     */
    public static void load() {
        // Condition 1: Voltage + Pollution
        enableVoltagePollutionSpawn = TriAxisConfig.getBoolean("nucleeper.enableVoltagePollutionSpawn", true);
        minVoltageTierForNucleeper = TriAxisConfig.getInt("nucleeper.minVoltageTier", 8);
        maxVoltageTierForNucleeper = TriAxisConfig.getInt("nucleeper.maxVoltageTier", 10);
        minPollutionForNucleeper = TriAxisConfig.getDouble("nucleeper.minPollution", 200.0);
        baseSpawnChanceVoltage = TriAxisConfig.getDouble("nucleeper.baseSpawnChanceVoltage", 0.02);

        // Condition 2: Hivemind
        enableHivemindSpawn = TriAxisConfig.getBoolean("nucleeper.enableHivemindSpawn", true);
        minHivemindCount = TriAxisConfig.getInt("nucleeper.minHivemindCount", 5);
        minBiomassForNucleeper = TriAxisConfig.getInt("nucleeper.minBiomass", 500);
        minVoltageTierForHivemindSpawn = TriAxisConfig.getInt("nucleeper.minVoltageTierHivemind", 7);
        maxVoltageTierForHivemindSpawn = TriAxisConfig.getInt("nucleeper.maxVoltageTierHivemind", 10);
        baseSpawnChanceHivemind = TriAxisConfig.getDouble("nucleeper.baseSpawnChanceHivemind", 0.02);
        spawnChancePerBiomass = TriAxisConfig.getDouble("nucleeper.spawnChancePerBiomass", 0.0075);
        biomassIncrement = TriAxisConfig.getInt("nucleeper.biomassIncrement", 10);

        // Horde Event
        enableHordeSpawn = TriAxisConfig.getBoolean("nucleeper.enableHordeSpawn", true);
        minHivemindCountForHorde = TriAxisConfig.getInt("nucleeper.minHivemindCountHorde", 5);
        minVoltageTierForHorde = TriAxisConfig.getInt("nucleeper.minVoltageTierHorde", 8);
        minPollutionForHorde = TriAxisConfig.getDouble("nucleeper.minPollutionHorde", 200.0);
        minDifficultyForHorde = TriAxisConfig.getDouble("nucleeper.minDifficultyHorde", 150.0);
        hordeSpawnChance = TriAxisConfig.getDouble("nucleeper.hordeSpawnChance", 0.10);

        // General
        spawnRadius = TriAxisConfig.getInt("nucleeper.spawnRadius", 32);
        minSpawnDistance = TriAxisConfig.getInt("nucleeper.minSpawnDistance", 16);
        maxNucleeperCount = TriAxisConfig.getInt("nucleeper.maxCount", 5);
        spawnCooldown = TriAxisConfig.getInt("nucleeper.spawnCooldown", 20);
        debugLogging = TriAxisConfig.getBoolean("nucleeper.debugLogging", false);
    }

    /**
     * Saves configuration to TriAxisConfig.
     * Called when configuration is modified via commands.
     */
    public static void save() {
        // Condition 1: Voltage + Pollution
        TriAxisConfig.setBoolean("nucleeper.enableVoltagePollutionSpawn", enableVoltagePollutionSpawn);
        TriAxisConfig.setInt("nucleeper.minVoltageTier", minVoltageTierForNucleeper);
        TriAxisConfig.setInt("nucleeper.maxVoltageTier", maxVoltageTierForNucleeper);
        TriAxisConfig.setDouble("nucleeper.minPollution", minPollutionForNucleeper);
        TriAxisConfig.setDouble("nucleeper.baseSpawnChanceVoltage", baseSpawnChanceVoltage);

        // Condition 2: Hivemind
        TriAxisConfig.setBoolean("nucleeper.enableHivemindSpawn", enableHivemindSpawn);
        TriAxisConfig.setInt("nucleeper.minHivemindCount", minHivemindCount);
        TriAxisConfig.setInt("nucleeper.minBiomass", minBiomassForNucleeper);
        TriAxisConfig.setInt("nucleeper.minVoltageTierHivemind", minVoltageTierForHivemindSpawn);
        TriAxisConfig.setInt("nucleeper.maxVoltageTierHivemind", maxVoltageTierForHivemindSpawn);
        TriAxisConfig.setDouble("nucleeper.baseSpawnChanceHivemind", baseSpawnChanceHivemind);
        TriAxisConfig.setDouble("nucleeper.spawnChancePerBiomass", spawnChancePerBiomass);
        TriAxisConfig.setInt("nucleeper.biomassIncrement", biomassIncrement);

        // Horde Event
        TriAxisConfig.setBoolean("nucleeper.enableHordeSpawn", enableHordeSpawn);
        TriAxisConfig.setInt("nucleeper.minHivemindCountHorde", minHivemindCountForHorde);
        TriAxisConfig.setInt("nucleeper.minVoltageTierHorde", minVoltageTierForHorde);
        TriAxisConfig.setDouble("nucleeper.minPollutionHorde", minPollutionForHorde);
        TriAxisConfig.setDouble("nucleeper.minDifficultyHorde", minDifficultyForHorde);
        TriAxisConfig.setDouble("nucleeper.hordeSpawnChance", hordeSpawnChance);

        // General
        TriAxisConfig.setInt("nucleeper.spawnRadius", spawnRadius);
        TriAxisConfig.setInt("nucleeper.minSpawnDistance", minSpawnDistance);
        TriAxisConfig.setInt("nucleeper.maxCount", maxNucleeperCount);
        TriAxisConfig.setInt("nucleeper.spawnCooldown", spawnCooldown);
        TriAxisConfig.setBoolean("nucleeper.debugLogging", debugLogging);

        TriAxisConfig.save();
    }
}
