package cn.minerealms.iic.commands;

import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.arguments.DoubleArgumentType;
import com.mojang.brigadier.arguments.IntegerArgumentType;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.context.CommandContext;
import cn.minerealms.iic.industrial.HordeIntegrationManager;
import cn.minerealms.iic.industrial.HordeManager;
import cn.minerealms.iic.industrial.TriAxisConfig;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;

/**
 * Hordes集成指令
 * /im hordes status - 查看尸潮状态
 * /im hordes test <difficulty> <pollution> <tier> - 测试尸潮生成
 * /im hordes config <key> <value> - 配置尸潮参数
 * /im hordes reload - 重载配置
 * /im hordes reset - 重置冷却
 */
public class HordesCommands {

    // 电压等级枚举
    private static final String[] VOLTAGE_TIERS = {
        "ULV", "LV", "MV", "HV", "EV", "IV", "LuV", "ZPM", "UV", "UHV", "UEV", "UIV", "UXV", "OpV", "MAX"
    };

    public static void register(CommandDispatcher<CommandSourceStack> dispatcher) {
        dispatcher.register(Commands.literal("im")
                .then(Commands.literal("hordes").requires(src -> src.hasPermission(2))
                        // 查看状态
                        .then(Commands.literal("status").executes(HordesCommands::showStatus))

                        // 测试尸潮生成
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
                                                        .executes(HordesCommands::testHorde)))))

                        // 配置参数
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

                                .then(Commands.literal("difficultyFactor")
                                        .then(Commands.argument("value", DoubleArgumentType.doubleArg(0.1, 2.0))
                                                .executes(ctx -> setDouble(ctx, "difficultyToIntensityFactor", DoubleArgumentType.getDouble(ctx, "value")))))

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
                                                .executes(ctx -> setBoolean(ctx, "enableMachineTargeting", StringArgumentType.getString(ctx, "value")))))

                                .then(Commands.literal("machineTargetingChance")
                                        .then(Commands.argument("value", DoubleArgumentType.doubleArg(0.1, 1.0))
                                                .executes(ctx -> setDouble(ctx, "machineTargetingChance", DoubleArgumentType.getDouble(ctx, "value"))))))

                        // 重载配置
                        .then(Commands.literal("reload").executes(HordesCommands::reloadConfig))

                        // 重置冷却
                        .then(Commands.literal("reset").executes(HordesCommands::resetCooldowns)))
        );
    }

    /**
     * 显示尸潮状态
     */
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

    /**
     * 测试尸潮生成
     */
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

        // 解析电压等级
        int tier = parseTier(tierName);
        if (tier < 0) {
            ctx.getSource().sendFailure(Component.literal("§cInvalid tier: " + tierName));
            return 0;
        }

        // 限制到最大等级
        int maxTier = TriAxisConfig.maxIndustrialTier;
        if (tier > maxTier) {
            tier = maxTier;
            ctx.getSource().sendSuccess(() ->
                Component.literal(String.format("§e⚠ Tier capped to max configured tier: %s (Tier %d)",
                    getTierName(maxTier), maxTier)), false);
        }

        // 计算尸潮参数
        boolean isMajorHorde = pollution >= TriAxisConfig.majorHordePollutionThreshold;
        double intensityMultiplier = isMajorHorde ? TriAxisConfig.majorHordeMultiplier : 1.0;
        double tierMultiplier = getTierIntensityMultiplier(tier);
        double difficultyBonus = difficulty * TriAxisConfig.difficultyToIntensityFactor;

        // 计算最终参数
        int baseDuration = HordeManager.getSpawnDuration();
        int baseAmount = HordeManager.getSpawnAmount();

        int duration = (int) (baseDuration * intensityMultiplier * tierMultiplier * (1.0 + difficultyBonus));
        int amount = (int) (baseAmount * intensityMultiplier * tierMultiplier * (1.0 + difficultyBonus));

        // 更新配置
        HordeManager.setSpawnDuration(duration);
        HordeManager.setSpawnAmount(amount);

        // 触发尸潮
        boolean success = HordeManager.startHorde(player, duration, true);

        if (success) {
            final int finalTier = tier;
            final String finalTierName = tierName;
            final boolean finalIsMajorHorde = isMajorHorde;
            final int finalDuration = duration;
            final int finalAmount = amount;
            final double finalIntensityMultiplier = intensityMultiplier;
            final double finalTierMultiplier = tierMultiplier;
            final double finalDifficultyBonus = difficultyBonus;

            ctx.getSource().sendSuccess(() ->
                Component.literal("§a§l[TEST HORDE SPAWNED]"), true);
            ctx.getSource().sendSuccess(() ->
                Component.literal(String.format("§7Parameters: difficulty=§e%.1f§7, pollution=§e%.1f§7, tier=§e%s§7 (Tier %d)",
                    difficulty, pollution, finalTierName, finalTier)), false);
            ctx.getSource().sendSuccess(() ->
                Component.literal(String.format("§7Type: §e%s§7, Duration: §e%d ticks§7, Amount: §e%d mobs",
                    finalIsMajorHorde ? "MAJOR HORDE" : "Normal Horde", finalDuration, finalAmount)), false);
            ctx.getSource().sendSuccess(() ->
                Component.literal(String.format("§7Multipliers: intensity=§e%.2fx§7, tier=§e%.2fx§7, difficulty=§e+%.2f",
                    finalIntensityMultiplier, finalTierMultiplier, finalDifficultyBonus)), false);
        } else {
            ctx.getSource().sendFailure(Component.literal("§cFailed to spawn test horde!"));
        }

        return success ? 1 : 0;
    }

    /**
     * 解析电压等级名称
     */
    private static int parseTier(String tierName) {
        for (int i = 0; i < VOLTAGE_TIERS.length; i++) {
            if (VOLTAGE_TIERS[i].equalsIgnoreCase(tierName)) {
                return i;
            }
        }
        return -1;
    }

    /**
     * 获取电压等级名称
     */
    private static String getTierName(int tier) {
        if (tier >= 0 && tier < VOLTAGE_TIERS.length) {
            return VOLTAGE_TIERS[tier];
        }
        return "UNKNOWN";
    }

    /**
     * 获取电压等级强度倍率
     */
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

    /**
     * 设置布尔值配置
     */
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

        TriAxisConfig.save();

        ctx.getSource().sendSuccess(() ->
            Component.literal(String.format("§a✓ %s set to: §e%s", key, value ? "ENABLED" : "DISABLED")), true);
        return 1;
    }

    /**
     * 设置浮点数配置
     */
    private static int setDouble(CommandContext<CommandSourceStack> ctx, String key, double value) {
        switch (key) {
            case "hordeIntensityMultiplier" -> {
                TriAxisConfig.hordeIntensityMultiplier = value;
                HordeIntegrationManager.hordeIntensityMultiplier = value;
            }
            case "difficultyToIntensityFactor" -> {
                TriAxisConfig.difficultyToIntensityFactor = value;
                HordeIntegrationManager.difficultyToIntensityFactor = value;
            }
            case "pollutionHordeTriggerThreshold" -> {
                TriAxisConfig.pollutionHordeTriggerThreshold = value;
                HordeIntegrationManager.pollutionHordeTriggerThreshold = value;
            }
            case "machineTargetingChance" -> {
                TriAxisConfig.machineTargetingChance = value;
                HordeIntegrationManager.machineTargetingChance = value;
            }
        }

        TriAxisConfig.save();

        ctx.getSource().sendSuccess(() ->
            Component.literal(String.format("§a✓ %s set to: §e%.3f", key, value)), true);
        return 1;
    }

    /**
     * 重载配置
     */
    private static int reloadConfig(CommandContext<CommandSourceStack> ctx) {
        TriAxisConfig.load();

        // 同步到 HordeIntegrationManager
        HordeIntegrationManager.enableHordeIntegration = TriAxisConfig.enableHordeIntegration;
        HordeIntegrationManager.hordeIntensityMultiplier = TriAxisConfig.hordeIntensityMultiplier;
        HordeIntegrationManager.difficultyToIntensityFactor = TriAxisConfig.difficultyToIntensityFactor;
        HordeIntegrationManager.enablePollutionTriggeredHordes = TriAxisConfig.enablePollutionTriggeredHordes;
        HordeIntegrationManager.pollutionHordeTriggerThreshold = TriAxisConfig.pollutionHordeTriggerThreshold;
        HordeIntegrationManager.pollutionHordeCheckInterval = TriAxisConfig.pollutionHordeCheckInterval;
        HordeIntegrationManager.pollutionHordeTriggerChance = TriAxisConfig.pollutionHordeTriggerChance;
        HordeIntegrationManager.enableSkirmishes = TriAxisConfig.enableSkirmishes;
        HordeIntegrationManager.skirmishPollutionThreshold = TriAxisConfig.skirmishPollutionThreshold;
        HordeIntegrationManager.skirmishInterval = TriAxisConfig.skirmishInterval;
        HordeIntegrationManager.skirmishMinCount = TriAxisConfig.skirmishMinCount;
        HordeIntegrationManager.skirmishMaxCount = TriAxisConfig.skirmishMaxCount;
        HordeIntegrationManager.majorHordePollutionThreshold = TriAxisConfig.majorHordePollutionThreshold;
        HordeIntegrationManager.majorHordeMultiplier = TriAxisConfig.majorHordeMultiplier;
        HordeIntegrationManager.enableMachineTargeting = TriAxisConfig.enableMachineTargeting;
        HordeIntegrationManager.machineTargetingRange = TriAxisConfig.machineTargetingRange;
        HordeIntegrationManager.machineTargetingChance = TriAxisConfig.machineTargetingChance;
        HordeIntegrationManager.enableVoltageTierScaling = TriAxisConfig.enableVoltageTierScaling;

        ctx.getSource().sendSuccess(() ->
            Component.literal("§a✓ Hordes configuration reloaded successfully!"), true);
        return 1;
    }

    /**
     * 重置冷却
     */
    private static int resetCooldowns(CommandContext<CommandSourceStack> ctx) {
        HordeIntegrationManager.resetCooldowns();

        ctx.getSource().sendSuccess(() ->
            Component.literal("§a✓ All horde cooldowns have been reset!"), true);
        return 1;
    }
}
