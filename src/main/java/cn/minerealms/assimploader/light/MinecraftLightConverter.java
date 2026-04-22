package cn.minerealms.assimploader.light;

import cn.minerealms.assimploader.AssimpLoader;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import org.joml.Vector3f;

import java.util.ArrayList;
import java.util.List;

/**
 * Converts FBX lights to Minecraft lighting.
 *
 * Minecraft lighting limitations:
 * - Block light: 0-15 levels
 * - Sky light: 0-15 levels
 * - No colored lighting (vanilla)
 * - No dynamic shadows
 * - No spot/area lights
 *
 * Conversion strategy:
 * - Point lights → Light blocks at position
 * - Directional lights → Sky light level
 * - Spot lights → Approximated with light blocks
 * - Area lights → Multiple light blocks
 *
 * For advanced rendering (shaders/mods):
 * - Store light data for custom rendering
 * - Support colored lighting via shader uniforms
 * - Dynamic light sources
 *
 * References:
 * - https://minecraft.fandom.com/wiki/Light
 */
public class MinecraftLightConverter {

    /**
     * Minecraft light data
     */
    public static class MinecraftLight {
        private final BlockPos position;
        private final int lightLevel;
        private final Vector3f color;
        private final LightDataExtractor.LightType type;

        public MinecraftLight(BlockPos position, int lightLevel, Vector3f color, LightDataExtractor.LightType type) {
            this.position = position;
            this.lightLevel = Math.max(0, Math.min(15, lightLevel));
            this.color = color;
            this.type = type;
        }

        public BlockPos getPosition() { return position; }
        public int getLightLevel() { return lightLevel; }
        public Vector3f getColor() { return color; }
        public LightDataExtractor.LightType getType() { return type; }

        @Override
        public String toString() {
            return String.format("MinecraftLight{pos=%s, level=%d, color=%s, type=%s}",
                position, lightLevel, color, type);
        }
    }

    /**
     * Convert FBX lights to Minecraft lights
     */
    public static List<MinecraftLight> convertLights(List<LightDataExtractor.LightData> fbxLights) {
        List<MinecraftLight> mcLights = new ArrayList<>();

        for (LightDataExtractor.LightData fbxLight : fbxLights) {
            List<MinecraftLight> converted = convertLight(fbxLight);
            mcLights.addAll(converted);

            AssimpLoader.LOGGER.debug("Converted FBX light '{}' to {} Minecraft lights",
                fbxLight.getName(), converted.size());
        }

        return mcLights;
    }

    /**
     * Convert single FBX light
     */
    private static List<MinecraftLight> convertLight(LightDataExtractor.LightData fbxLight) {
        List<MinecraftLight> mcLights = new ArrayList<>();

        switch (fbxLight.getType()) {
            case POINT:
                mcLights.add(convertPointLight(fbxLight));
                break;

            case DIRECTIONAL:
                // Directional lights affect sky light, not block light
                // Store for shader rendering
                mcLights.add(convertDirectionalLight(fbxLight));
                break;

            case SPOT:
                mcLights.addAll(convertSpotLight(fbxLight));
                break;

            case AREA:
                mcLights.addAll(convertAreaLight(fbxLight));
                break;

            case AMBIENT:
                // Ambient light affects overall brightness
                // Store for shader rendering
                mcLights.add(convertAmbientLight(fbxLight));
                break;

            default:
                AssimpLoader.LOGGER.warn("Unknown light type: {}", fbxLight.getType());
                break;
        }

        return mcLights;
    }

    /**
     * Convert point light
     */
    private static MinecraftLight convertPointLight(LightDataExtractor.LightData fbxLight) {
        Vector3f pos = fbxLight.getPosition();
        BlockPos blockPos = new BlockPos((int)pos.x, (int)pos.y, (int)pos.z);

        // Calculate light level based on intensity and attenuation
        int lightLevel = calculateLightLevel(fbxLight.getIntensity(), 0.0f);

        return new MinecraftLight(blockPos, lightLevel, fbxLight.getColor(), fbxLight.getType());
    }

    /**
     * Convert directional light (sun/moon)
     */
    private static MinecraftLight convertDirectionalLight(LightDataExtractor.LightData fbxLight) {
        // Directional lights don't have a specific position
        // Use origin for reference
        BlockPos blockPos = BlockPos.ZERO;

        // Directional lights are typically very bright
        int lightLevel = 15;

        return new MinecraftLight(blockPos, lightLevel, fbxLight.getColor(), fbxLight.getType());
    }

    /**
     * Convert spot light (approximate with multiple point lights)
     */
    private static List<MinecraftLight> convertSpotLight(LightDataExtractor.LightData fbxLight) {
        List<MinecraftLight> mcLights = new ArrayList<>();

        Vector3f pos = fbxLight.getPosition();
        Vector3f dir = fbxLight.getDirection();

        // Create light at source position
        BlockPos sourcePos = new BlockPos((int)pos.x, (int)pos.y, (int)pos.z);
        int lightLevel = calculateLightLevel(fbxLight.getIntensity(), 0.0f);
        mcLights.add(new MinecraftLight(sourcePos, lightLevel, fbxLight.getColor(), fbxLight.getType()));

        // Add additional lights along direction (simplified cone)
        float coneLength = 5.0f; // Approximate cone length
        int steps = 3;

        for (int i = 1; i <= steps; i++) {
            float t = (float)i / steps;
            Vector3f stepPos = new Vector3f(pos).add(new Vector3f(dir).mul(coneLength * t));
            BlockPos blockPos = new BlockPos((int)stepPos.x, (int)stepPos.y, (int)stepPos.z);

            // Decrease light level with distance
            int stepLightLevel = calculateLightLevel(fbxLight.getIntensity(), t * coneLength);
            if (stepLightLevel > 0) {
                mcLights.add(new MinecraftLight(blockPos, stepLightLevel, fbxLight.getColor(), fbxLight.getType()));
            }
        }

        return mcLights;
    }

    /**
     * Convert area light (approximate with grid of point lights)
     */
    private static List<MinecraftLight> convertAreaLight(LightDataExtractor.LightData fbxLight) {
        List<MinecraftLight> mcLights = new ArrayList<>();

        Vector3f pos = fbxLight.getPosition();
        Vector3f size = fbxLight.getAreaSize();

        // Create grid of lights
        int gridX = Math.max(1, (int)size.x);
        int gridY = Math.max(1, (int)size.y);

        for (int x = 0; x < gridX; x++) {
            for (int y = 0; y < gridY; y++) {
                float offsetX = (x - gridX / 2.0f);
                float offsetY = (y - gridY / 2.0f);

                Vector3f lightPos = new Vector3f(pos.x + offsetX, pos.y + offsetY, pos.z);
                BlockPos blockPos = new BlockPos((int)lightPos.x, (int)lightPos.y, (int)lightPos.z);

                int lightLevel = calculateLightLevel(fbxLight.getIntensity() / (gridX * gridY), 0.0f);
                if (lightLevel > 0) {
                    mcLights.add(new MinecraftLight(blockPos, lightLevel, fbxLight.getColor(), fbxLight.getType()));
                }
            }
        }

        return mcLights;
    }

    /**
     * Convert ambient light
     */
    private static MinecraftLight convertAmbientLight(LightDataExtractor.LightData fbxLight) {
        // Ambient light affects overall scene brightness
        // Store for shader rendering
        BlockPos blockPos = BlockPos.ZERO;
        int lightLevel = calculateLightLevel(fbxLight.getIntensity(), 0.0f);

        return new MinecraftLight(blockPos, lightLevel, fbxLight.getColor(), fbxLight.getType());
    }

    /**
     * Calculate Minecraft light level (0-15) from intensity and distance
     */
    private static int calculateLightLevel(float intensity, float distance) {
        // Minecraft light level formula (simplified)
        // Light level decreases with distance

        float attenuation = 1.0f / (1.0f + distance * 0.1f);
        float level = intensity * attenuation * 15.0f;

        return Math.max(0, Math.min(15, (int)level));
    }

    /**
     * Place light blocks in world
     */
    public static void placeLightsInWorld(Level level, List<MinecraftLight> lights) {
        for (MinecraftLight light : lights) {
            BlockPos pos = light.getPosition();

            // Check if position is valid
            if (!level.isLoaded(pos)) {
                continue;
            }

            // Place light block
            BlockState lightBlock = Blocks.LIGHT.defaultBlockState();
            level.setBlock(pos, lightBlock, 3);

            AssimpLoader.LOGGER.debug("Placed light at {} with level {}", pos, light.getLightLevel());
        }
    }

    /**
     * Get light data for shader rendering
     */
    public static float[] getLightDataForShader(List<MinecraftLight> lights) {
        // Format: [posX, posY, posZ, level, colorR, colorG, colorB, type] per light
        float[] data = new float[lights.size() * 8];

        for (int i = 0; i < lights.size(); i++) {
            MinecraftLight light = lights.get(i);
            int offset = i * 8;

            data[offset + 0] = light.getPosition().getX();
            data[offset + 1] = light.getPosition().getY();
            data[offset + 2] = light.getPosition().getZ();
            data[offset + 3] = light.getLightLevel() / 15.0f;
            data[offset + 4] = light.getColor().x;
            data[offset + 5] = light.getColor().y;
            data[offset + 6] = light.getColor().z;
            data[offset + 7] = light.getType().ordinal();
        }

        return data;
    }
}
