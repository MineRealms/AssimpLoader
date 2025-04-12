package io.github.flemmli97.improvedmobs.industrial;

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
            if (GTIntegration.isGTMachine(be) && GTIntegration.isMachineActive(be)) {
                int tier = GTIntegration.getVoltageTier(be);
                if (tier >= 0) {
                    double distSq = pos.distSqr(center);
                    double distanceWeight = 1.0 / (1.0 + Math.sqrt(distSq) * 0.1);
                    double typeWeight = GTIntegration.isMultiblock(be) ? 5.0 : 1.0;
                    
                    tiers.add(tier);
                    weights.add(distanceWeight * typeWeight);
                }
            }
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
}
