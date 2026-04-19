package cn.minerealms.iic.command;

import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import com.mojang.brigadier.context.CommandContext;
import cn.minerealms.iic.difficulty.*;
import cn.minerealms.iic.industrial.TriAxisConfig;
import cn.minerealms.iic.integration.gregtech.GTIntegration;
import cn.minerealms.iic.pollution.PollutionManager;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;

/**
 * Difficulty information commands.
 * /im difficulty status - Show current difficulty state
 * /im difficulty scan - Scan nearby machines
 */
public class DifficultyCommand {

    public static LiteralArgumentBuilder<CommandSourceStack> register() {
        return Commands.literal("difficulty")
                // Show difficulty status
                .then(Commands.literal("status")
                        .executes(DifficultyCommand::showStatus))

                // Scan nearby machines
                .then(Commands.literal("scan")
                        .executes(DifficultyCommand::scanMachines))

                // Default: show status
                .executes(DifficultyCommand::showStatus);
    }

    private static int showStatus(CommandContext<CommandSourceStack> ctx) {
        try {
            ServerPlayer player = ctx.getSource().getPlayerOrException();
            ServerLevel level = player.serverLevel();
            BlockPos pos = player.blockPosition();

            ctx.getSource().sendSuccess(() -> Component.literal("§b§l=== Industrial Difficulty Status ==="), false);

            // Tri-Axis Difficulty
            TriAxisDifficultyManager.DifficultyState triAxis =
                    TriAxisDifficultyManager.calculateLocalDifficulty(level, pos);

            ctx.getSource().sendSuccess(() ->
                    Component.literal(String.format("§6Total Difficulty: §e%.3f", triAxis.totalDifficulty)), false);
            ctx.getSource().sendSuccess(() ->
                    Component.literal(String.format("  §7Time Factor: §f%.3f%%", triAxis.timeFactor * 100)), false);
            ctx.getSource().sendSuccess(() ->
                    Component.literal(String.format("  §7Voltage Factor: §f%.3f%%", triAxis.voltageFactor * 100)), false);
            ctx.getSource().sendSuccess(() ->
                    Component.literal(String.format("  §7Pollution Factor: §f%.3f%%", triAxis.pollutionFactor * 100)), false);

            // Industrial Bonus
            float industrialBonus = DifficultyManager.getDifficultyFor(player);
            ctx.getSource().sendSuccess(() ->
                    Component.literal(String.format("§6Industrial Bonus: §e%.3f", industrialBonus)), false);

            // Pollution Data
            double localPollution = HazardScanner.getPollutionLevel(player);
            double globalPollution = PollutionManager.getPermanentPollution();

            ctx.getSource().sendSuccess(() ->
                    Component.literal(String.format("§6Pollution: §7Local: §f%.2f §7| Global: §f%.2f",
                            localPollution, globalPollution)), false);

            // Game Stage
            double gameStage = GameStageCalculator.calculateGlobalGameStage(level, player, triAxis);
            ctx.getSource().sendSuccess(() ->
                    Component.literal(String.format("§6Game Stage: §e%.1f%%", gameStage)), false);

            return 1;
        } catch (Exception e) {
            ctx.getSource().sendFailure(Component.literal("§cError: " + e.getMessage()));
            return 0;
        }
    }

    private static int scanMachines(CommandContext<CommandSourceStack> ctx) {
        try {
            ServerPlayer player = ctx.getSource().getPlayerOrException();
            ServerLevel level = player.serverLevel();
            BlockPos center = player.blockPosition();
            int radius = TriAxisConfig.scanRadiusBlocks;

            ctx.getSource().sendSuccess(() ->
                    Component.literal(String.format("§6Scanning for machines in %dx%dx%d area...",
                            radius*2, radius, radius*2)), false);

            int totalMachines = 0;
            int activeMachines = 0;
            int[] tierCounts = new int[15]; // 0-14 tiers

            // Scan area
            for (BlockPos pos : BlockPos.betweenClosed(
                    center.offset(-radius, -radius / 2, -radius),
                    center.offset(radius, radius / 2, radius))) {

                var be = level.getBlockEntity(pos);
                if (GTIntegration.isGTMachine(be)) {
                    totalMachines++;

                    int tier = GTIntegration.getVoltageTier(be);
                    if (tier >= 0 && tier < 15) {
                        tierCounts[tier]++;
                    }

                    if (GTIntegration.isMachineActive(be)) {
                        activeMachines++;
                    }
                }
            }

            // Display results
            final int finalTotal = totalMachines;
            final int finalActive = activeMachines;

            ctx.getSource().sendSuccess(() ->
                    Component.literal(String.format("§a✓ Found %d machines (%d active)", finalTotal, finalActive)), false);

            // Show voltage tier distribution
            ctx.getSource().sendSuccess(() -> Component.literal("§7Voltage Tier Distribution:"), false);
            String[] tierNames = {"ULV", "LV", "MV", "HV", "EV", "IV", "LuV", "ZPM", "UV", "UHV", "UEV", "UIV", "UXV", "OpV", "MAX"};
            for (int i = 0; i < 15; i++) {
                if (tierCounts[i] > 0) {
                    final int tier = i;
                    final int count = tierCounts[i];
                    ctx.getSource().sendSuccess(() ->
                            Component.literal(String.format("  §e%s (T%d): §f%d", tierNames[tier], tier, count)), false);
                }
            }

            // Calculate median tier
            MachineScanner.ScanResult scanResult = MachineScanner.scanNearbyMachines(player, radius);
            if (!scanResult.tiers().isEmpty()) {
                float medianTier = DifficultySmoother.weightedMedian(scanResult.tiers(), scanResult.weights());
                ctx.getSource().sendSuccess(() ->
                        Component.literal(String.format("§7Weighted Median Tier: §e%.2f", medianTier)), false);
            }

            return 1;
        } catch (Exception e) {
            ctx.getSource().sendFailure(Component.literal("§cError: " + e.getMessage()));
            e.printStackTrace();
            return 0;
        }
    }
}
