package cn.minerealms.iic.core.config;

import net.minecraftforge.common.ForgeConfigSpec;

/**
 * Main configuration class for Integrated Industrial Craft.
 * Uses Forge's ConfigSpec system for TOML-based configuration.
 */
public class IICConfig {
    public static final ForgeConfigSpec SPEC;
    public static final IICConfig INSTANCE;

    // Config sections
    public static final PollutionConfig POLLUTION;
    public static final DifficultyConfig DIFFICULTY;
    public static final ThreatConfig THREAT;
    public static final HordeConfig HORDE;
    public static final IntegrationConfig INTEGRATION;
    public static final DebugConfig DEBUG;

    static {
        ForgeConfigSpec.Builder builder = new ForgeConfigSpec.Builder();

        POLLUTION = new PollutionConfig(builder);
        DIFFICULTY = new DifficultyConfig(builder);
        THREAT = new ThreatConfig(builder);
        HORDE = new HordeConfig(builder);
        INTEGRATION = new IntegrationConfig(builder);
        DEBUG = new DebugConfig(builder);

        SPEC = builder.build();
        INSTANCE = new IICConfig();
    }
}
