package cn.minerealms.assimploader.material;

import cn.minerealms.assimploader.debug.DebugManager;
import cn.minerealms.assimploader.debug.DebugManager.DebugCategory;
import com.mojang.blaze3d.vertex.DefaultVertexFormat;
import com.mojang.blaze3d.vertex.VertexFormat;
import net.minecraft.client.renderer.RenderStateShard;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.resources.ResourceLocation;

/**
 * Converts PBR materials to Minecraft RenderType.
 *
 * Maps PBR material properties to appropriate Minecraft render states:
 * - Alpha mode → RenderType (solid/cutout/translucent)
 * - Textures → Texture binding
 * - Double-sided → Cull state
 * - Emissive → Lightmap state
 *
 * Minecraft RenderType limitations:
 * - No PBR shaders in vanilla (requires mods like Iris/Optifine)
 * - Limited to basic texture + color + alpha
 * - No metallic/roughness/normal maps in vanilla
 *
 * For advanced rendering:
 * - Use custom shaders via shader mods
 * - Export material data for shader uniforms
 * - Implement custom RenderType with PBR support
 *
 * References:
 * - https://forge.gemwire.uk/wiki/Rendering
 */
public class MaterialConverter {

    /**
     * Convert PBRMaterial to Minecraft RenderType
     */
    public static RenderType toRenderType(PBRMaterial material) {
        if (material == null) {
            return RenderType.solid();
        }

        // Select RenderType based on alpha mode
        switch (material.getAlphaMode()) {
            case BLEND:
                return createTranslucentRenderType(material);
            case MASK:
                return createCutoutRenderType(material);
            case OPAQUE:
            default:
                return createSolidRenderType(material);
        }
    }

    /**
     * Create solid (opaque) RenderType
     */
    private static RenderType createSolidRenderType(PBRMaterial material) {
        ResourceLocation texture = material.getBaseColorTexture();

        if (texture != null) {
            // Use textured solid render type
            return RenderType.entitySolid(texture);
        } else {
            // Use basic solid render type
            return RenderType.solid();
        }
    }

    /**
     * Create cutout (alpha test) RenderType
     */
    private static RenderType createCutoutRenderType(PBRMaterial material) {
        ResourceLocation texture = material.getBaseColorTexture();

        if (texture != null) {
            // Use textured cutout render type
            return RenderType.entityCutout(texture);
        } else {
            // Use basic cutout render type
            return RenderType.cutout();
        }
    }

    /**
     * Create translucent (alpha blend) RenderType
     */
    private static RenderType createTranslucentRenderType(PBRMaterial material) {
        ResourceLocation texture = material.getBaseColorTexture();

        if (texture != null) {
            // Use textured translucent render type
            return RenderType.entityTranslucent(texture);
        } else {
            // Use basic translucent render type
            return RenderType.translucent();
        }
    }

    /**
     * Create custom RenderType with PBR support (requires shader mod)
     */
    public static RenderType createPBRRenderType(PBRMaterial material) {
        ResourceLocation texture = material.getBaseColorTexture();
        if (texture == null) {
            texture = new ResourceLocation("minecraft", "textures/block/white_concrete.png");
        }

        // For now, fallback to standard render types
        // Custom PBR RenderType requires shader mod integration
        return toRenderType(material);
    }

    /**
     * Get transparency state based on alpha mode
     */
    private static RenderType.CompositeState.CompositeStateBuilder getTransparencyState(PBRMaterial material) {
        // Simplified version - just return basic composite state
        return RenderType.CompositeState.builder();
    }

    /**
     * Check if material requires custom rendering
     */
    public static boolean requiresCustomRendering(PBRMaterial material) {
        // Materials with PBR properties need custom shaders
        return material.getMetallicFactor() > 0.0f ||
               material.getRoughnessFactor() != 0.5f ||
               material.getNormalTexture() != null ||
               material.getMetallicRoughnessTexture() != null ||
               material.isEmissive();
    }

    /**
     * Get appropriate RenderType for block rendering
     */
    public static RenderType toBlockRenderType(PBRMaterial material) {
        switch (material.getAlphaMode()) {
            case BLEND:
                return RenderType.translucent();
            case MASK:
                return RenderType.cutout();
            case OPAQUE:
            default:
                return RenderType.solid();
        }
    }

    /**
     * Get appropriate RenderType for entity rendering
     */
    public static RenderType toEntityRenderType(PBRMaterial material) {
        ResourceLocation texture = material.getBaseColorTexture();

        if (texture == null) {
            // Fallback to basic render types
            return toRenderType(material);
        }

        switch (material.getAlphaMode()) {
            case BLEND:
                return RenderType.entityTranslucent(texture);
            case MASK:
                return RenderType.entityCutout(texture);
            case OPAQUE:
            default:
                return RenderType.entitySolid(texture);
        }
    }

    /**
     * Get appropriate RenderType for item rendering
     */
    public static RenderType toItemRenderType(PBRMaterial material) {
        // Items typically use entity render types
        return toEntityRenderType(material);
    }

    /**
     * Convert material to Minecraft color (for untextured rendering)
     */
    public static int toMinecraftColor(PBRMaterial material) {
        org.joml.Vector4f baseColor = material.getBaseColor();

        int r = (int)(baseColor.x * 255);
        int g = (int)(baseColor.y * 255);
        int b = (int)(baseColor.z * 255);
        int a = (int)(baseColor.w * 255);

        return (a << 24) | (r << 16) | (g << 8) | b;
    }

    /**
     * Check if material is compatible with vanilla rendering
     */
    public static boolean isVanillaCompatible(PBRMaterial material) {
        // Vanilla can only handle basic textures and alpha
        return !requiresCustomRendering(material);
    }

    /**
     * Log material conversion info
     */
    public static void logConversion(PBRMaterial material, RenderType renderType) {
        DebugManager.debug(DebugCategory.MATERIAL, "Converted material '{}' to RenderType: {}",
            material.getName(), renderType);

        if (requiresCustomRendering(material)) {
            DebugManager.warn(DebugCategory.MATERIAL, "Material '{}' requires custom rendering (PBR properties present)",
                material.getName());
        }
    }
}
