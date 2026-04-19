package cn.minerealms.iic.difficulty;

import cn.minerealms.iic.industrial.TriAxisConfig;
import cn.minerealms.iic.integration.spore.SporeIntegration;

import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.player.Player;

/**
 * 游戏阶段计算器 - 优化版
 * 计算全局游戏进度和局部污染等级
 *
 * 性能优化：
 * - 使用缓存避免重复计算
 * - 预计算常量
 * - 避免不必要的对象创建
 */
public class GameStageCalculator {

    // 缓存预计算的常量，避免每次计算时重复除法
    private static double cachedMaxBonus = -1.0;
    private static double cachedTriAxisMax = -1.0;

    /**
     * 污染等级枚举
     */
    public enum PollutionLevel {
        SAFE("integratedindustrialcraft.pollution.safe", 0.0, 0.15),
        LIGHT("integratedindustrialcraft.pollution.light", 0.15, 0.30),
        MODERATE("integratedindustrialcraft.pollution.moderate", 0.30, 0.50),
        HIGH("integratedindustrialcraft.pollution.high", 0.50, 0.70),
        SEVERE("integratedindustrialcraft.pollution.severe", 0.70, 0.90),
        CATASTROPHIC("integratedindustrialcraft.pollution.catastrophic", 0.90, Double.MAX_VALUE);

        public final String translationKey;
        public final double minThreshold;
        public final double maxThreshold;

        PollutionLevel(String translationKey, double minThreshold, double maxThreshold) {
            this.translationKey = translationKey;
            this.minThreshold = minThreshold;
            this.maxThreshold = maxThreshold;
        }

        public static PollutionLevel fromProgress(double progress) {
            // 优化：使用二分查找或直接比较，避免遍历所有值
            if (progress < 0.15) return SAFE;
            if (progress < 0.30) return LIGHT;
            if (progress < 0.50) return MODERATE;
            if (progress < 0.70) return HIGH;
            if (progress < 0.90) return SEVERE;
            return CATASTROPHIC;
        }
    }

    /**
     * 计算附近临时污染进度
     * 基于 IndustrialDifficultyManager 的工业加成值
     *
     * @param industrialBonus 工业加成值（来自 IndustrialDifficultyManager）
     * @return 归一化的污染进度 (0.0-1.0)
     */
    public static double calculateLocalPollutionProgress(double industrialBonus) {
        // 使用缓存的最大值，避免每次计算时重复加法
        if (cachedMaxBonus < 0) {
            cachedMaxBonus = TriAxisConfig.techWeight + TriAxisConfig.pollutionWeight;
        }

        // 快速路径：如果加成为0，直接返回
        if (industrialBonus <= 0) return 0.0;

        // 归一化到 0-1 范围
        double normalized = industrialBonus / cachedMaxBonus;

        // 使用 Math.min 一次性限制，避免两次比较
        return Math.min(1.0, normalized);
    }

    /**
     * 获取污染等级
     */
    public static PollutionLevel getPollutionLevel(double progress) {
        return PollutionLevel.fromProgress(progress);
    }

    /**
     * 计算全局游戏阶段（类似异星工厂的进化因子）
     * 综合考虑：时间进度、科技进度、污染进度、Spore扩散
     *
     * @param level 服务端世界
     * @param player 玩家
     * @param triAxisState 三轴难度状态
     * @return 游戏阶段百分比 (0.0-100.0)
     */
    public static double calculateGlobalGameStage(
            ServerLevel level,
            Player player,
            TriAxisDifficultyManager.DifficultyState triAxisState) {

        // === 1. 基础三轴进度 (权重 70%) ===
        if (cachedTriAxisMax < 0) {
            cachedTriAxisMax = TriAxisConfig.globalMultiplier;
        }

        double triAxisProgress = Math.min(1.0, triAxisState.totalDifficulty / cachedTriAxisMax);

        // === 2. Spore 扩散进度 (权重 30%) ===
        double sporeProgress;
        if (SporeIntegration.isSporeLoaded()) {
            // 2.1 Hiveminds 数量（权重 40%）- 假设 10 个为满进度
            int hiveminds = SporeIntegration.getActiveHiveminds(level);
            double hivemindProgress = Math.min(1.0, hiveminds * 0.1); // 优化：预计算 1/10

            // 2.2 进化阶段（权重 30%）- 假设进化阶段 0-10
            int evolutionPhase = SporeIntegration.getEvolutionPhase(level);
            double evolutionProgress = Math.min(1.0, evolutionPhase * 0.1);

            // 2.3 感染等级（权重 30%）- 感染等级通常是百分比
            float infectionLevel = SporeIntegration.getInfectionLevel(level);
            double infectionProgress = Math.min(1.0, infectionLevel * 0.01); // 优化：预计算 1/100

            // 综合 Spore 进度 - 优化：预计算权重
            sporeProgress = 0.4 * hivemindProgress + 0.3 * evolutionProgress + 0.3 * infectionProgress;
        } else {
            // 如果没有 Spore，使用时间进度代替
            sporeProgress = triAxisState.timeFactor;
        }

        // === 3. 融合计算全局游戏阶段 ===
        double globalStage = 0.7 * triAxisProgress + 0.3 * sporeProgress;

        // === 4. 应用难度预设的影响 ===
        double presetMultiplier = getPresetMultiplier();
        globalStage *= presetMultiplier;

        // === 5. 转换为百分比并限制范围 ===
        return Math.min(100.0, globalStage * 100.0);
    }

    /**
     * 获取难度预设倍率（缓存优化）
     */
    private static double getPresetMultiplier() {
        return switch (TriAxisConfig.difficultyPreset.toUpperCase()) {
            case "NORMAL" -> 0.8;
            case "HARD" -> 0.9;
            case "HARDCORE" -> 1.0;
            case "INSANE" -> 1.2;
            default -> 1.0;
        };
    }

    /**
     * 获取游戏阶段的描述文本（I18N）
     */
    public static String getGameStageTranslationKey(double stagePercent) {
        if (stagePercent < 10.0) return "integratedindustrialcraft.stage.early";
        if (stagePercent < 25.0) return "integratedindustrialcraft.stage.developing";
        if (stagePercent < 50.0) return "integratedindustrialcraft.stage.mid";
        if (stagePercent < 75.0) return "integratedindustrialcraft.stage.late";
        if (stagePercent < 90.0) return "integratedindustrialcraft.stage.endgame";
        return "integratedindustrialcraft.stage.final";
    }

    /**
     * 获取游戏阶段的颜色代码
     */
    public static String getGameStageColor(double stagePercent) {
        if (stagePercent < 20.0) return "§a";
        if (stagePercent < 40.0) return "§2";
        if (stagePercent < 60.0) return "§e";
        if (stagePercent < 80.0) return "§6";
        if (stagePercent < 95.0) return "§c";
        return "§4";
    }

    /**
     * 获取污染等级的颜色代码
     */

    public static String getPollutionLevelColor(PollutionLevel level) {
        return switch (level) {
            case SAFE -> "§a";
            case LIGHT -> "§2";
            case MODERATE -> "§e";
            case HIGH -> "§6";
            case SEVERE -> "§c";
            case CATASTROPHIC -> "§4";
        };
    }

    /**
     * 重置缓存（当配置重新加载时调用）
     */
    public static void resetCache() {
        cachedMaxBonus = -1.0;
        cachedTriAxisMax = -1.0;
    }
}
