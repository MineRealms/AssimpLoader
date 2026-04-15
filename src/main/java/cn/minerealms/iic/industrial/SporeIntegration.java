package cn.minerealms.iic.industrial;

import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.ai.attributes.AttributeInstance;
import net.minecraft.world.entity.ai.attributes.Attributes;

import java.lang.reflect.Method;
import java.lang.reflect.Field;
import java.util.List;

/**
 * Spore 2.0 集成 - 基于实际 API 重写
 *
 * 核心理念：类似 Factorio 虫巢系统
 * - Hiveminds (Proto) = 虫巢
 * - Biomass = 虫巢规模
 * - 感染强度 = Hiveminds数量 + 生物质 + 污染 + 电压
 *
 * 数据来源：遍历所有 Proto 实体获取实时数据
 */
public class SporeIntegration {

    private static boolean checkDone = false;
    private static boolean isSporeLoaded = false;

    // 缓存反射方法，避免重复查找
    private static Class<?> protoClass;
    private static Class<?> sporeSavedDataClass;
    private static Method getHivemindsMethod;
    private static Method getBiomassMethod;
    private static Method getHostsMethod;
    private static Field nodeField;
    private static Method getEntityDataMethod;

    public static boolean isSporeLoaded() {
        if (!checkDone) {
            try {
                sporeSavedDataClass = Class.forName("com.Harbinger.Spore.ExtremelySusThings.SporeSavedData");
                protoClass = Class.forName("com.Harbinger.Spore.Sentities.Organoids.Proto");

                // 预加载方法
                getHivemindsMethod = sporeSavedDataClass.getMethod("getHiveminds");
                getBiomassMethod = protoClass.getMethod("getBiomass");
                getHostsMethod = protoClass.getMethod("getHosts");

                // 预加载 NODE 字段和 getEntityData 方法（用于获取节点位置）
                try {
                    nodeField = protoClass.getDeclaredField("NODE");
                    nodeField.setAccessible(true);
                    getEntityDataMethod = Entity.class.getMethod("getEntityData");
                } catch (NoSuchFieldException | NoSuchMethodException e) {
                    IndustrialLogger.warn("Could not access Proto NODE field: " + e.getMessage());
                }

                isSporeLoaded = true;
                IndustrialLogger.info("Spore 2.0 detected and loaded successfully");
                IndustrialLogger.info("Using Proto-based data collection (Factorio-style)");
            } catch (ClassNotFoundException e) {
                isSporeLoaded = false;
                IndustrialLogger.warn("Spore mod not found, integration disabled");
            } catch (NoSuchMethodException e) {
                isSporeLoaded = false;
                IndustrialLogger.error("Spore API method not found: " + e.getMessage());
            }
            checkDone = true;
        }
        return isSporeLoaded;
    }

    public static boolean isSporeMob(LivingEntity entity) {
        if (!isSporeLoaded()) return false;
        try {
            String typeName = entity.getType().getDescriptionId().toLowerCase();
            boolean result = typeName.contains("spore") ||
                           entity.getType().getCategory().getName().toLowerCase().contains("spore");

            if (result) {
                IndustrialLogger.debugSpore("Detected Spore mob: " + entity.getType().getDescriptionId());
            }
            return result;
        } catch (Throwable t) {
            return false;
        }
    }

    /**
     * 获取活跃的 Hiveminds 数量（虫巢数量）
     */
    public static int getActiveHiveminds(ServerLevel level) {
        if (!isSporeLoaded()) return 0;
        try {
            @SuppressWarnings("unchecked")
            List<Object> hiveminds = (List<Object>) getHivemindsMethod.invoke(null);

            if (hiveminds == null) {
                IndustrialLogger.debugSpore("Hiveminds list is null - Spore not initialized yet");
                return 0;
            }

            int count = hiveminds.size();
            if (IndustrialLogger.isDebugEnabled() && level.getGameTime() % 200 == 0) {
                IndustrialLogger.debugSpore("Active Hiveminds (虫巢): " + count);
            }
            return count;

        } catch (Exception e) {
            IndustrialLogger.error("Error getting Hiveminds: " + e.getMessage());
            return 0;
        }
    }

    /**
     * 获取总生物质量（所有虫巢的生物质总和）
     * 类似 Factorio 的虫巢规模
     */
    public static int getTotalBiomass(ServerLevel level) {
        if (!isSporeLoaded()) return 0;
        try {
            @SuppressWarnings("unchecked")
            List<Object> hiveminds = (List<Object>) getHivemindsMethod.invoke(null);

            if (hiveminds == null || hiveminds.isEmpty()) return 0;

            int totalBiomass = 0;
            for (Object proto : hiveminds) {
                try {
                    int biomass = (int) getBiomassMethod.invoke(proto);
                    totalBiomass += biomass;
                } catch (Exception e) {
                    // 单个 Proto 失败不影响其他
                }
            }

            IndustrialLogger.debugSpore("Total Biomass (总生物质): " + totalBiomass);
            return totalBiomass;

        } catch (Exception e) {
            IndustrialLogger.error("Error calculating total biomass: " + e.getMessage());
            return 0;
        }
    }

    /**
     * 获取总宿主数量（所有虫巢的宿主总和）
     */
    public static int getTotalHosts(ServerLevel level) {
        if (!isSporeLoaded()) return 0;
        try {
            @SuppressWarnings("unchecked")
            List<Object> hiveminds = (List<Object>) getHivemindsMethod.invoke(null);

            if (hiveminds == null || hiveminds.isEmpty()) return 0;

            int totalHosts = 0;
            for (Object proto : hiveminds) {
                try {
                    int hosts = (int) getHostsMethod.invoke(proto);
                    totalHosts += hosts;
                } catch (Exception e) {
                    // 单个 Proto 失败不影响其他
                }
            }

            if (IndustrialLogger.isDebugEnabled() && level.getGameTime() % 200 == 0) {
                IndustrialLogger.debugSpore("Total Hosts (总宿主): " + totalHosts);
            }
            return totalHosts;

        } catch (Exception e) {
            IndustrialLogger.error("Error calculating total hosts: " + e.getMessage());
            return 0;
        }
    }

    /**
     * 计算感染强度（0-100）
     * 类似 Factorio 的进化因子
     *
     * 公式：基于 Hiveminds 数量 + 生物质规模 + 宿主数量
     */
    public static float calculateInfectionIntensity(ServerLevel level) {
        if (!isSporeLoaded()) return 0f;

        int hiveminds = getActiveHiveminds(level);
        int biomass = getTotalBiomass(level);
        int hosts = getTotalHosts(level);

        // 归一化计算（可配置）
        // Hiveminds: 每个贡献 10%，最多 10 个 = 100%
        // Biomass: 每 1000 贡献 10%，最多 10000 = 100%
        // Hosts: 每 50 贡献 10%，最多 500 = 100%
        float hivemindScore = Math.min(100f, hiveminds * 10f);
        float biomassScore = Math.min(100f, biomass / 100f);
        float hostScore = Math.min(100f, hosts / 5f);

        // 加权平均：Hiveminds 50%, Biomass 30%, Hosts 20%
        float intensity = (hivemindScore * 0.5f + biomassScore * 0.3f + hostScore * 0.2f);

        if (IndustrialLogger.isDebugEnabled() && level.getGameTime() % 200 == 0) {
            IndustrialLogger.debugSpore(String.format(
                    "Infection Intensity: %.1f%% (H:%.1f B:%.1f Ho:%.1f)",
                    intensity, hivemindScore, biomassScore, hostScore));
        }

        return intensity;
    }

    /**
     * 获取最近的 Hivemind 距离（用于局部威胁计算）
     */
    public static double getNearestHivemindDistance(ServerLevel level, BlockPos playerPos) {
        if (!isSporeLoaded()) return Double.MAX_VALUE;
        try {
            @SuppressWarnings("unchecked")
            List<Object> hiveminds = (List<Object>) getHivemindsMethod.invoke(null);

            if (hiveminds == null || hiveminds.isEmpty()) return Double.MAX_VALUE;

            double minDistance = Double.MAX_VALUE;
            for (Object proto : hiveminds) {
                if (proto instanceof Entity entity) {
                    double distance = Math.sqrt(entity.blockPosition().distSqr(playerPos));
                    if (distance < minDistance) {
                        minDistance = distance;
                    }
                }
            }

            return minDistance;

        } catch (Exception e) {
            return Double.MAX_VALUE;
        }
    }

    /**
     * Buff Spore 生物
     * 基于：污染 + 电压 + 感染强度
     */
    public static void buffSporeMob(Mob mob, double pollutionLevel, double localVoltageTier, ServerLevel level) {
        if (!isSporeLoaded()) return;
        try {
            if (isSporeMob((LivingEntity) mob)) {
                // 1. 污染加成
                double pollutionBonus = pollutionLevel / 100.0;

                // 2. 电压加成（类似 Factorio 的科技等级）
                double voltageBonus = Math.max(0, localVoltageTier - 1) * 0.10;

                // 3. 感染强度加成（新增）
                float infectionIntensity = calculateInfectionIntensity(level);
                double infectionBonus = infectionIntensity / 200.0; // 最多 50% 加成

                // 总倍率
                double totalMultiplier = 1.0 + pollutionBonus + voltageBonus + infectionBonus;

                // 应用血量加成
                AttributeInstance health = mob.getAttribute(Attributes.MAX_HEALTH);
                if (health != null) {
                    double oldHealth = health.getBaseValue();
                    health.setBaseValue(oldHealth * totalMultiplier);
                    mob.setHealth(mob.getMaxHealth());

                    if (IndustrialLogger.isDebugEnabled()) {
                        IndustrialLogger.debugSpore(String.format(
                                "Buffed Spore mob %s: HP %.1f -> %.1f (×%.2f) [P:%.0f%% V:%.0f%% I:%.0f%%]",
                                mob.getType().getDescriptionId(), oldHealth, health.getBaseValue(), totalMultiplier,
                                pollutionBonus * 100, voltageBonus * 100, infectionBonus * 100));
                    }
                }

                // 应用伤害加成（高污染时）
                AttributeInstance damage = mob.getAttribute(Attributes.ATTACK_DAMAGE);
                if (damage != null && pollutionLevel > 50) {
                    double damageBonus = (pollutionLevel / 50.0) * 0.5;
                    damage.setBaseValue(damage.getBaseValue() + damageBonus);
                }
            }
        } catch (Throwable t) {
            IndustrialLogger.error("Error buffing Spore mob: " + t.getMessage());
        }
    }

    // ===== 废弃的方法（保留兼容性，返回计算值） =====

    /**
     * @deprecated Spore 2.0 没有进化阶段，使用感染强度代替
     */
    @Deprecated
    public static int getEvolutionPhase(ServerLevel level) {
        // 基于感染强度估算进化阶段（0-10）
        float intensity = calculateInfectionIntensity(level);
        return (int) (intensity / 10f);
    }

    /**
     * @deprecated Spore 2.0 没有全局感染等级，使用感染强度代替
     */
    @Deprecated
    public static float getInfectionLevel(ServerLevel level) {
        return calculateInfectionIntensity(level);
    }

    /**
     * @deprecated Spore 2.0 没有感染区块统计，基于 Hiveminds 估算
     */
    @Deprecated
    public static int getInfectedChunks(ServerLevel level) {
        // 估算：每个 Hivemind 影响约 20 个区块（半径 20 方块）
        int hiveminds = getActiveHiveminds(level);
        return hiveminds * 20;
    }

    // ===== Hivemind Proximity System Support =====

    /**
     * Gets all active Hiveminds (Proto entities).
     * Used by HivemindProximityManager for caching positions.
     *
     * @return List of Proto objects, or null if Spore not loaded
     */
    public static List<Object> getHiveminds() {
        if (!isSporeLoaded()) {
            IndustrialLogger.debug("[IIC-PollutionSystem] Spore not loaded, cannot get Hiveminds");
            return null;
        }

        try {
            @SuppressWarnings("unchecked")
            List<Object> hiveminds = (List<Object>) getHivemindsMethod.invoke(null);

            IndustrialLogger.debug(String.format(
                    "[IIC-PollutionSystem] Retrieved %d Hiveminds from Spore",
                    hiveminds != null ? hiveminds.size() : 0));

            return hiveminds;
        } catch (Exception e) {
            IndustrialLogger.error("[IIC-PollutionSystem] Error getting Hiveminds list: " + e.getMessage(), e);
            return null;
        }
    }

    /**
     * Gets the node position (infection center) of a Proto entity.
     * <p>
     * Accesses the Proto.NODE EntityDataAccessor to get the BlockPos
     * where the Hivemind's infection center is located.
     *
     * @param proto The Proto entity object
     * @return BlockPos of the node, or BlockPos.ZERO if unavailable
     */
    public static BlockPos getNodePosition(Object proto) {
        if (!isSporeLoaded() || proto == null) {
            IndustrialLogger.debug("[IIC-PollutionSystem] Cannot get node position: Spore not loaded or proto is null");
            return BlockPos.ZERO;
        }

        try {
            if (nodeField == null || getEntityDataMethod == null) {
                IndustrialLogger.debug("[IIC-PollutionSystem] Node field or getEntityData method not available");
                return BlockPos.ZERO;
            }

            // Get the NODE EntityDataAccessor
            Object nodeAccessor = nodeField.get(null);

            // Get entity data from Proto
            Object entityData = getEntityDataMethod.invoke(proto);

            // Get BlockPos from entity data using NODE accessor
            Method getMethod = entityData.getClass().getMethod("get", net.minecraft.network.syncher.EntityDataAccessor.class);
            BlockPos nodePos = (BlockPos) getMethod.invoke(entityData, nodeAccessor);

            IndustrialLogger.debug(String.format(
                    "[IIC-PollutionSystem] Retrieved node position: %s",
                    nodePos != null ? nodePos : "null"));

            return nodePos != null ? nodePos : BlockPos.ZERO;

        } catch (IllegalAccessException e) {
            IndustrialLogger.error("[IIC-PollutionSystem] IllegalAccessException getting Proto node position: " + e.getMessage(), e);
            return BlockPos.ZERO;
        } catch (java.lang.reflect.InvocationTargetException e) {
            IndustrialLogger.error("[IIC-PollutionSystem] InvocationTargetException getting Proto node position: " + e.getMessage(), e);
            return BlockPos.ZERO;
        } catch (NoSuchMethodException e) {
            IndustrialLogger.error("[IIC-PollutionSystem] NoSuchMethodException getting Proto node position: " + e.getMessage(), e);
            return BlockPos.ZERO;
        } catch (Exception e) {
            IndustrialLogger.error("[IIC-PollutionSystem] Unexpected error getting Proto node position: " + e.getMessage(), e);
            return BlockPos.ZERO;
        }
    }

    /**
     * Gets the biomass of a specific Proto entity.
     *
     * @param proto The Proto entity object
     * @return Biomass value, or 0 if unavailable
     */
    public static int getBiomass(Object proto) {
        if (!isSporeLoaded() || proto == null) {
            IndustrialLogger.debug("[IIC-PollutionSystem] Cannot get biomass: Spore not loaded or proto is null");
            return 0;
        }

        try {
            int biomass = (int) getBiomassMethod.invoke(proto);

            IndustrialLogger.debug(String.format(
                    "[IIC-PollutionSystem] Retrieved biomass: %d",
                    biomass));

            return biomass;
        } catch (Exception e) {
            IndustrialLogger.error("[IIC-PollutionSystem] Error getting Proto biomass: " + e.getMessage(), e);
            return 0;
        }
    }
}
