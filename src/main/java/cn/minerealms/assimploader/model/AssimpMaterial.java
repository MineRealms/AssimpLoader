package cn.minerealms.assimploader.model;

import org.lwjgl.assimp.AIColor4D;
import org.lwjgl.assimp.AIMaterial;
import org.lwjgl.assimp.AIString;

import static org.lwjgl.assimp.Assimp.*;

/**
 * Wrapper for Assimp AIMaterial containing material properties.
 *
 * Supports:
 * - Diffuse, specular, ambient colors
 * - Texture maps (diffuse, normal, specular, etc.)
 * - PBR properties (metallic, roughness)
 * - Transparency and blending
 */
public class AssimpMaterial {

    private final AIMaterial aiMaterial;
    private final int index;

    public AssimpMaterial(AIMaterial aiMaterial, int index) {
        this.aiMaterial = aiMaterial;
        this.index = index;
    }

    public int getIndex() {
        return index;
    }

    /**
     * Get diffuse texture path if exists
     */
    public String getDiffuseTexture() {
        AIString path = AIString.calloc();
        if (aiGetMaterialTexture(aiMaterial, aiTextureType_DIFFUSE, 0, path, (int[]) null, null, null, null, null, null) == aiReturn_SUCCESS) {
            String texPath = path.dataString();
            path.free();
            return texPath;
        }
        path.free();
        return null;
    }

    /**
     * Check if material has any texture
     */
    public boolean hasTexture() {
        String diffuse = getDiffuseTexture();
        return diffuse != null && !diffuse.isEmpty();
    }

    /**
     * Get normal map texture path if exists
     */
    public String getNormalTexture() {
        AIString path = AIString.calloc();
        if (aiGetMaterialTexture(aiMaterial, aiTextureType_NORMALS, 0, path, (int[]) null, null, null, null, null, null) == aiReturn_SUCCESS) {
            String texPath = path.dataString();
            path.free();
            return texPath;
        }
        path.free();
        return null;
    }

    /**
     * Get diffuse color from material
     * Returns RGBA values in range [0, 1]
     */
    public float[] getDiffuseColor() {
        AIColor4D color = AIColor4D.create();

        // Try standard diffuse color first
        if (aiGetMaterialColor(aiMaterial, AI_MATKEY_COLOR_DIFFUSE, aiTextureType_NONE, 0, color) == aiReturn_SUCCESS) {
            float[] result = new float[]{color.r(), color.g(), color.b(), color.a()};

            // Check for separate opacity/transparency properties
            float opacity = getOpacityFromProperties();
            if (opacity >= 0.0f && opacity < 0.99f) {
                // Use the opacity property if it's explicitly set and not fully opaque
                result[3] = opacity;
                cn.minerealms.assimploader.AssimpLoader.LOGGER.info("Material {} diffuse color: R={}, G={}, B={}, A={} (overridden by opacity: {})",
                    index, result[0], result[1], result[2], result[3], opacity);
            } else {
                cn.minerealms.assimploader.AssimpLoader.LOGGER.info("Material {} diffuse color: R={}, G={}, B={}, A={}",
                    index, result[0], result[1], result[2], result[3]);
            }
            return result;
        }

        // Try base color (PBR materials)
        if (aiGetMaterialColor(aiMaterial, AI_MATKEY_BASE_COLOR, aiTextureType_NONE, 0, color) == aiReturn_SUCCESS) {
            float[] result = new float[]{color.r(), color.g(), color.b(), color.a()};

            float opacity = getOpacityFromProperties();
            if (opacity >= 0.0f && opacity < 0.99f) {
                result[3] = opacity;
                cn.minerealms.assimploader.AssimpLoader.LOGGER.info("Material {} base color: R={}, G={}, B={}, A={} (overridden by opacity: {})",
                    index, result[0], result[1], result[2], result[3], opacity);
            } else {
                cn.minerealms.assimploader.AssimpLoader.LOGGER.info("Material {} base color: R={}, G={}, B={}, A={}",
                    index, result[0], result[1], result[2], result[3]);
            }
            return result;
        }

        // Log warning and return white as default
        cn.minerealms.assimploader.AssimpLoader.LOGGER.warn("Material {} has no diffuse or base color, using white", index);
        return new float[]{1.0f, 1.0f, 1.0f, 1.0f};
    }

    /**
     * Get opacity from material properties (separate from diffuse alpha)
     * Returns -1 if no opacity property is found
     *
     * For FBX PhongE materials:
     * - TransparencyFactor: 0 = opaque, 1 = fully transparent (needs inversion)
     * - TransparentColor: RGB color that defines transparency
     */
    private float getOpacityFromProperties() {
        float[] value = new float[1];
        int[] pMax = new int[1];

        // Try AI_MATKEY_OPACITY first (FBX exports this as the primary opacity value)
        // Opacity: 0 = transparent, 1 = opaque (direct)
        if (aiGetMaterialFloatArray(aiMaterial, AI_MATKEY_OPACITY, aiTextureType_NONE, 0, value, pMax) == aiReturn_SUCCESS) {
            // Only use if it's explicitly set (not default 0.0 or 1.0)
            if (value[0] > 0.01f && value[0] < 0.99f) {
                cn.minerealms.assimploader.AssimpLoader.LOGGER.info("Material {} Opacity: {}", index, value[0]);
                return value[0];
            }
        }

        // Try AI_MATKEY_TRANSPARENCYFACTOR (backup, some formats use this)
        // TransparencyFactor: 0 = opaque, 1 = transparent (inverted)
        if (aiGetMaterialFloatArray(aiMaterial, AI_MATKEY_TRANSPARENCYFACTOR, aiTextureType_NONE, 0, value, pMax) == aiReturn_SUCCESS) {
            // Only use if it's explicitly set (not default 0.0 or 1.0)
            if (value[0] > 0.01f && value[0] < 0.99f) {
                float opacity = 1.0f - value[0];  // Invert: 0 becomes 1 (opaque), 1 becomes 0 (transparent)
                cn.minerealms.assimploader.AssimpLoader.LOGGER.info("Material {} TransparencyFactor: {} -> opacity: {}",
                    index, value[0], opacity);
                return opacity;
            }
        }

        // Check TransparentColor (RGB color defining transparency)
        AIColor4D transparentColor = AIColor4D.create();
        if (aiGetMaterialColor(aiMaterial, AI_MATKEY_COLOR_TRANSPARENT, aiTextureType_NONE, 0, transparentColor) == aiReturn_SUCCESS) {
            // If transparent color is black (0,0,0), material is opaque
            // If transparent color is white (1,1,1), material is fully transparent
            float avgTransparency = (transparentColor.r() + transparentColor.g() + transparentColor.b()) / 3.0f;
            if (avgTransparency > 0.01f) {
                float opacity = 1.0f - avgTransparency;
                cn.minerealms.assimploader.AssimpLoader.LOGGER.info("Material {} TransparentColor: ({},{},{}) -> opacity: {}",
                    index, transparentColor.r(), transparentColor.g(), transparentColor.b(), opacity);
                return opacity;
            }
        }

        cn.minerealms.assimploader.AssimpLoader.LOGGER.info("Material {} has no transparency properties", index);
        return -1.0f;  // No opacity property found
    }

    /**
     * Get opacity/transparency value
     * Returns value in range [0, 1] where 0 = fully transparent, 1 = fully opaque
     * This returns the final opacity considering all sources
     */
    public float getOpacity() {
        float[] diffuse = getDiffuseColor();
        return diffuse[3];  // Return the alpha from diffuse color (which may have been overridden)
    }

    /**
     * Check if material is transparent (has alpha < 1.0)
     */
    public boolean isTransparent() {
        float opacity = getOpacity();
        cn.minerealms.assimploader.AssimpLoader.LOGGER.info("Material {} final opacity: {}", index, opacity);

        if (opacity < 0.99f) {
            cn.minerealms.assimploader.AssimpLoader.LOGGER.info("Material {} is TRANSPARENT (opacity < 0.99)", index);
            return true;
        }

        cn.minerealms.assimploader.AssimpLoader.LOGGER.info("Material {} is OPAQUE", index);
        return false;
    }

    /**
     * Get specular color from material
     */
    public float[] getSpecularColor() {
        AIColor4D color = AIColor4D.create();
        if (aiGetMaterialColor(aiMaterial, AI_MATKEY_COLOR_SPECULAR, aiTextureType_NONE, 0, color) == aiReturn_SUCCESS) {
            return new float[]{color.r(), color.g(), color.b(), color.a()};
        }
        return new float[]{1.0f, 1.0f, 1.0f, 1.0f};
    }

    /**
     * Get ambient color from material
     */
    public float[] getAmbientColor() {
        AIColor4D color = AIColor4D.create();
        if (aiGetMaterialColor(aiMaterial, AI_MATKEY_COLOR_AMBIENT, aiTextureType_NONE, 0, color) == aiReturn_SUCCESS) {
            return new float[]{color.r(), color.g(), color.b(), color.a()};
        }
        return new float[]{0.2f, 0.2f, 0.2f, 1.0f};
    }

    public AIMaterial getNativeMaterial() {
        return aiMaterial;
    }
}
