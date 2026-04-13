package io.github.flemmli97.improvedmobs.industrial;

import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.player.Player;
import java.util.HashMap;
import java.util.Map;

public class TriAxisDifficultyManager {
    
    // 缓存上一次的难度，用于平滑过渡和限速 (Chunk/Pos -> Difficulty)
    // 实际项目中建议挂载到 Chunk 的 SavedData 或 Capability 中，这里为了模块化先用内存 Map 缓存
    private static final Map<BlockPos, Double> difficultyCache = new HashMap<>();
    private static final Map<BlockPos, Double> pollutionEmaCache = new HashMap<>();

    public static class DifficultyState {
        public final double totalDifficulty; // D
        public final double timeFactor;      // T
        public final double voltageFactor;   // V
        public final double pollutionFactor; // P
        
        public DifficultyState(double d, double t, double v, double p) {
            this.totalDifficulty = d;
            this.timeFactor = t;
            this.voltageFactor = v;
            this.pollutionFactor = p;
        }
    }

    public static DifficultyState calculateLocalDifficulty(ServerLevel level, BlockPos center) {
        // --- 1. 时间轴 (Time Axis - T) ---
        // 根据配置里的MC天数进行对数缩放，平滑前期并放缓后期
        long mcDays = level.getDayTime() / 24000L;
        
        double tRaw = Math.log1p(mcDays / TriAxisConfig.baseDays) / Math.log1p(TriAxisConfig.targetDays / TriAxisConfig.baseDays);
        double T = Mth.clamp(tRaw, 0.0, 1.0);

        // --- 2. 科技轴 (Voltage Axis - V) ---
        double V = 0.0;
        if (TriAxisConfig.hasGTCEu()) {
            int medianTier = MachineScanner.scanNearbyVoltageTierMedianSafely(level, center, TriAxisConfig.scanRadiusBlocks);
            V = Mth.clamp((double) medianTier / TriAxisConfig.maxGTTier, 0.0, 1.0);
        } else {
            // 降级兼容：如果没有GTCEu，把V的权重平摊或者按时间补偿
            V = T; // 简单的降级处理
        }

        // --- 3. 污染轴 (Pollution Axis - P) ---
        double P = 0.0;
        if (SporeIntegration.isSporeLoaded()) {
            double rawPollution = PollutionManager.getPermanentPollution(); // 简化：获取基础污染
            
            // EMA (指数移动平均) 平滑处理污染值
            double currentEma = pollutionEmaCache.getOrDefault(center, 0.0);
            double newEma = (TriAxisConfig.emaAlpha * rawPollution) + ((1.0 - TriAxisConfig.emaAlpha) * currentEma);
            pollutionEmaCache.put(center, newEma);
            
            P = 1.0 - Math.exp(-newEma / TriAxisConfig.pollutionDenominator);
        } else {
            P = T; // 降级处理
        }

        // --- 4. 融合计算总难度 (Total Difficulty - D) ---
        double targetD = TriAxisConfig.globalMultiplier * (
                TriAxisConfig.weightTime * T + 
                TriAxisConfig.weightVoltage * V + 
                TriAxisConfig.weightPollution * P
        );

        // --- 5. 迟滞与限速 (Hysteresis & Rate Limiting) ---
        double currentD = difficultyCache.getOrDefault(center, 0.0);
        double delta = targetD - currentD;
        
        // 限制每次计算的最大变化率 (防止难度瞬间暴走)
        delta = Mth.clamp(delta, -TriAxisConfig.maxChangePerSec, TriAxisConfig.maxChangePerSec);
        double finalD = currentD + delta;
        
        difficultyCache.put(center, finalD);

        return new DifficultyState(finalD, T, V, P);
    }
    
    // 清理缓存（可在服务器关闭或区块卸载时调用）
    public static void clearCache() {
        difficultyCache.clear();
        pollutionEmaCache.clear();
    }
}