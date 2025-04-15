package cn.minerealms.iic.industrial;

import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.entity.BlockEntity;

import java.util.ArrayList;
import java.util.List;

public class MachineScanner {

    public record ScanResult(List<Integer> tiers, List<Double> weights) {}

    public static ScanResult scanNearbyMachines(Player player, int radius) {
        Level level = player.level();
        BlockPos center = player.blockPosition();
        List<Integer> tiers = new ArrayList<>();
        List<Double> weights = new ArrayList<>();

        for (BlockPos pos : BlockPos.betweenClosed(center.offset(-radius, -radius / 2, -radius),
                                                   center.offset(radius, radius / 2, radius))) {
            BlockEntity be = level.getBlockEntity(pos);
            // 修改：扫描所有有电的机器，不仅仅是正在工作的
            if (GTIntegration.isGTMachine(be) && GTIntegration.hasEnergyOrActive(be)) {
                int tier = GTIntegration.getVoltageTier(be);
                if (tier >= 0) {
                    double distSq = pos.distSqr(center);
                    double distanceWeight = 1.0 / (1.0 + Math.sqrt(distSq) * 0.1);
                    double typeWeight = GTIntegration.isMultiblock(be) ? 5.0 : 1.0;

                    tiers.add(tier);
                    weights.add(distanceWeight * typeWeight);

                    // 调试日志
                    IndustrialLogger.debugMachine(String.format(
                            "Scanned machine at %s | Tier: %d | Active: %s",
                            pos, tier, GTIntegration.isMachineActive(be)));
                }
            }
        }

        // 输出扫描结果
        if (IndustrialLogger.isDebugEnabled() && !tiers.isEmpty()) {
            IndustrialLogger.debugMachine(String.format(
                    "Scan complete: Found %d machines with energy", tiers.size()));
        }

        return new ScanResult(tiers, weights);
    }
    
    public static double scanNearbyVoltageTier(ServerLevel level, BlockPos center) {
        int maxTier = 0;
        int radius = 8;
        
        for (BlockPos pos : BlockPos.betweenClosed(center.offset(-radius, -radius / 2, -radius), 
                                                   center.offset(radius, radius / 2, radius))) {
            BlockEntity be = level.getBlockEntity(pos);
            if (GTIntegration.isGTMachine(be) && GTIntegration.hasEnergyOrActive(be)) {
                int tier = GTIntegration.getVoltageTier(be);
                if (tier > maxTier) {
                    maxTier = tier;
                }
            }
        }
        return maxTier;
    }

    public static double scanNearbyVoltageTierSafely(ServerLevel level, BlockPos center) {
        int maxTier = 0;
        int radius = 8;
        
        for (BlockPos pos : BlockPos.betweenClosed(center.offset(-radius, -radius / 2, -radius), 
                                                   center.offset(radius, radius / 2, radius))) {
            // Check if chunk is loaded before trying to get block entity to avoid triggering chunk loads
            if (level.hasChunkAt(pos)) {
                try {
                    BlockEntity be = level.getBlockEntity(pos);
                    if (GTIntegration.isGTMachine(be) && GTIntegration.hasEnergyOrActive(be)) {
                        int tier = GTIntegration.getVoltageTier(be);
                        if (tier > maxTier) {
                            maxTier = tier;
                        }
                    }
                } catch (Exception e) {
                    // Ignore exceptions during async access
                }
            }
        }
        return maxTier;
    }

    public static int scanNearbyVoltageTierMedianSafely(ServerLevel level, BlockPos center, int radius) {
        List<Integer> tiers = new ArrayList<>();
        
        for (BlockPos pos : BlockPos.betweenClosed(center.offset(-radius, -radius / 2, -radius), 
                                                   center.offset(radius, radius / 2, radius))) {
            if (level.hasChunkAt(pos)) {
                try {
                    BlockEntity be = level.getBlockEntity(pos);
                    if (GTIntegration.isGTMachine(be) && GTIntegration.hasEnergyOrActive(be)) {
                        int tier = GTIntegration.getVoltageTier(be);
                        if (tier >= 0) {
                            tiers.add(tier);
                        }
                    }
                } catch (Exception e) {
                    // Ignore exceptions during async access
                }
            }
        }
        
        if (tiers.isEmpty()) return 0;
        
        // 取中位数 (Median)
        tiers.sort(Integer::compareTo);
        return tiers.get(tiers.size() / 2);
    }
}
