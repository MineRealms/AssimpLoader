package cn.minerealms.iic.threat.horde;

import cn.minerealms.iic.difficulty.DifficultyManager;
import cn.minerealms.iic.difficulty.MachineScanner;
import cn.minerealms.iic.industrial.IndustrialLogger;
import cn.minerealms.iic.industrial.TriAxisConfig;
import cn.minerealms.iic.integration.alexscaves.AlexsCavesIntegration;
import cn.minerealms.iic.integration.gregtech.GTIntegration;
import cn.minerealms.iic.pollution.PollutionManager;

import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.ai.goal.target.NearestAttackableTargetGoal;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.phys.AABB;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

/**
 * The Hordes Integration Manager
 *
 * 整合 The Hordes 尸潮系统与 ImprovedMobs 工业难度系统，实现：
 * 1. 基于 difficulty 和三轴难度动态调整尸潮强度
 * 2. 污染触发强制尸潮（高污染区域自动触发）
 * 3. 尸潮僵尸攻击机器（基于电压等级和污染）
 * 4. 小股袭扰和大尸潮机制（基于游戏阶段）
 * 5. 完整的配置系统和指令支持
 */
public class HordeIntegrationManager {

    // ==================== 配置参数 ====================

    // 尸潮强度调整
    public static boolean enableHordeIntegration = true;
    public static double hordeIntensityMultiplier = 1.0;  // 全局尸潮强度倍率
    public static double difficultyToIntensityFactor = 0.5;  // Difficulty 转换为尸潮强度的系数

    // 污染触发尸潮
    public static boolean enablePollutionTriggeredHordes = true;
    public static double pollutionHordeTriggerThreshold = 150.0;  // 污染触发阈值
    public static double pollutionHordeCheckInterval = 600.0;  // 检查间隔（tick）
    public static double pollutionHordeTriggerChance = 0.05;  // 触发概率（每次检查）

    // 小股袭扰配置
    public static boolean enableSkirmishes = true;
    public static double skirmishPollutionThreshold = 80.0;  // 小股袭扰污染阈值
    public static double skirmishInterval = 1200.0;  // 小股袭扰间隔（tick）
    public static int skirmishMinCount = 3;  // 最小生成数量
    public static int skirmishMaxCount = 8;  // 最大生成数量

    // 大尸潮配置
    public static double majorHordePollutionThreshold = 200.0;  // 大尸潮污染阈值
    public static double majorHordeMultiplier = 2.0;  // 大尸潮强度倍率

    // 机器攻击配置
    public static boolean enableMachineTargeting = true;
    public static double machineTargetingRange = 32.0;  // 机器检测范围
    public static double machineTargetingChance = 0.3;  // 僵尸攻击机器的概率

    // 电压等级影响
    public static boolean enableVoltageTierScaling = true;
    public static double[] tierIntensityMultipliers = {
        1.0,  // ULV (Tier 0)
        1.1,  // LV  (Tier 1)
        1.3,  // MV  (Tier 2) - 小股袭扰开始
        1.5,  // HV  (Tier 3) - 中等威胁
        1.8,  // EV  (Tier 4)
        2.2,  // IV  (Tier 5) - 大尸潮开始
        2.6,  // LuV (Tier 6)
        3.0,  // ZPM (Tier 7)
        3.5,  // UV  (Tier 8)
        4.0   // UHV (Tier 9) - 极限挑战
    };

    // ==================== 运行时数据 ====================

    // 玩家尸潮冷却时间（防止频繁触发）
    private static final Map<UUID, Long> playerHordeCooldowns = new ConcurrentHashMap<>();
    private static final long HORDE_COOLDOWN_TICKS = 6000;  // 5分钟冷却

    // 玩家小股袭扰冷却
    private static final Map<UUID, Long> playerSkirmishCooldowns = new ConcurrentHashMap<>();

    // 污染触发检查计时器
    private static long lastPollutionCheckTick = 0;

    // 已标记为攻击机器的僵尸
    private static final Set<UUID> machineTargetingZombies = ConcurrentHashMap.newKeySet();

    /**
     * 主更新方法 - 每 tick 调用
     */
    public static void tick(ServerLevel level) {
        if (!enableHordeIntegration || !HordeManager.isHordesLoaded()) {
            return;
        }

        long currentTick = level.getGameTime();

        // 污染触发尸潮检查
        if (enablePollutionTriggeredHordes &&
            currentTick - lastPollutionCheckTick >= pollutionHordeCheckInterval) {
            lastPollutionCheckTick = currentTick;
            checkPollutionTriggeredHordes(level);
        }

        // 小股袭扰检查
        if (enableSkirmishes) {
            checkSkirmishes(level, currentTick);
        }

        // 更新尸潮强度（基于 difficulty）
        updateHordeIntensity(level);

        // 处理机器攻击
        if (enableMachineTargeting) {
            updateMachineTargeting(level);
        }
    }

    /**
     * 检查污染触发的尸潮
     */
    private static void checkPollutionTriggeredHordes(ServerLevel level) {
        for (ServerPlayer player : level.players()) {
            // 检查冷却
            if (isOnCooldown(player, playerHordeCooldowns, HORDE_COOLDOWN_TICKS)) {
                continue;
            }

            // 获取玩家所在区块的污染
            ChunkPos chunkPos = player.chunkPosition();
            double pollution = PollutionManager.getTemporaryPollution(chunkPos);

            // 检查是否达到触发阈值
            if (pollution < pollutionHordeTriggerThreshold) {
                continue;
            }

            // 概率触发
            if (level.random.nextDouble() > pollutionHordeTriggerChance) {
                continue;
            }

            // 触发尸潮
            triggerPollutionHorde(player, pollution);
        }
    }

    /**
     * 触发污染尸潮
     */
    private static void triggerPollutionHorde(ServerPlayer player, double pollution) {
        ServerLevel level = player.serverLevel();

        // 计算尸潮强度
        boolean isMajorHorde = pollution >= majorHordePollutionThreshold;
        double intensityMultiplier = isMajorHorde ? majorHordeMultiplier : 1.0;

        // 获取玩家电压等级
        int voltageTier = getPlayerVoltageTier(player);
        double tierMultiplier = getTierIntensityMultiplier(voltageTier);

        // 计算持续时间和生成数量
        int baseDuration = HordeManager.getSpawnDuration();
        int baseAmount = HordeManager.getSpawnAmount();

        int duration = (int) (baseDuration * intensityMultiplier * tierMultiplier);
        int amount = (int) (baseAmount * intensityMultiplier * tierMultiplier);

        // 更新配置
        HordeManager.setSpawnDuration(duration);
        HordeManager.setSpawnAmount(amount);

        // 触发尸潮
        boolean success = HordeManager.startHorde(player, duration, false);

        if (success) {
            // 设置冷却
            playerHordeCooldowns.put(player.getUUID(), player.serverLevel().getGameTime());

            // 尝试生成 Nucleeper（AlexsCaves 集成）
            AlexsCavesIntegration.onHordeEvent(level, player);

            // 日志
            IndustrialLogger.info(String.format(
                "[IIC-Hordes] Pollution-triggered %s horde for %s: pollution=%.1f, tier=%d, duration=%d, amount=%d",
                isMajorHorde ? "MAJOR" : "normal",
                player.getName().getString(),
                pollution,
                voltageTier,
                duration,
                amount
            ));

            // 发送消息给玩家
            player.sendSystemMessage(net.minecraft.network.chat.Component.literal(
                String.format("§c§l[WARNING] §r§c%s detected! Horde incoming!",
                    isMajorHorde ? "Critical pollution" : "High pollution")
            ));
        }
    }

    /**
     * 检查小股袭扰
     */
    private static void checkSkirmishes(ServerLevel level, long currentTick) {
        for (ServerPlayer player : level.players()) {
            // 检查冷却
            if (isOnCooldown(player, playerSkirmishCooldowns, (long) skirmishInterval)) {
                continue;
            }

            // 获取污染
            ChunkPos chunkPos = player.chunkPosition();
            double pollution = PollutionManager.getTemporaryPollution(chunkPos);

            // 检查阈值
            if (pollution < skirmishPollutionThreshold) {
                continue;
            }

            // 获取电压等级
            int voltageTier = getPlayerVoltageTier(player);

            // MV 以下不触发小股袭扰
            if (voltageTier < 2) {
                continue;
            }

            // 触发小股袭扰
            triggerSkirmish(player, pollution, voltageTier);
        }
    }

    /**
     * 触发小股袭扰
     */
    private static void triggerSkirmish(ServerPlayer player, double pollution, int voltageTier) {
        // 计算生成数量（基于污染和电压等级）
        double pollutionFactor = Math.min(pollution / 150.0, 2.0);
        double tierFactor = getTierIntensityMultiplier(voltageTier);

        int count = (int) (skirmishMinCount +
            (skirmishMaxCount - skirmishMinCount) * pollutionFactor * tierFactor);

        // 生成一波
        boolean success = HordeManager.spawnWave(player, count);

        if (success) {
            // 设置冷却
            playerSkirmishCooldowns.put(player.getUUID(), player.serverLevel().getGameTime());

            // 日志
            IndustrialLogger.debug(String.format(
                "[IIC-Hordes] Skirmish for %s: count=%d, pollution=%.1f, tier=%d",
                player.getName().getString(),
                count,
                pollution,
                voltageTier
            ));
        }
    }

    /**
     * 更新尸潮强度（基于 difficulty）
     */
    private static void updateHordeIntensity(ServerLevel level) {
        for (ServerPlayer player : level.players()) {
            // 检查是否有活跃的尸潮
            if (!HordeManager.isHordeActive(player)) {
                continue;
            }

            // 获取当前 difficulty
            float difficulty = DifficultyManager.getDifficultyFor(player);

            // 计算强度调整
            double intensityBonus = difficulty * difficultyToIntensityFactor * hordeIntensityMultiplier;

            // 调整尸潮实体属性
            adjustHordeEntities(player, intensityBonus);
        }
    }

    /**
     * 调整尸潮实体属性
     */
    private static void adjustHordeEntities(ServerPlayer player, double intensityBonus) {
        Set<Mob> hordeEntities = HordeManager.getSpawnedEntities(player);

        for (Mob entity : hordeEntities) {
            // 检查是否已经调整过
            if (entity.getPersistentData().getBoolean("IIC_HordeAdjusted")) {
                continue;
            }

            // 标记为已调整
            entity.getPersistentData().putBoolean("IIC_HordeAdjusted", true);

            // 应用属性加成（基于 ImprovedMobs 的 difficulty 系统）
            // 这里不直接修改属性，而是让 ImprovedMobs 的系统处理
            // 只需要确保 difficulty 值正确传递即可

            // 如果启用机器攻击，添加机器目标AI
            if (enableMachineTargeting && entity instanceof net.minecraft.world.entity.monster.Zombie) {
                addMachineTargetingAI(entity, player);
            }
        }
    }

    /**
     * 添加机器攻击 AI
     */
    private static void addMachineTargetingAI(Mob zombie, ServerPlayer player) {
        // 概率决定是否攻击机器
        if (zombie.level().random.nextDouble() > machineTargetingChance) {
            return;
        }

        // 标记为机器攻击僵尸
        machineTargetingZombies.add(zombie.getUUID());
        zombie.getPersistentData().putBoolean("IIC_MachineTargeting", true);

        IndustrialLogger.debug(String.format(
            "[IIC-Hordes] Zombie %s marked for machine targeting",
            zombie.getUUID()
        ));
    }

    /**
     * 更新机器攻击逻辑
     */
    private static void updateMachineTargeting(ServerLevel level) {
        // 遍历所有标记的僵尸
        Iterator<UUID> iterator = machineTargetingZombies.iterator();
        while (iterator.hasNext()) {
            UUID zombieUUID = iterator.next();
            net.minecraft.world.entity.Entity entity = level.getEntity(zombieUUID);

            // 检查实体是否存在
            if (entity == null || !entity.isAlive() || !(entity instanceof Mob zombie)) {
                iterator.remove();
                continue;
            }

            // 检查是否有目标
            if (zombie.getTarget() != null) {
                continue;  // 已经有目标，不干扰
            }

            // 查找附近的机器
            BlockEntity nearestMachine = findNearestMachine(zombie);
            if (nearestMachine != null) {
                // 让僵尸移动到机器位置
                zombie.getNavigation().moveTo(
                    nearestMachine.getBlockPos().getX(),
                    nearestMachine.getBlockPos().getY(),
                    nearestMachine.getBlockPos().getZ(),
                    1.0
                );

                // 使用 ImprovedMobs 的机器攻击 AI
                // ZombieDestroyMachineGoal 会自动处理破坏逻辑
            }
        }
    }

    /**
     * 查找最近的机器
     */
    private static BlockEntity findNearestMachine(Mob zombie) {
        BlockPos zombiePos = zombie.blockPosition();
        ServerLevel level = (ServerLevel) zombie.level();

        double range = machineTargetingRange;
        AABB searchBox = new AABB(zombiePos).inflate(range);

        BlockEntity nearest = null;
        double nearestDist = Double.MAX_VALUE;

        // 遍历范围内的方块实体
        for (int x = (int) searchBox.minX; x <= searchBox.maxX; x++) {
            for (int y = (int) searchBox.minY; y <= searchBox.maxY; y++) {
                for (int z = (int) searchBox.minZ; z <= searchBox.maxZ; z++) {
                    BlockPos pos = new BlockPos(x, y, z);
                    BlockEntity be = level.getBlockEntity(pos);

                    if (be == null) continue;

                    // 检查是否是机器（使用 GTIntegration 的检测）
                    if (!GTIntegration.isGTMachine(be)) continue;

                    double dist = zombiePos.distSqr(pos);
                    if (dist < nearestDist) {
                        nearestDist = dist;
                        nearest = be;
                    }
                }
            }
        }

        return nearest;
    }

    /**
     * 获取玩家电压等级
     */
    private static int getPlayerVoltageTier(ServerPlayer player) {
        // 扫描玩家附近的机器
        MachineScanner.ScanResult result = MachineScanner.scanNearbyMachines(
            player,
            TriAxisConfig.scanRadiusBlocks
        );

        // 计算最大电压等级
        if (result.tiers().isEmpty()) {
            return 0;
        }

        return result.tiers().stream().max(Integer::compareTo).orElse(0);
    }

    /**
     * 获取电压等级强度倍率
     */
    private static double getTierIntensityMultiplier(int tier) {
        if (!enableVoltageTierScaling) {
            return 1.0;
        }

        if (tier < 0 || tier >= tierIntensityMultipliers.length) {
            // 超出范围，使用最高等级的倍率
            return tierIntensityMultipliers[tierIntensityMultipliers.length - 1];
        }

        return tierIntensityMultipliers[tier];
    }

    /**
     * 检查冷却
     */
    private static boolean isOnCooldown(ServerPlayer player, Map<UUID, Long> cooldownMap, long cooldownTicks) {
        UUID uuid = player.getUUID();
        if (!cooldownMap.containsKey(uuid)) {
            return false;
        }

        long lastTrigger = cooldownMap.get(uuid);
        long currentTick = player.serverLevel().getGameTime();

        if (currentTick - lastTrigger >= cooldownTicks) {
            cooldownMap.remove(uuid);
            return false;
        }

        return true;
    }

    /**
     * 获取调试信息
     */
    public static String getDebugInfo(ServerPlayer player) {
        StringBuilder sb = new StringBuilder();
        sb.append("[IIC-Hordes Integration]\n");
        sb.append("  Enabled: ").append(enableHordeIntegration).append("\n");
        sb.append("  Hordes Loaded: ").append(HordeManager.isHordesLoaded()).append("\n");

        if (HordeManager.isHordesLoaded()) {
            sb.append("  Active: ").append(HordeManager.isHordeActive(player)).append("\n");
            sb.append("  Horde Day: ").append(HordeManager.isHordeDay(player)).append("\n");
            sb.append("  Current Day: ").append(HordeManager.getCurrentDay(player)).append("\n");
            sb.append("  Next Horde: ").append(HordeManager.getNextHordeDay(player)).append("\n");
            sb.append("  Spawned Entities: ").append(HordeManager.getSpawnedEntities(player).size()).append("\n");

            ChunkPos chunkPos = player.chunkPosition();
            double pollution = PollutionManager.getTemporaryPollution(chunkPos);
            int voltageTier = getPlayerVoltageTier(player);

            sb.append("  Pollution: ").append(String.format("%.1f", pollution)).append("\n");
            sb.append("  Voltage Tier: ").append(voltageTier).append("\n");
            sb.append("  Tier Multiplier: ").append(String.format("%.2f", getTierIntensityMultiplier(voltageTier))).append("\n");

            boolean onCooldown = isOnCooldown(player, playerHordeCooldowns, HORDE_COOLDOWN_TICKS);
            sb.append("  Horde Cooldown: ").append(onCooldown ? "YES" : "NO").append("\n");

            boolean onSkirmishCooldown = isOnCooldown(player, playerSkirmishCooldowns, (long) skirmishInterval);
            sb.append("  Skirmish Cooldown: ").append(onSkirmishCooldown ? "YES" : "NO").append("\n");

            sb.append("  Machine Targeting Zombies: ").append(machineTargetingZombies.size());
        }

        return sb.toString();
    }

    /**
     * 重置所有冷却
     */
    public static void resetCooldowns() {
        playerHordeCooldowns.clear();
        playerSkirmishCooldowns.clear();
        machineTargetingZombies.clear();
    }

    /**
     * 清理无效的僵尸引用
     */
    public static void cleanup(ServerLevel level) {
        machineTargetingZombies.removeIf(uuid -> {
            net.minecraft.world.entity.Entity entity = level.getEntity(uuid);
            return entity == null || !entity.isAlive();
        });
    }
}
