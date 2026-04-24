package cn.minerealms.assimploader.material;

import cn.minerealms.assimploader.debug.DebugManager;
import cn.minerealms.assimploader.debug.DebugManager.DebugCategory;
import net.minecraft.resources.ResourceLocation;
import org.joml.Vector3f;
import org.joml.Vector4f;
import org.lwjgl.assimp.*;

import java.nio.IntBuffer;

import static org.lwjgl.assimp.Assimp.*;

/**
 * FBX material parser that extracts material properties from Assimp AIMaterial.
 *
 * Supports:
 * - Traditional materials (Phong, Lambert, Blinn)
 * - PBR materials (Metallic/Roughness, Specular/Glossiness)
 * - Multiple texture types
 * - Material properties (colors, factors, etc.)
 *
 * FBX Material Properties:
 * - $clr.diffuse, $clr.specular, $clr.ambient, $clr.emissive
 * - $mat.shininess, $mat.opacity
 * - $tex.file (texture paths)
 *
 * References:
 * - https://documentation.help/assimp/materials.html
 * - https://github.com/assimp/assimp/blob/master/include/assimp/material.h
 */
public class FBXMaterialParser {

    /**
     * Parse AIMaterial to PBRMaterial
     */
    public static PBRMaterial parse(AIMaterial aiMaterial, int materialIndex, String modelNamespace) {
        AIString aiName = AIString.calloc();
        aiGetMaterialString(aiMaterial, AI_MATKEY_NAME, aiTextureType_NONE, 0, aiName);
        String materialName = aiName.dataString();
        aiName.free();

        if (materialName == null || materialName.isEmpty()) {
            materialName = "material_" + materialIndex;
        }

        PBRMaterial material = new PBRMaterial(materialName);

        DebugManager.debug(DebugCategory.MATERIAL, "Parsing material: {}", materialName);

        // Parse colors
        parseBaseColor(aiMaterial, material);
        parseSpecularColor(aiMaterial, material);
        parseEmissiveColor(aiMaterial, material);

        // Parse factors
        parseMetallicRoughness(aiMaterial, material);
        parseShininess(aiMaterial, material);
        parseOpacity(aiMaterial, material);

        // Parse textures
        parseTextures(aiMaterial, material, modelNamespace);

        // Determine alpha mode
        determineAlphaMode(material);

        DebugManager.debug(DebugCategory.MATERIAL, "Material parsed: {}", material);

        return material;
    }

    /**
     * Parse base color (diffuse/albedo)
     */
    private static void parseBaseColor(AIMaterial aiMaterial, PBRMaterial material) {
        AIColor4D color = AIColor4D.create();

        if (aiGetMaterialColor(aiMaterial, AI_MATKEY_COLOR_DIFFUSE, aiTextureType_NONE, 0, color) == aiReturn_SUCCESS) {
            material.setBaseColor(color.r(), color.g(), color.b(), color.a());
            DebugManager.debug(DebugCategory.MATERIAL, "  Base color: ({}, {}, {}, {})", color.r(), color.g(), color.b(), color.a());
        }
    }

    /**
     * Parse specular color
     */
    private static void parseSpecularColor(AIMaterial aiMaterial, PBRMaterial material) {
        AIColor4D color = AIColor4D.create();

        if (aiGetMaterialColor(aiMaterial, AI_MATKEY_COLOR_SPECULAR, aiTextureType_NONE, 0, color) == aiReturn_SUCCESS) {
            material.setSpecularColor(new Vector3f(color.r(), color.g(), color.b()));
            DebugManager.debug(DebugCategory.MATERIAL, "  Specular color: ({}, {}, {})", color.r(), color.g(), color.b());
        }
    }

    /**
     * Parse emissive color
     */
    private static void parseEmissiveColor(AIMaterial aiMaterial, PBRMaterial material) {
        AIColor4D color = AIColor4D.create();

        if (aiGetMaterialColor(aiMaterial, AI_MATKEY_COLOR_EMISSIVE, aiTextureType_NONE, 0, color) == aiReturn_SUCCESS) {
            material.setEmissiveFactor(color.r(), color.g(), color.b());
            DebugManager.debug(DebugCategory.MATERIAL, "  Emissive color: ({}, {}, {})", color.r(), color.g(), color.b());
        }
    }

    /**
     * Parse metallic and roughness factors
     */
    private static void parseMetallicRoughness(AIMaterial aiMaterial, PBRMaterial material) {
        float[] metallic = new float[1];
        float[] roughness = new float[1];

        // Try to get PBR metallic factor
        if (aiGetMaterialFloatArray(aiMaterial, AI_MATKEY_METALLIC_FACTOR, aiTextureType_NONE, 0, metallic, new int[]{1}) == aiReturn_SUCCESS) {
            material.setMetallicFactor(metallic[0]);
            DebugManager.debug(DebugCategory.MATERIAL, "  Metallic factor: {}", metallic[0]);
        }

        // Try to get PBR roughness factor
        if (aiGetMaterialFloatArray(aiMaterial, AI_MATKEY_ROUGHNESS_FACTOR, aiTextureType_NONE, 0, roughness, new int[]{1}) == aiReturn_SUCCESS) {
            material.setRoughnessFactor(roughness[0]);
            DebugManager.debug(DebugCategory.MATERIAL, "  Roughness factor: {}", roughness[0]);
        }
    }

    /**
     * Parse shininess (for legacy materials)
     */
    private static void parseShininess(AIMaterial aiMaterial, PBRMaterial material) {
        float[] shininess = new float[1];

        if (aiGetMaterialFloatArray(aiMaterial, AI_MATKEY_SHININESS, aiTextureType_NONE, 0, shininess, new int[]{1}) == aiReturn_SUCCESS) {
            material.setShininess(shininess[0]);

            // Convert shininess to roughness (approximation)
            // Shininess range: 0-1000+, Roughness range: 0-1
            // roughness ≈ sqrt(2 / (shininess + 2))
            if (shininess[0] > 0) {
                float roughness = (float) Math.sqrt(2.0 / (shininess[0] + 2.0));
                material.setRoughnessFactor(roughness);
            }

            DebugManager.debug(DebugCategory.MATERIAL, "  Shininess: {} (roughness: {})", shininess[0], material.getRoughnessFactor());
        }
    }

    /**
     * Parse opacity/transparency
     */
    private static void parseOpacity(AIMaterial aiMaterial, PBRMaterial material) {
        float[] opacity = new float[1];

        if (aiGetMaterialFloatArray(aiMaterial, AI_MATKEY_OPACITY, aiTextureType_NONE, 0, opacity, new int[]{1}) == aiReturn_SUCCESS) {
            Vector4f baseColor = material.getBaseColor();
            baseColor.w = opacity[0];
            material.setBaseColor(baseColor);
            DebugManager.debug(DebugCategory.MATERIAL, "  Opacity: {}", opacity[0]);
        }
    }

    /**
     * Parse all texture types
     */
    private static void parseTextures(AIMaterial aiMaterial, PBRMaterial material, String modelNamespace) {
        // Base color / Diffuse
        parseTexture(aiMaterial, aiTextureType_DIFFUSE, material, modelNamespace, TextureType.BASE_COLOR);
        parseTexture(aiMaterial, aiTextureType_BASE_COLOR, material, modelNamespace, TextureType.BASE_COLOR);

        // Normal map
        parseTexture(aiMaterial, aiTextureType_NORMALS, material, modelNamespace, TextureType.NORMAL);
        parseTexture(aiMaterial, aiTextureType_HEIGHT, material, modelNamespace, TextureType.NORMAL); // Sometimes height is used for normals

        // Metallic/Roughness
        parseTexture(aiMaterial, aiTextureType_METALNESS, material, modelNamespace, TextureType.METALLIC);
        parseTexture(aiMaterial, aiTextureType_DIFFUSE_ROUGHNESS, material, modelNamespace, TextureType.ROUGHNESS);

        // Emissive
        parseTexture(aiMaterial, aiTextureType_EMISSIVE, material, modelNamespace, TextureType.EMISSIVE);
        parseTexture(aiMaterial, aiTextureType_EMISSION_COLOR, material, modelNamespace, TextureType.EMISSIVE);

        // Ambient Occlusion
        parseTexture(aiMaterial, aiTextureType_AMBIENT_OCCLUSION, material, modelNamespace, TextureType.AO);
        parseTexture(aiMaterial, aiTextureType_LIGHTMAP, material, modelNamespace, TextureType.AO);

        // Opacity
        parseTexture(aiMaterial, aiTextureType_OPACITY, material, modelNamespace, TextureType.OPACITY);
    }

    /**
     * Parse a specific texture type
     */
    private static void parseTexture(AIMaterial aiMaterial, int textureType, PBRMaterial material, String modelNamespace, TextureType targetType) {
        AIString path = AIString.calloc();

        if (aiGetMaterialTexture(aiMaterial, textureType, 0, path, (IntBuffer) null, null, null, null, null, null) == aiReturn_SUCCESS) {
            String texturePath = path.dataString();
            path.free();

            if (texturePath != null && !texturePath.isEmpty()) {
                ResourceLocation textureLocation = resolveTexturePath(texturePath, modelNamespace);

                switch (targetType) {
                    case BASE_COLOR:
                        material.setBaseColorTexture(textureLocation);
                        DebugManager.debug(DebugCategory.MATERIAL, "  Base color texture: {}", textureLocation);
                        break;
                    case NORMAL:
                        material.setNormalTexture(textureLocation);
                        DebugManager.debug(DebugCategory.MATERIAL, "  Normal texture: {}", textureLocation);
                        break;
                    case METALLIC:
                        material.setMetallicRoughnessTexture(textureLocation);
                        DebugManager.debug(DebugCategory.MATERIAL, "  Metallic texture: {}", textureLocation);
                        break;
                    case ROUGHNESS:
                        if (material.getMetallicRoughnessTexture() == null) {
                            material.setMetallicRoughnessTexture(textureLocation);
                        }
                        DebugManager.debug(DebugCategory.MATERIAL, "  Roughness texture: {}", textureLocation);
                        break;
                    case EMISSIVE:
                        material.setEmissiveTexture(textureLocation);
                        DebugManager.debug(DebugCategory.MATERIAL, "  Emissive texture: {}", textureLocation);
                        break;
                    case AO:
                        material.setAoTexture(textureLocation);
                        DebugManager.debug(DebugCategory.MATERIAL, "  AO texture: {}", textureLocation);
                        break;
                    case OPACITY:
                        // Opacity is usually in base color alpha channel
                        if (material.getBaseColorTexture() == null) {
                            material.setBaseColorTexture(textureLocation);
                        }
                        DebugManager.debug(DebugCategory.MATERIAL, "  Opacity texture: {}", textureLocation);
                        break;
                }
            }
        } else {
            path.free();
        }
    }

    /**
     * Resolve texture path to ResourceLocation
     */
    private static ResourceLocation resolveTexturePath(String texturePath, String modelNamespace) {
        // Remove leading/trailing whitespace
        texturePath = texturePath.trim();

        // Handle embedded textures (start with *)
        if (texturePath.startsWith("*")) {
            // Embedded texture index
            String index = texturePath.substring(1);
            return new ResourceLocation(modelNamespace, "textures/embedded/" + index + ".png");
        }

        // Extract filename from path
        String filename = texturePath.replace("\\", "/");
        int lastSlash = filename.lastIndexOf('/');
        if (lastSlash >= 0) {
            filename = filename.substring(lastSlash + 1);
        }

        // Remove extension
        int lastDot = filename.lastIndexOf('.');
        if (lastDot > 0) {
            filename = filename.substring(0, lastDot);
        }

        // Convert to lowercase and replace spaces
        filename = filename.toLowerCase().replace(" ", "_");

        return new ResourceLocation(modelNamespace, "textures/" + filename);
    }

    /**
     * Determine alpha mode based on material properties
     */
    private static void determineAlphaMode(PBRMaterial material) {
        Vector4f baseColor = material.getBaseColor();

        if (baseColor.w < 1.0f) {
            if (baseColor.w < 0.01f) {
                material.setAlphaMode(PBRMaterial.AlphaMode.MASK);
                material.setAlphaCutoff(0.5f);
            } else {
                material.setAlphaMode(PBRMaterial.AlphaMode.BLEND);
            }
        } else {
            material.setAlphaMode(PBRMaterial.AlphaMode.OPAQUE);
        }
    }

    /**
     * Texture type enum for internal use
     */
    private enum TextureType {
        BASE_COLOR,
        NORMAL,
        METALLIC,
        ROUGHNESS,
        EMISSIVE,
        AO,
        OPACITY
    }
}
