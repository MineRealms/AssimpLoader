package cn.minerealms.assimploader.rendering;

import com.mojang.blaze3d.platform.GlStateManager;
import com.mojang.blaze3d.systems.RenderSystem;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.ShaderInstance;
import net.minecraft.resources.ResourceLocation;
import org.joml.Matrix4f;
import org.lwjgl.opengl.GL20;
import org.lwjgl.opengl.GL30;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.FloatBuffer;
import java.util.stream.Collectors;

/**
 * Shader program for skinned mesh rendering with bone animation support.
 *
 * Features:
 * - Up to 256 bone matrices
 * - Vertex skinning with up to 4 bones per vertex
 * - Basic lighting support
 */
public class SkinnedMeshShader {

    private int programId;
    private int vertexShaderId;
    private int fragmentShaderId;

    // Uniform locations
    private int u_ModelViewProjection;
    private int u_ModelView;
    private int u_BoneMatrices;
    private int u_BoneMatricesTBO;
    private int u_UseTBO;
    private int u_Texture;
    private int u_LightDirection;
    private int u_LightColor;
    private int u_AmbientColor;
    private int u_LightLevel;
    private int u_Tint;
    private int u_BoneCount;
    private int u_Lightmap, u_Overlay, u_LightUV, u_OverlayUV, u_ColorModulator;
    private int u_FogStart, u_FogEnd, u_FogColor, u_FogShape;

    private boolean compiled = false;

    public SkinnedMeshShader() {
    }

    /**
     * Compile and link the shader program
     */
    public void compile() throws IOException {
        compile(cn.minerealms.assimploader.render.GLCapabilityDetector.isTBOSupported());
    }

    public void compile(boolean useTBO) throws IOException {
        if (compiled) {
            return;
        }

        // Load shader sources (try TBO version first, fallback to standard)
        String vertexSource;
        String fragmentSource;

        if (useTBO) {
            vertexSource = loadShaderSource(new ResourceLocation("assimploader", "shaders/skinned_mesh_tbo.vert"));
            fragmentSource = loadShaderSource(new ResourceLocation("assimploader", "shaders/skinned_mesh_tbo.frag"));
        } else {
            // Fallback to standard shaders
            vertexSource = loadShaderSource(new ResourceLocation("assimploader", "shaders/skinned_mesh.vert"));
            fragmentSource = loadShaderSource(new ResourceLocation("assimploader", "shaders/skinned_mesh.frag"));
        }

        // Compile shaders
        vertexShaderId = compileShader(GL20.GL_VERTEX_SHADER, vertexSource);
        fragmentShaderId = compileShader(GL20.GL_FRAGMENT_SHADER, fragmentSource);

        // Link program
        programId = GL20.glCreateProgram();
        GL20.glAttachShader(programId, vertexShaderId);
        GL20.glAttachShader(programId, fragmentShaderId);
        GL20.glLinkProgram(programId);

        // Check link status
        if (GL20.glGetProgrami(programId, GL20.GL_LINK_STATUS) == GL20.GL_FALSE) {
            String log = GL20.glGetProgramInfoLog(programId);
            throw new RuntimeException("Failed to link shader program: " + log);
        }

        // Get uniform locations
        u_ModelViewProjection = GL20.glGetUniformLocation(programId, "u_ModelViewProjection");
        u_ModelView = GL20.glGetUniformLocation(programId, "u_ModelView");
        u_BoneMatrices = GL20.glGetUniformLocation(programId, "u_BoneMatrices");
        u_BoneMatricesTBO = GL20.glGetUniformLocation(programId, "u_BoneMatricesTBO");
        u_UseTBO = GL20.glGetUniformLocation(programId, "u_UseTBO");
        u_Texture = GL20.glGetUniformLocation(programId, "u_Texture");
        u_LightDirection = GL20.glGetUniformLocation(programId, "u_LightDirection");
        u_LightColor = GL20.glGetUniformLocation(programId, "u_LightColor");
        u_AmbientColor = GL20.glGetUniformLocation(programId, "u_AmbientColor");
        u_LightLevel = GL20.glGetUniformLocation(programId, "u_LightLevel");
        u_Tint = GL20.glGetUniformLocation(programId, "u_Tint");
        u_BoneCount = GL20.glGetUniformLocation(programId, "u_BoneCount");
        u_Lightmap = GL20.glGetUniformLocation(programId, "u_Lightmap");
        u_Overlay = GL20.glGetUniformLocation(programId, "u_Overlay");
        u_LightUV = GL20.glGetUniformLocation(programId, "u_LightUV");
        u_OverlayUV = GL20.glGetUniformLocation(programId, "u_OverlayUV");
        u_ColorModulator = GL20.glGetUniformLocation(programId, "u_ColorModulator");
        u_FogStart = GL20.glGetUniformLocation(programId, "u_FogStart");
        u_FogEnd = GL20.glGetUniformLocation(programId, "u_FogEnd");
        u_FogColor = GL20.glGetUniformLocation(programId, "u_FogColor");
        u_FogShape = GL20.glGetUniformLocation(programId, "u_FogShape");

        compiled = true;
    }

    /**
     * Bind the shader program
     */
    public void bind() {
        if (!compiled) {
            throw new IllegalStateException("Shader not compiled");
        }
        GL20.glUseProgram(programId);
    }

    /**
     * Unbind the shader program
     */
    public void unbind() {
        GL20.glUseProgram(0);
    }

    /**
     * Set model-view-projection matrix
     */
    public void setModelViewProjection(Matrix4f matrix) {
        float[] data = new float[16];
        matrix.get(data);
        GL20.glUniformMatrix4fv(u_ModelViewProjection, false, data);
    }

    /**
     * Set model-view matrix
     */
    public void setModelView(Matrix4f matrix) {
        float[] data = new float[16];
        matrix.get(data);
        GL20.glUniformMatrix4fv(u_ModelView, false, data);
    }

    /**
     * Set bone matrices array (uniform fallback)
     *
     * @param matrices Flat array of bone matrices [bone0_m00, bone0_m01, ..., bone1_m00, ...]
     */
    public void setBoneMatrices(float[] matrices) {
        int maxBones = 64; // Reduced for compatibility
        int boneCount = Math.min(matrices.length / 16, maxBones);
        if (boneCount > 0) {
            if (matrices.length <= maxBones * 16) GL20.glUniformMatrix4fv(u_BoneMatrices, false, matrices);
            else {
                float[] truncated = java.util.Arrays.copyOf(matrices, boneCount * 16);
                GL20.glUniformMatrix4fv(u_BoneMatrices, false, truncated);
            }
        }
    }

    /**
     * Set whether to use TBO for bone matrices
     */
    public void setUseTBO(boolean useTBO) {
        if (u_UseTBO >= 0) {
            GL20.glUniform1i(u_UseTBO, useTBO ? 1 : 0);
        }
    }

    /**
     * Set TBO texture unit for bone matrices
     */
    public void setBoneMatricesTBO(int textureUnit) {
        if (u_BoneMatricesTBO >= 0) {
            GL20.glUniform1i(u_BoneMatricesTBO, textureUnit);
        }
    }

    /**
     * Set texture sampler
     */
    public void setTexture(int textureUnit) {
        GL20.glUniform1i(u_Texture, textureUnit);
    }

    /**
     * Set light direction
     */
    public void setLightDirection(float x, float y, float z) {
        GL20.glUniform3f(u_LightDirection, x, y, z);
    }

    /**
     * Set light color
     */
    public void setLightColor(float r, float g, float b) {
        GL20.glUniform3f(u_LightColor, r, g, b);
    }

    /**
     * Set ambient color
     */
    public void setAmbientColor(float r, float g, float b) {
        GL20.glUniform3f(u_AmbientColor, r, g, b);
    }

    /**
     * Set Minecraft light level (0.0 - 1.0)
     */
    public void setLightLevel(float level) {
        GL20.glUniform1f(u_LightLevel, level);
    }

    public void setTint(float[] tint) {
        GL20.glUniform4f(u_Tint, tint[0], tint[1], tint[2], tint[3]);
    }

    public void setBoneCount(int count) {
        GL20.glUniform1i(u_BoneCount, count);
    }

    public void setMinecraftLighting(int light, int overlay) {
        GL20.glUniform1i(u_Overlay, 1);
        GL20.glUniform1i(u_Lightmap, 2);
        GL20.glUniform2i(u_LightUV, light & 0xffff, light >>> 16 & 0xffff);
        GL20.glUniform2i(u_OverlayUV, overlay & 0xffff, overlay >>> 16 & 0xffff);
        float[] color = RenderSystem.getShaderColor(), fog = RenderSystem.getShaderFogColor();
        GL20.glUniform4f(u_ColorModulator, color[0], color[1], color[2], color[3]);
        GL20.glUniform1f(u_FogStart, RenderSystem.getShaderFogStart());
        GL20.glUniform1f(u_FogEnd, RenderSystem.getShaderFogEnd());
        GL20.glUniform4f(u_FogColor, fog[0], fog[1], fog[2], fog[3]);
        GL20.glUniform1i(u_FogShape, RenderSystem.getShaderFogShape().getIndex());
    }

    /**
     * Clean up shader resources
     */
    public void delete() {
        if (vertexShaderId != 0) {
            GL20.glDeleteShader(vertexShaderId);
            vertexShaderId = 0;
        }
        if (fragmentShaderId != 0) {
            GL20.glDeleteShader(fragmentShaderId);
            fragmentShaderId = 0;
        }
        if (programId != 0) {
            GL20.glDeleteProgram(programId);
            programId = 0;
        }
        compiled = false;
    }

    /**
     * Load shader source from resource location
     */
    private String loadShaderSource(ResourceLocation location) throws IOException {
        try (InputStream stream = Minecraft.getInstance().getResourceManager().open(location)) {
            try (BufferedReader reader = new BufferedReader(new InputStreamReader(stream))) {
                return reader.lines().collect(Collectors.joining("\n"));
            }
        }
    }

    /**
     * Compile a shader
     */
    private int compileShader(int type, String source) {
        int shaderId = GL20.glCreateShader(type);
        GL20.glShaderSource(shaderId, source);
        GL20.glCompileShader(shaderId);

        if (GL20.glGetShaderi(shaderId, GL20.GL_COMPILE_STATUS) == GL20.GL_FALSE) {
            String log = GL20.glGetShaderInfoLog(shaderId);
            GL20.glDeleteShader(shaderId);
            String typeName = type == GL20.GL_VERTEX_SHADER ? "vertex" : "fragment";
            throw new RuntimeException("Failed to compile " + typeName + " shader: " + log);
        }

        return shaderId;
    }
}
