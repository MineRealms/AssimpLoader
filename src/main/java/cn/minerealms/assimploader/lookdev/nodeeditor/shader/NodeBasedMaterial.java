package cn.minerealms.assimploader.lookdev.nodeeditor.shader;

import cn.minerealms.assimploader.debug.DebugManager;
import cn.minerealms.assimploader.debug.DebugManager.DebugCategory;
import cn.minerealms.assimploader.lookdev.nodeeditor.nodes.input.TextureSampleNode;
import com.mojang.blaze3d.systems.RenderSystem;
import net.minecraft.client.renderer.ShaderInstance;
import net.minecraft.resources.ResourceLocation;
import org.lwjgl.opengl.GL11;

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
    private final Map<Integer, TextureSampleNode.FilterMode> textureFilterModes = new HashMap<>();
    private final Map<Integer, TextureSampleNode.WrapMode> textureWrapModes = new HashMap<>();

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

        // Bind textures to OpenGL texture units
        int textureUnit = 0;
        for (Map.Entry<Integer, ResourceLocation> entry : textures.entrySet()) {
            ResourceLocation texture = entry.getValue();

            // Activate texture unit
            RenderSystem.activeTexture(org.lwjgl.opengl.GL13.GL_TEXTURE0 + textureUnit);

            // Bind texture using Minecraft's texture manager
            net.minecraft.client.Minecraft.getInstance()
                .getTextureManager()
                .bindForSetup(texture);

            // Apply texture filtering mode
            TextureSampleNode.FilterMode filterMode = textureFilterModes.getOrDefault(textureUnit, TextureSampleNode.FilterMode.LINEAR);
            GL11.glTexParameteri(GL11.GL_TEXTURE_2D, GL11.GL_TEXTURE_MIN_FILTER, filterMode.toGLConstant());
            GL11.glTexParameteri(GL11.GL_TEXTURE_2D, GL11.GL_TEXTURE_MAG_FILTER,
                filterMode == TextureSampleNode.FilterMode.NEAREST ? GL11.GL_NEAREST : GL11.GL_LINEAR);

            // Apply texture wrap mode
            TextureSampleNode.WrapMode wrapMode = textureWrapModes.getOrDefault(textureUnit, TextureSampleNode.WrapMode.REPEAT);
            GL11.glTexParameteri(GL11.GL_TEXTURE_2D, GL11.GL_TEXTURE_WRAP_S, wrapMode.toGLConstant());
            GL11.glTexParameteri(GL11.GL_TEXTURE_2D, GL11.GL_TEXTURE_WRAP_T, wrapMode.toGLConstant());

            // Set shader sampler uniform
            if (compiledShader.shaderInstance != null) {
                try {
                    String samplerName = "u_Texture" + textureUnit;
                    compiledShader.shaderInstance.setSampler(samplerName, textureUnit);
                    DebugManager.debug(DebugCategory.LOOKDEV,
                        "[NodeBasedMaterial] Bound texture {} to unit {} ({}) - Filter: {}, Wrap: {}",
                        texture, textureUnit, samplerName, filterMode, wrapMode);
                } catch (Exception e) {
                    DebugManager.warn(DebugCategory.LOOKDEV,
                        "[NodeBasedMaterial] Failed to set sampler for texture unit {}: {}",
                        textureUnit, e.getMessage());
                }
            }

            textureUnit++;
        }

        // Reset to texture unit 0
        RenderSystem.activeTexture(org.lwjgl.opengl.GL13.GL_TEXTURE0);

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
     * Set a texture with filtering and wrap modes
     */
    public void setTexture(int unit, ResourceLocation texture,
                          TextureSampleNode.FilterMode filterMode,
                          TextureSampleNode.WrapMode wrapMode) {
        textures.put(unit, texture);
        textureFilterModes.put(unit, filterMode);
        textureWrapModes.put(unit, wrapMode);
    }

    /**
     * Set texture filtering mode for a specific texture unit
     */
    public void setTextureFilterMode(int unit, TextureSampleNode.FilterMode filterMode) {
        textureFilterModes.put(unit, filterMode);
    }

    /**
     * Set texture wrap mode for a specific texture unit
     */
    public void setTextureWrapMode(int unit, TextureSampleNode.WrapMode wrapMode) {
        textureWrapModes.put(unit, wrapMode);
    }

    /**
     * Clear all uniforms
     */
    public void clearUniforms() {
        uniforms.clear();
        textures.clear();
        textureFilterModes.clear();
        textureWrapModes.clear();
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
