package cn.minerealms.assimploader.model;

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

    public AIMaterial getNativeMaterial() {
        return aiMaterial;
    }
}
