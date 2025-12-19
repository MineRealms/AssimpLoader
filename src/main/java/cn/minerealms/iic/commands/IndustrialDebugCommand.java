package cn.minerealms.iic.commands;

import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.context.CommandContext;
import com.mojang.brigadier.exceptions.CommandSyntaxException;
import cn.minerealms.iic.difficulty.*;
import cn.minerealms.iic.industrial.IndustrialLogger;
import cn.minerealms.iic.industrial.TriAxisConfig;
import cn.minerealms.iic.integration.gregtech.GTIntegration;
import cn.minerealms.iic.integration.spore.SporeIntegration;
import cn.minerealms.iic.pollution.PollutionManager;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.block.entity.BlockEntity;

/**
 * 工业集成统一调试命令
 *
 * 指令结构：
 * /im industrial debug [on|off] - 启用/禁用调试日志
 * /im industrial scan - 扫描附近机器并显示详细信息
 * /im industrial highlight [on|off] - 启用/禁用机器高亮显示
 * /im industrial status - 显示当前工业难度状态
 * /im industrial test - 测试 GT 集成是否正常工作
 */
public class IndustrialDebugCommand {

    public static void register(CommandDispatcher<CommandSourceStack> dispatcher) {
        dispatcher.register(Commands.literal("im")
                .then(Commands.literal("industrial").requires(src -> src.hasPermission(2))
                        // 调试开关
                        .then(Commands.literal("debug")
                                .then(Commands.literal("on").executes(ctx -> setDebug(ctx, true)))
                                .then(Commands.literal("off").executes(ctx -> setDebug(ctx, false)))
                                .executes(IndustrialDebugCommand::toggleDebug))

                        // 扫描附近机器
                        .then(Commands.literal("scan")
                                .executes(IndustrialDebugCommand::scanNearbyMachines))

                        // 高亮显示
                        .then(Commands.literal("highlight")
                                .then(Commands.literal("on").executes(ctx -> setHighlight(ctx, true)))
                                .then(Commands.literal("off").executes(ctx -> setHighlight(ctx, false)))
                                .executes(IndustrialDebugCommand::toggleHighlight))

                        // 显示状态
                        .then(Commands.literal("status")
                                .executes(IndustrialDebugCommand::showStatus))

                        // 测试 GT 集成
                        .then(Commands.literal("test")
                                .executes(IndustrialDebugCommand::testGTIntegration))

                        // 帮助
                        .executes(IndustrialDebugCommand::showHelp))
        );
    }

    private static int showHelp(CommandContext<CommandSourceStack> ctx) {
        ctx.getSource().sendSuccess(() -> Component.literal("§b§l=== Industrial Integration Debug ==="), false);
        ctx.getSource().sendSuccess(() -> Component.literal("§e/im industrial debug [on|off] §7- Toggle debug logging"), false);
        ctx.getSource().sendSuccess(() -> Component.literal("§e/im industrial scan §7- Scan nearby machines"), false);
        ctx.getSource().sendSuccess(() -> Component.literal("§e/im industrial highlight [on|off] §7- Toggle machine highlighting"), false);
        ctx.getSource().sendSuccess(() -> Component.literal("§e/im industrial status §7- Show current industrial difficulty"), false);
        ctx.getSource().sendSuccess(() -> Component.literal("§e/im industrial test §7- Test GT integration"), false);
        ctx.getSource().sendSuccess(() -> Component.literal(""), false);
        ctx.getSource().sendSuccess(() -> Component.literal("§7Debug logs are saved to: §e" + IndustrialLogger.getLogFilePath()), false);
        return 1;
    }

    private static int toggleDebug(CommandContext<CommandSourceStack> ctx) {
        boolean newState = !IndustrialLogger.isDebugEnabled();
        return setDebug(ctx, newState);
    }

    private static int setDebug(CommandContext<CommandSourceStack> ctx, boolean enabled) {
        IndustrialLogger.setDebugEnabled(enabled);
        TriAxisConfig.enableDifficultyLogging = enabled;
        TriAxisConfig.enableSporeDebug = enabled;
        TriAxisConfig.save();

        String status = enabled ? "§aENABLED" : "§cDISABLED";
        ctx.getSource().sendSuccess(() ->
                Component.literal(String.format("§6[IndustrialIntegrationCraft] Debug mode %s", status)), true);

        if (enabled) {
            ctx.getSource().sendSuccess(() ->
                    Component.literal("§7Debug logs will be saved to: §e" + IndustrialLogger.getLogFilePath()), false);
            ctx.getSource().sendSuccess(() ->
                    Component.literal("§7File is overwritten on each server restart"), false);
        }

        return 1;
    }

    private static int toggleHighlight(CommandContext<CommandSourceStack> ctx) {
        boolean newState = !TriAxisConfig.enableDebugLines;
        return setHighlight(ctx, newState);
    }

    private static int setHighlight(CommandContext<CommandSourceStack> ctx, boolean enabled) {
        TriAxisConfig.enableDebugLines = enabled;
        TriAxisConfig.save();

        String status = enabled ? "§aENABLED" : "§cDISABLED";
        ctx.getSource().sendSuccess(() ->
                Component.literal(String.format("§6Machine highlighting %s", status)), true);

        return 1;
    }

    private static int scanNearbyMachines(CommandContext<CommandSourceStack> ctx) {
        try {
            ServerPlayer player = ctx.getSource().getPlayerOrException();
            ServerLevel level = player.serverLevel();
            BlockPos center = player.blockPosition();
            int radius = 32;

            ctx.getSource().sendSuccess(() ->
                    Component.literal(String.format("§6Scanning for machines in %dx%dx%d area...", radius*2, radius, radius*2)), false);

            int totalMachines = 0;
            int activeMachines = 0;
            int[] tierCounts = new int[15]; // 0-14 tiers

            // 扫描区域
            for (BlockPos pos : BlockPos.betweenClosed(
                    center.offset(-radius, -radius / 2, -radius),
                    center.offset(radius, radius / 2, radius))) {

                BlockEntity be = level.getBlockEntity(pos);
                // 使用 verbose 版本，会输出详细日志
                if (GTIntegration.isGTMachineVerbose(be)) {
                    totalMachines++;

                    int tier = GTIntegration.getVoltageTierVerbose(be);
                    if (tier >= 0 && tier < 15) {
                        tierCounts[tier]++;
                    }

                    boolean isActive = GTIntegration.isMachineActive(be);
                    boolean hasEnergy = GTIntegration.hasEnergyOrActiveVerbose(be);

                    if (isActive) {
                        activeMachines++;
                    }

                    boolean isMulti = GTIntegration.isMultiblock(be);

                    // 详细日志
                    IndustrialLogger.debugMachine(String.format(
                            "Found machine at %s | Tier: %d | Active: %s | Multiblock: %s | HasEnergy: %s",
                            pos, tier, isActive, isMulti, hasEnergy
                    ));
                }
            }

            // 显示结果
            final int finalTotal = totalMachines;
            final int finalActive = activeMachines;

            ctx.getSource().sendSuccess(() ->
                    Component.literal(String.format("§a✓ Found %d machines (%d active)", finalTotal, finalActive)), false);

            // 显示电压等级分布
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

            // 计算中位数
            MachineScanner.ScanResult scanResult = MachineScanner.scanNearbyMachines(player, radius);
            if (!scanResult.tiers().isEmpty()) {
                float medianTier = DifficultySmoother.weightedMedian(scanResult.tiers(), scanResult.weights());
                ctx.getSource().sendSuccess(() ->
                        Component.literal(String.format("§7Weighted Median Tier: §e%.2f", medianTier)), false);
            }

            return 1;
        } catch (CommandSyntaxException e) {
            ctx.getSource().sendFailure(Component.literal("§cError: Command must be executed by a player"));
            return 0;
        }
    }

    private static int showStatus(CommandContext<CommandSourceStack> ctx) {
        try {
            ServerPlayer player = ctx.getSource().getPlayerOrException();
            ServerLevel level = player.serverLevel();
            BlockPos pos = player.blockPosition();

            ctx.getSource().sendSuccess(() -> Component.literal("§b§l=== Industrial Difficulty Status ==="), false);

            // 三轴难度
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

            // 工业加成
            float industrialBonus = DifficultyManager.getDifficultyFor(player);
            ctx.getSource().sendSuccess(() ->
                    Component.literal(String.format("§6Industrial Bonus: §e%.3f", industrialBonus)), false);

            // 污染数据
            double localPollution = HazardScanner.getPollutionLevel(player);
            double globalPollution = PollutionManager.getPermanentPollution();

            ctx.getSource().sendSuccess(() ->
                    Component.literal(String.format("§6Pollution: §7Local: §f%.2f §7| Global: §f%.2f",
                            localPollution, globalPollution)), false);

            // 游戏阶段
            double gameStage = GameStageCalculator.calculateGlobalGameStage(level, player, triAxis);
            ctx.getSource().sendSuccess(() ->
                    Component.literal(String.format("§6Game Stage: §e%.1f%%", gameStage)), false);

            return 1;
        } catch (CommandSyntaxException e) {
            ctx.getSource().sendFailure(Component.literal("§cError: Command must be executed by a player"));
            return 0;
        }
    }

    private static int testGTIntegration(CommandContext<CommandSourceStack> ctx) {
        ctx.getSource().sendSuccess(() -> Component.literal("§6Testing GregTech CEu integration..."), false);

        // 测试 GT 是否加载
        boolean gtLoaded = TriAxisConfig.hasGTCEu();
        String gtStatus = gtLoaded ? "§a✓ LOADED" : "§c✗ NOT FOUND";
        ctx.getSource().sendSuccess(() ->
                Component.literal(String.format("§7GregTech CEu: %s", gtStatus)), false);

        if (!gtLoaded) {
            ctx.getSource().sendFailure(Component.literal("§cGregTech CEu is not installed!"));
            return 0;
        }

        // 测试 Spore 是否加载
        boolean sporeLoaded = SporeIntegration.isSporeLoaded();
        String sporeStatus = sporeLoaded ? "§a✓ LOADED" : "§7○ NOT FOUND";
        ctx.getSource().sendSuccess(() ->
                Component.literal(String.format("§7Spore Mod: %s", sporeStatus)), false);

        // 显示配置
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
                Component.literal("§a✓ Integration test completed! Use §e/im industrial scan §ato scan for machines."), false);

        return 1;
    }
}
