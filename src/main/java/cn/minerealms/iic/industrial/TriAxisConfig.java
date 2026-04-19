package cn.minerealms.iic.industrial;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.util.Properties;

/**
 * Configuration manager for the Tri-Axis Difficulty System.
 * <p>
 * This class manages all configuration parameters for the industrial integration system,
 * including difficulty weights, pollution thresholds, threat triggers, and mob attribute multipliers.
 * <p>
 * The configuration supports multiple difficulty presets:
 * <ul>
 *   <li>NORMAL - Balanced for casual players, recommended for beginners</li>
 *   <li>HARD - Higher challenge for experienced players</li>
 *   <li>HARDCORE - High difficulty for hardcore players</li>
 *   <li>INSANE - Extreme challenge for masochists</li>
 *   <li>CUSTOM - Use custom values from config file</li>
 * </ul>
 * <p>
 * Configuration file location: config/triaxis-difficulty.properties
 * <p>
 * All parameters can be customized via the config file. The file is automatically
 * generated with detailed comments on first run.
 *
 * @see TriAxisDifficultyManager
 * @see IndustrialDifficultyManager
 * @see PollutionManager
 * @see ThreatManager
 * @author ImprovedMobs Team
 */
public class TriAxisConfig {
    // ========== Tri-Axis Weights ==========

    /**
     * Weight for time factor in tri-axis difficulty calculation (0.0-1.0).
     * Higher values make game time more influential on difficulty.
     */
    public static double weightTime = 0.35;

    /**
     * Weight for voltage/technology factor in tri-axis difficulty calculation (0.0-1.0).
     * Higher values make machine voltage tiers more influential on difficulty.
     */
    public static double weightVoltage = 0.35;

    /**
     * Weight for pollution factor in tri-axis difficulty calculation (0.0-1.0).
     * Higher values make pollution more influential on difficulty.
     * Note: The three weights should sum to approximately 1.0.
     */
    public static double weightPollution = 0.30;

    // ========== Global Difficulty Multipliers ==========

    /**
     * Global difficulty multiplier applied to final difficulty value (1.0-5.0 recommended).
     * Higher values make mobs stronger overall.
     */
    public static double globalMultiplier = 2.8;

    /**
     * Exponential moving average alpha for smoothing (0.01-0.2).
     * Lower values = smoother transitions, higher values = faster response.
     */
    public static double emaAlpha = 0.05;

    /**
     * Pollution saturation threshold (500-1500 recommended).
     * Higher values require more pollution to reach high difficulty.
     * Preset values: NORMAL=1000, HARD=900, HARDCORE=800, INSANE=600
     */
    public static double pollutionDenominator = 800.0;

    // ========== Industrial Bonus Configuration ==========

    /**
     * Technology weight for industrial bonus calculation (10.0-50.0).
     * Controls how much voltage tier affects mob strength.
     */
    public static double techWeight = 30.0;

    /**
     * Pollution weight for industrial bonus calculation (5.0-30.0).
     * Controls how much pollution affects mob strength.
     */
    public static double pollutionWeight = 18.0;

    /**
     * Maximum industrial voltage tier for normalization (0=ULV, 9=UHV, 14=MAX).
     * Should be set to the highest voltage tier in your modpack.
     * Recommended: 9 (UHV) for most modpacks.
     */
    public static int maxIndustrialTier = 9;

    // ========== Difficulty Contribution Weights ==========

    /**
     * Weight for player industrial bonus in difficulty calculation.
     */
    public static double playerBonusWeight = 1.0;

    /**
     * Weight for local temporary pollution in difficulty calculation.
     */
    public static double localPollutionWeight = 0.5;

    /**
     * Weight for global permanent pollution in difficulty calculation.
     */
    public static double globalPollutionWeight = 0.3;

    /**
     * Weight for time factor in difficulty calculation.
     */
    public static double timeFactorWeight = 0.2;

    // ========== Threat System Configuration ==========

    /**
     * MV stage: Pollution threshold for zombies to attack machines.
     * When chunk pollution reaches this value, nearby zombies will target machines.
     */
    public static double mvZombieAttackThreshold = 50.0;

    /**
     * HV stage: Pollution threshold for active zombie spawning.
     * When chunk pollution reaches this value, zombies will spawn near machines.
     */
    public static double hvZombieSpawnThreshold = 80.0;

    /**
     * HV stage: Pollution threshold for active creeper spawning.
     * When chunk pollution reaches this value, creepers will spawn near machines.
     */
    public static double hvCreeperSpawnThreshold = 120.0;

    /**
     * Pollution threshold for charged creeper spawning.
     * When chunk pollution reaches this value, charged creepers may spawn.
     */
    public static double chargedCreeperThreshold = 200.0;

    /**
     * Zombie spawn chance per second (0.0-1.0).
     * Default: 0.01 (1% chance per second)
     */
    public static double zombieSpawnChance = 0.01;

    /**
     * Creeper spawn chance per second (0.0-1.0).
     * Default: 0.005 (0.5% chance per second)
     */
    public static double creeperSpawnChance = 0.005;

    /**
     * Charged creeper spawn chance per second (0.0-1.0).
     * Default: 0.001 (0.1% chance per second)
     */
    public static double chargedCreeperChance = 0.001;

    // ========== Pollution System Configuration ==========

    /**
     * Threshold for converting temporary pollution to permanent pollution.
     * When temporary pollution exceeds this value, it starts converting to permanent.
     */
    public static double tempToPermanentThreshold = 200.0;

    /**
     * Conversion rate from temporary to permanent pollution per second (0.0-1.0).
     * Default: 0.001 (0.1% per second)
     */
    public static double tempToPermanentRate = 0.001;

    /**
     * Conversion rate from permanent pollution to difficulty.
     * Higher values make permanent pollution more impactful on difficulty.
     */
    public static double permanentToDifficultyRate = 0.1;

    // ========== Scanning & Thresholds ==========

    /**
     * Radius in blocks for machine scanning.
     * Larger values increase scan range but may impact performance.
     * Recommended: 64 blocks
     */
    public static int scanRadiusBlocks = 64;

    /**
     * Maximum GT voltage tier supported by the mod (for compatibility).
     * Usually 14 for full GregTech CEu support.
     */
    public static int maxGTTier = 14;

    /**
     * Maximum difficulty change per second to prevent sudden spikes.
     * Higher values allow faster difficulty changes.
     */
    public static double maxChangePerSec = 0.015;

    // ========== Time Scaling Parameters ==========

    /**
     * Target number of Minecraft days to reach peak time difficulty.
     * Longer values make time progression slower.
     * Preset values: NORMAL=800, HARD=1000, HARDCORE=1200, INSANE=1500
     */
    public static double targetDays = 1200.0;

    /**
     * Base days for logarithmic scaling (10-50).
     * Lower values make early game difficulty ramp up faster.
     * Recommended: 30
     */
    public static double baseDays = 30.0;

    // ========== Attribute Multipliers ==========

    /**
     * Health multiplier factor (0.5-5.0).
     * Multiplied with ImprovedMobs base config to determine final mob health.
     */
    public static double hpMultFactor = 2.2;

    /**
     * Attack damage multiplier factor (0.5-3.0).
     * Multiplied with ImprovedMobs base config to determine final mob damage.
     */
    public static double attackMultFactor = 1.6;

    /**
     * Speed multiplier factor (0.3-2.0).
     * Multiplied with ImprovedMobs base config to determine final mob speed.
     */
    public static double speedMultFactor = 0.8;

    /**
     * Armor multiplier factor (0.5-2.0).
     * Multiplied with ImprovedMobs base config to determine final mob armor.
     */
    public static double armorMultFactor = 1.2;

    // ========== Voltage Tier HP Targets ==========

    /**
     * HP multiplier target for ULV tier (Tier 0).
     * Base zombie health is 20, so 1.0 = 20 HP.
     */
    public static double ulvHpTarget = 1.0;

    /**
     * HP multiplier target for LV tier (Tier 1).
     * Default: 1.3 = 26 HP
     */
    public static double lvHpTarget = 1.3;

    /**
     * HP multiplier target for MV tier (Tier 2).
     * Default: 1.8 = 36 HP
     */
    public static double mvHpTarget = 1.8;

    /**
     * HP multiplier target for HV tier (Tier 3).
     * Default: 2.5 = 50 HP
     */
    public static double hvHpTarget = 2.5;

    /**
     * HP multiplier target for EV tier (Tier 4).
     * Default: 3.5 = 70 HP
     */
    public static double evHpTarget = 3.5;

    /**
     * HP multiplier target for IV tier (Tier 5).
     * Default: 4.8 = 96 HP
     */
    public static double ivHpTarget = 4.8;

    /**
     * HP multiplier target for LuV tier (Tier 6).
     * Default: 6.5 = 130 HP
     */
    public static double luvHpTarget = 6.5;

    /**
     * HP multiplier target for ZPM tier (Tier 7).
     * Default: 8.5 = 170 HP
     */
    public static double zpmHpTarget = 8.5;

    /**
     * HP multiplier target for UV tier (Tier 8).
     * Default: 11.0 = 220 HP
     */
    public static double uvHpTarget = 11.0;

    /**
     * HP multiplier target for UHV tier (Tier 9).
     * Default: 14.0 = 280 HP
     */
    public static double uhvHpTarget = 14.0;

    // ========== Difficulty Curve Presets ==========

    /**
     * Difficulty preset selection.
     * Valid values: NORMAL, HARD, HARDCORE, INSANE, CUSTOM
     * <p>
     * CUSTOM uses values from config file without applying preset.
     */
    public static String difficultyPreset = "NORMAL";

    // ========== GT Pollution Integration ==========

    /**
     * Weight for GT pollution contribution to temporary pollution (0.0-2.0).
     * Higher values make GT pollution more impactful.
     * Default: 0.8
     */
    public static double gtPollutionWeight = 0.8;

    /**
     * Base multiplier per 10 sources above threshold (0.1-1.0).
     * Controls how quickly multiplier increases with source count.
     * Default: 0.5
     */
    public static double gtSourceMultiplierBase = 0.5;

    /**
     * Threshold for GT pollution source multiplier activation.
     * When source count exceeds this, multipliers start applying.
     * Default: 10 sources
     */
    public static int gtSourceThreshold = 10;

    /**
     * Air Scrubber cleaning efficiency multiplier (0.5-2.0).
     * Higher values make Air Scrubbers more effective at reducing pollution.
     * Default: 1.0
     */
    public static double airScrubberEfficiency = 1.0;

    // ========== Hivemind Proximity System ==========

    /**
     * Proximity radius in chunks for Hivemind acceleration (4-16).
     * Pollution within this distance from Hiveminds accelerates difficulty.
     * Default: 8 chunks (128 blocks)
     */
    public static double hivemindProximityRadius = 8.0;

    /**
     * Maximum acceleration factor for Hivemind proximity (1.0-5.0).
     * Controls how much Hiveminds accelerate difficulty growth.
     * Default: 2.0 (up to 2x acceleration)
     */
    public static double hivemindAccelerationFactor = 2.0;

    /**
     * Enable Hivemind proximity acceleration system.
     * When enabled, pollution near Hiveminds accelerates difficulty growth.
     * Default: true
     */
    public static boolean enableHivemindAcceleration = true;

    // ========== Hordes Integration ==========

    /**
     * Enable The Hordes integration system.
     * When enabled, hordes are dynamically adjusted based on difficulty and pollution.
     * Default: true
     */
    public static boolean enableHordeIntegration = true;

    /**
     * Global horde intensity multiplier (0.5-3.0).
     * Higher values make hordes stronger overall.
     * Default: 1.0
     */
    public static double hordeIntensityMultiplier = 1.0;

    /**
     * Difficulty to horde intensity conversion factor (0.1-2.0).
     * Controls how much ImprovedMobs difficulty affects horde strength.
     * Default: 0.5
     */
    public static double difficultyToIntensityFactor = 0.5;

    /**
     * Enable pollution-triggered hordes.
     * When enabled, high pollution automatically triggers hordes.
     * Default: true
     */
    public static boolean enablePollutionTriggeredHordes = true;

    /**
     * Pollution threshold for triggering hordes (100-300).
     * When chunk pollution exceeds this, hordes may be triggered.
     * Default: 150.0
     */
    public static double pollutionHordeTriggerThreshold = 150.0;

    /**
     * Pollution horde check interval in ticks (300-1200).
     * How often to check for pollution-triggered hordes.
     * Default: 600 (30 seconds)
     */
    public static double pollutionHordeCheckInterval = 600.0;

    /**
     * Pollution horde trigger chance per check (0.01-0.2).
     * Probability of triggering a horde when pollution is high enough.
     * Default: 0.05 (5% per check)
     */
    public static double pollutionHordeTriggerChance = 0.05;

    /**
     * Enable small skirmishes (mini-hordes).
     * When enabled, small groups of mobs spawn periodically in polluted areas.
     * Default: true
     */
    public static boolean enableSkirmishes = true;

    /**
     * Pollution threshold for skirmishes (50-150).
     * When chunk pollution exceeds this, skirmishes may occur.
     * Default: 80.0
     */
    public static double skirmishPollutionThreshold = 80.0;

    /**
     * Skirmish interval in ticks (600-2400).
     * Time between skirmish checks.
     * Default: 1200 (1 minute)
     */
    public static double skirmishInterval = 1200.0;

    /**
     * Minimum skirmish mob count (1-10).
     * Default: 3
     */
    public static int skirmishMinCount = 3;

    /**
     * Maximum skirmish mob count (5-20).
     * Default: 8
     */
    public static int skirmishMaxCount = 8;

    /**
     * Major horde pollution threshold (150-300).
     * When pollution exceeds this, major hordes (2x strength) are triggered.
     * Default: 200.0
     */
    public static double majorHordePollutionThreshold = 200.0;

    /**
     * Major horde strength multiplier (1.5-3.0).
     * How much stronger major hordes are compared to normal hordes.
     * Default: 2.0
     */
    public static double majorHordeMultiplier = 2.0;

    /**
     * Enable machine targeting for horde zombies.
     * When enabled, some horde zombies will attack nearby machines.
     * Default: true
     */
    public static boolean enableMachineTargeting = true;

    /**
     * Machine targeting range in blocks (16-64).
     * How far zombies can detect machines to attack.
     * Default: 32.0
     */
    public static double machineTargetingRange = 32.0;

    /**
     * Machine targeting chance (0.1-1.0).
     * Probability that a horde zombie will target machines instead of players.
     * Default: 0.3 (30%)
     */
    public static double machineTargetingChance = 0.3;

    /**
     * Enable voltage tier scaling for hordes.
     * When enabled, horde strength scales with player's voltage tier.
     * Default: true
     */
    public static boolean enableVoltageTierScaling = true;

    // ========== Debug Settings ==========

    /**
     * Enable debug line rendering (e.g., zombie attack lines).
     * Default: false (disabled)
     */
    public static boolean enableDebugLines = false;

    /**
     * Enable detailed difficulty logging.
     * Default: false (disabled)
     */
    public static boolean enableDifficultyLogging = false;

    /**
     * Enable Spore integration debug information.
     * Default: false (disabled)
     */
    public static boolean enableSporeDebug = false;

    /**
     * Configuration file location.
     */
    private static final File CONFIG_FILE = new File("config/triaxis-difficulty.properties");

    /**
     * Applies a difficulty preset, overriding current values.
     * <p>
     * This method sets multiple configuration values at once based on the selected preset.
     * CUSTOM preset does not modify any values.
     *
     * @param preset the preset name (NORMAL, HARD, HARDCORE, INSANE, or CUSTOM)
     */
    public static void applyPreset(String preset) {
        switch (preset.toUpperCase()) {
            case "NORMAL" -> {
                globalMultiplier = 2.0;
                techWeight = 25.0;
                pollutionWeight = 15.0;
                hpMultFactor = 1.5;
                attackMultFactor = 1.2;
                targetDays = 800.0;
                pollutionDenominator = 1000.0;
            }
            case "HARD" -> {
                globalMultiplier = 2.5;
                techWeight = 28.0;
                pollutionWeight = 17.0;
                hpMultFactor = 2.0;
                attackMultFactor = 1.5;
                targetDays = 1000.0;
                pollutionDenominator = 900.0;
            }
            case "HARDCORE" -> {
                globalMultiplier = 2.8;
                techWeight = 30.0;
                pollutionWeight = 18.0;
                hpMultFactor = 2.2;
                attackMultFactor = 1.6;
                targetDays = 1200.0;
                pollutionDenominator = 800.0;
            }
            case "INSANE" -> {
                globalMultiplier = 3.5;
                techWeight = 35.0;
                pollutionWeight = 22.0;
                hpMultFactor = 3.0;
                attackMultFactor = 2.0;
                targetDays = 1500.0;
                pollutionDenominator = 600.0;
            }
            // CUSTOM does not modify any values, uses config file values
        }
    }

    /**
     * Gets the target HP multiplier for a given voltage tier.
     * <p>
     * For tiers beyond UHV (tier 9), the multiplier continues to increase
     * by 30% per tier.
     *
     * @param tier the voltage tier (0=ULV, 1=LV, 2=MV, etc.)
     * @return the HP multiplier for that tier
     */
    public static double getHpTargetForTier(int tier) {
        return switch (tier) {
            case 0 -> ulvHpTarget;
            case 1 -> lvHpTarget;
            case 2 -> mvHpTarget;
            case 3 -> hvHpTarget;
            case 4 -> evHpTarget;
            case 5 -> ivHpTarget;
            case 6 -> luvHpTarget;
            case 7 -> zpmHpTarget;
            case 8 -> uvHpTarget;
            case 9 -> uhvHpTarget;
            default -> uhvHpTarget * (1 + (tier - 9) * 0.3); // Beyond UHV continues to grow
        };
    }

    /**
     * Gets the effective maximum voltage tier for normalization.
     * <p>
     * This is the minimum of the user-configured max tier and the GT mod's max tier,
     * ensuring compatibility.
     *
     * @return the effective maximum tier
     */
    public static int getEffectiveMaxTier() {
        return Math.min(maxIndustrialTier, maxGTTier);
    }

    /**
     * Loads configuration from the properties file.
     * <p>
     * If the file doesn't exist, applies the default preset and saves it.
     * If the file exists, loads all values and applies the selected preset
     * (unless preset is CUSTOM).
     */
    public static void load() {
        Properties props = new Properties();
        if (CONFIG_FILE.exists()) {
            try (FileInputStream in = new FileInputStream(CONFIG_FILE)) {
                props.load(in);

                // 三轴权重
                weightTime = Double.parseDouble(props.getProperty("weightTime", String.valueOf(weightTime)));
                weightVoltage = Double.parseDouble(props.getProperty("weightVoltage", String.valueOf(weightVoltage)));
                weightPollution = Double.parseDouble(props.getProperty("weightPollution", String.valueOf(weightPollution)));

                // 全局系数
                globalMultiplier = Double.parseDouble(props.getProperty("globalMultiplier", String.valueOf(globalMultiplier)));
                emaAlpha = Double.parseDouble(props.getProperty("emaAlpha", String.valueOf(emaAlpha)));
                pollutionDenominator = Double.parseDouble(props.getProperty("pollutionDenominator", String.valueOf(pollutionDenominator)));

                // 工业加成
                techWeight = Double.parseDouble(props.getProperty("techWeight", String.valueOf(techWeight)));
                pollutionWeight = Double.parseDouble(props.getProperty("pollutionWeight", String.valueOf(pollutionWeight)));
                maxIndustrialTier = Integer.parseInt(props.getProperty("maxIndustrialTier", String.valueOf(maxIndustrialTier)));

                // 扫描参数
                scanRadiusBlocks = Integer.parseInt(props.getProperty("scanRadiusBlocks", String.valueOf(scanRadiusBlocks)));
                maxGTTier = Integer.parseInt(props.getProperty("maxGTTier", String.valueOf(maxGTTier)));
                maxChangePerSec = Double.parseDouble(props.getProperty("maxChangePerSec", String.valueOf(maxChangePerSec)));

                // 时间曲线
                targetDays = Double.parseDouble(props.getProperty("targetDays", String.valueOf(targetDays)));
                baseDays = Double.parseDouble(props.getProperty("baseDays", String.valueOf(baseDays)));

                // 属性增幅
                hpMultFactor = Double.parseDouble(props.getProperty("hpMultFactor", String.valueOf(hpMultFactor)));
                attackMultFactor = Double.parseDouble(props.getProperty("attackMultFactor", String.valueOf(attackMultFactor)));
                speedMultFactor = Double.parseDouble(props.getProperty("speedMultFactor", String.valueOf(speedMultFactor)));
                armorMultFactor = Double.parseDouble(props.getProperty("armorMultFactor", String.valueOf(armorMultFactor)));

                // 电压阶段血量目标
                ulvHpTarget = Double.parseDouble(props.getProperty("ulvHpTarget", String.valueOf(ulvHpTarget)));
                lvHpTarget = Double.parseDouble(props.getProperty("lvHpTarget", String.valueOf(lvHpTarget)));
                mvHpTarget = Double.parseDouble(props.getProperty("mvHpTarget", String.valueOf(mvHpTarget)));
                hvHpTarget = Double.parseDouble(props.getProperty("hvHpTarget", String.valueOf(hvHpTarget)));
                evHpTarget = Double.parseDouble(props.getProperty("evHpTarget", String.valueOf(evHpTarget)));
                ivHpTarget = Double.parseDouble(props.getProperty("ivHpTarget", String.valueOf(ivHpTarget)));
                luvHpTarget = Double.parseDouble(props.getProperty("luvHpTarget", String.valueOf(luvHpTarget)));
                zpmHpTarget = Double.parseDouble(props.getProperty("zpmHpTarget", String.valueOf(zpmHpTarget)));
                uvHpTarget = Double.parseDouble(props.getProperty("uvHpTarget", String.valueOf(uvHpTarget)));
                uhvHpTarget = Double.parseDouble(props.getProperty("uhvHpTarget", String.valueOf(uhvHpTarget)));

                // GT污染集成
                gtPollutionWeight = Double.parseDouble(props.getProperty("gtPollutionWeight", String.valueOf(gtPollutionWeight)));
                gtSourceMultiplierBase = Double.parseDouble(props.getProperty("gtSourceMultiplierBase", String.valueOf(gtSourceMultiplierBase)));
                gtSourceThreshold = Integer.parseInt(props.getProperty("gtSourceThreshold", String.valueOf(gtSourceThreshold)));
                airScrubberEfficiency = Double.parseDouble(props.getProperty("airScrubberEfficiency", String.valueOf(airScrubberEfficiency)));

                // Hivemind接近系统
                hivemindProximityRadius = Double.parseDouble(props.getProperty("hivemindProximityRadius", String.valueOf(hivemindProximityRadius)));
                hivemindAccelerationFactor = Double.parseDouble(props.getProperty("hivemindAccelerationFactor", String.valueOf(hivemindAccelerationFactor)));
                enableHivemindAcceleration = Boolean.parseBoolean(props.getProperty("enableHivemindAcceleration", String.valueOf(enableHivemindAcceleration)));

                // Hordes集成
                enableHordeIntegration = Boolean.parseBoolean(props.getProperty("enableHordeIntegration", String.valueOf(enableHordeIntegration)));
                hordeIntensityMultiplier = Double.parseDouble(props.getProperty("hordeIntensityMultiplier", String.valueOf(hordeIntensityMultiplier)));
                difficultyToIntensityFactor = Double.parseDouble(props.getProperty("difficultyToIntensityFactor", String.valueOf(difficultyToIntensityFactor)));
                enablePollutionTriggeredHordes = Boolean.parseBoolean(props.getProperty("enablePollutionTriggeredHordes", String.valueOf(enablePollutionTriggeredHordes)));
                pollutionHordeTriggerThreshold = Double.parseDouble(props.getProperty("pollutionHordeTriggerThreshold", String.valueOf(pollutionHordeTriggerThreshold)));
                pollutionHordeCheckInterval = Double.parseDouble(props.getProperty("pollutionHordeCheckInterval", String.valueOf(pollutionHordeCheckInterval)));
                pollutionHordeTriggerChance = Double.parseDouble(props.getProperty("pollutionHordeTriggerChance", String.valueOf(pollutionHordeTriggerChance)));
                enableSkirmishes = Boolean.parseBoolean(props.getProperty("enableSkirmishes", String.valueOf(enableSkirmishes)));
                skirmishPollutionThreshold = Double.parseDouble(props.getProperty("skirmishPollutionThreshold", String.valueOf(skirmishPollutionThreshold)));
                skirmishInterval = Double.parseDouble(props.getProperty("skirmishInterval", String.valueOf(skirmishInterval)));
                skirmishMinCount = Integer.parseInt(props.getProperty("skirmishMinCount", String.valueOf(skirmishMinCount)));
                skirmishMaxCount = Integer.parseInt(props.getProperty("skirmishMaxCount", String.valueOf(skirmishMaxCount)));
                majorHordePollutionThreshold = Double.parseDouble(props.getProperty("majorHordePollutionThreshold", String.valueOf(majorHordePollutionThreshold)));
                majorHordeMultiplier = Double.parseDouble(props.getProperty("majorHordeMultiplier", String.valueOf(majorHordeMultiplier)));
                enableMachineTargeting = Boolean.parseBoolean(props.getProperty("enableMachineTargeting", String.valueOf(enableMachineTargeting)));
                machineTargetingRange = Double.parseDouble(props.getProperty("machineTargetingRange", String.valueOf(machineTargetingRange)));
                machineTargetingChance = Double.parseDouble(props.getProperty("machineTargetingChance", String.valueOf(machineTargetingChance)));
                enableVoltageTierScaling = Boolean.parseBoolean(props.getProperty("enableVoltageTierScaling", String.valueOf(enableVoltageTierScaling)));

                // 预设和调试
                difficultyPreset = props.getProperty("difficultyPreset", difficultyPreset);
                enableDebugLines = Boolean.parseBoolean(props.getProperty("enableDebugLines", String.valueOf(enableDebugLines)));
                enableDifficultyLogging = Boolean.parseBoolean(props.getProperty("enableDifficultyLogging", String.valueOf(enableDifficultyLogging)));
                enableSporeDebug = Boolean.parseBoolean(props.getProperty("enableSporeDebug", String.valueOf(enableSporeDebug)));

                // 应用预设（如果不是CUSTOM）
                if (!difficultyPreset.equalsIgnoreCase("CUSTOM")) {
                    applyPreset(difficultyPreset);
                }
            } catch (Exception e) {
                e.printStackTrace();
            }
        } else {
            // First run, apply default preset
            applyPreset(difficultyPreset);
            save();
        }
    }

    /**
     * Saves the current configuration to the properties file.
     * <p>
     * The saved file includes extensive comments explaining each parameter.
     * This method is called automatically after loading if the file doesn't exist,
     * or can be called manually to persist configuration changes.
     */
    public static void save() {
        Properties props = new Properties();

        // 三轴权重
        props.setProperty("weightTime", String.valueOf(weightTime));
        props.setProperty("weightVoltage", String.valueOf(weightVoltage));
        props.setProperty("weightPollution", String.valueOf(weightPollution));

        // 全局系数
        props.setProperty("globalMultiplier", String.valueOf(globalMultiplier));
        props.setProperty("emaAlpha", String.valueOf(emaAlpha));
        props.setProperty("pollutionDenominator", String.valueOf(pollutionDenominator));

        // 工业加成
        props.setProperty("techWeight", String.valueOf(techWeight));
        props.setProperty("pollutionWeight", String.valueOf(pollutionWeight));
        props.setProperty("maxIndustrialTier", String.valueOf(maxIndustrialTier));

        // 扫描参数
        props.setProperty("scanRadiusBlocks", String.valueOf(scanRadiusBlocks));
        props.setProperty("maxGTTier", String.valueOf(maxGTTier));
        props.setProperty("maxChangePerSec", String.valueOf(maxChangePerSec));

        // 时间曲线
        props.setProperty("targetDays", String.valueOf(targetDays));
        props.setProperty("baseDays", String.valueOf(baseDays));

        // 属性增幅
        props.setProperty("hpMultFactor", String.valueOf(hpMultFactor));
        props.setProperty("attackMultFactor", String.valueOf(attackMultFactor));
        props.setProperty("speedMultFactor", String.valueOf(speedMultFactor));
        props.setProperty("armorMultFactor", String.valueOf(armorMultFactor));

        // 电压阶段血量目标
        props.setProperty("ulvHpTarget", String.valueOf(ulvHpTarget));
        props.setProperty("lvHpTarget", String.valueOf(lvHpTarget));
        props.setProperty("mvHpTarget", String.valueOf(mvHpTarget));
        props.setProperty("hvHpTarget", String.valueOf(hvHpTarget));
        props.setProperty("evHpTarget", String.valueOf(evHpTarget));
        props.setProperty("ivHpTarget", String.valueOf(ivHpTarget));
        props.setProperty("luvHpTarget", String.valueOf(luvHpTarget));
        props.setProperty("zpmHpTarget", String.valueOf(zpmHpTarget));
        props.setProperty("uvHpTarget", String.valueOf(uvHpTarget));
        props.setProperty("uhvHpTarget", String.valueOf(uhvHpTarget));

        // GT污染集成
        props.setProperty("gtPollutionWeight", String.valueOf(gtPollutionWeight));
        props.setProperty("gtSourceMultiplierBase", String.valueOf(gtSourceMultiplierBase));
        props.setProperty("gtSourceThreshold", String.valueOf(gtSourceThreshold));
        props.setProperty("airScrubberEfficiency", String.valueOf(airScrubberEfficiency));

        // Hivemind接近系统
        props.setProperty("hivemindProximityRadius", String.valueOf(hivemindProximityRadius));
        props.setProperty("hivemindAccelerationFactor", String.valueOf(hivemindAccelerationFactor));
        props.setProperty("enableHivemindAcceleration", String.valueOf(enableHivemindAcceleration));

        // Hordes集成
        props.setProperty("enableHordeIntegration", String.valueOf(enableHordeIntegration));
        props.setProperty("hordeIntensityMultiplier", String.valueOf(hordeIntensityMultiplier));
        props.setProperty("difficultyToIntensityFactor", String.valueOf(difficultyToIntensityFactor));
        props.setProperty("enablePollutionTriggeredHordes", String.valueOf(enablePollutionTriggeredHordes));
        props.setProperty("pollutionHordeTriggerThreshold", String.valueOf(pollutionHordeTriggerThreshold));
        props.setProperty("pollutionHordeCheckInterval", String.valueOf(pollutionHordeCheckInterval));
        props.setProperty("pollutionHordeTriggerChance", String.valueOf(pollutionHordeTriggerChance));
        props.setProperty("enableSkirmishes", String.valueOf(enableSkirmishes));
        props.setProperty("skirmishPollutionThreshold", String.valueOf(skirmishPollutionThreshold));
        props.setProperty("skirmishInterval", String.valueOf(skirmishInterval));
        props.setProperty("skirmishMinCount", String.valueOf(skirmishMinCount));
        props.setProperty("skirmishMaxCount", String.valueOf(skirmishMaxCount));
        props.setProperty("majorHordePollutionThreshold", String.valueOf(majorHordePollutionThreshold));
        props.setProperty("majorHordeMultiplier", String.valueOf(majorHordeMultiplier));
        props.setProperty("enableMachineTargeting", String.valueOf(enableMachineTargeting));
        props.setProperty("machineTargetingRange", String.valueOf(machineTargetingRange));
        props.setProperty("machineTargetingChance", String.valueOf(machineTargetingChance));
        props.setProperty("enableVoltageTierScaling", String.valueOf(enableVoltageTierScaling));

        // 预设和调试
        props.setProperty("difficultyPreset", difficultyPreset);
        props.setProperty("enableDebugLines", String.valueOf(enableDebugLines));
        props.setProperty("enableDifficultyLogging", String.valueOf(enableDifficultyLogging));
        props.setProperty("enableSporeDebug", String.valueOf(enableSporeDebug));

        try {
            if (!CONFIG_FILE.getParentFile().exists()) {
                CONFIG_FILE.getParentFile().mkdirs();
            }
            try (FileOutputStream out = new FileOutputStream(CONFIG_FILE)) {
                String comments = """
                        ================================================================================
                        Tri-Axis Difficulty System Configuration / 三轴难度系统配置
                        ================================================================================

                        [难度预设 Difficulty Presets] - 推荐使用预设，简单快捷
                        difficultyPreset: NORMAL, HARD, HARDCORE, INSANE, CUSTOM
                          - NORMAL (默认): 适合休闲玩家，难度适中，推荐新手使用
                          - HARD: 适合有经验的玩家，挑战性较高
                          - HARDCORE: 适合硬核玩家，高难度高挑战
                          - INSANE: 适合极限挑战，极高难度
                          - CUSTOM: 使用下方自定义配置，高级用户使用

                        [三轴权重 Tri-Axis Weights] - 控制三个难度轴的影响比例
                        weightTime: 时间因子权重 (0.0-1.0) - 游戏时间越长难度越高
                        weightVoltage: 机器电压权重 (0.0-1.0) - 科技等级越高难度越高
                        weightPollution: 污染权重 (0.0-1.0) - 污染越严重难度越高
                        注意：三个权重之和建议为1.0

                        [全局难度系数 Global Multipliers]
                        globalMultiplier: 全局难度倍率 (1.0-5.0推荐)
                          - 影响最终难度值，越高怪物越强
                        emaAlpha: 平滑系数 (0.01-0.2)
                          - 越小难度变化越平滑，越大响应越快
                        pollutionDenominator: 污染饱和阈值 (500-1500推荐)
                          - 越大需要更多污染才能达到高难度
                          - NORMAL: 1000, HARD: 900, HARDCORE: 800, INSANE: 600

                        [工业加成 Industrial Bonus] - 影响怪物属性的加成系数
                        techWeight: 科技加成权重 (10.0-50.0)
                          - 控制电压等级对怪物强度的影响
                        pollutionWeight: 污染加成权重 (5.0-30.0)
                          - 控制污染对怪物强度的影响
                        maxIndustrialTier: 游戏最大电压等级 (0=ULV, 9=UHV, 14=MAX)
                          - 推荐设置: 9 (UHV) - 适合大多数整合包
                          - 设置为你整合包的最高电压等级

                        [GT污染集成 GT Pollution Integration] - GregTech Modern污染系统集成
                        gtPollutionWeight: GT污染贡献权重 (0.0-2.0)
                          - 控制GT污染对临时污染的影响，默认0.8
                        gtSourceMultiplierBase: 污染源倍率基数 (0.1-1.0)
                          - 控制污染源数量对倍率的影响速度，默认0.5
                        gtSourceThreshold: 污染源倍率触发阈值 (5-20)
                          - 超过此数量的污染源开始应用倍率，默认10
                          - 倍率规则: 11-20源=1.5x, 21-30源=2.0x, 31-50源=3.0x, 51+源=4.0x
                        airScrubberEfficiency: 空气净化器效率 (0.5-2.0)
                          - 控制Air Scrubber清理污染的效率，默认1.0

                        [Hivemind接近系统 Hivemind Proximity System] - Spore虫巢加速系统
                        hivemindProximityRadius: 接近半径(区块) (4-16)
                          - 污染在此距离内接近Hivemind时触发加速，默认8区块(128方块)
                        hivemindAccelerationFactor: 最大加速倍率 (1.0-5.0)
                          - 控制Hivemind对难度增长的加速程度，默认2.0(最高2倍加速)
                          - 加速基于: 距离(越近越强) × 生物质(越多越强) × 污染(越高越强)
                        enableHivemindAcceleration: 启用Hivemind加速 (true/false)
                          - 是否启用污染接近虫巢时的难度加速，默认true

                        [Hordes集成 Hordes Integration] - The Hordes尸潮系统集成
                        enableHordeIntegration: 启用Hordes集成 (true/false)
                          - 是否启用尸潮系统与工业难度的联动，默认true
                        hordeIntensityMultiplier: 全局尸潮强度倍率 (0.5-3.0)
                          - 控制尸潮整体强度，默认1.0
                        difficultyToIntensityFactor: Difficulty转换系数 (0.1-2.0)
                          - 控制ImprovedMobs难度对尸潮强度的影响，默认0.5

                        enablePollutionTriggeredHordes: 启用污染触发尸潮 (true/false)
                          - 高污染区域自动触发尸潮，默认true
                        pollutionHordeTriggerThreshold: 污染触发阈值 (100-300)
                          - 区块污染超过此值可能触发尸潮，默认150
                        pollutionHordeCheckInterval: 检查间隔(tick) (300-1200)
                          - 多久检查一次污染触发，默认600(30秒)
                        pollutionHordeTriggerChance: 触发概率 (0.01-0.2)
                          - 每次检查的触发概率，默认0.05(5%)

                        enableSkirmishes: 启用小股袭扰 (true/false)
                          - 污染区域定期生成小股怪物，默认true
                        skirmishPollutionThreshold: 袭扰污染阈值 (50-150)
                          - 区块污染超过此值可能触发袭扰，默认80
                        skirmishInterval: 袭扰间隔(tick) (600-2400)
                          - 袭扰检查间隔，默认1200(1分钟)
                        skirmishMinCount: 袭扰最小数量 (1-10)
                          - 默认3
                        skirmishMaxCount: 袭扰最大数量 (5-20)
                          - 默认8

                        majorHordePollutionThreshold: 大尸潮污染阈值 (150-300)
                          - 污染超过此值触发大尸潮(2倍强度)，默认200
                        majorHordeMultiplier: 大尸潮强度倍率 (1.5-3.0)
                          - 大尸潮相对普通尸潮的强度，默认2.0

                        enableMachineTargeting: 启用机器攻击 (true/false)
                          - 尸潮僵尸攻击附近机器，默认true
                        machineTargetingRange: 机器检测范围(方块) (16-64)
                          - 僵尸检测机器的距离，默认32
                        machineTargetingChance: 机器攻击概率 (0.1-1.0)
                          - 僵尸攻击机器而非玩家的概率，默认0.3(30%)

                        enableVoltageTierScaling: 启用电压等级缩放 (true/false)
                          - 尸潮强度随玩家电压等级提升，默认true
                          - 倍率: ULV=1.0x, LV=1.1x, MV=1.3x, HV=1.5x, EV=1.8x
                                 IV=2.2x, LuV=2.6x, ZPM=3.0x, UV=3.5x, UHV=4.0x

                        [时间曲线 Time Scaling] - 控制时间对难度的影响
                        targetDays: 达到最高时间难度的MC天数 (300-2000)
                          - NORMAL: 800天, HARD: 1000天, HARDCORE: 1200天, INSANE: 1500天
                        baseDays: 对数缩放基数 (10-50)
                          - 越小前期爬升越快，建议30

                        [属性增幅系数 Attribute Multipliers] - 怪物属性增强倍率
                        hpMultFactor: 血量增幅倍率 (0.5-5.0)
                        attackMultFactor: 伤害增幅倍率 (0.5-3.0)
                        speedMultFactor: 速度增幅倍率 (0.3-2.0)
                        armorMultFactor: 护甲增幅倍率 (0.5-2.0)

                        [电压阶段血量目标 Voltage Tier HP Targets]
                        僵尸基础血量20，这些是目标倍率
                        ulvHpTarget: ULV阶段血量倍率 (推荐1.0 = 20血)
                        lvHpTarget: LV阶段血量倍率 (推荐1.3 = 26血)
                        mvHpTarget: MV阶段血量倍率 (推荐1.8 = 36血)
                        hvHpTarget: HV阶段血量倍率 (推荐2.5 = 50血)
                        evHpTarget: EV阶段血量倍率 (推荐3.5 = 70血)
                        ivHpTarget: IV阶段血量倍率 (推荐4.8 = 96血)
                        luvHpTarget: LuV阶段血量倍率 (推荐6.5 = 130血)
                        zpmHpTarget: ZPM阶段血量倍率 (推荐8.5 = 170血)
                        uvHpTarget: UV阶段血量倍率 (推荐11.0 = 220血)
                        uhvHpTarget: UHV阶段血量倍率 (推荐14.0 = 280血)

                        [扫描参数 Scanning Parameters]
                        scanRadiusBlocks: 扫描机器的半径范围 (32-128)
                          - 影响性能，建议64
                        maxGTTier: GT模组支持的最大等级 (通常14)
                        maxChangePerSec: 难度变化速率限制 (0.005-0.05)
                          - 防止难度突变

                        [调试选项 Debug Options] - 仅用于开发和调试
                        enableDebugLines: 启用调试线渲染 (默认false)
                          - 显示僵尸攻击机器的连线
                        enableDifficultyLogging: 启用难度日志输出 (默认false)
                          - 输出详细的难度计算日志
                        enableSporeDebug: 启用Spore集成调试信息 (默认false)
                          - 输出Spore mod集成的调试信息

                        注意：调试选项可以通过游戏内指令 /im industrial debug on 开启

                        [污染系统说明]
                        - 机器运行会产生污染，污染会吸引怪物攻击机器
                        - 树叶、草、花、水可以净化污染
                        - 污染 >= 50: 僵尸会攻击机器
                        - 污染 >= 80: Creeper会攻击机器
                        - 污染 >= 100: 开始生成污染Creeper
                        - GT污染源(Muffler)会增加临时污染，源越多倍率越高
                        - Air Scrubber可以清理污染
                        - 污染接近Spore Hivemind时会加速难度增长，提升怪物血量

                        [机器数量参考]
                        MV阶段: 20-30台机器
                        HV阶段: 40-60台机器
                        IV-LuV: 100+台机器
                        ZPM-UHV: 200+台机器
                        ================================================================================
                        """;
                props.store(out, comments);
            }
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    /**
     * Checks if GregTech CEu is loaded.
     * <p>
     * This method attempts to load the GTCEu main class to determine if the mod is present.
     *
     * @return true if GregTech CEu is loaded
     */
    public static boolean hasGTCEu() {
        try {
            Class.forName("com.gregtechceu.gtceu.GTCEu");
            return true;
        } catch (ClassNotFoundException e) {
            return false;
        }
    }

    /**
     * Synchronize values from the new IICConfig system to this legacy config.
     * This method is called when the config is loaded or reloaded.
     *
     * @deprecated This class is deprecated in favor of {@link cn.minerealms.iic.core.config.IICConfig}.
     *             This method exists only for backward compatibility.
     * @since 1.0.0
     */
    @Deprecated(forRemoval = true, since = "1.1.0")
    public static void syncFromConfig() {
        cn.minerealms.iic.core.config.IICConfig config = cn.minerealms.iic.core.config.IICConfig.INSTANCE;
        if (config == null) return;

        // Tri-Axis Weights
        weightTime = config.DIFFICULTY.weightTime.get();
        weightVoltage = config.DIFFICULTY.weightVoltage.get();
        weightPollution = config.DIFFICULTY.weightPollution.get();

        // Global Difficulty Multipliers
        globalMultiplier = config.DIFFICULTY.globalMultiplier.get();
        emaAlpha = config.DIFFICULTY.emaAlpha.get();
        pollutionDenominator = config.DIFFICULTY.pollutionDenominator.get();

        // Industrial Bonus Configuration
        techWeight = config.DIFFICULTY.techWeight.get();
        pollutionWeight = config.DIFFICULTY.pollutionWeight.get();
        maxIndustrialTier = config.DIFFICULTY.maxIndustrialTier.get();

        // Difficulty Contribution Weights
        playerBonusWeight = config.DIFFICULTY.playerBonusWeight.get();
        localPollutionWeight = config.DIFFICULTY.localPollutionWeight.get();
        globalPollutionWeight = config.DIFFICULTY.globalPollutionWeight.get();
        timeFactorWeight = config.DIFFICULTY.timeFactorWeight.get();

        // Time Scaling
        targetDays = config.DIFFICULTY.targetDays.get();
        baseDays = config.DIFFICULTY.baseDays.get();

        // Attribute Multipliers
        hpMultFactor = config.DIFFICULTY.hpMultFactor.get();
        attackMultFactor = config.DIFFICULTY.attackMultFactor.get();
        speedMultFactor = config.DIFFICULTY.speedMultFactor.get();
        armorMultFactor = config.DIFFICULTY.armorMultFactor.get();

        // Voltage Tier HP Targets
        ulvHpTarget = config.DIFFICULTY.ulvHpTarget.get();
        lvHpTarget = config.DIFFICULTY.lvHpTarget.get();
        mvHpTarget = config.DIFFICULTY.mvHpTarget.get();
        hvHpTarget = config.DIFFICULTY.hvHpTarget.get();
        evHpTarget = config.DIFFICULTY.evHpTarget.get();
        ivHpTarget = config.DIFFICULTY.ivHpTarget.get();
        luvHpTarget = config.DIFFICULTY.luvHpTarget.get();
        zpmHpTarget = config.DIFFICULTY.zpmHpTarget.get();
        uvHpTarget = config.DIFFICULTY.uvHpTarget.get();
        uhvHpTarget = config.DIFFICULTY.uhvHpTarget.get();

        // Scanning Parameters
        scanRadiusBlocks = config.DIFFICULTY.scanRadiusBlocks.get();
        maxGTTier = config.DIFFICULTY.maxGTTier.get();
        maxChangePerSec = config.DIFFICULTY.maxChangePerSec.get();

        // Pollution System - map to correct field names
        tempToPermanentThreshold = config.POLLUTION.conversionThreshold.get();
        tempToPermanentRate = config.POLLUTION.conversionRate.get();
        permanentToDifficultyRate = config.POLLUTION.difficultyMultiplier.get();

        // Threat System - map to correct field names
        mvZombieAttackThreshold = config.THREAT.mvZombieAttackThreshold.get();
        hvZombieSpawnThreshold = config.THREAT.hvZombieSpawnThreshold.get();
        hvCreeperSpawnThreshold = config.THREAT.hvCreeperSpawnThreshold.get();
        chargedCreeperThreshold = config.THREAT.chargedCreeperThreshold.get();
        zombieSpawnChance = config.THREAT.zombieSpawnChance.get();
        creeperSpawnChance = config.THREAT.creeperSpawnChance.get();
        chargedCreeperChance = config.THREAT.chargedCreeperChance.get();

        // Horde Integration
        enableHordeIntegration = config.HORDE.enableHordeIntegration.get();
        hordeIntensityMultiplier = config.HORDE.hordeIntensityMultiplier.get();
        difficultyToIntensityFactor = config.HORDE.difficultyToIntensityFactor.get();
        enablePollutionTriggeredHordes = config.HORDE.enablePollutionTriggeredHordes.get();
        pollutionHordeTriggerThreshold = config.HORDE.pollutionHordeTriggerThreshold.get();
        pollutionHordeCheckInterval = config.HORDE.pollutionHordeCheckInterval.get();
        pollutionHordeTriggerChance = config.HORDE.pollutionHordeTriggerChance.get();
        enableSkirmishes = config.HORDE.enableSkirmishes.get();
        skirmishPollutionThreshold = config.HORDE.skirmishPollutionThreshold.get();
        skirmishInterval = config.HORDE.skirmishInterval.get();
        skirmishMinCount = config.HORDE.skirmishMinCount.get();
        skirmishMaxCount = config.HORDE.skirmishMaxCount.get();
        majorHordePollutionThreshold = config.HORDE.majorHordePollutionThreshold.get();
        majorHordeMultiplier = config.HORDE.majorHordeMultiplier.get();
        enableMachineTargeting = config.HORDE.enableMachineTargeting.get();
        machineTargetingRange = config.HORDE.machineTargetingRange.get();
        machineTargetingChance = config.HORDE.machineTargetingChance.get();
        enableVoltageTierScaling = config.HORDE.enableVoltageTierScaling.get();

        // Integration
        gtPollutionWeight = config.INTEGRATION.gtPollutionWeight.get();
        gtSourceMultiplierBase = config.INTEGRATION.gtSourceMultiplierBase.get();
        gtSourceThreshold = config.INTEGRATION.gtSourceThreshold.get();
        airScrubberEfficiency = config.INTEGRATION.airScrubberEfficiency.get();
        enableHivemindAcceleration = config.INTEGRATION.enableHivemindAcceleration.get();
        hivemindProximityRadius = config.INTEGRATION.hivemindProximityRadius.get();
        hivemindAccelerationFactor = config.INTEGRATION.hivemindAccelerationFactor.get();

        // Debug Options
        enableDebugLines = config.DEBUG.enableDebugLines.get();
        enableDifficultyLogging = config.DEBUG.enableDifficultyLogging.get();
        enableSporeDebug = config.DEBUG.enableSporeDebug.get();
    }

    /**
     * Synchronize values from this legacy config to the new IICConfig system.
     * This method is called when values are changed via commands.
     *
     * @deprecated This class is deprecated in favor of {@link cn.minerealms.iic.core.config.IICConfig}.
     *             This method exists only for backward compatibility.
     */
    @Deprecated(forRemoval = true, since = "1.1.0")
    public static void syncToConfig() {
        cn.minerealms.iic.core.config.IICConfig config = cn.minerealms.iic.core.config.IICConfig.INSTANCE;
        if (config == null) return;

        // Tri-Axis Weights
        config.DIFFICULTY.weightTime.set(weightTime);
        config.DIFFICULTY.weightVoltage.set(weightVoltage);
        config.DIFFICULTY.weightPollution.set(weightPollution);

        // Global Difficulty Multipliers
        config.DIFFICULTY.globalMultiplier.set(globalMultiplier);
        config.DIFFICULTY.emaAlpha.set(emaAlpha);
        config.DIFFICULTY.pollutionDenominator.set(pollutionDenominator);

        // Industrial Bonus Configuration
        config.DIFFICULTY.techWeight.set(techWeight);
        config.DIFFICULTY.pollutionWeight.set(pollutionWeight);
        config.DIFFICULTY.maxIndustrialTier.set(maxIndustrialTier);

        // Difficulty Contribution Weights
        config.DIFFICULTY.playerBonusWeight.set(playerBonusWeight);
        config.DIFFICULTY.localPollutionWeight.set(localPollutionWeight);
        config.DIFFICULTY.globalPollutionWeight.set(globalPollutionWeight);
        config.DIFFICULTY.timeFactorWeight.set(timeFactorWeight);

        // Time Scaling
        config.DIFFICULTY.targetDays.set(targetDays);
        config.DIFFICULTY.baseDays.set(baseDays);

        // Attribute Multipliers
        config.DIFFICULTY.hpMultFactor.set(hpMultFactor);
        config.DIFFICULTY.attackMultFactor.set(attackMultFactor);
        config.DIFFICULTY.speedMultFactor.set(speedMultFactor);
        config.DIFFICULTY.armorMultFactor.set(armorMultFactor);

        // Voltage Tier HP Targets
        config.DIFFICULTY.ulvHpTarget.set(ulvHpTarget);
        config.DIFFICULTY.lvHpTarget.set(lvHpTarget);
        config.DIFFICULTY.mvHpTarget.set(mvHpTarget);
        config.DIFFICULTY.hvHpTarget.set(hvHpTarget);
        config.DIFFICULTY.evHpTarget.set(evHpTarget);
        config.DIFFICULTY.ivHpTarget.set(ivHpTarget);
        config.DIFFICULTY.luvHpTarget.set(luvHpTarget);
        config.DIFFICULTY.zpmHpTarget.set(zpmHpTarget);
        config.DIFFICULTY.uvHpTarget.set(uvHpTarget);
        config.DIFFICULTY.uhvHpTarget.set(uhvHpTarget);

        // Scanning Parameters
        config.DIFFICULTY.scanRadiusBlocks.set(scanRadiusBlocks);
        config.DIFFICULTY.maxGTTier.set(maxGTTier);
        config.DIFFICULTY.maxChangePerSec.set(maxChangePerSec);

        // Pollution System - map to correct field names
        config.POLLUTION.conversionThreshold.set(tempToPermanentThreshold);
        config.POLLUTION.conversionRate.set(tempToPermanentRate);
        config.POLLUTION.difficultyMultiplier.set(permanentToDifficultyRate);

        // Threat System - map to correct field names
        config.THREAT.mvZombieAttackThreshold.set(mvZombieAttackThreshold);
        config.THREAT.hvZombieSpawnThreshold.set(hvZombieSpawnThreshold);
        config.THREAT.hvCreeperSpawnThreshold.set(hvCreeperSpawnThreshold);
        config.THREAT.chargedCreeperThreshold.set(chargedCreeperThreshold);
        config.THREAT.zombieSpawnChance.set(zombieSpawnChance);
        config.THREAT.creeperSpawnChance.set(creeperSpawnChance);
        config.THREAT.chargedCreeperChance.set(chargedCreeperChance);

        // Horde Integration
        config.HORDE.enableHordeIntegration.set(enableHordeIntegration);
        config.HORDE.hordeIntensityMultiplier.set(hordeIntensityMultiplier);
        config.HORDE.difficultyToIntensityFactor.set(difficultyToIntensityFactor);
        config.HORDE.enablePollutionTriggeredHordes.set(enablePollutionTriggeredHordes);
        config.HORDE.pollutionHordeTriggerThreshold.set(pollutionHordeTriggerThreshold);
        config.HORDE.pollutionHordeCheckInterval.set(pollutionHordeCheckInterval);
        config.HORDE.pollutionHordeTriggerChance.set(pollutionHordeTriggerChance);
        config.HORDE.enableSkirmishes.set(enableSkirmishes);
        config.HORDE.skirmishPollutionThreshold.set(skirmishPollutionThreshold);
        config.HORDE.skirmishInterval.set(skirmishInterval);
        config.HORDE.skirmishMinCount.set(skirmishMinCount);
        config.HORDE.skirmishMaxCount.set(skirmishMaxCount);
        config.HORDE.majorHordePollutionThreshold.set(majorHordePollutionThreshold);
        config.HORDE.majorHordeMultiplier.set(majorHordeMultiplier);
        config.HORDE.enableMachineTargeting.set(enableMachineTargeting);
        config.HORDE.machineTargetingRange.set(machineTargetingRange);
        config.HORDE.machineTargetingChance.set(machineTargetingChance);
        config.HORDE.enableVoltageTierScaling.set(enableVoltageTierScaling);

        // Integration
        config.INTEGRATION.gtPollutionWeight.set(gtPollutionWeight);
        config.INTEGRATION.gtSourceMultiplierBase.set(gtSourceMultiplierBase);
        config.INTEGRATION.gtSourceThreshold.set(gtSourceThreshold);
        config.INTEGRATION.airScrubberEfficiency.set(airScrubberEfficiency);
        config.INTEGRATION.enableHivemindAcceleration.set(enableHivemindAcceleration);
        config.INTEGRATION.hivemindProximityRadius.set(hivemindProximityRadius);
        config.INTEGRATION.hivemindAccelerationFactor.set(hivemindAccelerationFactor);

        // Debug Options
        config.DEBUG.enableDebugLines.set(enableDebugLines);
        config.DEBUG.enableDifficultyLogging.set(enableDifficultyLogging);
        config.DEBUG.enableSporeDebug.set(enableSporeDebug);

        // Save the config
        cn.minerealms.iic.core.config.IICConfig.SPEC.save();
    }
}
