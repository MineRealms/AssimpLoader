package cn.minerealms.iic.industrial;

import cn.minerealms.iic.IntegratedIndustrialCraft;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Mob;
import net.minecraftforge.common.ForgeConfigSpec;

import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.*;

/**
 * The Hordes Mod 集成管理器
 *
 * 提供反射式访问 The Hordes mod 的核心功能，包括：
 * - 尸潮事件管理（触发、停止、查询）
 * - 配置项访问（生成数量、间隔、难度等）
 * - 实体追踪（检测尸潮生成的实体）
 *
 * 非强依赖：如果 The Hordes 未安装，所有方法安全返回默认值
 */
public class HordeManager {

    // ==================== 检测与初始化 ====================

    private static boolean hordesAvailable = false;
    private static boolean hordesChecked = false;

    // 反射缓存
    private static Class<?> hordeSavedDataClass;
    private static Class<?> hordeEventClass;
    private static Class<?> hordeEventConfigClass;
    private static Class<?> hordeSpawnClass;

    private static Method getDataMethod;
    private static Method getEventMethod;
    private static Method tryStartEventMethod;
    private static Method spawnWaveMethod;
    private static Method stopEventMethod;
    private static Method isActiveMethod;
    private static Method isHordeDayMethod;
    private static Method getCurrentDayMethod;
    private static Method getNextDayMethod;
    private static Method setNextDayMethod;
    private static Method resetMethod;
    private static Method getHordePlayerMethod;

    private static Field timerField;
    private static Field entitiesSpawnedField;
    private static Field dayField;

    /**
     * 检查 The Hordes 是否可用
     */
    public static boolean isHordesLoaded() {
        if (hordesChecked) return hordesAvailable;
        hordesChecked = true;

        try {
            // 尝试加载核心类
            hordeSavedDataClass = Class.forName("net.smileycorp.hordes.hordeevent.capability.HordeSavedData");
            hordeEventClass = Class.forName("net.smileycorp.hordes.hordeevent.capability.HordeEvent");
            hordeEventConfigClass = Class.forName("net.smileycorp.hordes.config.HordeEventConfig");
            hordeSpawnClass = Class.forName("net.smileycorp.hordes.hordeevent.capability.HordeSpawn");

            // 缓存方法
            getDataMethod = hordeSavedDataClass.getMethod("getData", ServerLevel.class);
            getEventMethod = hordeSavedDataClass.getMethod("getEvent", ServerPlayer.class);
            tryStartEventMethod = hordeEventClass.getMethod("tryStartEvent", ServerPlayer.class, int.class, boolean.class);
            spawnWaveMethod = hordeEventClass.getMethod("spawnWave", ServerPlayer.class, int.class);
            stopEventMethod = hordeEventClass.getMethod("stopEvent", ServerPlayer.class, boolean.class);
            isActiveMethod = hordeEventClass.getMethod("isActive", ServerPlayer.class);
            isHordeDayMethod = hordeEventClass.getMethod("isHordeDay", ServerPlayer.class);
            getCurrentDayMethod = hordeEventClass.getMethod("getCurrentDay", ServerPlayer.class);
            getNextDayMethod = hordeEventClass.getMethod("getNextDay");
            setNextDayMethod = hordeEventClass.getMethod("setNextDay", int.class);
            resetMethod = hordeEventClass.getMethod("reset", ServerPlayer.class);
            getHordePlayerMethod = hordeSpawnClass.getMethod("getHordePlayer", net.minecraft.world.entity.Entity.class);

            // 缓存字段
            timerField = hordeEventClass.getDeclaredField("timer");
            timerField.setAccessible(true);
            entitiesSpawnedField = hordeEventClass.getDeclaredField("entitiesSpawned");
            entitiesSpawnedField.setAccessible(true);
            dayField = hordeEventClass.getDeclaredField("day");
            dayField.setAccessible(true);

            hordesAvailable = true;
            IntegratedIndustrialCraft.LOGGER.info("[IIC-Hordes] The Hordes detected, integration enabled");
            return true;

        } catch (ClassNotFoundException e) {
            hordesAvailable = false;
            IntegratedIndustrialCraft.LOGGER.info("[IIC-Hordes] The Hordes not found, integration disabled");
            return false;
        } catch (Exception e) {
            hordesAvailable = false;
            IntegratedIndustrialCraft.LOGGER.warn("[IIC-Hordes] Failed to initialize Hordes integration", e);
            return false;
        }
    }

    // ==================== 事件管理 ====================

    /**
     * 获取玩家的尸潮事件实例
     * @return HordeEvent 实例，如果 Hordes 未加载则返回 null
     */
    public static Object getHordeEvent(ServerPlayer player) {
        if (!isHordesLoaded()) return null;
        try {
            Object savedData = getDataMethod.invoke(null, player.serverLevel());
            return getEventMethod.invoke(savedData, player);
        } catch (Exception e) {
            IntegratedIndustrialCraft.LOGGER.error("[IIC-Hordes] Failed to get HordeEvent", e);
            return null;
        }
    }

    /**
     * 手动触发尸潮事件
     * @param player 目标玩家
     * @param duration 持续时间（tick），-1 使用默认配置
     * @param isCommand 是否为命令触发
     * @return 是否成功触发
     */
    public static boolean startHorde(ServerPlayer player, int duration, boolean isCommand) {
        if (!isHordesLoaded()) return false;
        try {
            Object hordeEvent = getHordeEvent(player);
            if (hordeEvent == null) return false;

            tryStartEventMethod.invoke(hordeEvent, player, duration, isCommand);
            return true;
        } catch (Exception e) {
            IntegratedIndustrialCraft.LOGGER.error("[IIC-Hordes] Failed to start horde", e);
            return false;
        }
    }

    /**
     * 生成一波尸潮怪物
     * @param player 目标玩家
     * @param count 生成数量
     * @return 是否成功生成
     */
    public static boolean spawnWave(ServerPlayer player, int count) {
        if (!isHordesLoaded()) return false;
        try {
            Object hordeEvent = getHordeEvent(player);
            if (hordeEvent == null) return false;

            spawnWaveMethod.invoke(hordeEvent, player, count);
            return true;
        } catch (Exception e) {
            IntegratedIndustrialCraft.LOGGER.error("[IIC-Hordes] Failed to spawn wave", e);
            return false;
        }
    }

    /**
     * 停止当前尸潮事件
     * @param player 目标玩家
     * @param isCommand 是否为命令触发
     * @return 是否成功停止
     */
    public static boolean stopHorde(ServerPlayer player, boolean isCommand) {
        if (!isHordesLoaded()) return false;
        try {
            Object hordeEvent = getHordeEvent(player);
            if (hordeEvent == null) return false;

            stopEventMethod.invoke(hordeEvent, player, isCommand);
            return true;
        } catch (Exception e) {
            IntegratedIndustrialCraft.LOGGER.error("[IIC-Hordes] Failed to stop horde", e);
            return false;
        }
    }

    /**
     * 重置玩家的尸潮数据
     * @param player 目标玩家
     * @return 是否成功重置
     */
    public static boolean resetHorde(ServerPlayer player) {
        if (!isHordesLoaded()) return false;
        try {
            Object hordeEvent = getHordeEvent(player);
            if (hordeEvent == null) return false;

            resetMethod.invoke(hordeEvent, player);
            return true;
        } catch (Exception e) {
            IntegratedIndustrialCraft.LOGGER.error("[IIC-Hordes] Failed to reset horde", e);
            return false;
        }
    }

    // ==================== 状态查询 ====================

    /**
     * 检查尸潮事件是否正在进行
     * @param player 目标玩家
     * @return 是否正在进行
     */
    public static boolean isHordeActive(ServerPlayer player) {
        if (!isHordesLoaded()) return false;
        try {
            Object hordeEvent = getHordeEvent(player);
            if (hordeEvent == null) return false;

            return (boolean) isActiveMethod.invoke(hordeEvent, player);
        } catch (Exception e) {
            IntegratedIndustrialCraft.LOGGER.error("[IIC-Hordes] Failed to check horde active", e);
            return false;
        }
    }

    /**
     * 检查今天是否是尸潮日
     * @param player 目标玩家
     * @return 是否是尸潮日
     */
    public static boolean isHordeDay(ServerPlayer player) {
        if (!isHordesLoaded()) return false;
        try {
            Object hordeEvent = getHordeEvent(player);
            if (hordeEvent == null) return false;

            return (boolean) isHordeDayMethod.invoke(hordeEvent, player);
        } catch (Exception e) {
            IntegratedIndustrialCraft.LOGGER.error("[IIC-Hordes] Failed to check horde day", e);
            return false;
        }
    }

    /**
     * 获取玩家当前天数
     * @param player 目标玩家
     * @return 当前天数，失败返回 0
     */
    public static int getCurrentDay(ServerPlayer player) {
        if (!isHordesLoaded()) return 0;
        try {
            Object hordeEvent = getHordeEvent(player);
            if (hordeEvent == null) return 0;

            return (int) getCurrentDayMethod.invoke(hordeEvent, player);
        } catch (Exception e) {
            IntegratedIndustrialCraft.LOGGER.error("[IIC-Hordes] Failed to get current day", e);
            return 0;
        }
    }

    /**
     * 获取下次尸潮触发天数
     * @param player 目标玩家
     * @return 下次触发天数，失败返回 -1
     */
    public static int getNextHordeDay(ServerPlayer player) {
        if (!isHordesLoaded()) return -1;
        try {
            Object hordeEvent = getHordeEvent(player);
            if (hordeEvent == null) return -1;

            return (int) getNextDayMethod.invoke(hordeEvent);
        } catch (Exception e) {
            IntegratedIndustrialCraft.LOGGER.error("[IIC-Hordes] Failed to get next horde day", e);
            return -1;
        }
    }

    /**
     * 设置下次尸潮触发天数
     * @param player 目标玩家
     * @param day 天数
     * @return 是否成功设置
     */
    public static boolean setNextHordeDay(ServerPlayer player, int day) {
        if (!isHordesLoaded()) return false;
        try {
            Object hordeEvent = getHordeEvent(player);
            if (hordeEvent == null) return false;

            setNextDayMethod.invoke(hordeEvent, day);
            return true;
        } catch (Exception e) {
            IntegratedIndustrialCraft.LOGGER.error("[IIC-Hordes] Failed to set next horde day", e);
            return false;
        }
    }

    /**
     * 获取尸潮剩余时间（tick）
     * @param player 目标玩家
     * @return 剩余 tick，失败或未激活返回 0
     */
    public static int getRemainingTicks(ServerPlayer player) {
        if (!isHordesLoaded()) return 0;
        try {
            Object hordeEvent = getHordeEvent(player);
            if (hordeEvent == null) return 0;

            return timerField.getInt(hordeEvent);
        } catch (Exception e) {
            IntegratedIndustrialCraft.LOGGER.error("[IIC-Hordes] Failed to get remaining ticks", e);
            return 0;
        }
    }

    /**
     * 获取当前尸潮的难度天数（用于计算难度）
     * @param player 目标玩家
     * @return 难度天数，失败返回 0
     */
    public static int getHordeDifficultyDay(ServerPlayer player) {
        if (!isHordesLoaded()) return 0;
        try {
            Object hordeEvent = getHordeEvent(player);
            if (hordeEvent == null) return 0;

            return dayField.getInt(hordeEvent);
        } catch (Exception e) {
            IntegratedIndustrialCraft.LOGGER.error("[IIC-Hordes] Failed to get horde difficulty day", e);
            return 0;
        }
    }

    /**
     * 获取当前尸潮生成的实体列表
     * @param player 目标玩家
     * @return 实体集合，失败返回空集合
     */
    @SuppressWarnings("unchecked")
    public static Set<Mob> getSpawnedEntities(ServerPlayer player) {
        if (!isHordesLoaded()) return Collections.emptySet();
        try {
            Object hordeEvent = getHordeEvent(player);
            if (hordeEvent == null) return Collections.emptySet();

            return (Set<Mob>) entitiesSpawnedField.get(hordeEvent);
        } catch (Exception e) {
            IntegratedIndustrialCraft.LOGGER.error("[IIC-Hordes] Failed to get spawned entities", e);
            return Collections.emptySet();
        }
    }

    /**
     * 检查实体是否由尸潮生成
     * @param entity 实体
     * @return 是否为尸潮生成，失败返回 false
     */
    public static boolean isHordeEntity(Mob entity) {
        if (!isHordesLoaded()) return false;
        try {
            ServerPlayer hordePlayer = (ServerPlayer) getHordePlayerMethod.invoke(null, entity);
            return hordePlayer != null;
        } catch (Exception e) {
            IntegratedIndustrialCraft.LOGGER.error("[IIC-Hordes] Failed to check horde entity", e);
            return false;
        }
    }

    /**
     * 获取尸潮实体的目标玩家
     * @param entity 实体
     * @return 目标玩家，如果不是尸潮实体则返回 null
     */
    public static ServerPlayer getHordeTargetPlayer(Mob entity) {
        if (!isHordesLoaded()) return null;
        try {
            return (ServerPlayer) getHordePlayerMethod.invoke(null, entity);
        } catch (Exception e) {
            IntegratedIndustrialCraft.LOGGER.error("[IIC-Hordes] Failed to get horde target player", e);
            return null;
        }
    }

    // ==================== 配置访问 ====================

    /**
     * 获取配置值
     * @param configName 配置字段名（如 "hordeSpawnAmount"）
     * @return 配置值，失败返回 null
     */
    @SuppressWarnings("unchecked")
    private static <T> T getConfigValue(String configName) {
        if (!isHordesLoaded()) return null;
        try {
            Field configField = hordeEventConfigClass.getField(configName);
            ForgeConfigSpec.ConfigValue<?> configValue = (ForgeConfigSpec.ConfigValue<?>) configField.get(null);
            return (T) configValue.get();
        } catch (Exception e) {
            IntegratedIndustrialCraft.LOGGER.error("[IIC-Hordes] Failed to get config: " + configName, e);
            return null;
        }
    }

    /**
     * 设置配置值
     * @param configName 配置字段名
     * @param value 新值
     * @return 是否成功设置
     */
    @SuppressWarnings("unchecked")
    private static <T> boolean setConfigValue(String configName, T value) {
        if (!isHordesLoaded()) return false;
        try {
            Field configField = hordeEventConfigClass.getField(configName);
            ForgeConfigSpec.ConfigValue<T> configValue = (ForgeConfigSpec.ConfigValue<T>) configField.get(null);
            configValue.set(value);
            configValue.save();
            return true;
        } catch (Exception e) {
            IntegratedIndustrialCraft.LOGGER.error("[IIC-Hordes] Failed to set config: " + configName, e);
            return false;
        }
    }

    // 常用配置的便捷方法

    public static int getSpawnAmount() {
        Integer value = getConfigValue("hordeSpawnAmount");
        return value != null ? value : 25;
    }

    public static boolean setSpawnAmount(int amount) {
        return setConfigValue("hordeSpawnAmount", amount);
    }

    public static int getSpawnDuration() {
        Integer value = getConfigValue("hordeSpawnDuration");
        return value != null ? value : 6000;
    }

    public static boolean setSpawnDuration(int duration) {
        return setConfigValue("hordeSpawnDuration", duration);
    }

    public static int getSpawnInterval() {
        Integer value = getConfigValue("hordeSpawnInterval");
        return value != null ? value : 1000;
    }

    public static boolean setSpawnInterval(int interval) {
        return setConfigValue("hordeSpawnInterval", interval);
    }

    public static int getSpawnDays() {
        Integer value = getConfigValue("hordeSpawnDays");
        return value != null ? value : 10;
    }

    public static boolean setSpawnDays(int days) {
        return setConfigValue("hordeSpawnDays", days);
    }

    public static int getSpawnMax() {
        Integer value = getConfigValue("hordeSpawnMax");
        return value != null ? value : 160;
    }

    public static boolean setSpawnMax(int max) {
        return setConfigValue("hordeSpawnMax", max);
    }

    public static double getSpawnMultiplier() {
        Double value = getConfigValue("hordeSpawnMultiplier");
        return value != null ? value : 1.05;
    }

    public static boolean setSpawnMultiplier(double multiplier) {
        return setConfigValue("hordeSpawnMultiplier", multiplier);
    }

    public static double getEntitySpeed() {
        Double value = getConfigValue("hordeEntitySpeed");
        return value != null ? value : 1.0;
    }

    public static boolean setEntitySpeed(double speed) {
        return setConfigValue("hordeEntitySpeed", speed);
    }

    public static double getSpawnDistance() {
        Double value = getConfigValue("hordeSpawnDistance");
        return value != null ? value : 75.0;
    }

    public static boolean setSpawnDistance(double distance) {
        return setConfigValue("hordeSpawnDistance", distance);
    }

    public static boolean isHordeEventEnabled() {
        Boolean value = getConfigValue("enableHordeEvent");
        return value != null ? value : true;
    }

    public static boolean setHordeEventEnabled(boolean enabled) {
        return setConfigValue("enableHordeEvent", enabled);
    }

    public static boolean isCommandOnly() {
        Boolean value = getConfigValue("hordesCommandOnly");
        return value != null ? value : false;
    }

    public static boolean setCommandOnly(boolean commandOnly) {
        return setConfigValue("hordesCommandOnly", commandOnly);
    }

    // ==================== 调试信息 ====================

    /**
     * 获取尸潮状态的调试信息
     * @param player 目标玩家
     * @return 调试信息字符串
     */
    public static String getDebugInfo(ServerPlayer player) {
        if (!isHordesLoaded()) {
            return "[IIC-Hordes] The Hordes not loaded";
        }

        StringBuilder sb = new StringBuilder();
        sb.append("[IIC-Hordes] Status for ").append(player.getName().getString()).append(":\n");
        sb.append("  Active: ").append(isHordeActive(player)).append("\n");
        sb.append("  Horde Day: ").append(isHordeDay(player)).append("\n");
        sb.append("  Current Day: ").append(getCurrentDay(player)).append("\n");
        sb.append("  Next Horde Day: ").append(getNextHordeDay(player)).append("\n");
        sb.append("  Remaining Ticks: ").append(getRemainingTicks(player)).append("\n");
        sb.append("  Difficulty Day: ").append(getHordeDifficultyDay(player)).append("\n");
        sb.append("  Spawned Entities: ").append(getSpawnedEntities(player).size()).append("\n");
        sb.append("  Config - Spawn Amount: ").append(getSpawnAmount()).append("\n");
        sb.append("  Config - Spawn Days: ").append(getSpawnDays()).append("\n");
        sb.append("  Config - Enabled: ").append(isHordeEventEnabled());

        return sb.toString();
    }
}
