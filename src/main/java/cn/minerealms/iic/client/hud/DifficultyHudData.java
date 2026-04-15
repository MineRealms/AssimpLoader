package cn.minerealms.iic.client.hud;

import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 客户端HUD数据存储
 */
public class DifficultyHudData {

    private static final ConcurrentHashMap<UUID, PlayerHudData> playerData = new ConcurrentHashMap<>();

    public static class PlayerHudData {
        // 三轴难度数据
        public double totalDifficulty = 0.0;
        public double timeFactor = 0.0;
        public double voltageFactor = 0.0;
        public double pollutionFactor = 0.0;

        // 工业数据
        public int nearbyMachines = 0;
        public double medianTier = 0.0;
        public double industrialBonus = 0.0;

        // 污染数据
        public double localPollution = 0.0;
        public double globalPollution = 0.0;

        // Spore数据（基于 Proto 实体）
        public int activeHiveminds = 0;          // 虫巢数量
        public double sporeMultiplier = 1.0;     // Spore 倍率
        public int evolutionPhase = 0;           // 进化阶段（0-10）
        public float infectionLevel = 0.0f;      // 感染强度（0-100）
        public int totalBiomass = 0;             // 总生物质
        public int totalHosts = 0;               // 总宿主数
        public int infectedChunks = 0;           // 影响区块数（估算）

        // 新增：游戏阶段指标
        public double localPollutionProgress = 0.0;  // 附近临时污染进度 (0-1)
        public String pollutionLevel = "SAFE";        // 污染等级文本
        public double globalGameStage = 0.0;          // 全局游戏阶段 (0-100)

        // 显示控制
        public boolean showHud = false;
        public long lastUpdate = 0;
    }

    public static PlayerHudData getOrCreate(UUID playerId) {
        return playerData.computeIfAbsent(playerId, k -> new PlayerHudData());
    }

    public static void remove(UUID playerId) {
        playerData.remove(playerId);
    }

    public static void clear() {
        playerData.clear();
    }
}
