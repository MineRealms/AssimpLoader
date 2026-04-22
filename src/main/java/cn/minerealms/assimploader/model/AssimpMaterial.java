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
        if (aiGetMaterialColor(aiMaterial, AI_MATKEY_COLOR_DIFFUSE, aiTextureType_NONE, 0, color) == aiReturn_SUCCESS) {
            return new float[]{color.r(), color.g(), color.b(), color.a()};
        }
        // Default to white if no color found
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
