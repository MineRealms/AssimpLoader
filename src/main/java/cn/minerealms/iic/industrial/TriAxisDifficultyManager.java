package cn.minerealms.iic.industrial;

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
        long mcDays = level.getDayTime() / 24000L;

        double tRaw = Math.log1p(mcDays / TriAxisConfig.baseDays) / Math.log1p(TriAxisConfig.targetDays / TriAxisConfig.baseDays);
        double T = Mth.clamp(tRaw, 0.0, 1.0);

        // --- 2. 科技轴 (Voltage Axis - V) ---
        double V = 0.0;
        if (TriAxisConfig.hasGTCEu()) {
            int medianTier = MachineScanner.scanNearbyVoltageTierMedianSafely(level, center, TriAxisConfig.scanRadiusBlocks);
            // 使用配置的最大电压等级进行归一化
            int effectiveMaxTier = TriAxisConfig.getEffectiveMaxTier();
            V = Mth.clamp((double) medianTier / effectiveMaxTier, 0.0, 1.0);
        } else {
            V = T; // 降级处理
        }

        // --- 3. Pollution Axis (P) ---
        // Always use temporary + permanent pollution, regardless of Spore
        double localPollution = PollutionManager.getTemporaryPollution(new net.minecraft.world.level.ChunkPos(center));
        double globalPollution = PollutionManager.getPermanentPollution();
        double totalPollution = localPollution + globalPollution;

        // EMA smoothing
        double currentEma = pollutionEmaCache.getOrDefault(center, 0.0);
        double newEma = (TriAxisConfig.emaAlpha * totalPollution) + ((1.0 - TriAxisConfig.emaAlpha) * currentEma);
        pollutionEmaCache.put(center, newEma);

        // Normalize pollution to 0-1 range
        // Use a more sensitive formula for low pollution values
        double P = Math.min(1.0, newEma / 100.0); // Linear scaling: 100 pollution = 1.0

        // --- 4. 融合计算总难度 (Total Difficulty - D) ---
        double targetD = TriAxisConfig.globalMultiplier * (
                TriAxisConfig.weightTime * T +
                TriAxisConfig.weightVoltage * V +
                TriAxisConfig.weightPollution * P
        );

        // --- 5. 迟滞与限速 (Hysteresis & Rate Limiting) ---
        double currentD = difficultyCache.getOrDefault(center, 0.0);
        double delta = targetD - currentD;

        // 限制每次计算的最大变化率
        delta = Mth.clamp(delta, -TriAxisConfig.maxChangePerSec, TriAxisConfig.maxChangePerSec);
        double finalD = currentD + delta;

        difficultyCache.put(center, finalD);

        // --- 6. Debug Logging - output every 5 seconds (100 ticks) ---
        if (IndustrialLogger.isDebugEnabled() && level.getGameTime() % 100 == 0) {
            IndustrialLogger.debugDifficulty(String.format(
                    "TriAxis at %s | T: %.4f | V: %.4f | P: %.4f | D: %.4f | LocalPollution: %.2f | GlobalPollution: %.2f | TotalPollution: %.2f",
                    center, T, V, P, finalD, localPollution, globalPollution, totalPollution));
        }

        return new DifficultyState(finalD, T, V, P);
    }
    
    // 清理缓存（可在服务器关闭或区块卸载时调用）
    public static void clearCache() {
        difficultyCache.clear();
        pollutionEmaCache.clear();
    }
}