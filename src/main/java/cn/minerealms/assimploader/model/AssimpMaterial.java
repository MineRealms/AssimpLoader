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
            cn.minerealms.assimploader.AssimpLoader.LOGGER.info("Material {} diffuse color: R={}, G={}, B={}, A={}",
                index, result[0], result[1], result[2], result[3]);
            return result;
        }

        // Try base color (PBR materials)
        if (aiGetMaterialColor(aiMaterial, AI_MATKEY_BASE_COLOR, aiTextureType_NONE, 0, color) == aiReturn_SUCCESS) {
            float[] result = new float[]{color.r(), color.g(), color.b(), color.a()};
            cn.minerealms.assimploader.AssimpLoader.LOGGER.info("Material {} base color: R={}, G={}, B={}, A={}",
                index, result[0], result[1], result[2], result[3]);
            return result;
        }

        // Log warning and return white as default
        cn.minerealms.assimploader.AssimpLoader.LOGGER.warn("Material {} has no diffuse or base color, using white", index);
        return new float[]{1.0f, 1.0f, 1.0f, 1.0f};
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
