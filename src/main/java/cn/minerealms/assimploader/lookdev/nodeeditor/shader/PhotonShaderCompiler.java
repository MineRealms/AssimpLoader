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
        DebugManager.debug(DebugCategory.LOOKDEV,
            "[PhotonShaderCompiler] Starting compilation for shader: {}", shaderName);

        return DebugManager.tryCatch("[PhotonShaderCompiler] compile", () -> {
            if (!PhotonIntegration.hasShaderSupport()) {
                DebugManager.warn(DebugCategory.LOOKDEV,
                    "[PhotonShaderCompiler] Shader compilation not available in STANDALONE mode");
                return null;
            }

            // Check cache first
            if (shaderCache.containsKey(shaderName)) {
                DebugManager.debug(DebugCategory.LOOKDEV,
                    "[PhotonShaderCompiler] Using cached shader: {}", shaderName);
                return shaderCache.get(shaderName);
            }

            DebugManager.debug(DebugCategory.LOOKDEV,
                "[PhotonShaderCompiler] Shader not in cache, compiling: {}", shaderName);

            // Write shader files to disk
            DebugManager.debug(DebugCategory.LOOKDEV,
                "[PhotonShaderCompiler] Writing shader files to disk");
            writeShaderFiles(shaderName, shaderCode);

            // Compile shader
            DebugManager.debug(DebugCategory.LOOKDEV,
                "[PhotonShaderCompiler] Creating shader instance");
            ResourceLocation shaderLocation = new ResourceLocation(SHADER_NAMESPACE, shaderName);
            ShaderInstance shaderInstance = compileShaderInstance(shaderLocation);

            if (shaderInstance != null) {
                CompiledShader compiled = new CompiledShader(shaderName, shaderInstance, null);
                shaderCache.put(shaderName, compiled);

                DebugManager.info(DebugCategory.LOOKDEV,
                    "[PhotonShaderCompiler] Successfully compiled and cached shader: {}", shaderName);

                return compiled;
            } else {
                String error = "Failed to compile shader instance";
                DebugManager.error(DebugCategory.LOOKDEV,
                    "[PhotonShaderCompiler] {}: {}", error, shaderName);
                return new CompiledShader(shaderName, null, error);
            }

        }, null);
    }

    /**
     * Write shader files to disk (JSON config, vertex shader, fragment shader)
     */
    private static void writeShaderFiles(String shaderName, ShaderCodeGenerator.ShaderCode shaderCode) throws IOException {
        DebugManager.debug(DebugCategory.LOOKDEV,
            "[PhotonShaderCompiler] Writing shader files for: {}", shaderName);

        DebugManager.tryCatch("[PhotonShaderCompiler] writeShaderFiles", () -> {
            // Create shader directory
            DebugManager.debug(DebugCategory.LOOKDEV,
                "[PhotonShaderCompiler] Creating shader directory: {}", SHADER_BASE_PATH);
            Files.createDirectories(SHADER_BASE_PATH);

            // Write vertex shader
            Path vshPath = SHADER_BASE_PATH.resolve(shaderName + ".vsh");
            DebugManager.debug(DebugCategory.LOOKDEV,
                "[PhotonShaderCompiler] Writing vertex shader: {}", vshPath);
            Files.writeString(vshPath, shaderCode.vertexShader);

            // Write fragment shader
            Path fshPath = SHADER_BASE_PATH.resolve(shaderName + ".fsh");
            DebugManager.debug(DebugCategory.LOOKDEV,
                "[PhotonShaderCompiler] Writing fragment shader: {}", fshPath);
            Files.writeString(fshPath, shaderCode.fragmentShader);

            // Write shader config JSON
            String configJson = generateShaderConfig(shaderName);
            Path jsonPath = SHADER_BASE_PATH.resolve(shaderName + ".json");
            DebugManager.debug(DebugCategory.LOOKDEV,
                "[PhotonShaderCompiler] Writing shader config: {}", jsonPath);
            Files.writeString(jsonPath, configJson);

            DebugManager.info(DebugCategory.LOOKDEV,
                "[PhotonShaderCompiler] Successfully wrote all shader files for: {}", shaderName);
        });
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
        DebugManager.debug(DebugCategory.LOOKDEV,
            "[PhotonShaderCompiler] Compiling shader instance: {}", shaderLocation);

        return DebugManager.tryCatch("[PhotonShaderCompiler] compileShaderInstance", () -> {
            ResourceManager resourceManager = Minecraft.getInstance().getResourceManager();
            ShaderInstance instance = new ShaderInstance(resourceManager, shaderLocation.toString(), DefaultVertexFormat.NEW_ENTITY);

            DebugManager.debug(DebugCategory.LOOKDEV,
                "[PhotonShaderCompiler] Successfully created shader instance: {}", shaderLocation);

            return instance;
        }, null);
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
        DebugManager.debug(DebugCategory.LOOKDEV,
            "[PhotonShaderCompiler] Clearing shader cache ({} shaders)", shaderCache.size());

        DebugManager.tryCatch("[PhotonShaderCompiler] clearCache", () -> {
            // Close all shader instances
            for (CompiledShader shader : shaderCache.values()) {
                if (shader.shaderInstance != null) {
                    DebugManager.debug(DebugCategory.LOOKDEV,
                        "[PhotonShaderCompiler] Closing shader instance: {}", shader.name);
                    shader.shaderInstance.close();
                }
            }
            shaderCache.clear();

            DebugManager.info(DebugCategory.LOOKDEV,
                "[PhotonShaderCompiler] Shader cache cleared");
        });
    }

    /**
     * Reload shader (recompile from source)
     */
    public static CompiledShader reload(String shaderName, ShaderCodeGenerator.ShaderCode shaderCode) {
        DebugManager.debug(DebugCategory.LOOKDEV,
            "[PhotonShaderCompiler] Reloading shader: {}", shaderName);

        return DebugManager.tryCatch("[PhotonShaderCompiler] reload", () -> {
            // Remove from cache
            CompiledShader old = shaderCache.remove(shaderName);
            if (old != null && old.shaderInstance != null) {
                DebugManager.debug(DebugCategory.LOOKDEV,
                    "[PhotonShaderCompiler] Closing old shader instance: {}", shaderName);
                old.shaderInstance.close();
            }

            // Recompile
            DebugManager.debug(DebugCategory.LOOKDEV,
                "[PhotonShaderCompiler] Recompiling shader: {}", shaderName);
            return compile(shaderName, shaderCode);
        }, null);
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
