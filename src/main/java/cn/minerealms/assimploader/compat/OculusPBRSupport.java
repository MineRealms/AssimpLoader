package cn.minerealms.assimploader.compat;

import cn.minerealms.assimploader.debug.DebugManager;
import cn.minerealms.assimploader.debug.DebugManager.DebugCategory;
import cn.minerealms.assimploader.model.AssimpMaterial;
import net.minecraft.resources.ResourceLocation;

/**
 * PBR (Physically Based Rendering) texture support for Oculus shaders.
 *
 * This class provides integration with Oculus PBR texture system, allowing FBX models
 * to use normal maps, specular maps, and other PBR textures when shader packs support them.
 *
 * PBR Texture Conventions:
 * - Normal map: texture_n.png (tangent-space normal map)
 * - Specular map: texture_s.png (specular/metallic/roughness)
 * - Emissive map: texture_e.png (self-illumination)
 *
 * All methods gracefully degrade when Oculus is not loaded or PBR is not supported.
 */
public class OculusPBRSupport {

    private static Object pbrTextureManager = null;
    private static boolean pbrInitFailed = false;

    /**
     * Check if PBR textures are supported by the current shader pack.
     *
     * @return true if Oculus is loaded, shaders are active, and PBR is supported
     */
    public static boolean isPBRAvailable() {
        if (!OculusCompat.isShadersActive() || pbrInitFailed) {
            return false;
        }

        try {
            // Try to access PBRTextureManager
            if (pbrTextureManager == null) {
                Class<?> pbrManagerClass = Class.forName("net.irisshaders.iris.pbr.PBRTextureManager");
                java.lang.reflect.Field instanceField = pbrManagerClass.getField("INSTANCE");
                pbrTextureManager = instanceField.get(null);

                if (pbrTextureManager != null) {
                    DebugManager.info(DebugCategory.CORE, "PBR texture system initialized successfully");
                }
            }

            return pbrTextureManager != null;

        } catch (ClassNotFoundException e) {
            DebugManager.debug(DebugCategory.CORE, "PBR texture system not available (Oculus version may not support it)");
            pbrInitFailed = true;
            return false;
        } catch (Exception e) {
            DebugManager.warn(DebugCategory.CORE, "Failed to initialize PBR texture system: {}", e.getMessage());
            pbrInitFailed = true;
            return false;
        }
    }

    /**
     * Register PBR textures for a given albedo texture.
     * This tells Oculus to look for corresponding normal/specular maps.
     *
     * @param albedoTexture The base texture ResourceLocation
     * @param material The material containing PBR texture paths
     */
    public static void registerPBRTextures(ResourceLocation albedoTexture, AssimpMaterial material) {
        if (!isPBRAvailable() || material == null) {
            return;
        }

        try {
            // Get normal map path from material
            String normalMapPath = material.getNormalMapPath();
            if (normalMapPath != null && !normalMapPath.isEmpty()) {
                ResourceLocation normalMap = new ResourceLocation(normalMapPath);
                bindNormalMap(albedoTexture, normalMap);
            }

            // Get specular map path from material
            String specularMapPath = material.getSpecularMapPath();
            if (specularMapPath != null && !specularMapPath.isEmpty()) {
                ResourceLocation specularMap = new ResourceLocation(specularMapPath);
                bindSpecularMap(albedoTexture, specularMap);
            }

        } catch (Exception e) {
            DebugManager.debug(DebugCategory.CORE, "Failed to register PBR textures for {}: {}",
                albedoTexture, e.getMessage());
        }
    }

    /**
     * Bind a normal map to an albedo texture.
     *
     * @param albedoTexture The base texture
     * @param normalMap The normal map texture
     */
    private static void bindNormalMap(ResourceLocation albedoTexture, ResourceLocation normalMap) {
        if (pbrTextureManager == null) {
            return;
        }

        try {
            // Call PBRTextureManager.setNormalTexture(albedo, normal)
            java.lang.reflect.Method setNormalTexture = pbrTextureManager.getClass()
                .getMethod("setNormalTexture", ResourceLocation.class, ResourceLocation.class);
            setNormalTexture.invoke(pbrTextureManager, albedoTexture, normalMap);

            DebugManager.debug(DebugCategory.CORE, "Bound normal map {} to {}", normalMap, albedoTexture);

        } catch (NoSuchMethodException e) {
            // Method signature might be different in this Oculus version
            DebugManager.debug(DebugCategory.CORE, "PBR normal map binding not supported in this Oculus version");
        } catch (Exception e) {
            DebugManager.debug(DebugCategory.CORE, "Failed to bind normal map: {}", e.getMessage());
        }
    }

    /**
     * Bind a specular map to an albedo texture.
     *
     * @param albedoTexture The base texture
     * @param specularMap The specular map texture
     */
    private static void bindSpecularMap(ResourceLocation albedoTexture, ResourceLocation specularMap) {
        if (pbrTextureManager == null) {
            return;
        }

        try {
            // Call PBRTextureManager.setSpecularTexture(albedo, specular)
            java.lang.reflect.Method setSpecularTexture = pbrTextureManager.getClass()
                .getMethod("setSpecularTexture", ResourceLocation.class, ResourceLocation.class);
            setSpecularTexture.invoke(pbrTextureManager, albedoTexture, specularMap);

            DebugManager.debug(DebugCategory.CORE, "Bound specular map {} to {}", specularMap, albedoTexture);

        } catch (NoSuchMethodException e) {
            // Method signature might be different in this Oculus version
            DebugManager.debug(DebugCategory.CORE, "PBR specular map binding not supported in this Oculus version");
        } catch (Exception e) {
            DebugManager.debug(DebugCategory.CORE, "Failed to bind specular map: {}", e.getMessage());
        }
    }

    /**
     * Get the expected normal map path for a given texture.
     * Convention: texture.png -> texture_n.png
     *
     * @param texturePath The base texture path
     * @return The expected normal map path, or null if invalid
     */
    public static String getNormalMapPath(String texturePath) {
        if (texturePath == null || texturePath.isEmpty()) {
            return null;
        }

        int dotIndex = texturePath.lastIndexOf('.');
        if (dotIndex > 0) {
            return texturePath.substring(0, dotIndex) + "_n" + texturePath.substring(dotIndex);
        }

        return texturePath + "_n";
    }

    /**
     * Get the expected specular map path for a given texture.
     * Convention: texture.png -> texture_s.png
     *
     * @param texturePath The base texture path
     * @return The expected specular map path, or null if invalid
     */
    public static String getSpecularMapPath(String texturePath) {
        if (texturePath == null || texturePath.isEmpty()) {
            return null;
        }

        int dotIndex = texturePath.lastIndexOf('.');
        if (dotIndex > 0) {
            return texturePath.substring(0, dotIndex) + "_s" + texturePath.substring(dotIndex);
        }

        return texturePath + "_s";
    }

    /**
     * Reset cached state. Used for testing or when mods are reloaded.
     */
    public static void reset() {
        pbrTextureManager = null;
        pbrInitFailed = false;
        DebugManager.debug(DebugCategory.CORE, "OculusPBRSupport state reset");
    }
}
