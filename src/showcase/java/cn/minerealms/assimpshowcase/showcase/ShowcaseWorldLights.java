package cn.minerealms.assimpshowcase.showcase;

import cn.minerealms.assimploader.light.MinecraftLightConverter.MinecraftLight;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.LightBlock;

import java.util.List;

/** Explicit showcase world edits; rendering lights in the engine never places blocks. */
public final class ShowcaseWorldLights {
    private ShowcaseWorldLights() {}

    public static void placeLightsInWorld(ServerLevel level, List<MinecraftLight> lights) {
        if (!level.getServer().isSameThread()) throw new IllegalStateException("Expected server thread");
        for (MinecraftLight light : lights) {
            var position = light.getPosition();
            if (level.isLoaded(position) && level.getBlockState(position).canBeReplaced()) {
                level.setBlock(position, Blocks.LIGHT.defaultBlockState()
                        .setValue(LightBlock.LEVEL, Math.max(0, Math.min(15, light.getLightLevel()))), 3);
            }
        }
    }
}
