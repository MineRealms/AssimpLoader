package io.github.flemmli97.improvedmobs.industrial;

import io.github.flemmli97.improvedmobs.api.difficulty.DifficultyGetter;
import io.github.flemmli97.improvedmobs.config.Config;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.phys.Vec3;

public class IndustrialDifficultyGetter implements DifficultyGetter {

    @Override
    public float getDifficulty(ServerLevel level, Vec3 pos) {
        float maxBonus = 0;
        for (Player player : level.players()) {
            if (player.position().closerThan(pos, 64)) {
                float playerBonus = IndustrialDifficultyManager.getDifficultyFor(player);
                if (playerBonus > maxBonus) {
                    maxBonus = playerBonus;
                }
            }
        }
        return maxBonus;
    }

    @Override
    public Config.IntegrationType getType() {
        return Config.IntegrationType.ADD;
    }
}
