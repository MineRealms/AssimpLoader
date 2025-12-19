package cn.minerealms.iic.command;

import com.mojang.brigadier.arguments.DoubleArgumentType;
import com.mojang.brigadier.arguments.IntegerArgumentType;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import com.mojang.brigadier.context.CommandContext;
import cn.minerealms.iic.core.config.ConfigManager;
import cn.minerealms.iic.core.config.IICConfig;
import cn.minerealms.iic.industrial.TriAxisConfig;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.network.chat.Component;

/**
 * Configuration management commands.
 * /im config reload - Reload configuration
 * /im config preset <NORMAL|HARD|HARDCORE|INSANE|CUSTOM> - Set difficulty preset
 * /im config <category> <key> <value> - Set specific config value
 */
public class ConfigCommand {

    public static LiteralArgumentBuilder<CommandSourceStack> register() {
        return Commands.literal("config").requires(src -> src.hasPermission(2))
                // Reload configuration
                .then(Commands.literal("reload").executes(ConfigCommand::reloadConfig))

                // Set difficulty preset
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
                                .executes(ConfigCommand::setPreset)))

                // Global settings
                .then(Commands.literal("global")
                        .then(Commands.literal("multiplier")
                                .then(Commands.argument("value", DoubleArgumentType.doubleArg(0.1, 10.0))
                                        .executes(ctx -> setDouble(ctx, "globalMultiplier", DoubleArgumentType.getDouble(ctx, "value")))))
                        .then(Commands.literal("maxTier")
                                .then(Commands.argument("value", IntegerArgumentType.integer(0, 14))
                                        .executes(ctx -> setInt(ctx, "maxIndustrialTier", IntegerArgumentType.getInteger(ctx, "value"))))))

                // Tri-axis weights
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

                // Attribute multipliers
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

                // Industrial bonus
                .then(Commands.literal("industrial")
                        .then(Commands.literal("tech")
                                .then(Commands.argument("value", DoubleArgumentType.doubleArg(0.0, 100.0))
                                        .executes(ctx -> setDouble(ctx, "techWeight", DoubleArgumentType.getDouble(ctx, "value")))))
                        .then(Commands.literal("pollution")
                                .then(Commands.argument("value", DoubleArgumentType.doubleArg(0.0, 100.0))
                                        .executes(ctx -> setDouble(ctx, "pollutionWeight", DoubleArgumentType.getDouble(ctx, "value"))))))

                // Time scaling
                .then(Commands.literal("time")
                        .then(Commands.literal("target")
                                .then(Commands.argument("days", DoubleArgumentType.doubleArg(100.0, 5000.0))
                                        .executes(ctx -> setDouble(ctx, "targetDays", DoubleArgumentType.getDouble(ctx, "days")))))
                        .then(Commands.literal("base")
                                .then(Commands.argument("days", DoubleArgumentType.doubleArg(1.0, 100.0))
                                        .executes(ctx -> setDouble(ctx, "baseDays", DoubleArgumentType.getDouble(ctx, "days"))))))

                // GregTech integration
                .then(Commands.literal("gt")
                        .then(Commands.literal("weight")
                                .then(Commands.argument("value", DoubleArgumentType.doubleArg(0.0, 2.0))
                                        .executes(ctx -> setDouble(ctx, "gtPollutionWeight", DoubleArgumentType.getDouble(ctx, "value")))))
                        .then(Commands.literal("multiplier")
                                .then(Commands.argument("value", DoubleArgumentType.doubleArg(0.1, 1.0))
                                        .executes(ctx -> setDouble(ctx, "gtSourceMultiplierBase", DoubleArgumentType.getDouble(ctx, "value")))))
                        .then(Commands.literal("threshold")
                                .then(Commands.argument("value", IntegerArgumentType.integer(5, 50))
                                        .executes(ctx -> setInt(ctx, "gtSourceThreshold", IntegerArgumentType.getInteger(ctx, "value")))))
                        .then(Commands.literal("scrubber")
                                .then(Commands.argument("value", DoubleArgumentType.doubleArg(0.5, 2.0))
                                        .executes(ctx -> setDouble(ctx, "airScrubberEfficiency", DoubleArgumentType.getDouble(ctx, "value"))))))

                // Hivemind proximity system
                .then(Commands.literal("hivemind")
                        .then(Commands.literal("radius")
                                .then(Commands.argument("chunks", DoubleArgumentType.doubleArg(4.0, 16.0))
                                        .executes(ctx -> setDouble(ctx, "hivemindProximityRadius", DoubleArgumentType.getDouble(ctx, "chunks")))))
                        .then(Commands.literal("acceleration")
                                .then(Commands.argument("value", DoubleArgumentType.doubleArg(1.0, 5.0))
                                        .executes(ctx -> setDouble(ctx, "hivemindAccelerationFactor", DoubleArgumentType.getDouble(ctx, "value")))))
                        .then(Commands.literal("enable")
                                .then(Commands.argument("value", StringArgumentType.word())
                                        .suggests((ctx, builder) -> {
                                            builder.suggest("true");
                                            builder.suggest("false");
                                            return builder.buildFuture();
                                        })
                                        .executes(ctx -> {
                                            boolean value = Boolean.parseBoolean(StringArgumentType.getString(ctx, "value"));
                                            TriAxisConfig.enableHivemindAcceleration = value;
                                            TriAxisConfig.syncToConfig();
                                            ctx.getSource().sendSuccess(() ->
                                                    Component.literal(String.format("§a✓ Hivemind acceleration: §e%s", value ? "ENABLED" : "DISABLED")), true);
                                            return 1;
                                        }))))

                // Show current configuration
                .executes(ConfigCommand::showConfig);
    }

    private static int reloadConfig(CommandContext<CommandSourceStack> ctx) {
        ConfigManager.reload();
        ctx.getSource().sendSuccess(() ->
                Component.literal("§a✓ Configuration reloaded successfully!"), true);
        return 1;
    }

    private static int setPreset(CommandContext<CommandSourceStack> ctx) {
        String presetName = StringArgumentType.getString(ctx, "preset").toUpperCase();

        try {
            var preset = cn.minerealms.iic.core.config.DifficultyConfig.DifficultyPreset.valueOf(presetName);
            IICConfig.DIFFICULTY.preset.set(preset);
            ConfigManager.reload(); // Apply preset

            ctx.getSource().sendSuccess(() ->
                    Component.literal(String.format("§a✓ Difficulty preset set to: §e%s", presetName)), true);
            return 1;
        } catch (IllegalArgumentException e) {
            ctx.getSource().sendFailure(Component.literal("§cInvalid preset: " + presetName));
            return 0;
        }
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
            case "gtPollutionWeight" -> TriAxisConfig.gtPollutionWeight = value;
            case "gtSourceMultiplierBase" -> TriAxisConfig.gtSourceMultiplierBase = value;
            case "airScrubberEfficiency" -> TriAxisConfig.airScrubberEfficiency = value;
            case "hivemindProximityRadius" -> TriAxisConfig.hivemindProximityRadius = value;
            case "hivemindAccelerationFactor" -> TriAxisConfig.hivemindAccelerationFactor = value;
        }
        TriAxisConfig.syncToConfig();

        ctx.getSource().sendSuccess(() ->
                Component.literal(String.format("§a✓ %s set to: §e%.3f", key, value)), true);
        return 1;
    }

    private static int setInt(CommandContext<CommandSourceStack> ctx, String key, int value) {
        switch (key) {
            case "maxIndustrialTier" -> TriAxisConfig.maxIndustrialTier = value;
            case "gtSourceThreshold" -> TriAxisConfig.gtSourceThreshold = value;
        }
        TriAxisConfig.syncToConfig();

        ctx.getSource().sendSuccess(() ->
                Component.literal(String.format("§a✓ %s set to: §e%d", key, value)), true);
        return 1;
    }

    private static int showConfig(CommandContext<CommandSourceStack> ctx) {
        ctx.getSource().sendSuccess(() -> Component.literal("§b§l=== IIC Configuration ==="), false);
        ctx.getSource().sendSuccess(() -> Component.literal(String.format("§6Preset: §e%s", IICConfig.DIFFICULTY.preset.get())), false);
        ctx.getSource().sendSuccess(() -> Component.literal(String.format("§6Global Multiplier: §e%.2f", TriAxisConfig.globalMultiplier)), false);
        ctx.getSource().sendSuccess(() -> Component.literal(String.format("§6Max Tier: §e%d §7(0=ULV, 9=UHV)", TriAxisConfig.maxIndustrialTier)), false);
        ctx.getSource().sendSuccess(() -> Component.literal(""), false);
        ctx.getSource().sendSuccess(() -> Component.literal("§a[Tri-Axis Weights]"), false);
        ctx.getSource().sendSuccess(() -> Component.literal(String.format("  §7Time: §f%.2f", TriAxisConfig.weightTime)), false);
        ctx.getSource().sendSuccess(() -> Component.literal(String.format("  §7Voltage: §f%.2f", TriAxisConfig.weightVoltage)), false);
        ctx.getSource().sendSuccess(() -> Component.literal(String.format("  §7Pollution: §f%.2f", TriAxisConfig.weightPollution)), false);
        ctx.getSource().sendSuccess(() -> Component.literal(""), false);
        ctx.getSource().sendSuccess(() -> Component.literal("§c[Attribute Multipliers]"), false);
        ctx.getSource().sendSuccess(() -> Component.literal(String.format("  §7HP: §f%.2f", TriAxisConfig.hpMultFactor)), false);
        ctx.getSource().sendSuccess(() -> Component.literal(String.format("  §7Attack: §f%.2f", TriAxisConfig.attackMultFactor)), false);
        ctx.getSource().sendSuccess(() -> Component.literal(String.format("  §7Speed: §f%.2f", TriAxisConfig.speedMultFactor)), false);
        ctx.getSource().sendSuccess(() -> Component.literal(String.format("  §7Armor: §f%.2f", TriAxisConfig.armorMultFactor)), false);
        ctx.getSource().sendSuccess(() -> Component.literal(""), false);
        ctx.getSource().sendSuccess(() -> Component.literal("§d[GregTech Integration]"), false);
        ctx.getSource().sendSuccess(() -> Component.literal(String.format("  §7Pollution Weight: §f%.2f", TriAxisConfig.gtPollutionWeight)), false);
        ctx.getSource().sendSuccess(() -> Component.literal(String.format("  §7Source Multiplier: §f%.2f", TriAxisConfig.gtSourceMultiplierBase)), false);
        ctx.getSource().sendSuccess(() -> Component.literal(String.format("  §7Source Threshold: §f%d", TriAxisConfig.gtSourceThreshold)), false);
        ctx.getSource().sendSuccess(() -> Component.literal(String.format("  §7Air Scrubber Efficiency: §f%.2f", TriAxisConfig.airScrubberEfficiency)), false);
        ctx.getSource().sendSuccess(() -> Component.literal(""), false);
        ctx.getSource().sendSuccess(() -> Component.literal("§5[Spore - Hivemind Proximity]"), false);
        ctx.getSource().sendSuccess(() -> Component.literal(String.format("  §7Enabled: §f%s", TriAxisConfig.enableHivemindAcceleration ? "§aYES" : "§cNO")), false);
        ctx.getSource().sendSuccess(() -> Component.literal(String.format("  §7Radius: §f%.1f chunks", TriAxisConfig.hivemindProximityRadius)), false);
        ctx.getSource().sendSuccess(() -> Component.literal(String.format("  §7Acceleration Factor: §f%.2f", TriAxisConfig.hivemindAccelerationFactor)), false);
        ctx.getSource().sendSuccess(() -> Component.literal(""), false);
        ctx.getSource().sendSuccess(() -> Component.literal("§7Use §e/im config reload §7to reload from file"), false);
        return 1;
    }
}
