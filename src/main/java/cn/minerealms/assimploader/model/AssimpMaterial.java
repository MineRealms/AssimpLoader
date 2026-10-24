package cn.minerealms.assimploader.model;

import org.lwjgl.assimp.AIColor4D;
import org.lwjgl.assimp.AIMaterial;
import org.lwjgl.assimp.AIString;

import static org.lwjgl.assimp.Assimp.*;

/**
 * Immutable snapshot of one Assimp material.
 *
 * <p>All properties are read out of native memory once, in the constructor, so this object
 * remains usable after the owning {@code aiScene} has been released.
 */
public class AssimpMaterial {

    private final int index;
    private final String name;
    private final float[] diffuseColor;
    private final float[] specularColor;
    private final float[] ambientColor;
    private final String diffuseTexture;
    private final String normalTexture;
    private final String specularTexture;

    public AssimpMaterial(AIMaterial aiMaterial, int index) {
        this.index = index;
        this.name = readName(aiMaterial);
        this.diffuseColor = readDiffuseColor(aiMaterial);
        this.specularColor = readColor(aiMaterial, AI_MATKEY_COLOR_SPECULAR, 1f, 1f, 1f, 1f);
        this.ambientColor = readColor(aiMaterial, AI_MATKEY_COLOR_AMBIENT, 0.2f, 0.2f, 0.2f, 1f);
        this.diffuseTexture = readTexturePath(aiMaterial, aiTextureType_DIFFUSE);
        this.normalTexture = readTexturePath(aiMaterial, aiTextureType_NORMALS);
        this.specularTexture = readTexturePath(aiMaterial, aiTextureType_SPECULAR);
    }

    // ------------------------------------------------------------------ native readers

    private static String readName(AIMaterial aiMaterial) {
        AIString path = AIString.calloc();
        try {
            if (aiGetMaterialString(aiMaterial, AI_MATKEY_NAME, aiTextureType_NONE, 0, path) == aiReturn_SUCCESS) {
                return path.dataString();
            }
            return "material_" + System.identityHashCode(aiMaterial);
        } finally {
            path.free();
        }
    }

    private static String readTexturePath(AIMaterial aiMaterial, int textureType) {
        AIString path = AIString.calloc();
        try {
            if (aiGetMaterialTexture(aiMaterial, textureType, 0, path,
                    (int[]) null, null, null, null, null, null) == aiReturn_SUCCESS) {
                String value = path.dataString();
                return value == null || value.isEmpty() ? null : value;
            }
            return null;
        } finally {
            path.free();
        }
    }

    private static float[] readColor(AIMaterial aiMaterial, String key, float r, float g, float b, float a) {
        AIColor4D color = AIColor4D.create();
        if (aiGetMaterialColor(aiMaterial, key, aiTextureType_NONE, 0, color) == aiReturn_SUCCESS) {
            return new float[]{color.r(), color.g(), color.b(), color.a()};
        }
        return new float[]{r, g, b, a};
    }

    /**
     * Diffuse/base colour, with the separate opacity keys folded into the alpha channel
     * (FBX PhongE exports transparency separately from the diffuse colour).
     */
    private static float[] readDiffuseColor(AIMaterial aiMaterial) {
        AIColor4D color = AIColor4D.create();

        float[] result = null;
        if (aiGetMaterialColor(aiMaterial, AI_MATKEY_COLOR_DIFFUSE, aiTextureType_NONE, 0, color) == aiReturn_SUCCESS) {
            result = new float[]{color.r(), color.g(), color.b(), color.a()};
        } else if (aiGetMaterialColor(aiMaterial, AI_MATKEY_BASE_COLOR, aiTextureType_NONE, 0, color) == aiReturn_SUCCESS) {
            result = new float[]{color.r(), color.g(), color.b(), color.a()};
        }

        if (result == null) {
            return new float[]{1f, 1f, 1f, 1f};
        }

        float opacity = readOpacityProperty(aiMaterial);
        if (opacity >= 0f && opacity < 0.99f) {
            result[3] = opacity;
        }
        return result;
    }

    /**
     * @return an explicit opacity in [0,1], or -1 when the material does not define one
     */
    private static float readOpacityProperty(AIMaterial aiMaterial) {
        float[] value = new float[1];
        int[] max = new int[1];

        // Opacity: 0 = transparent, 1 = opaque
        if (aiGetMaterialFloatArray(aiMaterial, AI_MATKEY_OPACITY, aiTextureType_NONE, 0, value, max) == aiReturn_SUCCESS) {
            if (value[0] > 0.01f && value[0] < 0.99f) {
                return value[0];
            }
        }

        // TransparencyFactor: inverted relative to opacity
        if (aiGetMaterialFloatArray(aiMaterial, AI_MATKEY_TRANSPARENCYFACTOR, aiTextureType_NONE, 0, value, max) == aiReturn_SUCCESS) {
            if (value[0] > 0.01f && value[0] < 0.99f) {
                return 1.0f - value[0];
            }
        }

        // TransparentColor: white = fully transparent, black = opaque
        AIColor4D transparent = AIColor4D.create();
        if (aiGetMaterialColor(aiMaterial, AI_MATKEY_COLOR_TRANSPARENT, aiTextureType_NONE, 0, transparent) == aiReturn_SUCCESS) {
            float average = (transparent.r() + transparent.g() + transparent.b()) / 3.0f;
            if (average > 0.01f) {
                return 1.0f - average;
            }
        }

        return -1f;
    }

    // ------------------------------------------------------------------ accessors

    public int getIndex() {
        return index;
    }

    public String getName() {
        return name;
    }

    public float[] getDiffuseColor() {
        return diffuseColor.clone();
    }

    public float[] getSpecularColor() {
        return specularColor.clone();
    }

    public float[] getAmbientColor() {
        return ambientColor.clone();
    }

    public String getDiffuseTexture() {
        return diffuseTexture;
    }

    public String getNormalTexture() {
        return normalTexture;
    }

    public String getSpecularTexture() {
        return specularTexture;
    }

    /** @return 1 = opaque, 0 = fully transparent */
    public float getOpacity() {
        return diffuseColor[3];
    }

    public boolean isTransparent() {
        return getOpacity() < 0.99f;
    }

    public boolean hasTexture() {
        return diffuseTexture != null;
    }

    /** Embedded textures are referenced by the material as "*0", "*1", ... */
    public boolean isEmbeddedTexture(String texturePath) {
        return texturePath != null && texturePath.startsWith("*");
    }

    public int getEmbeddedTextureIndex(String texturePath) {
        if (!isEmbeddedTexture(texturePath)) {
            return -1;
        }
        try {
            return Integer.parseInt(texturePath.substring(1));
        } catch (NumberFormatException e) {
            return -1;
        }
    }

    /** Explicit normal map, or the conventional {@code <diffuse>_n.png} sibling. */
    public String getNormalMapPath() {
        if (normalTexture != null && !normalTexture.isEmpty()) {
            return normalTexture;
        }
        return siblingTexture("_n");
    }

    /** Explicit specular map, or the conventional {@code <diffuse>_s.png} sibling. */
    public String getSpecularMapPath() {
        if (specularTexture != null && !specularTexture.isEmpty()) {
            return specularTexture;
        }
        return siblingTexture("_s");
    }

    private String siblingTexture(String suffix) {
        if (diffuseTexture == null || diffuseTexture.isEmpty() || isEmbeddedTexture(diffuseTexture)) {
            return null;
        }
        int dot = diffuseTexture.lastIndexOf('.');
        if (dot <= 0) {
            return null;
        }
        return diffuseTexture.substring(0, dot) + suffix + diffuseTexture.substring(dot);
    }

    @Override
    public String toString() {
        return "AssimpMaterial[" + index + ", " + name + ", diffuse=" + diffuseTexture + "]";
    }
}
