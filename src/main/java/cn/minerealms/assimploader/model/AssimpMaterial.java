package cn.minerealms.assimploader.model;
import cn.minerealms.assimploader.debug.DebugManager;
import cn.minerealms.assimploader.debug.DebugManager.DebugCategory;

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
     * Returns either a file path or an embedded texture reference like "*0", "*1", etc.
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
     * Check if the texture path refers to an embedded texture
     * Embedded textures are referenced as "*0", "*1", "*2", etc.
     */
    public boolean isEmbeddedTexture(String texturePath) {
        return texturePath != null && texturePath.startsWith("*");
    }

    /**
     * Get the embedded texture index from a texture path like "*0", "*1"
     * Returns -1 if not an embedded texture reference
     */
    public int getEmbeddedTextureIndex(String texturePath) {
        if (isEmbeddedTexture(texturePath)) {
            try {
                return Integer.parseInt(texturePath.substring(1));
            } catch (NumberFormatException e) {
                return -1;
            }
        }
        return -1;
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
     * Get specular map texture path if exists
     */
    public String getSpecularTexture() {
        AIString path = AIString.calloc();
        if (aiGetMaterialTexture(aiMaterial, aiTextureType_SPECULAR, 0, path, (int[]) null, null, null, null, null, null) == aiReturn_SUCCESS) {
            String texPath = path.dataString();
            path.free();
            return texPath;
        }
        path.free();
        return null;
    }

    /**
     * Get normal map path for PBR rendering.
     * Returns the path from material if available, otherwise generates conventional path.
     *
     * @return Normal map path, or null if no diffuse texture exists
     */
    public String getNormalMapPath() {
        // First check if material has explicit normal map
        String normalTex = getNormalTexture();
        if (normalTex != null && !normalTex.isEmpty()) {
            return normalTex;
        }

        // Generate conventional path from diffuse texture
        String diffuseTex = getDiffuseTexture();
        if (diffuseTex != null && !diffuseTex.isEmpty() && !isEmbeddedTexture(diffuseTex)) {
            // Convention: texture.png -> texture_n.png
            int dotIndex = diffuseTex.lastIndexOf('.');
            if (dotIndex > 0) {
                return diffuseTex.substring(0, dotIndex) + "_n" + diffuseTex.substring(dotIndex);
            }
        }

        return null;
    }

    /**
     * Get specular map path for PBR rendering.
     * Returns the path from material if available, otherwise generates conventional path.
     *
     * @return Specular map path, or null if no diffuse texture exists
     */
    public String getSpecularMapPath() {
        // First check if material has explicit specular map
        String specularTex = getSpecularTexture();
        if (specularTex != null && !specularTex.isEmpty()) {
            return specularTex;
        }

        // Generate conventional path from diffuse texture
        String diffuseTex = getDiffuseTexture();
        if (diffuseTex != null && !diffuseTex.isEmpty() && !isEmbeddedTexture(diffuseTex)) {
            // Convention: texture.png -> texture_s.png
            int dotIndex = diffuseTex.lastIndexOf('.');
            if (dotIndex > 0) {
                return diffuseTex.substring(0, dotIndex) + "_s" + diffuseTex.substring(dotIndex);
            }
        }

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
            }
            return result;
        }

        // Try base color (PBR materials)
        if (aiGetMaterialColor(aiMaterial, AI_MATKEY_BASE_COLOR, aiTextureType_NONE, 0, color) == aiReturn_SUCCESS) {
            float[] result = new float[]{color.r(), color.g(), color.b(), color.a()};

            float opacity = getOpacityFromProperties();
            if (opacity >= 0.0f && opacity < 0.99f) {
                result[3] = opacity;
            }
            return result;
        }

        // Return white as default (no warning needed - this is normal for some FBX materials)
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
                return value[0];
            }
        }

        // Try AI_MATKEY_TRANSPARENCYFACTOR (backup, some formats use this)
        // TransparencyFactor: 0 = opaque, 1 = transparent (inverted)
        if (aiGetMaterialFloatArray(aiMaterial, AI_MATKEY_TRANSPARENCYFACTOR, aiTextureType_NONE, 0, value, pMax) == aiReturn_SUCCESS) {
            // Only use if it's explicitly set (not default 0.0 or 1.0)
            if (value[0] > 0.01f && value[0] < 0.99f) {
                float opacity = 1.0f - value[0];  // Invert: 0 becomes 1 (opaque), 1 becomes 0 (transparent)
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
                return opacity;
            }
        }

        return -1.0f;  // No opacity property found
    }

    /**
     * Get opacity/transparency value
     * Returns value in range [0, 1] where 0 = fully transparent, 1 = fully opaque
     * This returns the final opacity considering all sources
     */
    public float getOpacity() {
        float[] diffuse = getDiffuseColor();
        float opacity = diffuse[3];  // Return the alpha from diffuse color (which may have been overridden)

        // Debug log for transparency detection
        if (opacity < 0.99f) {
            DebugManager.debug(DebugCategory.CORE, 
                "Material {} has transparency: opacity={}, diffuse alpha={}",
                index, opacity, diffuse[3]
            );
        }

        return opacity;
    }

    /**
     * Check if material is transparent (has alpha < 1.0)
     */
    public boolean isTransparent() {
        float opacity = getOpacity();
        return opacity < 0.99f;
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
