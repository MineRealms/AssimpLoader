package io.github.flemmli97.improvedmobs.commands;

import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.arguments.DoubleArgumentType;
import com.mojang.brigadier.arguments.IntegerArgumentType;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.context.CommandContext;
import io.github.flemmli97.improvedmobs.industrial.TriAxisConfig;
import net.minecraft.ChatFormatting;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.network.chat.Component;

/**
 * 配置管理指令
 * /im config <category> <key> <value>
 * /im config reload
 * /im config preset <NORMAL|HARD|HARDCORE|INSANE|CUSTOM>
 */
public class ConfigCommands {

    public static void register(CommandDispatcher<CommandSourceStack> dispatcher) {
        dispatcher.register(Commands.literal("im")
                .then(Commands.literal("config").requires(src -> src.hasPermission(2))
                        // 重载配置
                        .then(Commands.literal("reload").executes(ConfigCommands::reloadConfig))

                        // 设置预设
                        .then(Commands.literal("preset")
                                .then(Commands.argument("preset", StringArgumentType.word())
                                        .suggests((ctx, builder) -> {
                                            builder.suggest("NORMAL");
                                            builder.suggest("HARD");
                                            builder.suggest("HARDCORE");
                                            builder.suggest("INSANE");
                                            builder.suggest("CUSTOM");
                                            return builder.buildFuture();
                                        })
                                        .executes(ConfigCommands::setPreset)))

                        // 全局系数
                        .then(Commands.literal("global")
                                .then(Commands.literal("multiplier")
                                        .then(Commands.argument("value", DoubleArgumentType.doubleArg(0.1, 10.0))
                                                .executes(ctx -> setDouble(ctx, "globalMultiplier", DoubleArgumentType.getDouble(ctx, "value")))))
                                .then(Commands.literal("maxTier")
                                        .then(Commands.argument("value", IntegerArgumentType.integer(0, 14))
                                                .executes(ctx -> setInt(ctx, "maxIndustrialTier", IntegerArgumentType.getInteger(ctx, "value"))))))

                        // 权重配置
                        .then(Commands.literal("weight")
                                .then(Commands.literal("time")
                                        .then(Commands.argument("value", DoubleArgumentType.doubleArg(0.0, 1.0))
                                                .executes(ctx -> setDouble(ctx, "weightTime", DoubleArgumentType.getDouble(ctx, "value")))))
                                .then(Commands.literal("voltage")
                                        .then(Commands.argument("value", DoubleArgumentType.doubleArg(0.0, 1.0))
                                                .executes(ctx -> setDouble(ctx, "weightVoltage", DoubleArgumentType.getDouble(ctx, "value")))))
                                .then(Commands.literal("pollution")
                                        .then(Commands.argument("value", DoubleArgumentType.doubleArg(0.0, 1.0))
                                                .executes(ctx -> setDouble(ctx, "weightPollution", DoubleArgumentType.getDouble(ctx, "value"))))))

                        // 属性增幅
                        .then(Commands.literal("multiplier")
                                .then(Commands.literal("hp")
                                        .then(Commands.argument("value", DoubleArgumentType.doubleArg(0.1, 10.0))
                                                .executes(ctx -> setDouble(ctx, "hpMultFactor", DoubleArgumentType.getDouble(ctx, "value")))))
                                .then(Commands.literal("attack")
                                        .then(Commands.argument("value", DoubleArgumentType.doubleArg(0.1, 5.0))
                                                .executes(ctx -> setDouble(ctx, "attackMultFactor", DoubleArgumentType.getDouble(ctx, "value")))))
                                .then(Commands.literal("speed")
                                        .then(Commands.argument("value", DoubleArgumentType.doubleArg(0.1, 3.0))
                                                .executes(ctx -> setDouble(ctx, "speedMultFactor", DoubleArgumentType.getDouble(ctx, "value")))))
                                .then(Commands.literal("armor")
                                        .then(Commands.argument("value", DoubleArgumentType.doubleArg(0.1, 3.0))
                                                .executes(ctx -> setDouble(ctx, "armorMultFactor", DoubleArgumentType.getDouble(ctx, "value"))))))

                        // 工业加成
                        .then(Commands.literal("industrial")
                                .then(Commands.literal("tech")
                                        .then(Commands.argument("value", DoubleArgumentType.doubleArg(0.0, 100.0))
                                                .executes(ctx -> setDouble(ctx, "techWeight", DoubleArgumentType.getDouble(ctx, "value")))))
                                .then(Commands.literal("pollution")
                                        .then(Commands.argument("value", DoubleArgumentType.doubleArg(0.0, 100.0))
                                                .executes(ctx -> setDouble(ctx, "pollutionWeight", DoubleArgumentType.getDouble(ctx, "value"))))))

                        // 时间曲线
                        .then(Commands.literal("time")
                                .then(Commands.literal("target")
                                        .then(Commands.argument("days", DoubleArgumentType.doubleArg(100.0, 5000.0))
                                                .executes(ctx -> setDouble(ctx, "targetDays", DoubleArgumentType.getDouble(ctx, "days")))))
                                .then(Commands.literal("base")
                                        .then(Commands.argument("days", DoubleArgumentType.doubleArg(1.0, 100.0))
                                                .executes(ctx -> setDouble(ctx, "baseDays", DoubleArgumentType.getDouble(ctx, "days"))))))

                        // 显示当前配置
                        .executes(ConfigCommands::showConfig))
        );
    }

    private static int reloadConfig(CommandContext<CommandSourceStack> ctx) {
        TriAxisConfig.load();
        ctx.getSource().sendSuccess(() ->
                Component.literal("§a✓ Configuration reloaded successfully!"), true);
        return 1;
    }

    private static int setPreset(CommandContext<CommandSourceStack> ctx) {
        String preset = StringArgumentType.getString(ctx, "preset").toUpperCase();
        TriAxisConfig.difficultyPreset = preset;
        TriAxisConfig.applyPreset(preset);
        TriAxisConfig.save();

        ctx.getSource().sendSuccess(() ->
                Component.literal(String.format("§a✓ Difficulty preset set to: §e%s", preset)), true);
        return 1;
    }

    private static int setDouble(CommandContext<CommandSourceStack> ctx, String key, double value) {
        switch (key) {
            case "globalMultiplier" -> TriAxisConfig.globalMultiplier = value;
            case "weightTime" -> TriAxisConfig.weightTime = value;
            case "weightVoltage" -> TriAxisConfig.weightVoltage = value;
            case "weightPollution" -> TriAxisConfig.weightPollution = value;
            case "hpMultFactor" -> TriAxisConfig.hpMultFactor = value;
            case "attackMultFactor" -> TriAxisConfig.attackMultFactor = value;
            case "speedMultFactor" -> TriAxisConfig.speedMultFactor = value;
            case "armorMultFactor" -> TriAxisConfig.armorMultFactor = value;
            case "techWeight" -> TriAxisConfig.techWeight = value;
            case "pollutionWeight" -> TriAxisConfig.pollutionWeight = value;
            case "targetDays" -> TriAxisConfig.targetDays = value;
            case "baseDays" -> TriAxisConfig.baseDays = value;
        }
        TriAxisConfig.save();

        ctx.getSource().sendSuccess(() ->
                Component.literal(String.format("§a✓ %s set to: §e%.3f", key, value)), true);
        return 1;
    }

    private static int setInt(CommandContext<CommandSourceStack> ctx, String key, int value) {
        if (key.equals("maxIndustrialTier")) {
            TriAxisConfig.maxIndustrialTier = value;
        }
        TriAxisConfig.save();

        ctx.getSource().sendSuccess(() ->
                Component.literal(String.format("§a✓ %s set to: §e%d", key, value)), true);
        return 1;
    }

    private static int showConfig(CommandContext<CommandSourceStack> ctx) {
        ctx.getSource().sendSuccess(() -> Component.literal("§b§l=== Tri-Axis Configuration ==="), false);
        ctx.getSource().sendSuccess(() -> Component.literal(String.format("§6Preset: §e%s", TriAxisConfig.difficultyPreset)), false);
        ctx.getSource().sendSuccess(() -> Component.literal(String.format("§6Global Multiplier: §e%.2f", TriAxisConfig.globalMultiplier)), false);
        ctx.getSource().sendSuccess(() -> Component.literal(String.format("§6Max Tier: §e%d §7(0=ULV, 9=UHV)", TriAxisConfig.maxIndustrialTier)), false);
        ctx.getSource().sendSuccess(() -> Component.literal(""), false);
        ctx.getSource().sendSuccess(() -> Component.literal("§a[Weights]"), false);
        ctx.getSource().sendSuccess(() -> Component.literal(String.format("  §7Time: §f%.2f", TriAxisConfig.weightTime)), false);
        ctx.getSource().sendSuccess(() -> Component.literal(String.format("  §7Voltage: §f%.2f", TriAxisConfig.weightVoltage)), false);
        ctx.getSource().sendSuccess(() -> Component.literal(String.format("  §7Pollution: §f%.2f", TriAxisConfig.weightPollution)), false);
        ctx.getSource().sendSuccess(() -> Component.literal(""), false);
        ctx.getSource().sendSuccess(() -> Component.literal("§c[Multipliers]"), false);
        ctx.getSource().sendSuccess(() -> Component.literal(String.format("  §7HP: §f%.2f", TriAxisConfig.hpMultFactor)), false);
        ctx.getSource().sendSuccess(() -> Component.literal(String.format("  §7Attack: §f%.2f", TriAxisConfig.attackMultFactor)), false);
        ctx.getSource().sendSuccess(() -> Component.literal(String.format("  §7Speed: §f%.2f", TriAxisConfig.speedMultFactor)), false);
        ctx.getSource().sendSuccess(() -> Component.literal(String.format("  §7Armor: §f%.2f", TriAxisConfig.armorMultFactor)), false);
        return 1;
    }
}
