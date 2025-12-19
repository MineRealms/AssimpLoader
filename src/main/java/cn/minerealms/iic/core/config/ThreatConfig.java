package cn.minerealms.iic.core.config;

import net.minecraftforge.common.ForgeConfigSpec;

/**
 * Threat system configuration section.
 */
public class ThreatConfig {
    // Thresholds
    public final ForgeConfigSpec.DoubleValue mvZombieAttackThreshold;
    public final ForgeConfigSpec.DoubleValue hvZombieSpawnThreshold;
    public final ForgeConfigSpec.DoubleValue hvCreeperSpawnThreshold;
    public final ForgeConfigSpec.DoubleValue chargedCreeperThreshold;

    // Spawn chances
    public final ForgeConfigSpec.DoubleValue zombieSpawnChance;
    public final ForgeConfigSpec.DoubleValue creeperSpawnChance;
    public final ForgeConfigSpec.DoubleValue chargedCreeperChance;

    public ThreatConfig(ForgeConfigSpec.Builder builder) {
        builder.comment("Threat System Configuration - Pollution-triggered mob threats")
                .push("threat");

        builder.comment("Pollution Thresholds for Threat Activation")
                .push("thresholds");

        mvZombieAttackThreshold = builder
                .comment("MV stage: Pollution threshold for zombies to attack machines")
                .defineInRange("mv_zombie_attack", 50.0, 0.0, 1000.0);

        hvZombieSpawnThreshold = builder
                .comment("HV stage: Pollution threshold for active zombie spawning")
                .defineInRange("hv_zombie_spawn", 80.0, 0.0, 1000.0);

        hvCreeperSpawnThreshold = builder
                .comment("HV stage: Pollution threshold for active creeper spawning")
                .defineInRange("hv_creeper_spawn", 120.0, 0.0, 1000.0);

        chargedCreeperThreshold = builder
                .comment("Pollution threshold for charged creeper spawning")
                .defineInRange("charged_creeper", 200.0, 0.0, 1000.0);

        builder.pop();

        builder.comment("Spawn Probabilities (per second)")
                .push("spawn_chances");

        zombieSpawnChance = builder
                .comment("Zombie spawn chance per second (0.0-1.0)")
                .defineInRange("zombie", 0.01, 0.0, 1.0);

        creeperSpawnChance = builder
                .comment("Creeper spawn chance per second (0.0-1.0)")
                .defineInRange("creeper", 0.005, 0.0, 1.0);

        chargedCreeperChance = builder
                .comment("Charged creeper spawn chance per second (0.0-1.0)")
                .defineInRange("charged_creeper", 0.001, 0.0, 1.0);

        builder.pop();
        builder.pop();
    }
}
