package cn.minerealms.assimploader.lookdev.nodeeditor.shader;

import cn.minerealms.assimploader.debug.DebugManager;
import cn.minerealms.assimploader.debug.DebugManager.DebugCategory;
import com.mojang.blaze3d.systems.RenderSystem;
import net.minecraft.client.renderer.ShaderInstance;
import net.minecraft.resources.ResourceLocation;

import java.util.HashMap;
import java.util.Map;

/**
 * Material class for node-based custom shaders.
 *
 * This material:
 * - Uses shaders compiled from node graphs
 * - Manages uniform binding (textures, parameters)
 * - Integrates with Photon's material system
 * - Supports hot-reloading when node graph changes
 */
public class NodeBasedMaterial {

    private final String materialName;
    private String shaderName;
    private PhotonShaderCompiler.CompiledShader compiledShader;

    // Uniform data
    private final Map<String, Object> uniforms = new HashMap<>();
    private final Map<Integer, ResourceLocation> textures = new HashMap<>();

    // State
    private boolean needsRecompile = false;
    private String lastError = null;

    public NodeBasedMaterial(String materialName) {
        this.materialName = materialName;
        this.shaderName = "node_material_" + materialName.toLowerCase().replaceAll("[^a-z0-9_]", "_");
    }

    /**
     * Compile shader from generated code
     */
    public boolean compile(ShaderCodeGenerator.ShaderCode shaderCode) {
        try {
            compiledShader = PhotonShaderCompiler.compile(shaderName, shaderCode);

            if (compiledShader != null && compiledShader.isSuccess()) {
                needsRecompile = false;
                lastError = null;
                DebugManager.info(DebugCategory.LOOKDEV,
                    "[NodeBasedMaterial] Compiled material: " + materialName);
                return true;
            } else {
                lastError = compiledShader != null ? compiledShader.errorMessage : "Unknown error";
                DebugManager.error(DebugCategory.LOOKDEV,
                    "[NodeBasedMaterial] Failed to compile material: " + materialName + " - " + lastError);
                return false;
            }
        } catch (Exception e) {
            lastError = e.getMessage();
            DebugManager.error(DebugCategory.LOOKDEV,
                "[NodeBasedMaterial] Exception during compilation: " + e.getMessage());
            e.printStackTrace();
            return false;
        }
    }

    /**
     * Reload shader (recompile from new code)
     */
    public boolean reload(ShaderCodeGenerator.ShaderCode shaderCode) {
        try {
            compiledShader = PhotonShaderCompiler.reload(shaderName, shaderCode);

            if (compiledShader != null && compiledShader.isSuccess()) {
                needsRecompile = false;
                lastError = null;
                DebugManager.info(DebugCategory.LOOKDEV,
                    "[NodeBasedMaterial] Reloaded material: " + materialName);
                return true;
            } else {
                lastError = compiledShader != null ? compiledShader.errorMessage : "Unknown error";
                return false;
            }
        } catch (Exception e) {
            lastError = e.getMessage();
            e.printStackTrace();
            return false;
        }
    }

    /**
     * Begin rendering with this material
     */
    public void begin() {
        if (compiledShader == null || !compiledShader.isSuccess()) {
            return;
        }

        // Set shader
        RenderSystem.setShader(() -> compiledShader.shaderInstance);

        // Bind textures
        for (Map.Entry<Integer, ResourceLocation> entry : textures.entrySet()) {
            int unit = entry.getKey();
            ResourceLocation texture = entry.getValue();
            // TODO: Bind texture to unit
            // RenderSystem.setShaderTexture(unit, texture);
        }

        // Set uniforms
        setupUniforms();
    }

    /**
     * End rendering with this material
     */
    public void end() {
        // Reset to default shader
        RenderSystem.setShader(null);
    }

    /**
     * Set up shader uniforms
     */
    private void setupUniforms() {
        if (compiledShader == null || compiledShader.shaderInstance == null) {
            return;
        }

        ShaderInstance shader = compiledShader.shaderInstance;

        for (Map.Entry<String, Object> entry : uniforms.entrySet()) {
            String name = entry.getKey();
            Object value = entry.getValue();

            try {
                if (value instanceof Float) {
                    shader.safeGetUniform(name).set((Float) value);
                } else if (value instanceof float[]) {
                    float[] arr = (float[]) value;
                    if (arr.length == 2) {
                        shader.safeGetUniform(name).set(arr[0], arr[1]);
                    } else if (arr.length == 3) {
                        shader.safeGetUniform(name).set(arr[0], arr[1], arr[2]);
                    } else if (arr.length == 4) {
                        shader.safeGetUniform(name).set(arr[0], arr[1], arr[2], arr[3]);
                    }
                }
            } catch (Exception e) {
                DebugManager.warn(DebugCategory.LOOKDEV,
                    "[NodeBasedMaterial] Failed to set uniform '" + name + "': " + e.getMessage());
            }
        }
    }

    /**
     * Set a float uniform
     */
    public void setUniform(String name, float value) {
        uniforms.put(name, value);
    }

    /**
     * Set a vec2 uniform
     */
    public void setUniform(String name, float x, float y) {
        uniforms.put(name, new float[]{x, y});
    }

    /**
     * Set a vec3 uniform
     */
    public void setUniform(String name, float x, float y, float z) {
        uniforms.put(name, new float[]{x, y, z});
    }

    /**
     * Set a vec4 uniform
     */
    public void setUniform(String name, float x, float y, float z, float w) {
        uniforms.put(name, new float[]{x, y, z, w});
    }

    /**
     * Set a texture uniform
     */
    public void setTexture(int unit, ResourceLocation texture) {
        textures.put(unit, texture);
    }

    /**
     * Clear all uniforms
     */
    public void clearUniforms() {
        uniforms.clear();
        textures.clear();
    }

    /**
     * Mark material as needing recompilation
     */
    public void markDirty() {
        needsRecompile = true;
    }

    /**
     * Check if material needs recompilation
     */
    public boolean needsRecompile() {
        return needsRecompile;
    }

    /**
     * Check if material is compiled and ready
     */
    public boolean isReady() {
        return compiledShader != null && compiledShader.isSuccess();
    }

    /**
     * Check if material has compilation error
     */
    public boolean hasError() {
        return lastError != null;
    }

    /**
     * Get last compilation error message
     */
    public String getErrorMessage() {
        return lastError;
    }

    /**
     * Get material name
     */
    public String getMaterialName() {
        return materialName;
    }

    /**
     * Get shader name
     */
    public String getShaderName() {
        return shaderName;
    }

    /**
     * Get compiled shader instance
     */
    public ShaderInstance getShaderInstance() {
        return compiledShader != null ? compiledShader.shaderInstance : null;
    }
}
