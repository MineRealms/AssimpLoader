package io.github.flemmli97.improvedmobs.commands;

import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.arguments.FloatArgumentType;
import com.mojang.brigadier.context.CommandContext;
import com.mojang.brigadier.exceptions.CommandSyntaxException;
import io.github.flemmli97.improvedmobs.config.EquipmentList;
import io.github.flemmli97.improvedmobs.difficulty.DifficultyData;
import io.github.flemmli97.improvedmobs.industrial.TriAxisConfig;
import io.github.flemmli97.improvedmobs.mekanism_turrets.LaserDamageConfig;
import net.minecraft.ChatFormatting;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;

/**
 * 重构后的主指令系统
 *
 * 指令结构：
 * /im - 显示帮助
 * /im hud [toggle|on|off] - HUD控制
 * /im config [...] - 配置管理
 * /im difficulty - 查看当前难度
 * /im reload - 重载所有配置
 * /im laser [...] - 激光炮塔配置
 * /im debug [on|off] - 调试模式
 */
public class ImprovedMobsCommand {

    public static void register(CommandDispatcher<CommandSourceStack> dispatcher) {
        // 注册子指令系统
        ConfigCommands.register(dispatcher);
        HudCommands.register(dispatcher);
        SporeDebugCommand.register(dispatcher);  // 注册 Spore 调试命令
        IndustrialDebugCommand.register(dispatcher);  // 注册工业集成调试命令

        // 主指令
        dispatcher.register(Commands.literal("im")
                // 帮助信息
                .executes(ImprovedMobsCommand::showHelp)

                // 查看难度
                .then(Commands.literal("difficulty")
                        .executes(ImprovedMobsCommand::showDifficulty))

                // 重载配置
                .then(Commands.literal("reload").requires(src -> src.hasPermission(2))
                        .executes(ImprovedMobsCommand::reloadAll))

                // 激光炮塔配置
                .then(Commands.literal("laser").requires(src -> src.hasPermission(2))
                        .then(Commands.literal("basic")
                                .then(Commands.argument("damage", FloatArgumentType.floatArg(0.1F, 1000F))
                                        .executes(ctx -> setLaserDamage(ctx, "basic", FloatArgumentType.getFloat(ctx, "damage")))))
                        .then(Commands.literal("advanced")
                                .then(Commands.argument("damage", FloatArgumentType.floatArg(0.1F, 1000F))
                                        .executes(ctx -> setLaserDamage(ctx, "advanced", FloatArgumentType.getFloat(ctx, "damage")))))
                        .then(Commands.literal("elite")
                                .then(Commands.argument("damage", FloatArgumentType.floatArg(0.1F, 1000F))
                                        .executes(ctx -> setLaserDamage(ctx, "elite", FloatArgumentType.getFloat(ctx, "damage")))))
                        .then(Commands.literal("ultimate")
                                .then(Commands.argument("damage", FloatArgumentType.floatArg(0.1F, 1000F))
                                        .executes(ctx -> setLaserDamage(ctx, "ultimate", FloatArgumentType.getFloat(ctx, "damage")))))
                        .executes(ImprovedMobsCommand::showLaserDamage))

                // 调试模式
                .then(Commands.literal("debug").requires(src -> src.hasPermission(2))
                        .then(Commands.literal("on").executes(ctx -> setDebug(ctx, true)))
                        .then(Commands.literal("off").executes(ctx -> setDebug(ctx, false)))
                        .executes(ImprovedMobsCommand::toggleDebug))
        );
    }

    private static int showHelp(CommandContext<CommandSourceStack> ctx) {
        ctx.getSource().sendSuccess(() -> Component.literal("§b§l=== Improved Mobs Commands ==="), false);
        ctx.getSource().sendSuccess(() -> Component.literal("§e/im difficulty §7- Show current difficulty"), false);
        ctx.getSource().sendSuccess(() -> Component.literal("§e/im hud [toggle|on|off] §7- Toggle HUD display"), false);
        ctx.getSource().sendSuccess(() -> Component.literal("§e/im config §7- Manage configuration"), false);
        ctx.getSource().sendSuccess(() -> Component.literal("§e/im reload §7- Reload all configs"), false);
        ctx.getSource().sendSuccess(() -> Component.literal("§e/im laser §7- Laser turret settings"), false);
        ctx.getSource().sendSuccess(() -> Component.literal("§e/im debug §7- Toggle debug mode"), false);
        ctx.getSource().sendSuccess(() -> Component.literal("§e/im industrial §7- Industrial integration debug"), false);
        ctx.getSource().sendSuccess(() -> Component.literal("§e/im spore debug §7- Toggle Spore debug info"), false);
        ctx.getSource().sendSuccess(() -> Component.literal("§e/im spore diagnose §7- Diagnose Spore API"), false);
        ctx.getSource().sendSuccess(() -> Component.literal(""), false);
        ctx.getSource().sendSuccess(() -> Component.literal("§7Use §e/im config §7for detailed config options"), false);
        ctx.getSource().sendSuccess(() -> Component.literal("§7Use §e/im industrial §7for GT/Spore integration debug"), false);
        return 1;
    }

    private static int showDifficulty(CommandContext<CommandSourceStack> ctx) {
        try {
            ServerPlayer player = ctx.getSource().getPlayerOrException();
            float difficulty = DifficultyData.getDifficulty(player.serverLevel(), player);

            ctx.getSource().sendSuccess(() ->
                    Component.literal(String.format("§6Current Difficulty: §e%.3f", difficulty)), false);

            // 显示难度等级
            String level;
            ChatFormatting color;
            if (difficulty < 10) {
                level = "Very Easy";
                color = ChatFormatting.GREEN;
            } else if (difficulty < 30) {
                level = "Easy";
                color = ChatFormatting.DARK_GREEN;
            } else if (difficulty < 60) {
                level = "Normal";
                color = ChatFormatting.YELLOW;
            } else if (difficulty < 100) {
                level = "Hard";
                color = ChatFormatting.GOLD;
            } else if (difficulty < 150) {
                level = "Very Hard";
                color = ChatFormatting.RED;
            } else {
                level = "Extreme";
                color = ChatFormatting.DARK_RED;
            }

            ctx.getSource().sendSuccess(() ->
                    Component.literal(String.format("§7Level: %s%s", color, level)), false);

            return 1;
        } catch (CommandSyntaxException e) {
            ctx.getSource().sendFailure(Component.literal("§cError: Command must be executed by a player"));
            return 0;
        }
    }

    private static int reloadAll(CommandContext<CommandSourceStack> ctx) {
        ctx.getSource().sendSuccess(() -> Component.literal("§6Reloading all configurations..."), false);

        try {
            EquipmentList.initEquip();
            TriAxisConfig.load();

            ctx.getSource().sendSuccess(() -> Component.literal("§a✓ All configurations reloaded successfully!"), true);
            return 1;
        } catch (Exception e) {
            ctx.getSource().sendFailure(Component.literal("§c✗ Error reloading configurations: " + e.getMessage()));
            return 0;
        }
    }

    private static int showLaserDamage(CommandContext<CommandSourceStack> ctx) {
        ctx.getSource().sendSuccess(() -> Component.literal("§b§l=== Laser Turret Damage ==="), false);
        ctx.getSource().sendSuccess(() -> Component.literal(String.format("§aBASIC: §e%.1f §7(%.0f/sec)", LaserDamageConfig.getBasicDamage(), LaserDamageConfig.getBasicDamage() * 20)), false);
        ctx.getSource().sendSuccess(() -> Component.literal(String.format("§bADVANCED: §e%.1f §7(%.0f/sec)", LaserDamageConfig.getAdvancedDamage(), LaserDamageConfig.getAdvancedDamage() * 20)), false);
        ctx.getSource().sendSuccess(() -> Component.literal(String.format("§dELITE: §e%.1f §7(%.0f/sec)", LaserDamageConfig.getEliteDamage(), LaserDamageConfig.getEliteDamage() * 20)), false);
        ctx.getSource().sendSuccess(() -> Component.literal(String.format("§5ULTIMATE: §e%.1f §7(%.0f/sec)", LaserDamageConfig.getUltimateDamage(), LaserDamageConfig.getUltimateDamage() * 20)), false);
        return 1;
    }

    private static int setLaserDamage(CommandContext<CommandSourceStack> ctx, String tier, float damage) {
        switch (tier) {
            case "basic" -> LaserDamageConfig.setBasicDamage(damage);
            case "advanced" -> LaserDamageConfig.setAdvancedDamage(damage);
            case "elite" -> LaserDamageConfig.setEliteDamage(damage);
            case "ultimate" -> LaserDamageConfig.setUltimateDamage(damage);
        }

        String tierColor = switch (tier) {
            case "basic" -> "§a";
            case "advanced" -> "§b";
            case "elite" -> "§d";
            case "ultimate" -> "§5";
            default -> "§f";
        };

        ctx.getSource().sendSuccess(() ->
                Component.literal(String.format("%s%s §6laser damage set to: §e%.1f §7(%.0f/sec)",
                        tierColor, tier.toUpperCase(), damage, damage * 20)), true);
        return 1;
    }

    private static int toggleDebug(CommandContext<CommandSourceStack> ctx) {
        boolean newState = !TriAxisConfig.enableDebugLines;
        return setDebug(ctx, newState);
    }

    private static int setDebug(CommandContext<CommandSourceStack> ctx, boolean enabled) {
        TriAxisConfig.enableDebugLines = enabled;
        TriAxisConfig.enableDifficultyLogging = enabled;
        TriAxisConfig.save();

        String status = enabled ? "§aenabled" : "§cdisabled";
        ctx.getSource().sendSuccess(() ->
                Component.literal(String.format("§6Debug mode %s", status)), true);
        return 1;
    }
}
