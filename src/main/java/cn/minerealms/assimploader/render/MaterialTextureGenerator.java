package cn.minerealms.assimploader.render;

import cn.minerealms.assimploader.AssimpLoader;
import cn.minerealms.assimploader.model.AssimpMaterial;
import com.mojang.blaze3d.platform.NativeImage;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.texture.DynamicTexture;
import net.minecraft.resources.ResourceLocation;

import java.util.HashMap;
import java.util.Map;

/**
 * Generates dynamic textures from material colors.
 *
 * For materials without textures (like PhongE with only diffuse color),
 * this creates a 1x1 solid color texture that can be used in Minecraft's
 * normal rendering pipeline.
 */
public class MaterialTextureGenerator {

    private static final Map<String, ResourceLocation> GENERATED_TEXTURES = new HashMap<>();

    /**
     * Get or create a texture for a material's diffuse color
     *
     * @param material The material to generate texture for
     * @param materialIndex Index for unique naming
     * @return ResourceLocation of the generated texture
     */
    public static ResourceLocation getOrCreateColorTexture(AssimpMaterial material, int materialIndex) {
        if (material == null || material.getDiffuseColor() == null) {
            // Return white texture as fallback
            return new ResourceLocation("minecraft", "white");
        }

        float[] color = material.getDiffuseColor();
        String key = String.format("material_%d_%.3f_%.3f_%.3f_%.3f",
            materialIndex, color[0], color[1], color[2], color[3]);

        return GENERATED_TEXTURES.computeIfAbsent(key, k -> {
            try {
                // Create 1x1 texture with material color
                NativeImage image = new NativeImage(1, 1, false);

                int r = (int) (color[0] * 255);
                int g = (int) (color[1] * 255);
                int b = (int) (color[2] * 255);
                int a = (int) (color[3] * 255);

                // ABGR format
                int abgr = (a << 24) | (b << 16) | (g << 8) | r;
                image.setPixelRGBA(0, 0, abgr);

                // Register as dynamic texture
                DynamicTexture dynamicTexture = new DynamicTexture(image);
                ResourceLocation textureLoc = new ResourceLocation(AssimpLoader.MODID, "generated/" + k);

                Minecraft.getInstance().getTextureManager().register(textureLoc, dynamicTexture);

                AssimpLoader.LOGGER.info("Generated color texture: {} for material color ({}, {}, {}, {})",
                    textureLoc, color[0], color[1], color[2], color[3]);

                return textureLoc;
            } catch (Exception e) {
                AssimpLoader.LOGGER.error("Failed to generate color texture for material {}", materialIndex, e);
                return new ResourceLocation("minecraft", "white");
            }
        });
    }

    /**
     * Clear all generated textures (call on resource reload)
     */
    public static void clearCache() {
        GENERATED_TEXTURES.clear();
    }
}
