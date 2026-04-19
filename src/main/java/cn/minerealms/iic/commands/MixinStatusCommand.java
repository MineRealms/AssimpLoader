package cn.minerealms.iic.commands;

import cn.minerealms.iic.util.MixinLoadTracker;
import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.context.CommandContext;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.network.chat.Component;

import java.util.Map;

/**
 * Command to check mixin loading status.
 */
public class MixinStatusCommand {

    public static void register(CommandDispatcher<CommandSourceStack> dispatcher) {
        dispatcher.register(Commands.literal("im")
                .then(Commands.literal("mixin")
                        .then(Commands.literal("status")
                                .executes(MixinStatusCommand::showStatus))
                        .then(Commands.literal("check")
                                .executes(MixinStatusCommand::checkMixins))
                        .then(Commands.literal("verify")
                                .executes(MixinStatusCommand::verifyMixins))
                )
        );
    }

    private static int showStatus(CommandContext<CommandSourceStack> ctx) {
        ctx.getSource().sendSuccess(() -> Component.literal("§b§l=== Mixin Status ==="), false);

        // Check EnhancedVisuals presence
        boolean evPresent = MixinLoadTracker.isEnhancedVisualsPresent();
        String evStatus = evPresent ? "§a✓ PRESENT" : "§c✗ NOT PRESENT";
        ctx.getSource().sendSuccess(() -> Component.literal("§eEnhancedVisuals Mod: " + evStatus), false);

        // Show all tracked mixins
        Map<String, MixinLoadTracker.MixinStatus> statuses = MixinLoadTracker.getAllStatuses();
        if (statuses.isEmpty()) {
            ctx.getSource().sendSuccess(() -> Component.literal("§7No mixins tracked yet (may load on first use)"), false);
        } else {
            ctx.getSource().sendSuccess(() -> Component.literal("§eTracked Mixins:"), false);
            statuses.forEach((name, status) -> {
                String statusColor = switch (status) {
                    case APPLIED -> "§a";
                    case LOADED -> "§e";
                    case FAILED -> "§c";
                    case NOT_LOADED -> "§7";
                };
                String statusText = statusColor + status.name();
                ctx.getSource().sendSuccess(() ->
                    Component.literal(String.format("  §7- §f%s: %s", name, statusText)), false);
            });
        }

        ctx.getSource().sendSuccess(() -> Component.literal("§7Check logs/iic-mixin-status.log for details"), false);
        return 1;
    }

    private static int checkMixins(CommandContext<CommandSourceStack> ctx) {
        ctx.getSource().sendSuccess(() -> Component.literal("§6Checking mixin compatibility..."), false);

        // Initialize tracker
        MixinLoadTracker.init();

        // Check EnhancedVisuals
        boolean evPresent = MixinLoadTracker.isEnhancedVisualsPresent();
        if (evPresent) {
            ctx.getSource().sendSuccess(() -> Component.literal("§a✓ EnhancedVisuals detected"), false);
            ctx.getSource().sendSuccess(() -> Component.literal("§7  Mixin will be applied on next client tick"), false);
        } else {
            ctx.getSource().sendSuccess(() -> Component.literal("§c✗ EnhancedVisuals not found"), false);
            ctx.getSource().sendSuccess(() -> Component.literal("§7  VisualManagerMixin will not be loaded"), false);
        }

        ctx.getSource().sendSuccess(() -> Component.literal("§7Check logs/iic-mixin-status.log for details"), false);
        return 1;
    }

    private static int verifyMixins(CommandContext<CommandSourceStack> ctx) {
        ctx.getSource().sendSuccess(() -> Component.literal("§6Verifying mixin functionality..."), false);

        // Verify EnhancedVisuals mixin
        boolean evMixinWorking = MixinLoadTracker.verifyEnhancedVisualsMixin();
        if (evMixinWorking) {
            ctx.getSource().sendSuccess(() -> Component.literal("§a✓ VisualManagerMixin is working"), false);
        } else {
            MixinLoadTracker.MixinStatus status = MixinLoadTracker.getStatus("VisualManagerMixin");
            ctx.getSource().sendSuccess(() ->
                Component.literal("§c✗ VisualManagerMixin status: " + status.name()), false);

            if (status == MixinLoadTracker.MixinStatus.NOT_LOADED) {
                ctx.getSource().sendSuccess(() ->
                    Component.literal("§7  Mixin has not been triggered yet (join world on client)"), false);
            }
        }

        ctx.getSource().sendSuccess(() -> Component.literal("§7Full report in logs/iic-mixin-status.log"), false);
        return 1;
    }
}
