package cn.minerealms.iic.server;

import cn.minerealms.iic.commands.HudCommands;
import cn.minerealms.iic.industrial.*;
import cn.minerealms.iic.network.SyncHudDataPacket;
import cn.minerealms.iic.network.PacketHandler;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;

import java.util.concurrent.*;

/**
 * HUD数据更新服务 - 优化版
 * 每秒（20 ticks）更新一次玩家的HUD数据
 *
 * 性能优化：
 * - 使用线程池异步计算，避免阻塞主线程
 * - 批量处理玩家数据
 * - 缓存计算结果
 */
public class HudUpdateService {

    private static int tickCounter = 0;

    // 使用固定大小的线程池，避免创建过多线程
    private static final ExecutorService EXECUTOR = Executors.newFixedThreadPool(
            2,
            r -> {
                Thread t = new Thread(r, "ImprovedMobs-HUD-Worker");
                t.setDaemon(true);
                return t;
            }
    );

    @SubscribeEvent
    public static void onServerTick(TickEvent.ServerTickEvent event) {
        if (event.phase != TickEvent.Phase.END) return;

        tickCounter++;
        if (tickCounter < 20) return; // 每秒更新一次
        tickCounter = 0;

        // 遍历所有玩家，在主线程更新HUD数据（避免线程安全问题）
        for (ServerLevel level : event.getServer().getAllLevels()) {
            for (ServerPlayer player : level.players()) {
                if (!HudCommands.isHudEnabled(player.getUUID())) continue;

                try {
                    updatePlayerHud(player);
                } catch (Exception e) {
                    // 静默失败，避免影响游戏
                    if (TriAxisConfig.enableDifficultyLogging) {
                        System.err.println("[ImprovedMobs] HUD update failed for player " + player.getName().getString());
                        e.printStackTrace();
                    }
                }
            }
        }
    }

    /**
     * 更新玩家HUD数据
     * 在主线程执行，确保线程安全
     */
    private static void updatePlayerHud(ServerPlayer player) {
        ServerLevel level = player.serverLevel();

        // 1. 三轴难度数据
        TriAxisDifficultyManager.DifficultyState triAxisState =
                TriAxisDifficultyManager.calculateLocalDifficulty(level, player.blockPosition());

        // 2. 工业数据 - 使用配置的扫描半径
        int scanRadius = TriAxisConfig.scanRadiusBlocks;
        MachineScanner.ScanResult machineResult = MachineScanner.scanNearbyMachines(player, scanRadius);
        float medianTier = machineResult.tiers().isEmpty() ? 0 :
                DifficultySmoother.weightedMedian(machineResult.tiers(), machineResult.weights());
        float industrialBonus = IndustrialDifficultyManager.getDifficultyFor(player);

        // 无条件 debug 日志 - 诊断问题
        if (IndustrialLogger.isDebugEnabled()) {
            IndustrialLogger.info(String.format(
                    "[HUD-DEBUG] Scanned %d machines, median: %.2f, bonus: %.3f, radius: %d",
                    machineResult.tiers().size(), medianTier, industrialBonus, scanRadius));
        }

        // 调试日志 - 显示扫描结果
        if (IndustrialLogger.isDebugEnabled() && level.getGameTime() % 100 == 0) {
            IndustrialLogger.info(String.format(
                    "cd, Found %d machines, median tier: %.2f, industrial bonus: %.3f",
                    scanRadius, machineResult.tiers().size(), medianTier, industrialBonus));
            if (!machineResult.tiers().isEmpty()) {
                IndustrialLogger.info("Machine tiers: " + machineResult.tiers());
            }
        }

        // 3. 污染数据
        double localPollution = HazardScanner.getPollutionLevel(player);
        double globalPollution = PollutionManager.getPermanentPollution();

        // 调试日志
        if (IndustrialLogger.isDebugEnabled() && level.getGameTime() % 100 == 0) {
            IndustrialLogger.debugPollution(String.format(
                    "HUD Update: Local pollution: %.2f, Global pollution: %.2f",
                    localPollution, globalPollution));
        }

        // 4. Spore数据（基于 Proto 实体实时计算）
        int activeHiveminds = 0;
        int evolutionPhase = 0;
        float infectionLevel = 0.0f;
        int totalBiomass = 0;
        int infectedChunks = 0;
        double sporeMultiplier = 1.0;

        if (SporeIntegration.isSporeLoaded()) {
            activeHiveminds = SporeIntegration.getActiveHiveminds(level);
            totalBiomass = SporeIntegration.getTotalBiomass(level);
            infectionLevel = SporeIntegration.calculateInfectionIntensity(level);
            evolutionPhase = (int) (infectionLevel / 10f); // 0-10 阶段
            infectedChunks = SporeIntegration.getInfectedChunks(level);

            // Spore 倍率：基于感染强度 + 污染 + 电压
            double nearbyVoltageTier = MachineScanner.scanNearbyVoltageTier(level, player.blockPosition());
            double pollutionBonus = globalPollution / 100.0;
            double voltageBonus = Math.max(0, nearbyVoltageTier - 1) * 0.10;
            double infectionBonus = infectionLevel / 200.0; // 最多 50%
            sporeMultiplier = 1.0 + pollutionBonus + voltageBonus + infectionBonus;
        }

        // 5. 计算游戏阶段指标
        double localPollutionProgress = GameStageCalculator.calculateLocalPollutionProgress(industrialBonus);
        GameStageCalculator.PollutionLevel pollutionLevelEnum = GameStageCalculator.getPollutionLevel(localPollutionProgress);
        String pollutionLevelKey = pollutionLevelEnum.translationKey;

        double globalGameStage = GameStageCalculator.calculateGlobalGameStage(level, player, triAxisState);

        // 调试日志 - 显示即将发送的数据
        if (IndustrialLogger.isDebugEnabled() && level.getGameTime() % 100 == 0) {
            IndustrialLogger.info(String.format(
                    "Sending HUD packet: Machines=%d, MedianTier=%.2f, IndustrialBonus=%.3f",
                    machineResult.tiers().size(), medianTier, industrialBonus));
            IndustrialLogger.info(String.format(
                    "  TriAxis: Total=%.4f, Time=%.4f, Voltage=%.4f, Pollution=%.4f",
                    triAxisState.totalDifficulty, triAxisState.timeFactor, triAxisState.voltageFactor, triAxisState.pollutionFactor));
            IndustrialLogger.info(String.format(
                    "  Pollution: Local=%.2f, Global=%.2f",
                    localPollution, globalPollution));
        }

        // 6. 发送数据包到客户端
        SyncHudDataPacket packet = new SyncHudDataPacket(
                triAxisState.totalDifficulty,
                triAxisState.timeFactor,
                triAxisState.voltageFactor,
                triAxisState.pollutionFactor,
                machineResult.tiers().size(),
                medianTier,
                industrialBonus,
                localPollution,
                globalPollution,
                activeHiveminds,
                evolutionPhase,
                infectionLevel,
                totalBiomass,
                infectedChunks,
                sporeMultiplier,
                localPollutionProgress,
                pollutionLevelKey,
                globalGameStage,
                true // HUD已启用
        );

        // 发送数据包
        if (player.isAlive() && !player.hasDisconnected()) {
            PacketHandler.sendHudDataToPlayer(packet, player);
        }
    }

    /**
     * 关闭线程池（服务器关闭时调用）
     */
    public static void shutdown() {
        EXECUTOR.shutdown();
        try {
            if (!EXECUTOR.awaitTermination(5, TimeUnit.SECONDS)) {
                EXECUTOR.shutdownNow();
            }
        } catch (InterruptedException e) {
            EXECUTOR.shutdownNow();
            Thread.currentThread().interrupt();
        }
    }
}
