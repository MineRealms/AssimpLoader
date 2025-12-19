package cn.minerealms.iic.command;

import com.mojang.brigadier.arguments.DoubleArgumentType;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import com.mojang.brigadier.context.CommandContext;
import cn.minerealms.iic.industrial.HordeIntegrationManager;
import cn.minerealms.iic.industrial.HordeManager;
import cn.minerealms.iic.industrial.TriAxisConfig;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;

/**
 * Horde integration commands.
 * /im horde status - Show horde status
 * /im horde test <difficulty> <pollution> <tier> - Test horde spawning
 * /im horde config <key> <value> - Configure horde parameters
 * /im horde reload - Reload configuration
 * /im horde reset - Reset cooldowns
 */
public class HordeCommand {

    private static final String[] VOLTAGE_TIERS = {
        "ULV", "LV", "MV", "HV", "EV", "IV", "LuV", "ZPM", "UV", "UHV", "UEV", "UIV", "UXV", "OpV", "MAX"
    };

    public static LiteralArgumentBuilder<CommandSourceStack> register() {
        return Commands.literal("horde").requires(src -> src.hasPermission(2))
                // Show status
                .then(Commands.literal("status").executes(HordeCommand::showStatus))

                // Test horde spawning
                .then(Commands.literal("test")
                        .then(Commands.argument("difficulty", DoubleArgumentType.doubleArg(0.0, 100.0))
                                .then(Commands.argument("pollution", DoubleArgumentType.doubleArg(0.0, 1000.0))
                                        .then(Commands.argument("tier", StringArgumentType.word())
                                                .suggests((ctx, builder) -> {
                                                    for (String tier : VOLTAGE_TIERS) {
                                                        builder.suggest(tier);
                                                    }
                                                    return builder.buildFuture();
                                                })
                                                .executes(HordeCommand::testHorde)))))

                // Configuration
                .then(Commands.literal("config")
                        .then(Commands.literal("enable")
                                .then(Commands.argument("value", StringArgumentType.word())
                                        .suggests((ctx, builder) -> {
                                            builder.suggest("true");
                                            builder.suggest("false");
                                            return builder.buildFuture();
                                        })
                                        .executes(ctx -> setBoolean(ctx, "enableHordeIntegration", StringArgumentType.getString(ctx, "value")))))
                        .then(Commands.literal("intensity")
                                .then(Commands.argument("value", DoubleArgumentType.doubleArg(0.5, 3.0))
                                        .executes(ctx -> setDouble(ctx, "hordeIntensityMultiplier", DoubleArgumentType.getDouble(ctx, "value")))))
                        .then(Commands.literal("pollutionTrigger")
                                .then(Commands.argument("value", StringArgumentType.word())
                                        .suggests((ctx, builder) -> {
                                            builder.suggest("true");
                                            builder.suggest("false");
                                            return builder.buildFuture();
                                        })
                                        .executes(ctx -> setBoolean(ctx, "enablePollutionTriggeredHordes", StringArgumentType.getString(ctx, "value")))))
                        .then(Commands.literal("pollutionThreshold")
                                .then(Commands.argument("value", DoubleArgumentType.doubleArg(100.0, 300.0))
                                        .executes(ctx -> setDouble(ctx, "pollutionHordeTriggerThreshold", DoubleArgumentType.getDouble(ctx, "value")))))
                        .then(Commands.literal("skirmishes")
                                .then(Commands.argument("value", StringArgumentType.word())
                                        .suggests((ctx, builder) -> {
                                            builder.suggest("true");
                                            builder.suggest("false");
                                            return builder.buildFuture();
                                        })
                                        .executes(ctx -> setBoolean(ctx, "enableSkirmishes", StringArgumentType.getString(ctx, "value")))))
                        .then(Commands.literal("machineTargeting")
                                .then(Commands.argument("value", StringArgumentType.word())
                                        .suggests((ctx, builder) -> {
                                            builder.suggest("true");
                                            builder.suggest("false");
                                            return builder.buildFuture();
                                        })
                                        .executes(ctx -> setBoolean(ctx, "enableMachineTargeting", StringArgumentType.getString(ctx, "value"))))))

                // Reload configuration
                .then(Commands.literal("reload").executes(HordeCommand::reloadConfig))

                // Reset cooldowns
                .then(Commands.literal("reset").executes(HordeCommand::resetCooldowns))

                // Default: show status
                .executes(HordeCommand::showStatus);
    }

    private static int showStatus(CommandContext<CommandSourceStack> ctx) {
        if (!(ctx.getSource().getEntity() instanceof ServerPlayer player)) {
            ctx.getSource().sendFailure(Component.literal("§cThis command can only be used by players!"));
            return 0;
        }

        if (!HordeManager.isHordesLoaded()) {
            ctx.getSource().sendFailure(Component.literal("§c[IIC-Hordes] The Hordes mod is not loaded!"));
            return 0;
        }

        String debugInfo = HordeIntegrationManager.getDebugInfo(player);
        for (String line : debugInfo.split("\n")) {
            ctx.getSource().sendSuccess(() -> Component.literal("§7" + line), false);
        }

        return 1;
    }

    private static int testHorde(CommandContext<CommandSourceStack> ctx) {
        if (!(ctx.getSource().getEntity() instanceof ServerPlayer player)) {
            ctx.getSource().sendFailure(Component.literal("§cThis command can only be used by players!"));
            return 0;
        }

        if (!HordeManager.isHordesLoaded()) {
            ctx.getSource().sendFailure(Component.literal("§c[IIC-Hordes] The Hordes mod is not loaded!"));
            return 0;
        }

        double difficulty = DoubleArgumentType.getDouble(ctx, "difficulty");
        double pollution = DoubleArgumentType.getDouble(ctx, "pollution");
        String tierName = StringArgumentType.getString(ctx, "tier").toUpperCase();

        // Parse tier
        int tier = parseTier(tierName);
        if (tier < 0) {
            ctx.getSource().sendFailure(Component.literal("§cInvalid tier: " + tierName));
            return 0;
        }

        // Cap to max tier
        int maxTier = TriAxisConfig.maxIndustrialTier;
        if (tier > maxTier) {
            tier = maxTier;
            final int finalTier = tier;
            ctx.getSource().sendSuccess(() ->
                Component.literal(String.format("§e⚠ Tier capped to max: %s (Tier %d)",
                    getTierName(finalTier), finalTier)), false);
        }

        // Calculate horde parameters
        boolean isMajorHorde = pollution >= TriAxisConfig.majorHordePollutionThreshold;
        double intensityMultiplier = isMajorHorde ? TriAxisConfig.majorHordeMultiplier : 1.0;
        double tierMultiplier = getTierIntensityMultiplier(tier);
        double difficultyBonus = difficulty * TriAxisConfig.difficultyToIntensityFactor;

        // Calculate final parameters
        int baseDuration = HordeManager.getSpawnDuration();
        int baseAmount = HordeManager.getSpawnAmount();

        int duration = (int) (baseDuration * intensityMultiplier * tierMultiplier * (1.0 + difficultyBonus));
        int amount = (int) (baseAmount * intensityMultiplier * tierMultiplier * (1.0 + difficultyBonus));

        // Update config
        HordeManager.setSpawnDuration(duration);
        HordeManager.setSpawnAmount(amount);

        // Trigger horde
        boolean success = HordeManager.startHorde(player, duration, true);

        if (success) {
            final int finalTier = tier;
            ctx.getSource().sendSuccess(() ->
                Component.literal("§a§l[TEST HORDE SPAWNED]"), true);
            ctx.getSource().sendSuccess(() ->
                Component.literal(String.format("§7Parameters: difficulty=§e%.1f§7, pollution=§e%.1f§7, tier=§e%s§7 (T%d)",
                    difficulty, pollution, tierName, finalTier)), false);
            ctx.getSource().sendSuccess(() ->
                Component.literal(String.format("§7Type: §e%s§7, Duration: §e%d ticks§7, Amount: §e%d mobs",
                    isMajorHorde ? "MAJOR HORDE" : "Normal Horde", duration, amount)), false);
        } else {
            ctx.getSource().sendFailure(Component.literal("§cFailed to spawn test horde!"));
        }

        return success ? 1 : 0;
    }

    private static int parseTier(String tierName) {
        for (int i = 0; i < VOLTAGE_TIERS.length; i++) {
            if (VOLTAGE_TIERS[i].equalsIgnoreCase(tierName)) {
                return i;
            }
        }
        return -1;
    }

    private static String getTierName(int tier) {
        if (tier >= 0 && tier < VOLTAGE_TIERS.length) {
            return VOLTAGE_TIERS[tier];
        }
        return "UNKNOWN";
    }

    private static double getTierIntensityMultiplier(int tier) {
        if (!TriAxisConfig.enableVoltageTierScaling) {
            return 1.0;
        }

        double[] multipliers = HordeIntegrationManager.tierIntensityMultipliers;
        if (tier < 0 || tier >= multipliers.length) {
            return multipliers[multipliers.length - 1];
        }

        return multipliers[tier];
    }

    private static int setBoolean(CommandContext<CommandSourceStack> ctx, String key, String valueStr) {
        boolean value = Boolean.parseBoolean(valueStr);

        switch (key) {
            case "enableHordeIntegration" -> {
                TriAxisConfig.enableHordeIntegration = value;
                HordeIntegrationManager.enableHordeIntegration = value;
            }
            case "enablePollutionTriggeredHordes" -> {
                TriAxisConfig.enablePollutionTriggeredHordes = value;
                HordeIntegrationManager.enablePollutionTriggeredHordes = value;
            }
            case "enableSkirmishes" -> {
                TriAxisConfig.enableSkirmishes = value;
                HordeIntegrationManager.enableSkirmishes = value;
            }
            case "enableMachineTargeting" -> {
                TriAxisConfig.enableMachineTargeting = value;
                HordeIntegrationManager.enableMachineTargeting = value;
            }
        }

        TriAxisConfig.syncToConfig();

        ctx.getSource().sendSuccess(() ->
            Component.literal(String.format("§a✓ %s set to: §e%s", key, value ? "ENABLED" : "DISABLED")), true);
        return 1;
    }

    private static int setDouble(CommandContext<CommandSourceStack> ctx, String key, double value) {
        switch (key) {
            case "hordeIntensityMultiplier" -> {
                TriAxisConfig.hordeIntensityMultiplier = value;
                HordeIntegrationManager.hordeIntensityMultiplier = value;
            }
            case "pollutionHordeTriggerThreshold" -> {
                TriAxisConfig.pollutionHordeTriggerThreshold = value;
                HordeIntegrationManager.pollutionHordeTriggerThreshold = value;
            }
        }

        TriAxisConfig.syncToConfig();

        ctx.getSource().sendSuccess(() ->
            Component.literal(String.format("§a✓ %s set to: §e%.3f", key, value)), true);
        return 1;
    }

    private static int reloadConfig(CommandContext<CommandSourceStack> ctx) {
        cn.minerealms.iic.core.config.ConfigManager.reload();

        // Sync to HordeIntegrationManager
        HordeIntegrationManager.enableHordeIntegration = TriAxisConfig.enableHordeIntegration;
        HordeIntegrationManager.hordeIntensityMultiplier = TriAxisConfig.hordeIntensityMultiplier;
        HordeIntegrationManager.enablePollutionTriggeredHordes = TriAxisConfig.enablePollutionTriggeredHordes;
        HordeIntegrationManager.pollutionHordeTriggerThreshold = TriAxisConfig.pollutionHordeTriggerThreshold;
        HordeIntegrationManager.enableSkirmishes = TriAxisConfig.enableSkirmishes;
        HordeIntegrationManager.enableMachineTargeting = TriAxisConfig.enableMachineTargeting;

        ctx.getSource().sendSuccess(() ->
            Component.literal("§a✓ Horde configuration reloaded successfully!"), true);
        return 1;
    }

    private static int resetCooldowns(CommandContext<CommandSourceStack> ctx) {
        HordeIntegrationManager.resetCooldowns();

        ctx.getSource().sendSuccess(() ->
            Component.literal("§a✓ All horde cooldowns have been reset!"), true);
        return 1;
    }
}
