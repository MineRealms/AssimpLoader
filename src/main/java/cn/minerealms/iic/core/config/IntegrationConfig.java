package cn.minerealms.iic.core.config;

import net.minecraftforge.common.ForgeConfigSpec;

/**
 * Mod integration configuration section.
 */
public class IntegrationConfig {
    // GregTech integration
    public final ForgeConfigSpec.DoubleValue gtPollutionWeight;
    public final ForgeConfigSpec.DoubleValue gtSourceMultiplierBase;
    public final ForgeConfigSpec.IntValue gtSourceThreshold;
    public final ForgeConfigSpec.DoubleValue airScrubberEfficiency;

    // Spore integration
    public final ForgeConfigSpec.BooleanValue enableHivemindAcceleration;
    public final ForgeConfigSpec.DoubleValue hivemindProximityRadius;
    public final ForgeConfigSpec.DoubleValue hivemindAccelerationFactor;

    public IntegrationConfig(ForgeConfigSpec.Builder builder) {
        builder.comment("Mod Integration Configuration")
                .push("integration");

        builder.comment("GregTech Modern Integration")
                .push("gregtech");

        gtPollutionWeight = builder
                .comment("GT pollution contribution weight (0.0-2.0) - Controls how much GT pollution affects temporary pollution")
                .defineInRange("pollution_weight", 0.8, 0.0, 5.0);

        gtSourceMultiplierBase = builder
                .comment("Base multiplier per 10 sources above threshold (0.1-1.0)")
                .defineInRange("source_multiplier_base", 0.5, 0.0, 2.0);

        gtSourceThreshold = builder
                .comment("Threshold for GT pollution source multiplier activation")
                .defineInRange("source_threshold", 10, 0, 100);

        airScrubberEfficiency = builder
                .comment("Air Scrubber cleaning efficiency multiplier (0.5-2.0)")
                .defineInRange("air_scrubber_efficiency", 1.0, 0.1, 5.0);

        builder.pop();

        builder.comment("Spore Integration - Hivemind proximity system")
                .push("spore");

        enableHivemindAcceleration = builder
                .comment("Enable Hivemind proximity acceleration system")
                .define("enable_hivemind_acceleration", true);

        hivemindProximityRadius = builder
                .comment("Proximity radius in chunks for Hivemind acceleration (4-16)")
                .defineInRange("proximity_radius", 8.0, 1.0, 32.0);

        hivemindAccelerationFactor = builder
                .comment("Maximum acceleration factor for Hivemind proximity (1.0-5.0)")
                .defineInRange("acceleration_factor", 2.0, 1.0, 10.0);

        builder.pop();
        builder.pop();
    }
}
