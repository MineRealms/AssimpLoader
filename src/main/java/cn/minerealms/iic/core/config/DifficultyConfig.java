package cn.minerealms.iic.core.config;

import net.minecraftforge.common.ForgeConfigSpec;

/**
 * Difficulty calculation configuration section.
 */
public class DifficultyConfig {
    // Tri-axis weights
    public final ForgeConfigSpec.DoubleValue weightTime;
    public final ForgeConfigSpec.DoubleValue weightVoltage;
    public final ForgeConfigSpec.DoubleValue weightPollution;

    // Global multipliers
    public final ForgeConfigSpec.DoubleValue globalMultiplier;
    public final ForgeConfigSpec.DoubleValue emaAlpha;
    public final ForgeConfigSpec.DoubleValue pollutionDenominator;

    // Industrial bonus
    public final ForgeConfigSpec.DoubleValue techWeight;
    public final ForgeConfigSpec.DoubleValue pollutionWeight;
    public final ForgeConfigSpec.IntValue maxIndustrialTier;

    // Contribution weights
    public final ForgeConfigSpec.DoubleValue playerBonusWeight;
    public final ForgeConfigSpec.DoubleValue localPollutionWeight;
    public final ForgeConfigSpec.DoubleValue globalPollutionWeight;
    public final ForgeConfigSpec.DoubleValue timeFactorWeight;

    // Time scaling
    public final ForgeConfigSpec.DoubleValue targetDays;
    public final ForgeConfigSpec.DoubleValue baseDays;

    // Attribute multipliers
    public final ForgeConfigSpec.DoubleValue hpMultFactor;
    public final ForgeConfigSpec.DoubleValue attackMultFactor;
    public final ForgeConfigSpec.DoubleValue speedMultFactor;
    public final ForgeConfigSpec.DoubleValue armorMultFactor;

    // Voltage tier HP targets
    public final ForgeConfigSpec.DoubleValue ulvHpTarget;
    public final ForgeConfigSpec.DoubleValue lvHpTarget;
    public final ForgeConfigSpec.DoubleValue mvHpTarget;
    public final ForgeConfigSpec.DoubleValue hvHpTarget;
    public final ForgeConfigSpec.DoubleValue evHpTarget;
    public final ForgeConfigSpec.DoubleValue ivHpTarget;
    public final ForgeConfigSpec.DoubleValue luvHpTarget;
    public final ForgeConfigSpec.DoubleValue zpmHpTarget;
    public final ForgeConfigSpec.DoubleValue uvHpTarget;
    public final ForgeConfigSpec.DoubleValue uhvHpTarget;

    // Scanning
    public final ForgeConfigSpec.IntValue scanRadiusBlocks;
    public final ForgeConfigSpec.IntValue maxGTTier;
    public final ForgeConfigSpec.DoubleValue maxChangePerSec;

    // Preset
    public final ForgeConfigSpec.EnumValue<DifficultyPreset> preset;

    public DifficultyConfig(ForgeConfigSpec.Builder builder) {
        builder.comment("Difficulty Calculation Configuration")
                .push("difficulty");

        builder.comment("Tri-Axis Weights - Controls influence of time, voltage, and pollution")
                .push("tri_axis");

        weightTime = builder
                .comment("Time factor weight (0.0-1.0) - Higher values make game time more influential")
                .defineInRange("weight_time", 0.35, 0.0, 1.0);

        weightVoltage = builder
                .comment("Voltage/technology factor weight (0.0-1.0) - Higher values make machine tiers more influential")
                .defineInRange("weight_voltage", 0.35, 0.0, 1.0);

        weightPollution = builder
                .comment("Pollution factor weight (0.0-1.0) - Higher values make pollution more influential")
                .defineInRange("weight_pollution", 0.30, 0.0, 1.0);

        builder.pop();

        builder.comment("Global Multipliers")
                .push("global");

        globalMultiplier = builder
                .comment("Global difficulty multiplier (1.0-5.0 recommended) - Higher values make mobs stronger overall")
                .defineInRange("global_multiplier", 2.8, 0.1, 10.0);

        emaAlpha = builder
                .comment("Exponential moving average alpha for smoothing (0.01-0.2) - Lower = smoother, higher = faster response")
                .defineInRange("ema_alpha", 0.05, 0.01, 1.0);

        pollutionDenominator = builder
                .comment("Pollution saturation threshold (500-1500 recommended) - Higher values require more pollution for high difficulty")
                .defineInRange("pollution_denominator", 800.0, 100.0, 5000.0);

        builder.pop();

        builder.comment("Industrial Bonus Configuration")
                .push("industrial");

        techWeight = builder
                .comment("Technology weight (10.0-50.0) - Controls how much voltage tier affects mob strength")
                .defineInRange("tech_weight", 30.0, 0.0, 100.0);

        pollutionWeight = builder
                .comment("Pollution weight (5.0-30.0) - Controls how much pollution affects mob strength")
                .defineInRange("pollution_weight", 18.0, 0.0, 100.0);

        maxIndustrialTier = builder
                .comment("Maximum industrial voltage tier (0=ULV, 9=UHV, 14=MAX) - Set to highest tier in your modpack")
                .defineInRange("max_industrial_tier", 9, 0, 14);

        builder.pop();

        builder.comment("Difficulty Contribution Weights")
                .push("contribution");

        playerBonusWeight = builder
                .comment("Weight for player industrial bonus")
                .defineInRange("player_bonus_weight", 1.0, 0.0, 10.0);

        localPollutionWeight = builder
                .comment("Weight for local temporary pollution")
                .defineInRange("local_pollution_weight", 0.5, 0.0, 10.0);

        globalPollutionWeight = builder
                .comment("Weight for global permanent pollution")
                .defineInRange("global_pollution_weight", 0.3, 0.0, 10.0);

        timeFactorWeight = builder
                .comment("Weight for time factor")
                .defineInRange("time_factor_weight", 0.2, 0.0, 10.0);

        builder.pop();

        builder.comment("Time Scaling Parameters")
                .push("time");

        targetDays = builder
                .comment("Target MC days to reach peak time difficulty (300-2000)")
                .defineInRange("target_days", 1200.0, 100.0, 5000.0);

        baseDays = builder
                .comment("Base days for logarithmic scaling (10-50) - Lower values make early game ramp up faster")
                .defineInRange("base_days", 30.0, 1.0, 100.0);

        builder.pop();

        builder.comment("Attribute Multipliers - Mob attribute enhancement factors")
                .push("attributes");

        hpMultFactor = builder
                .comment("Health multiplier factor (0.5-5.0)")
                .defineInRange("hp_mult_factor", 2.2, 0.1, 10.0);

        attackMultFactor = builder
                .comment("Attack damage multiplier factor (0.5-3.0)")
                .defineInRange("attack_mult_factor", 1.6, 0.1, 10.0);

        speedMultFactor = builder
                .comment("Speed multiplier factor (0.3-2.0)")
                .defineInRange("speed_mult_factor", 0.8, 0.1, 5.0);

        armorMultFactor = builder
                .comment("Armor multiplier factor (0.5-2.0)")
                .defineInRange("armor_mult_factor", 1.2, 0.1, 5.0);

        builder.pop();

        builder.comment("Voltage Tier HP Targets - Base zombie health is 20")
                .push("hp_targets");

        ulvHpTarget = builder.comment("ULV (Tier 0) HP multiplier").defineInRange("ulv", 1.0, 0.5, 20.0);
        lvHpTarget = builder.comment("LV (Tier 1) HP multiplier").defineInRange("lv", 1.3, 0.5, 20.0);
        mvHpTarget = builder.comment("MV (Tier 2) HP multiplier").defineInRange("mv", 1.8, 0.5, 20.0);
        hvHpTarget = builder.comment("HV (Tier 3) HP multiplier").defineInRange("hv", 2.5, 0.5, 20.0);
        evHpTarget = builder.comment("EV (Tier 4) HP multiplier").defineInRange("ev", 3.5, 0.5, 20.0);
        ivHpTarget = builder.comment("IV (Tier 5) HP multiplier").defineInRange("iv", 4.8, 0.5, 20.0);
        luvHpTarget = builder.comment("LuV (Tier 6) HP multiplier").defineInRange("luv", 6.5, 0.5, 20.0);
        zpmHpTarget = builder.comment("ZPM (Tier 7) HP multiplier").defineInRange("zpm", 8.5, 0.5, 20.0);
        uvHpTarget = builder.comment("UV (Tier 8) HP multiplier").defineInRange("uv", 11.0, 0.5, 20.0);
        uhvHpTarget = builder.comment("UHV (Tier 9) HP multiplier").defineInRange("uhv", 14.0, 0.5, 20.0);

        builder.pop();

        builder.comment("Scanning Parameters")
                .push("scanning");

        scanRadiusBlocks = builder
                .comment("Machine scanning radius in blocks (32-128) - Larger values may impact performance")
                .defineInRange("scan_radius_blocks", 64, 16, 256);

        maxGTTier = builder
                .comment("Maximum GT voltage tier supported (usually 14)")
                .defineInRange("max_gt_tier", 14, 0, 20);

        maxChangePerSec = builder
                .comment("Maximum difficulty change per second to prevent sudden spikes")
                .defineInRange("max_change_per_sec", 0.015, 0.001, 1.0);

        builder.pop();

        preset = builder
                .comment("Difficulty preset: NORMAL, HARD, HARDCORE, INSANE, CUSTOM",
                        "CUSTOM uses values from config file without applying preset")
                .defineEnum("preset", DifficultyPreset.NORMAL);

        builder.pop();
    }

    public enum DifficultyPreset {
        NORMAL,
        HARD,
        HARDCORE,
        INSANE,
        CUSTOM
    }
}
