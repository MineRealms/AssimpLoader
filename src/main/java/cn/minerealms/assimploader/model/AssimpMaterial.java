package cn.minerealms.assimploader.model;

import cn.minerealms.assimploader.material.PbrMaterialInputs;
import cn.minerealms.assimploader.material.MaterialAlpha;
import org.lwjgl.assimp.AIMaterial;
import org.lwjgl.assimp.AIString;

import java.util.Locale;

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
    private final PbrMaterialInputs pbrInputs;
    private final boolean blendAlpha;
    private final MaterialAlpha alphaPolicy;
    private final boolean doubleSided;
    private final boolean detached;

    public AssimpMaterial(AIMaterial aiMaterial, int index) {
        this(aiMaterial, index, "", "");
    }

    public AssimpMaterial(AIMaterial aiMaterial, int index, String sourceFormat, String sourceGenerator) {
        this.index = index;
        this.name = readName(aiMaterial);
        this.diffuseColor = readDiffuseColor(aiMaterial);
        this.specularColor = readColor(aiMaterial, AI_MATKEY_COLOR_SPECULAR, 1f, 1f, 1f, 1f);
        this.ambientColor = readColor(aiMaterial, AI_MATKEY_COLOR_AMBIENT, 0.2f, 0.2f, 0.2f, 1f);
        this.diffuseTexture = firstTexturePath(aiMaterial, aiTextureType_DIFFUSE, aiTextureType_BASE_COLOR);
        this.normalTexture = firstTexturePath(aiMaterial, aiTextureType_NORMALS, aiTextureType_NORMAL_CAMERA);
        this.specularTexture = readTexturePath(aiMaterial, aiTextureType_SPECULAR);
        this.pbrInputs = readPbrInputs(aiMaterial, sourceFormat, sourceGenerator);
        this.blendAlpha = readBlendAlpha(aiMaterial);
        this.alphaPolicy = MaterialAlpha.SOURCE;
        this.doubleSided = blendAlpha || diffuseColor[3] < 0.99f;
        this.detached = false;
    }

    /** Detached material variant; the shared imported material remains immutable. */
    public AssimpMaterial(AssimpMaterial source, float[] color, String albedo, String normal, String specular,
                          PbrMaterialInputs inputs, boolean blend) {
        this(source, color, albedo, normal, specular, inputs, blend, blend || color[3] < 0.99f, false);
    }

    /** A detached render policy does not change the shared material or texture data. */
    public AssimpMaterial(AssimpMaterial source, float[] color, String albedo, String normal, String specular,
                          PbrMaterialInputs inputs, boolean blend, boolean doubleSided) {
        this(source, color, albedo, normal, specular, inputs, blend, doubleSided, false);
    }

    /** Compatibility overload for explicit OPAQUE/BLEND; use MaterialAlpha for MASK cutoff. */
    public AssimpMaterial(AssimpMaterial source, float[] color, String albedo, String normal, String specular,
                          PbrMaterialInputs inputs, boolean blend, boolean doubleSided, boolean explicitAlphaMode) {
        this(source, color, albedo, normal, specular, inputs, blend, doubleSided, explicitAlphaMode
                ? new MaterialAlpha(blend ? MaterialAlpha.Mode.BLEND : MaterialAlpha.Mode.OPAQUE, color[3], .1f)
                : MaterialAlpha.SOURCE);
    }

    public AssimpMaterial(AssimpMaterial source, float[] color, String albedo, String normal, String specular,
                          PbrMaterialInputs inputs, MaterialAlpha alphaPolicy, boolean doubleSided) {
        this(source, color, albedo, normal, specular, inputs,
                alphaPolicy.mode() == MaterialAlpha.Mode.SOURCE ? source.blendAlpha : alphaPolicy.mode() == MaterialAlpha.Mode.BLEND,
                doubleSided, alphaPolicy);
    }

    private AssimpMaterial(AssimpMaterial source, float[] color, String albedo, String normal, String specular,
                           PbrMaterialInputs inputs, boolean blend, boolean doubleSided, MaterialAlpha alphaPolicy) {
        this.index = source.index; this.name = source.name;
        this.diffuseColor = color.clone(); this.specularColor = source.specularColor.clone();
        this.ambientColor = source.ambientColor.clone();
        this.diffuseTexture = albedo; this.normalTexture = normal; this.specularTexture = specular;
        this.pbrInputs = java.util.Objects.requireNonNull(inputs); this.blendAlpha = blend;
        this.alphaPolicy = java.util.Objects.requireNonNull(alphaPolicy);
        this.doubleSided = doubleSided;
        this.detached = true;
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
        PbrMaterialInputs.TextureRef texture = readTexture(aiMaterial, textureType, 0);
        return texture == null ? null : texture.path();
    }

    private static boolean readBlendAlpha(AIMaterial material) {
        AIString mode = AIString.calloc();
        try {
            return aiGetMaterialString(material, AI_MATKEY_GLTF_ALPHAMODE, aiTextureType_NONE, 0, mode) == aiReturn_SUCCESS
                    && "BLEND".equals(mode.dataString());
        } finally {
            mode.free();
        }
    }

    private static String firstTexturePath(AIMaterial material, int first, int second) {
        String path = readTexturePath(material, first);
        return path != null ? path : readTexturePath(material, second);
    }

    private static PbrMaterialInputs.TextureRef readTexture(AIMaterial aiMaterial, int textureType, int channel) {
        AIString path = AIString.calloc();
        try {
            int[] uv = {0};
            if (aiGetMaterialTexture(aiMaterial, textureType, 0, path,
                    null, uv, null, null, null, null) == aiReturn_SUCCESS) {
                String value = path.dataString();
                return value == null || value.isBlank() ? null
                        : new PbrMaterialInputs.TextureRef(value, channel, Math.max(0, uv[0]));
            }
            return null;
        } finally {
            path.free();
        }
    }

    private static PbrMaterialInputs.TextureRef firstTexture(AIMaterial material, int first, int second) {
        var texture = readTexture(material, first, 0);
        return texture != null ? texture : readTexture(material, second, 0);
    }

    private static PbrMaterialInputs readPbrInputs(AIMaterial material, String sourceFormat, String sourceGenerator) {
        String format = sourceFormat == null ? "" : sourceFormat.toLowerCase(Locale.ROOT);
        String generator = sourceGenerator == null ? "" : sourceGenerator.toLowerCase(Locale.ROOT);
        boolean blenderFbx = format.contains("fbx") && generator.contains("blender");
        boolean gltf = format.contains("gltf") || format.contains("glb");
        // Assimp maps Wavefront OBJ's legacy map_Ns gloss map to
        // aiTextureType_DIFFUSE_ROUGHNESS. That slot is not evidence of a
        // metallic/roughness workflow, so only formats with an unambiguous
        // modern PBR contract may populate these two channels.
        boolean modernPbr = blenderFbx || gltf;
        var normal = firstTexture(material, aiTextureType_NORMALS, aiTextureType_NORMAL_CAMERA);
        var roughness = modernPbr ? readTexture(material, aiTextureType_DIFFUSE_ROUGHNESS, 0) : null;
        var metallic = modernPbr ? readTexture(material, aiTextureType_METALNESS, 0) : null;
        if (blenderFbx && roughness == null) roughness = readTexture(material, aiTextureType_SHININESS, 0);
        if (gltf) {
            var combined = readTexture(material, AI_MATKEY_GLTF_PBRMETALLICROUGHNESS_METALLICROUGHNESS_TEXTURE, 0);
            if (combined != null) {
                // Newer Assimp also exposes the packed glTF image through the individual slots.
                if (roughness == null || roughness.path().equals(combined.path()))
                    roughness = new PbrMaterialInputs.TextureRef(combined.path(), 1, combined.uvIndex());
                if (metallic == null || metallic.path().equals(combined.path()))
                    metallic = new PbrMaterialInputs.TextureRef(combined.path(), 2, combined.uvIndex());
            }
        }
        var occlusion = firstTexture(material, aiTextureType_AMBIENT_OCCLUSION, aiTextureType_LIGHTMAP);
        var emissive = firstTexture(material, aiTextureType_EMISSION_COLOR, aiTextureType_EMISSIVE);
        float roughnessFactor = modernPbr ? readScalarProperty(material, AI_MATKEY_ROUGHNESS_FACTOR) : Float.NaN;
        float metallicFactor = modernPbr ? readScalarProperty(material, AI_MATKEY_METALLIC_FACTOR) : Float.NaN;
        PbrMaterialInputs.Workflow workflow = PbrMaterialInputs.Workflow.NONE;
        float f0 = 0.04f;
        if (blenderFbx) {
            workflow = PbrMaterialInputs.Workflow.BLENDER_FBX;
            float shininess = readScalarProperty(material, AI_MATKEY_SHININESS);
            roughnessFactor = roughness != null ? 1f : Float.isFinite(shininess)
                    ? 1f - (float) Math.sqrt(Math.max(0, shininess) / 100f) : 0.5f;
            metallicFactor = metallic != null ? 1f : fallback(readScalarProperty(material, AI_MATKEY_REFLECTIVITY), 0f);
            f0 = fallback(readScalarProperty(material, AI_MATKEY_SHININESS_STRENGTH), 0.25f) * 0.16f;
        } else if (gltf) {
            workflow = PbrMaterialInputs.Workflow.METALLIC_ROUGHNESS;
            roughnessFactor = fallback(roughnessFactor, 1f);
            metallicFactor = fallback(metallicFactor, metallic != null || gltf ? 1f : 0f);
        } else {
            roughnessFactor = 0.5f;
            metallicFactor = 0f;
        }
        float normalScale = readScalarProperty(material, _AI_MATKEY_GLTF_SCALE_BASE, aiTextureType_NORMALS);
        if (!Float.isFinite(normalScale) && blenderFbx) normalScale = readScalarProperty(material, AI_MATKEY_BUMPSCALING);
        float occlusionStrength = readScalarProperty(material, _AI_MATKEY_GLTF_STRENGTH_BASE,
                aiTextureType_AMBIENT_OCCLUSION);
        if (!Float.isFinite(occlusionStrength))
            occlusionStrength = readScalarProperty(material, _AI_MATKEY_GLTF_STRENGTH_BASE, aiTextureType_LIGHTMAP);
        // LWJGL 3.3.3's native importer stores this under $tex.file.strength,
        // although its generated Java header still declares $tex.strength.
        if (gltf && !Float.isFinite(occlusionStrength)) {
            occlusionStrength = readScalarProperty(material, "$tex.file.strength", aiTextureType_AMBIENT_OCCLUSION);
            if (!Float.isFinite(occlusionStrength))
                occlusionStrength = readScalarProperty(material, "$tex.file.strength", aiTextureType_LIGHTMAP);
        }
        float[] emission = readColorProperty(material, AI_MATKEY_COLOR_EMISSIVE);
        if (emission == null) emission = emissive == null ? new float[]{0, 0, 0} : new float[]{1, 1, 1};
        float intensity = fallback(readScalarProperty(material, AI_MATKEY_EMISSIVE_INTENSITY), 1f);
        return new PbrMaterialInputs(workflow, normal, roughness, metallic, occlusion, emissive,
                roughnessFactor, metallicFactor, fallback(normalScale, 1f), fallback(occlusionStrength, 1f),
                emission[0] * intensity, emission[1] * intensity, emission[2] * intensity, f0);
    }

    private static float fallback(float value, float fallback) { return Float.isFinite(value) ? value : fallback; }

    private static float[] readColor(AIMaterial aiMaterial, String key, float r, float g, float b, float a) {
        float[] color = readColorProperty(aiMaterial, key);
        return color == null ? new float[]{r, g, b, a} : color;
    }

    private static float[] readColorProperty(AIMaterial aiMaterial, String key) {
        float[] values = {0f, 0f, 0f, 1f};
        int[] count = {4};
        if (aiGetMaterialFloatArray(aiMaterial, key, aiTextureType_NONE, 0, values, count) != aiReturn_SUCCESS
                || count[0] < 3) return null;
        // RGB properties have no fourth component; only an authored RGBA property supplies alpha.
        values[3] = count[0] < 4 || !Float.isFinite(values[3]) ? 1f : clampUnit(values[3]);
        return values;
    }

    /**
     * Diffuse/base colour, with the separate opacity keys folded into the alpha channel
     * (FBX PhongE exports transparency separately from the diffuse colour).
     */
    private static float[] readDiffuseColor(AIMaterial aiMaterial) {
        float[] result = readColorProperty(aiMaterial, AI_MATKEY_COLOR_DIFFUSE);
        if (result == null) result = readColorProperty(aiMaterial, AI_MATKEY_BASE_COLOR);
        if (result == null) result = new float[]{1f, 1f, 1f, 1f};

        float opacity = readOpacityProperty(aiMaterial);
        // Importers may expose the same alpha through both properties; multiplying would apply it twice.
        if (opacity >= 0f) result[3] = Math.min(result[3], opacity);
        return result;
    }

    /**
     * @return an explicit opacity in [0,1], or -1 when the material does not define one
     */
    private static float readOpacityProperty(AIMaterial aiMaterial) {
        // Opacity: 0 = transparent, 1 = opaque
        float opacity = readScalarProperty(aiMaterial, AI_MATKEY_OPACITY);
        if (Float.isFinite(opacity)) return clampUnit(opacity);

        // TransparentColor: white = fully transparent, black = opaque
        // FBX's importer already applies TransparencyFactor to this color.
        float[] transparent = readColorProperty(aiMaterial, AI_MATKEY_COLOR_TRANSPARENT);
        if (transparent != null) {
            float average = (transparent[0] + transparent[1] + transparent[2]) / 3f;
            if (Float.isFinite(average)) return 1f - clampUnit(average);
        }

        float transparency = readScalarProperty(aiMaterial, AI_MATKEY_TRANSPARENCYFACTOR);
        if (Float.isFinite(transparency)) return 1f - clampUnit(transparency);
        return -1f;
    }

    private static float readScalarProperty(AIMaterial aiMaterial, String key) {
        return readScalarProperty(aiMaterial, key, aiTextureType_NONE);
    }

    private static float readScalarProperty(AIMaterial aiMaterial, String key, int type) {
        float[] value = new float[1];
        int[] count = {1};
        return aiGetMaterialFloatArray(aiMaterial, key, type, 0, value, count) == aiReturn_SUCCESS
                && count[0] == 1 ? value[0] : Float.NaN;
    }

    private static float clampUnit(float value) { return Math.max(0f, Math.min(1f, value)); }

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

    /** OPAQUE/MASK have already applied their factor to albedo coverage on the image worker. */
    public float[] getRenderColor() {
        float[] color = diffuseColor.clone();
        if (alphaPolicy.baked()) color[3] = 1;
        return color;
    }

    public MaterialAlpha getAlphaPolicy() { return alphaPolicy; }

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

    public PbrMaterialInputs getPbrInputs() { return pbrInputs; }
    public boolean isDetached() { return detached; }
    public boolean isDoubleSided() { return doubleSided; }

    /** @return 1 = opaque, 0 = fully transparent */
    public float getOpacity() {
        return diffuseColor[3];
    }

    public boolean isTransparent() {
        return alphaPolicy.mode() == MaterialAlpha.Mode.SOURCE
                ? blendAlpha || getOpacity() < 0.99f : alphaPolicy.mode() == MaterialAlpha.Mode.BLEND;
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
        int slash = Math.max(diffuseTexture.lastIndexOf('/'), diffuseTexture.lastIndexOf('\\'));
        return dot > slash ? diffuseTexture.substring(0, dot) + suffix + diffuseTexture.substring(dot)
                : diffuseTexture + suffix;
    }

    @Override
    public String toString() {
        return "AssimpMaterial[" + index + ", " + name + ", diffuse=" + diffuseTexture + "]";
    }
}
