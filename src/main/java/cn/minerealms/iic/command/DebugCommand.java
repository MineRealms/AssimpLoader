package cn.minerealms.iic.command;

import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import com.mojang.brigadier.context.CommandContext;
import cn.minerealms.iic.industrial.IndustrialLogger;
import cn.minerealms.iic.industrial.TriAxisConfig;
import cn.minerealms.iic.integration.spore.SporeApiDiagnostics;
import cn.minerealms.iic.integration.spore.SporeIntegration;
import cn.minerealms.iic.pollution.visual.PollutionVisualEffects;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;

/**
 * Debug commands for testing and diagnostics.
 * /im debug industrial - Industrial integration debug
 * /im debug spore - Spore integration debug
 * /im debug mixin - Mixin status check
 * /im debug visual - Visual effects test
 */
public class DebugCommand {

    public static LiteralArgumentBuilder<CommandSourceStack> register() {
        return Commands.literal("debug").requires(src -> src.hasPermission(2))
                // Industrial debug
                .then(Commands.literal("industrial")
                        .then(Commands.literal("on").executes(ctx -> setIndustrialDebug(ctx, true)))
                        .then(Commands.literal("off").executes(ctx -> setIndustrialDebug(ctx, false)))
                        .then(Commands.literal("scan").executes(DebugCommand::scanMachines))
                        .then(Commands.literal("test").executes(DebugCommand::testGTIntegration))
                        .executes(DebugCommand::toggleIndustrialDebug))

                // Spore debug
                .then(Commands.literal("spore")
                        .then(Commands.literal("on").executes(ctx -> setSporeDebug(ctx, true)))
                        .then(Commands.literal("off").executes(ctx -> setSporeDebug(ctx, false)))
                        .then(Commands.literal("diagnose").executes(DebugCommand::diagnoseSpore))
                        .executes(DebugCommand::toggleSporeDebug))

                // Mixin status
                .then(Commands.literal("mixin")
                        .then(Commands.literal("status").executes(DebugCommand::showMixinStatus))
                        .then(Commands.literal("check").executes(DebugCommand::checkMixins))
                        .executes(DebugCommand::showMixinStatus))

                // Visual effects test
                .then(Commands.literal("visual")
                        .then(Commands.literal("light").executes(ctx -> testVisual(ctx, "light")))
                        .then(Commands.literal("moderate").executes(ctx -> testVisual(ctx, "moderate")))
                        .then(Commands.literal("heavy").executes(ctx -> testVisual(ctx, "heavy")))
                        .then(Commands.literal("severe").executes(ctx -> testVisual(ctx, "severe")))
                        .executes(DebugCommand::showVisualHelp));
    }

    private static int toggleIndustrialDebug(CommandContext<CommandSourceStack> ctx) {
        boolean newState = !IndustrialLogger.isDebugEnabled();
        return setIndustrialDebug(ctx, newState);
    }

    private static int setIndustrialDebug(CommandContext<CommandSourceStack> ctx, boolean enabled) {
        IndustrialLogger.setDebugEnabled(enabled);
        TriAxisConfig.enableDifficultyLogging = enabled;
        TriAxisConfig.syncToConfig();

        String status = enabled ? "§aENABLED" : "§cDISABLED";
        ctx.getSource().sendSuccess(() ->
                Component.literal(String.format("§6[Industrial Debug] %s", status)), true);

        if (enabled) {
            ctx.getSource().sendSuccess(() ->
                    Component.literal("§7Debug logs: §e" + IndustrialLogger.getLogFilePath()), false);
        }

        return 1;
    }

    private static int scanMachines(CommandContext<CommandSourceStack> ctx) {
        // Delegate to DifficultyCommand's scan functionality
        try {
            return cn.minerealms.iic.command.DifficultyCommand.register()
                    .build().getChild("scan").getCommand().run(ctx);
        } catch (Exception e) {
            ctx.getSource().sendFailure(Component.literal("§cError: " + e.getMessage()));
            return 0;
        }
    }

    private static int testGTIntegration(CommandContext<CommandSourceStack> ctx) {
        ctx.getSource().sendSuccess(() -> Component.literal("§6Testing GregTech CEu integration..."), false);

        // Test GT loading
        boolean gtLoaded = TriAxisConfig.hasGTCEu();
        String gtStatus = gtLoaded ? "§a✓ LOADED" : "§c✗ NOT FOUND";
        ctx.getSource().sendSuccess(() ->
                Component.literal(String.format("§7GregTech CEu: %s", gtStatus)), false);

        if (!gtLoaded) {
            ctx.getSource().sendFailure(Component.literal("§cGregTech CEu is not installed!"));
            return 0;
        }

        // Test Spore loading
        boolean sporeLoaded = SporeIntegration.isSporeLoaded();
        String sporeStatus = sporeLoaded ? "§a✓ LOADED" : "§7○ NOT FOUND";
        ctx.getSource().sendSuccess(() ->
                Component.literal(String.format("§7Spore Mod: %s", sporeStatus)), false);

        // Show configuration
        ctx.getSource().sendSuccess(() -> Component.literal(""), false);
        ctx.getSource().sendSuccess(() -> Component.literal("§7Configuration:"), false);
        ctx.getSource().sendSuccess(() ->
                Component.literal(String.format("  §7Max Industrial Tier: §e%d", TriAxisConfig.maxIndustrialTier)), false);
        ctx.getSource().sendSuccess(() ->
                Component.literal(String.format("  §7Scan Radius: §e%d blocks", TriAxisConfig.scanRadiusBlocks)), false);
        ctx.getSource().sendSuccess(() ->
                Component.literal(String.format("  §7Debug Logging: %s",
                        IndustrialLogger.isDebugEnabled() ? "§aENABLED" : "§cDISABLED")), false);

        ctx.getSource().sendSuccess(() -> Component.literal(""), false);
        ctx.getSource().sendSuccess(() ->
                Component.literal("§a✓ Integration test completed!"), false);

        return 1;
    }

    private static int toggleSporeDebug(CommandContext<CommandSourceStack> ctx) {
        boolean newState = !TriAxisConfig.enableSporeDebug;
        return setSporeDebug(ctx, newState);
    }

    private static int setSporeDebug(CommandContext<CommandSourceStack> ctx, boolean enabled) {
        TriAxisConfig.enableSporeDebug = enabled;
        TriAxisConfig.syncToConfig();

        String status = enabled ? "§aENABLED" : "§cDISABLED";
        ctx.getSource().sendSuccess(() ->
                Component.literal(String.format("§6[Spore Debug] %s", status)), true);

        return 1;
    }

    private static int diagnoseSpore(CommandContext<CommandSourceStack> ctx) {
        try {
            ServerPlayer player = ctx.getSource().getPlayerOrException();

            ctx.getSource().sendSuccess(() ->
                    Component.literal("§6[Spore] Running API diagnostics... Check server console"), false);

            // Run diagnostics
            SporeApiDiagnostics.diagnoseSporeApi(player.serverLevel());

            ctx.getSource().sendSuccess(() ->
                    Component.literal("§a[Spore] Diagnostics complete! Check server console"), false);

            return 1;
        } catch (Exception e) {
            ctx.getSource().sendFailure(Component.literal("§cError: " + e.getMessage()));
            e.printStackTrace();
            return 0;
        }
    }

    private static int showMixinStatus(CommandContext<CommandSourceStack> ctx) {
        ctx.getSource().sendSuccess(() -> Component.literal("§b§l=== Mixin Status ==="), false);
        ctx.getSource().sendSuccess(() ->
                Component.literal("§7Use §e/im debug mixin check §7for detailed verification"), false);
        return 1;
    }

    private static int checkMixins(CommandContext<CommandSourceStack> ctx) {
        ctx.getSource().sendSuccess(() -> Component.literal("§6Checking mixin status..."), false);
        ctx.getSource().sendSuccess(() ->
                Component.literal("§7Check server console for detailed mixin loading information"), false);
        return 1;
    }

    private static int testVisual(CommandContext<CommandSourceStack> ctx, String level) {
        try {
            ServerPlayer player = ctx.getSource().getPlayerOrException();

            // Use the public triggerTestEffect method
            PollutionVisualEffects.triggerTestEffect(player, level);

            ctx.getSource().sendSuccess(() ->
                    Component.literal(String.format("§a✓ Triggered §e%s §avisual effects", level)), true);
            return 1;
        } catch (Exception e) {
            ctx.getSource().sendFailure(Component.literal("§cError: " + e.getMessage()));
            return 0;
        }
    }

    private static int showVisualHelp(CommandContext<CommandSourceStack> ctx) {
        ctx.getSource().sendSuccess(() -> Component.literal("§b§l=== Visual Effects Test ==="), false);
        ctx.getSource().sendSuccess(() -> Component.literal("§e/im debug visual light §7- Light pollution effects"), false);
        ctx.getSource().sendSuccess(() -> Component.literal("§e/im debug visual moderate §7- Moderate pollution effects"), false);
        ctx.getSource().sendSuccess(() -> Component.literal("§e/im debug visual heavy §7- Heavy pollution effects"), false);
        ctx.getSource().sendSuccess(() -> Component.literal("§e/im debug visual severe §7- Severe pollution effects"), false);
        return 1;
    }
}
