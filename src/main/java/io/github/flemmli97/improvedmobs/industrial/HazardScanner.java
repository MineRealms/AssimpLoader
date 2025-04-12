package io.github.flemmli97.improvedmobs.industrial;

import com.gregtechceu.gtceu.common.capability.EnvironmentalHazardSavedData;
import net.minecraft.core.BlockPos;
import net.minecraft.world.entity.player.Player;
import net.minecraft.server.level.ServerLevel;

public class HazardScanner {

    public static double getPollutionLevel(Player player) {
        if (!(player.level() instanceof ServerLevel serverLevel)) return 0;
        
        BlockPos pos = player.blockPosition();
        try {
            EnvironmentalHazardSavedData data = EnvironmentalHazardSavedData.getOrCreate(serverLevel);
            EnvironmentalHazardSavedData.HazardZone zone = data.getZoneByContainedPos(pos);
            if (zone != null) {
                return zone.strength();
            }
        } catch (Exception e) {
            // Fallback to searching all zones if specific lookup fails
            try {
                EnvironmentalHazardSavedData data = EnvironmentalHazardSavedData.getOrCreate(serverLevel);
                return data.getHazardZones().values().stream()
                    .filter(z -> z.source().distSqr(pos) < 1024) // Approx 32 block radius
                    .mapToDouble(EnvironmentalHazardSavedData.HazardZone::strength)
                    .sum();
            } catch (Exception e2) {
                return 0;
            }
        }
        return 0;
    }
}
