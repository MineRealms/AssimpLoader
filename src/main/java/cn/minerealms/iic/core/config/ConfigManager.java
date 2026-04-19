package cn.minerealms.iic.core.config;

import cn.minerealms.iic.IntegratedIndustrialCraft;
import net.minecraftforge.fml.event.config.ModConfigEvent;

/**
 * Configuration manager for loading, saving, and reloading configs.
 */
public class ConfigManager {
    private static boolean initialized = false;

    /**
     * Called when config is loaded or reloaded.
     */
    public static void onConfigLoad(ModConfigEvent event) {
        if (!initialized) {
            IntegratedIndustrialCraft.LOGGER.info("IIC configuration loaded");
            initialized = true;
        } else {
            IntegratedIndustrialCraft.LOGGER.info("IIC configuration reloaded");
        }

        // Apply difficulty preset if not CUSTOM
        applyPreset();

        // Sync to legacy TriAxisConfig for backward compatibility
        cn.minerealms.iic.industrial.TriAxisConfig.syncFromConfig();
    }

    /**
     * Reload configuration from file.
     */
    public static void reload() {
        IntegratedIndustrialCraft.LOGGER.info("Reloading IIC configuration...");
        // Forge will automatically reload the config file
        IICConfig.SPEC.save();
        applyPreset();
        cn.minerealms.iic.industrial.TriAxisConfig.syncFromConfig();
        IntegratedIndustrialCraft.LOGGER.info("IIC configuration reloaded successfully");
    }

    /**
     * Apply difficulty preset if not CUSTOM.
     */
    private static void applyPreset() {
        DifficultyConfig.DifficultyPreset preset = IICConfig.DIFFICULTY.preset.get();

        if (preset == DifficultyConfig.DifficultyPreset.CUSTOM) {
            return; // Don't modify values for CUSTOM preset
        }

        IntegratedIndustrialCraft.LOGGER.info("Applying difficulty preset: {}", preset);

        switch (preset) {
            case NORMAL -> {
                IICConfig.DIFFICULTY.globalMultiplier.set(2.0);
                IICConfig.DIFFICULTY.techWeight.set(25.0);
                IICConfig.DIFFICULTY.pollutionWeight.set(15.0);
                IICConfig.DIFFICULTY.hpMultFactor.set(1.5);
                IICConfig.DIFFICULTY.attackMultFactor.set(1.2);
                IICConfig.DIFFICULTY.targetDays.set(800.0);
                IICConfig.DIFFICULTY.pollutionDenominator.set(1000.0);
            }
            case HARD -> {
                IICConfig.DIFFICULTY.globalMultiplier.set(2.5);
                IICConfig.DIFFICULTY.techWeight.set(28.0);
                IICConfig.DIFFICULTY.pollutionWeight.set(17.0);
                IICConfig.DIFFICULTY.hpMultFactor.set(2.0);
                IICConfig.DIFFICULTY.attackMultFactor.set(1.5);
                IICConfig.DIFFICULTY.targetDays.set(1000.0);
                IICConfig.DIFFICULTY.pollutionDenominator.set(900.0);
            }
            case HARDCORE -> {
                IICConfig.DIFFICULTY.globalMultiplier.set(2.8);
                IICConfig.DIFFICULTY.techWeight.set(30.0);
                IICConfig.DIFFICULTY.pollutionWeight.set(18.0);
                IICConfig.DIFFICULTY.hpMultFactor.set(2.2);
                IICConfig.DIFFICULTY.attackMultFactor.set(1.6);
                IICConfig.DIFFICULTY.targetDays.set(1200.0);
                IICConfig.DIFFICULTY.pollutionDenominator.set(800.0);
            }
            case INSANE -> {
                IICConfig.DIFFICULTY.globalMultiplier.set(3.5);
                IICConfig.DIFFICULTY.techWeight.set(35.0);
                IICConfig.DIFFICULTY.pollutionWeight.set(22.0);
                IICConfig.DIFFICULTY.hpMultFactor.set(3.0);
                IICConfig.DIFFICULTY.attackMultFactor.set(2.0);
                IICConfig.DIFFICULTY.targetDays.set(1500.0);
                IICConfig.DIFFICULTY.pollutionDenominator.set(600.0);
            }
        }

        IICConfig.SPEC.save();
    }

    /**
     * Get HP target for a given voltage tier.
     */
    public static double getHpTargetForTier(int tier) {
        return switch (tier) {
            case 0 -> IICConfig.DIFFICULTY.ulvHpTarget.get();
            case 1 -> IICConfig.DIFFICULTY.lvHpTarget.get();
            case 2 -> IICConfig.DIFFICULTY.mvHpTarget.get();
            case 3 -> IICConfig.DIFFICULTY.hvHpTarget.get();
            case 4 -> IICConfig.DIFFICULTY.evHpTarget.get();
            case 5 -> IICConfig.DIFFICULTY.ivHpTarget.get();
            case 6 -> IICConfig.DIFFICULTY.luvHpTarget.get();
            case 7 -> IICConfig.DIFFICULTY.zpmHpTarget.get();
            case 8 -> IICConfig.DIFFICULTY.uvHpTarget.get();
            case 9 -> IICConfig.DIFFICULTY.uhvHpTarget.get();
            default -> IICConfig.DIFFICULTY.uhvHpTarget.get() * (1 + (tier - 9) * 0.3); // Beyond UHV continues to grow
        };
    }

    /**
     * Get effective maximum voltage tier.
     */
    public static int getEffectiveMaxTier() {
        return Math.min(
            IICConfig.DIFFICULTY.maxIndustrialTier.get(),
            IICConfig.DIFFICULTY.maxGTTier.get()
        );
    }
}
