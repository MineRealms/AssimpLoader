package cn.minerealms.iic.industrial;

import net.minecraft.core.BlockPos;
import net.minecraft.world.entity.player.Player;
import net.minecraft.server.level.ServerLevel;

public class HazardScanner {

    private static boolean gtceuHazardAvailable = true;
    private static boolean checkedAvailability = false;

    public static double getPollutionLevel(Player player) {
        if (!(player.level() instanceof ServerLevel serverLevel)) return 0;

        BlockPos pos = player.blockPosition();

        // 尝试使用 GTCEU 的污染系统
        if (gtceuHazardAvailable) {
            try {
                Class<?> hazardDataClass = Class.forName("com.gregtechceu.gtceu.common.capability.EnvironmentalHazardSavedData");
                Object data = hazardDataClass.getMethod("getOrCreate", ServerLevel.class).invoke(null, serverLevel);

                // 尝试获取区域污染
                try {
                    Object zone = hazardDataClass.getMethod("getZoneByContainedPos", BlockPos.class).invoke(data, pos);
                    if (zone != null) {
                        double strength = (double) zone.getClass().getMethod("strength").invoke(zone);
                        IndustrialLogger.debugPollution(String.format("GTCEU pollution at %s: %.2f", pos, strength));
                        return strength;
                    }
                } catch (Exception e) {
                    // Fallback: 搜索附近所有区域
                    try {
                        Object zones = hazardDataClass.getMethod("getHazardZones").invoke(data);
                        if (zones instanceof java.util.Map) {
                            double totalStrength = 0;
                            for (Object zone : ((java.util.Map<?, ?>) zones).values()) {
                                Object source = zone.getClass().getMethod("source").invoke(zone);
                                if (source instanceof BlockPos sourcePos) {
                                    if (sourcePos.distSqr(pos) < 1024) { // 32 block radius
                                        double strength = (double) zone.getClass().getMethod("strength").invoke(zone);
                                        totalStrength += strength;
                                    }
                                }
                            }
                            if (totalStrength > 0) {
                                IndustrialLogger.debugPollution(String.format("GTCEU pollution (nearby zones) at %s: %.2f", pos, totalStrength));
                            }
                            return totalStrength;
                        }
                    } catch (Exception e2) {
                        // 完全失败
                    }
                }
            } catch (ClassNotFoundException e) {
                if (!checkedAvailability) {
                    IndustrialLogger.warn("GTCEU EnvironmentalHazardSavedData not found, pollution detection disabled");
                    gtceuHazardAvailable = false;
                    checkedAvailability = true;
                }
            } catch (Exception e) {
                if (!checkedAvailability) {
                    IndustrialLogger.error("Error accessing GTCEU pollution data: " + e.getMessage());
                    gtceuHazardAvailable = false;
                    checkedAvailability = true;
                }
            }
        }

        // Fallback: 使用 PollutionManager 的临时污染
        double tempPollution = PollutionManager.getTemporaryPollution(new net.minecraft.world.level.ChunkPos(pos));
        if (tempPollution > 0) {
            IndustrialLogger.debugPollution(String.format("Temporary pollution at %s: %.2f", pos, tempPollution));
        }
        return tempPollution;
    }
}
