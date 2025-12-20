/**
 * <h1>AlexsCaves Integration</h1>
 *
 * <p>This package provides integration with AlexsCaves mod, specifically for
 * spawning Nucleepers based on industrial progression and Hivemind infestation.
 *
 * <h2>Overview</h2>
 * <p>Nucleepers are nuclear-powered creepers from AlexsCaves that can be spawned
 * under extreme industrial conditions. This integration adds three spawn conditions:
 *
 * <h3>Condition 1: High Voltage + High Pollution</h3>
 * <ul>
 *   <li>Average voltage tier: UV~UHV (8~10)</li>
 *   <li>Pollution level: ≥ 200</li>
 *   <li>Base spawn chance: 2% per second</li>
 * </ul>
 *
 * <h3>Condition 2: Hivemind Infestation</h3>
 * <ul>
 *   <li>Hivemind count: ≥ 5</li>
 *   <li>Total biomass: ≥ 500</li>
 *   <li>Voltage tier: ZPM~UHV (7~10)</li>
 *   <li>Base spawn chance: 2% + 0.75% per 10 biomass</li>
 * </ul>
 *
 * <h3>Condition 3: Horde Event</h3>
 * <ul>
 *   <li>Hivemind count: ≥ 5</li>
 *   <li>Voltage tier: ≥ UV (8)</li>
 *   <li>Pollution: ≥ 200</li>
 *   <li>Difficulty: ≥ 150</li>
 *   <li>Spawn chance: 10% per Horde event</li>
 * </ul>
 *
 * <h2>Key Classes</h2>
 * <ul>
 *   <li>{@link cn.minerealms.iic.integration.alexscaves.AlexsCavesIntegration} - Main integration class</li>
 *   <li>{@link cn.minerealms.iic.integration.alexscaves.NucleeperSpawnManager} - Spawn logic and condition checking</li>
 *   <li>{@link cn.minerealms.iic.integration.alexscaves.NucleeperSpawnConfig} - Configuration parameters</li>
 *   <li>{@link cn.minerealms.iic.integration.alexscaves.NucleeperTargetMachineGoal} - AI for targeting machines</li>
 * </ul>
 *
 * <h2>Configuration</h2>
 * <p>All parameters are configurable via {@code triaxis-difficulty.properties}:
 * <pre>
 * # Nucleeper Spawning - Condition 1: High Voltage + Pollution
 * nucleeper.enableVoltagePollutionSpawn=true
 * nucleeper.minVoltageTier=8
 * nucleeper.maxVoltageTier=10
 * nucleeper.minPollution=200.0
 * nucleeper.baseSpawnChanceVoltage=0.02
 *
 * # Nucleeper Spawning - Condition 2: Hivemind Infestation
 * nucleeper.enableHivemindSpawn=true
 * nucleeper.minHivemindCount=5
 * nucleeper.minBiomass=500
 * nucleeper.minVoltageTierHivemind=7
 * nucleeper.maxVoltageTierHivemind=10
 * nucleeper.baseSpawnChanceHivemind=0.02
 * nucleeper.spawnChancePerBiomass=0.0075
 * nucleeper.biomassIncrement=10
 *
 * # Nucleeper Spawning - Condition 3: Horde Event
 * nucleeper.enableHordeSpawn=true
 * nucleeper.minHivemindCountHorde=5
 * nucleeper.minVoltageTierHorde=8
 * nucleeper.minPollutionHorde=200.0
 * nucleeper.minDifficultyHorde=150.0
 * nucleeper.hordeSpawnChance=0.10
 *
 * # General Settings
 * nucleeper.spawnRadius=32
 * nucleeper.minSpawnDistance=16
 * nucleeper.maxCount=5
 * nucleeper.spawnCooldown=20
 * nucleeper.debugLogging=false
 * </pre>
 *
 * <h2>Usage Example</h2>
 * <pre>{@code
 * // Initialize integration
 * AlexsCavesIntegration.initialize();
 *
 * // Tick integration (called from ServerTickEvent)
 * AlexsCavesIntegration.tick(level);
 *
 * // Trigger Horde event spawn
 * boolean spawned = AlexsCavesIntegration.onHordeEvent(level, player);
 * }</pre>
 *
 * @author I3C Team
 * @version 1.0.0
 * @since 1.0.0
 */
package cn.minerealms.iic.integration.alexscaves;
