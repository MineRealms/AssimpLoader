package cn.minerealms.assimploader.lookdev.nodeeditor.shader;

import cn.minerealms.assimploader.debug.DebugManager;
import cn.minerealms.assimploader.debug.DebugManager.DebugCategory;
import cn.minerealms.assimploader.lookdev.PhotonIntegration;
import com.mojang.blaze3d.vertex.DefaultVertexFormat;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.ShaderInstance;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.packs.resources.ResourceManager;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.HashMap;
import java.util.Map;

/**
 * Compiles generated GLSL shader code into Photon/Minecraft shaders.
 *
 * This class handles:
 * - Writing shader files to disk
 * - Creating shader configuration JSON
 * - Compiling shaders via Minecraft's ShaderInstance
 * - Caching compiled shaders
 * - Error handling and reporting
 *
 * Only available in FULL_INTEGRATION mode (when Photon is loaded).
 */
public class PhotonShaderCompiler {

    private static final Map<String, CompiledShader> shaderCache = new HashMap<>();
    private static final String SHADER_NAMESPACE = "assimploader";
    private static final Path SHADER_BASE_PATH = Paths.get("resourcepacks/assimploader_shaders/assets/" + SHADER_NAMESPACE + "/shaders/core");

    /**
     * Compile shader code into a usable shader instance
     */
    public static CompiledShader compile(String shaderName, ShaderCodeGenerator.ShaderCode shaderCode) {
        if (!PhotonIntegration.hasShaderSupport()) {
            DebugManager.warn(DebugCategory.LOOKDEV,
                "[PhotonShaderCompiler] Shader compilation not available in STANDALONE mode");
            return null;
        }

        // Check cache first
        if (shaderCache.containsKey(shaderName)) {
            DebugManager.info(DebugCategory.LOOKDEV,
                "[PhotonShaderCompiler] Using cached shader: " + shaderName);
            return shaderCache.get(shaderName);
        }

        try {
            // Write shader files to disk
            writeShaderFiles(shaderName, shaderCode);

            // Compile shader
            ResourceLocation shaderLocation = new ResourceLocation(SHADER_NAMESPACE, shaderName);
            ShaderInstance shaderInstance = compileShaderInstance(shaderLocation);

            if (shaderInstance != null) {
                CompiledShader compiled = new CompiledShader(shaderName, shaderInstance, null);
                shaderCache.put(shaderName, compiled);

                DebugManager.info(DebugCategory.LOOKDEV,
                    "[PhotonShaderCompiler] Successfully compiled shader: " + shaderName);

                return compiled;
            } else {
                String error = "Failed to compile shader instance";
                DebugManager.error(DebugCategory.LOOKDEV,
                    "[PhotonShaderCompiler] " + error + ": " + shaderName);
                return new CompiledShader(shaderName, null, error);
            }

        } catch (Exception e) {
            String error = "Compilation error: " + e.getMessage();
            DebugManager.error(DebugCategory.LOOKDEV,
                "[PhotonShaderCompiler] " + error);
            e.printStackTrace();
            return new CompiledShader(shaderName, null, error);
        }
    }

    /**
     * Write shader files to disk (JSON config, vertex shader, fragment shader)
     */
    private static void writeShaderFiles(String shaderName, ShaderCodeGenerator.ShaderCode shaderCode) throws IOException {
        // Create shader directory
        Files.createDirectories(SHADER_BASE_PATH);

        // Write vertex shader
        Path vshPath = SHADER_BASE_PATH.resolve(shaderName + ".vsh");
        Files.writeString(vshPath, shaderCode.vertexShader);

        // Write fragment shader
        Path fshPath = SHADER_BASE_PATH.resolve(shaderName + ".fsh");
        Files.writeString(fshPath, shaderCode.fragmentShader);

        // Write shader config JSON
        String configJson = generateShaderConfig(shaderName);
        Path jsonPath = SHADER_BASE_PATH.resolve(shaderName + ".json");
        Files.writeString(jsonPath, configJson);

        DebugManager.info(DebugCategory.LOOKDEV,
            "[PhotonShaderCompiler] Wrote shader files: " + shaderName);
    }

    /**
     * Generate shader configuration JSON
     */
    private static String generateShaderConfig(String shaderName) {
        StringBuilder json = new StringBuilder();
        json.append("{\n");
        json.append("  \"blend\": {\n");
        json.append("    \"func\": \"add\",\n");
        json.append("    \"srcrgb\": \"srcalpha\",\n");
        json.append("    \"dstrgb\": \"1-srcalpha\"\n");
        json.append("  },\n");
        json.append("  \"vertex\": \"").append(SHADER_NAMESPACE).append(":").append(shaderName).append("\",\n");
        json.append("  \"fragment\": \"").append(SHADER_NAMESPACE).append(":").append(shaderName).append("\",\n");
        json.append("  \"attributes\": [\n");
        json.append("    \"Position\",\n");
        json.append("    \"Normal\",\n");
        json.append("    \"UV0\",\n");
        json.append("    \"UV1\"\n");
        json.append("  ],\n");
        json.append("  \"samplers\": [\n");
        json.append("    { \"name\": \"Sampler0\" },\n");
        json.append("    { \"name\": \"Sampler1\" },\n");
        json.append("    { \"name\": \"Sampler2\" }\n");
        json.append("  ],\n");
        json.append("  \"uniforms\": [\n");
        json.append("    { \"name\": \"ModelViewMat\", \"type\": \"matrix4x4\", \"count\": 16, \"values\": [1.0, 0.0, 0.0, 0.0, 0.0, 1.0, 0.0, 0.0, 0.0, 0.0, 1.0, 0.0, 0.0, 0.0, 0.0, 1.0] },\n");
        json.append("    { \"name\": \"ProjMat\", \"type\": \"matrix4x4\", \"count\": 16, \"values\": [1.0, 0.0, 0.0, 0.0, 0.0, 1.0, 0.0, 0.0, 0.0, 0.0, 1.0, 0.0, 0.0, 0.0, 0.0, 1.0] }\n");
        json.append("  ]\n");
        json.append("}\n");
        return json.toString();
    }

    /**
     * Compile shader instance using Minecraft's shader system
     */
    private static ShaderInstance compileShaderInstance(ResourceLocation shaderLocation) {
        try {
            ResourceManager resourceManager = Minecraft.getInstance().getResourceManager();
            return new ShaderInstance(resourceManager, shaderLocation.toString(), DefaultVertexFormat.NEW_ENTITY);
        } catch (IOException e) {
            DebugManager.error(DebugCategory.LOOKDEV,
                "[PhotonShaderCompiler] Failed to compile shader: " + e.getMessage());
            return null;
        }
    }

    /**
     * Get cached shader by name
     */
    public static CompiledShader getCached(String shaderName) {
        return shaderCache.get(shaderName);
    }

    /**
     * Check if shader is cached
     */
    public static boolean isCached(String shaderName) {
        return shaderCache.containsKey(shaderName);
    }

    /**
     * Clear shader cache
     */
    public static void clearCache() {
        // Close all shader instances
        for (CompiledShader shader : shaderCache.values()) {
            if (shader.shaderInstance != null) {
                shader.shaderInstance.close();
            }
        }
        shaderCache.clear();
        DebugManager.info(DebugCategory.LOOKDEV,
            "[PhotonShaderCompiler] Cleared shader cache");
    }

    /**
     * Reload shader (recompile from source)
     */
    public static CompiledShader reload(String shaderName, ShaderCodeGenerator.ShaderCode shaderCode) {
        // Remove from cache
        CompiledShader old = shaderCache.remove(shaderName);
        if (old != null && old.shaderInstance != null) {
            old.shaderInstance.close();
        }

        // Recompile
        return compile(shaderName, shaderCode);
    }

    /**
     * Result of shader compilation
     */
    public static class CompiledShader {
        public final String name;
        public final ShaderInstance shaderInstance;
        public final String errorMessage;

        public CompiledShader(String name, ShaderInstance shaderInstance, String errorMessage) {
            this.name = name;
            this.shaderInstance = shaderInstance;
            this.errorMessage = errorMessage;
        }

        public boolean isSuccess() {
            return shaderInstance != null && errorMessage == null;
        }

        public boolean hasError() {
            return errorMessage != null;
        }
    }
}
