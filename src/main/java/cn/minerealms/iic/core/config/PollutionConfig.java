package cn.minerealms.iic.core.config;

import net.minecraftforge.common.ForgeConfigSpec;

/**
 * Pollution system configuration section.
 */
public class PollutionConfig {
    // Temporary pollution
    public final ForgeConfigSpec.DoubleValue decayRate;
    public final ForgeConfigSpec.DoubleValue spreadRate;
    public final ForgeConfigSpec.DoubleValue absorptionRate;
    public final ForgeConfigSpec.DoubleValue maxPerChunk;

    // Permanent pollution
    public final ForgeConfigSpec.DoubleValue conversionThreshold;
    public final ForgeConfigSpec.DoubleValue conversionRate;
    public final ForgeConfigSpec.DoubleValue difficultyMultiplier;

    public PollutionConfig(ForgeConfigSpec.Builder builder) {
        builder.comment("Pollution System Configuration")
                .push("pollution");

        builder.comment("Temporary Pollution Settings - Chunk-based pollution that decays over time")
                .push("temporary");

        decayRate = builder
                .comment("Decay rate per second (0.0-1.0)")
                .defineInRange("decay_rate", 0.1, 0.0, 1.0);

        spreadRate = builder
                .comment("Spread rate to adjacent chunks (0.0-1.0)")
                .defineInRange("spread_rate", 0.05, 0.0, 1.0);

        absorptionRate = builder
                .comment("Absorption rate by environment (trees, grass, water) (0.0-1.0)")
                .defineInRange("absorption_rate", 0.02, 0.0, 1.0);

        maxPerChunk = builder
                .comment("Maximum temporary pollution per chunk")
                .defineInRange("max_per_chunk", 500.0, 0.0, 10000.0);

        builder.pop();

        builder.comment("Permanent Pollution Settings - Global pollution that affects difficulty")
                .push("permanent");

        conversionThreshold = builder
                .comment("Threshold for converting temporary to permanent pollution")
                .defineInRange("conversion_threshold", 200.0, 0.0, 1000.0);

        conversionRate = builder
                .comment("Conversion rate from temporary to permanent per second (0.0-1.0)")
                .defineInRange("conversion_rate", 0.001, 0.0, 1.0);

        difficultyMultiplier = builder
                .comment("Multiplier for converting permanent pollution to difficulty")
                .defineInRange("difficulty_multiplier", 0.3, 0.0, 10.0);

        builder.pop();
        builder.pop();
    }
}
