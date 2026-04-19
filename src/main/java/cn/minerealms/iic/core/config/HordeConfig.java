package cn.minerealms.iic.core.config;

import net.minecraftforge.common.ForgeConfigSpec;

/**
 * Horde integration configuration section.
 */
public class HordeConfig {
    // General
    public final ForgeConfigSpec.BooleanValue enableHordeIntegration;
    public final ForgeConfigSpec.DoubleValue hordeIntensityMultiplier;
    public final ForgeConfigSpec.DoubleValue difficultyToIntensityFactor;

    // Pollution-triggered hordes
    public final ForgeConfigSpec.BooleanValue enablePollutionTriggeredHordes;
    public final ForgeConfigSpec.DoubleValue pollutionHordeTriggerThreshold;
    public final ForgeConfigSpec.DoubleValue pollutionHordeCheckInterval;
    public final ForgeConfigSpec.DoubleValue pollutionHordeTriggerChance;

    // Skirmishes
    public final ForgeConfigSpec.BooleanValue enableSkirmishes;
    public final ForgeConfigSpec.DoubleValue skirmishPollutionThreshold;
    public final ForgeConfigSpec.DoubleValue skirmishInterval;
    public final ForgeConfigSpec.IntValue skirmishMinCount;
    public final ForgeConfigSpec.IntValue skirmishMaxCount;

    // Major hordes
    public final ForgeConfigSpec.DoubleValue majorHordePollutionThreshold;
    public final ForgeConfigSpec.DoubleValue majorHordeMultiplier;

    // Machine targeting
    public final ForgeConfigSpec.BooleanValue enableMachineTargeting;
    public final ForgeConfigSpec.DoubleValue machineTargetingRange;
    public final ForgeConfigSpec.DoubleValue machineTargetingChance;

    // Voltage tier scaling
    public final ForgeConfigSpec.BooleanValue enableVoltageTierScaling;

    public HordeConfig(ForgeConfigSpec.Builder builder) {
        builder.comment("Horde Integration Configuration - The Hordes mod integration")
                .push("horde");

        builder.comment("General Horde Settings")
                .push("general");

        enableHordeIntegration = builder
                .comment("Enable The Hordes integration system")
                .define("enable_horde_integration", true);

        hordeIntensityMultiplier = builder
                .comment("Global horde intensity multiplier (0.5-3.0)")
                .defineInRange("intensity_multiplier", 1.0, 0.1, 10.0);

        difficultyToIntensityFactor = builder
                .comment("Difficulty to horde intensity conversion factor (0.1-2.0)")
                .defineInRange("difficulty_to_intensity_factor", 0.5, 0.0, 5.0);

        builder.pop();

        builder.comment("Pollution-Triggered Hordes")
                .push("pollution_triggered");

        enablePollutionTriggeredHordes = builder
                .comment("Enable pollution-triggered hordes")
                .define("enable", true);

        pollutionHordeTriggerThreshold = builder
                .comment("Pollution threshold for triggering hordes (100-300)")
                .defineInRange("trigger_threshold", 150.0, 0.0, 1000.0);

        pollutionHordeCheckInterval = builder
                .comment("Horde check interval in ticks (300-1200)")
                .defineInRange("check_interval", 600.0, 20.0, 6000.0);

        pollutionHordeTriggerChance = builder
                .comment("Horde trigger chance per check (0.01-0.2)")
                .defineInRange("trigger_chance", 0.05, 0.0, 1.0);

        builder.pop();

        builder.comment("Skirmishes - Small mob groups in polluted areas")
                .push("skirmishes");

        enableSkirmishes = builder
                .comment("Enable small skirmishes (mini-hordes)")
                .define("enable", true);

        skirmishPollutionThreshold = builder
                .comment("Pollution threshold for skirmishes (50-150)")
                .defineInRange("pollution_threshold", 80.0, 0.0, 1000.0);

        skirmishInterval = builder
                .comment("Skirmish interval in ticks (600-2400)")
                .defineInRange("interval", 1200.0, 20.0, 6000.0);

        skirmishMinCount = builder
                .comment("Minimum skirmish mob count (1-10)")
                .defineInRange("min_count", 3, 1, 50);

        skirmishMaxCount = builder
                .comment("Maximum skirmish mob count (5-20)")
                .defineInRange("max_count", 8, 1, 100);

        builder.pop();

        builder.comment("Major Hordes - Stronger hordes at high pollution")
                .push("major_hordes");

        majorHordePollutionThreshold = builder
                .comment("Major horde pollution threshold (150-300)")
                .defineInRange("pollution_threshold", 200.0, 0.0, 1000.0);

        majorHordeMultiplier = builder
                .comment("Major horde strength multiplier (1.5-3.0)")
                .defineInRange("strength_multiplier", 2.0, 1.0, 10.0);

        builder.pop();

        builder.comment("Machine Targeting - Horde zombies attack machines")
                .push("machine_targeting");

        enableMachineTargeting = builder
                .comment("Enable machine targeting for horde zombies")
                .define("enable", true);

        machineTargetingRange = builder
                .comment("Machine targeting range in blocks (16-64)")
                .defineInRange("range", 32.0, 8.0, 128.0);

        machineTargetingChance = builder
                .comment("Machine targeting chance (0.1-1.0)")
                .defineInRange("chance", 0.3, 0.0, 1.0);

        builder.pop();

        builder.comment("Voltage Tier Scaling")
                .push("voltage_scaling");

        enableVoltageTierScaling = builder
                .comment("Enable voltage tier scaling for hordes")
                .define("enable", true);

        builder.pop();
        builder.pop();
    }
}
