package cn.minerealms.iic.commands;

import cn.minerealms.iic.util.MixinLoadTracker;
import cn.minerealms.iic.mixin.enhancedvisuals.EnhancedVisualsHelper;
import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.arguments.IntegerArgumentType;
import com.mojang.brigadier.context.CommandContext;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.player.Player;

/**
 * Commands to test EnhancedVisuals effects.
 *
 * These commands trigger various visual effects to verify the mixin integration.
 */
public class VisualTestCommand {

    public static void register(CommandDispatcher<CommandSourceStack> dispatcher) {
        dispatcher.register(Commands.literal("im")
                .then(Commands.literal("visual")
                        .then(Commands.literal("test")
                                .then(Commands.literal("light")
                                        .executes(ctx -> triggerEffect(ctx, "light")))
                                .then(Commands.literal("moderate")
                                        .executes(ctx -> triggerEffect(ctx, "moderate")))
                                .then(Commands.literal("heavy")
                                        .executes(ctx -> triggerEffect(ctx, "heavy")))
                                .then(Commands.literal("severe")
                                        .executes(ctx -> triggerEffect(ctx, "severe")))
                                .then(Commands.literal("all")
                                        .executes(VisualTestCommand::triggerAllEffects))
                        )
                        .then(Commands.literal("spam")
                                .then(Commands.argument("count", IntegerArgumentType.integer(1, 100))
                                        .then(Commands.literal("light")
                                                .executes(ctx -> spamEffect(ctx, "light", IntegerArgumentType.getInteger(ctx, "count"))))
                                        .then(Commands.literal("moderate")
                                                .executes(ctx -> spamEffect(ctx, "moderate", IntegerArgumentType.getInteger(ctx, "count"))))
                                        .then(Commands.literal("heavy")
                                                .executes(ctx -> spamEffect(ctx, "heavy", IntegerArgumentType.getInteger(ctx, "count"))))
                                        .then(Commands.literal("severe")
                                                .executes(ctx -> spamEffect(ctx, "severe", IntegerArgumentType.getInteger(ctx, "count"))))
                                )
                        )
                        .then(Commands.literal("check")
                                .executes(VisualTestCommand::checkAvailability))
                )
        );
    }

    private static int triggerEffect(CommandContext<CommandSourceStack> ctx, String level) {
        // Check if EnhancedVisuals is available
        if (!MixinLoadTracker.isEnhancedVisualsPresent()) {
            ctx.getSource().sendFailure(Component.literal("§c✗ EnhancedVisuals mod not found!"));
            ctx.getSource().sendFailure(Component.literal("§7  Install EnhancedVisuals to use visual effects"));
            return 0;
        }

        try {
            // Trigger the effect (client-side)
            switch (level) {
                case "light" -> {
                    EnhancedVisualsHelper.triggerLightEffects();
                    ctx.getSource().sendSuccess(() ->
                        Component.literal("§a✓ Triggered §elight pollution §aeffects"), false);
                }
                case "moderate" -> {
                    EnhancedVisualsHelper.triggerModerateEffects();
                    ctx.getSource().sendSuccess(() ->
                        Component.literal("§a✓ Triggered §6moderate pollution §aeffects"), false);
                }
                case "heavy" -> {
                    EnhancedVisualsHelper.triggerHeavyEffects();
                    ctx.getSource().sendSuccess(() ->
                        Component.literal("§a✓ Triggered §cheavy pollution §aeffects"), false);
                }
                case "severe" -> {
                    EnhancedVisualsHelper.triggerSevereEffects();
                    ctx.getSource().sendSuccess(() ->
                        Component.literal("§a✓ Triggered §4severe pollution §aeffects"), false);
                }
            }
            return 1;
        } catch (Exception e) {
            ctx.getSource().sendFailure(Component.literal("§c✗ Error triggering effect: " + e.getMessage()));
            return 0;
        }
    }

    private static int triggerAllEffects(CommandContext<CommandSourceStack> ctx) {
        if (!MixinLoadTracker.isEnhancedVisualsPresent()) {
            ctx.getSource().sendFailure(Component.literal("§c✗ EnhancedVisuals mod not found!"));
            return 0;
        }

        ctx.getSource().sendSuccess(() -> Component.literal("§6Triggering all pollution effects..."), false);

        try {
            EnhancedVisualsHelper.triggerLightEffects();
            ctx.getSource().sendSuccess(() -> Component.literal("§a✓ Light"), false);

            Thread.sleep(500);
            EnhancedVisualsHelper.triggerModerateEffects();
            ctx.getSource().sendSuccess(() -> Component.literal("§a✓ Moderate"), false);

            Thread.sleep(500);
            EnhancedVisualsHelper.triggerHeavyEffects();
            ctx.getSource().sendSuccess(() -> Component.literal("§a✓ Heavy"), false);

            Thread.sleep(500);
            EnhancedVisualsHelper.triggerSevereEffects();
            ctx.getSource().sendSuccess(() -> Component.literal("§a✓ Severe"), false);

            ctx.getSource().sendSuccess(() -> Component.literal("§aAll effects triggered!"), false);
            return 1;
        } catch (Exception e) {
            ctx.getSource().sendFailure(Component.literal("§c✗ Error: " + e.getMessage()));
            return 0;
        }
    }

    private static int spamEffect(CommandContext<CommandSourceStack> ctx, String level, int count) {
        if (!MixinLoadTracker.isEnhancedVisualsPresent()) {
            ctx.getSource().sendFailure(Component.literal("§c✗ EnhancedVisuals mod not found!"));
            return 0;
        }

        ctx.getSource().sendSuccess(() ->
            Component.literal(String.format("§6Spamming §e%s §6effect §e%d §6times...", level, count)), false);

        try {
            for (int i = 0; i < count; i++) {
                switch (level) {
                    case "light" -> EnhancedVisualsHelper.triggerLightEffects();
                    case "moderate" -> EnhancedVisualsHelper.triggerModerateEffects();
                    case "heavy" -> EnhancedVisualsHelper.triggerHeavyEffects();
                    case "severe" -> EnhancedVisualsHelper.triggerSevereEffects();
                }

                if (i % 10 == 0 && i > 0) {
                    final int progress = i;
                    final int total = count;
                    ctx.getSource().sendSuccess(() ->
                        Component.literal(String.format("§7  Progress: %d/%d", progress, total)), false);
                }
            }

            ctx.getSource().sendSuccess(() ->
                Component.literal(String.format("§a✓ Triggered %d effects!", count)), false);
            return 1;
        } catch (Exception e) {
            ctx.getSource().sendFailure(Component.literal("§c✗ Error: " + e.getMessage()));
            return 0;
        }
    }

    private static int checkAvailability(CommandContext<CommandSourceStack> ctx) {
        ctx.getSource().sendSuccess(() -> Component.literal("§b§l=== Visual Effects Status ==="), false);

        // Check EnhancedVisuals
        boolean evPresent = MixinLoadTracker.isEnhancedVisualsPresent();
        String evStatus = evPresent ? "§a✓ AVAILABLE" : "§c✗ NOT AVAILABLE";
        ctx.getSource().sendSuccess(() -> Component.literal("§eEnhancedVisuals: " + evStatus), false);

        // Check mixin status
        if (evPresent) {
            boolean mixinWorking = MixinLoadTracker.verifyEnhancedVisualsMixin();
            String mixinStatus = mixinWorking ? "§a✓ WORKING" : "§e⚠ NOT VERIFIED";
            ctx.getSource().sendSuccess(() -> Component.literal("§eVisualManagerMixin: " + mixinStatus), false);

            if (!mixinWorking) {
                ctx.getSource().sendSuccess(() ->
                    Component.literal("§7  (Mixin will activate on first client tick)"), false);
            }
        }

        // Show available commands
        if (evPresent) {
            ctx.getSource().sendSuccess(() -> Component.literal(""), false);
            ctx.getSource().sendSuccess(() -> Component.literal("§eAvailable test commands:"), false);
            ctx.getSource().sendSuccess(() -> Component.literal("§7  /im visual test light §f- Light pollution"), false);
            ctx.getSource().sendSuccess(() -> Component.literal("§7  /im visual test moderate §f- Moderate pollution"), false);
            ctx.getSource().sendSuccess(() -> Component.literal("§7  /im visual test heavy §f- Heavy pollution"), false);
            ctx.getSource().sendSuccess(() -> Component.literal("§7  /im visual test severe §f- Severe pollution"), false);
            ctx.getSource().sendSuccess(() -> Component.literal("§7  /im visual test all §f- All effects in sequence"), false);
            ctx.getSource().sendSuccess(() -> Component.literal("§7  /im visual spam <count> <level> §f- Spam effects"), false);
        } else {
            ctx.getSource().sendSuccess(() -> Component.literal(""), false);
            ctx.getSource().sendSuccess(() -> Component.literal("§cInstall EnhancedVisuals to enable visual effects"), false);
        }

        return 1;
    }
}
