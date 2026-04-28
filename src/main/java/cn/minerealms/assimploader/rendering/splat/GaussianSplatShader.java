package cn.minerealms.assimploader.rendering.splat;

import cn.minerealms.assimploader.debug.DebugManager;
import cn.minerealms.assimploader.debug.DebugManager.DebugCategory;
import net.minecraft.resources.ResourceLocation;
import org.joml.Matrix4f;
import org.joml.Vector3f;
import org.lwjgl.opengl.GL20;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.util.stream.Collectors;

/**
 * Shader program for Gaussian splat rendering.
 *
 * Manages compilation and uniform binding for splat shaders.
 */
public class GaussianSplatShader {

    private int programId;
    private int vertexShaderId;
    private int fragmentShaderId;

    // Uniform locations
    private int u_view_matrix;
    private int u_projection_matrix;
    private int u_hfovxy_focal;
    private int u_cam_pos;
    private int u_sh_dim;
    private int u_scale_modifier;
    private int u_render_mod;

    private boolean compiled = false;

    public GaussianSplatShader() {
    }

    /**
     * Compile and link the shader program
     */
    public void compile() throws IOException {
        if (compiled) {
            return;
        }

        // Load shader sources
        String vertexSource = loadShaderSource(new ResourceLocation("assimploader", "shaders/core/gaussian_splat.vert"));
        String fragmentSource = loadShaderSource(new ResourceLocation("assimploader", "shaders/core/gaussian_splat.frag"));

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
            throw new RuntimeException("Failed to link Gaussian splat shader: " + log);
        }

        // Get uniform locations
        u_view_matrix = GL20.glGetUniformLocation(programId, "view_matrix");
        u_projection_matrix = GL20.glGetUniformLocation(programId, "projection_matrix");
        u_hfovxy_focal = GL20.glGetUniformLocation(programId, "hfovxy_focal");
        u_cam_pos = GL20.glGetUniformLocation(programId, "cam_pos");
        u_sh_dim = GL20.glGetUniformLocation(programId, "sh_dim");
        u_scale_modifier = GL20.glGetUniformLocation(programId, "scale_modifier");
        u_render_mod = GL20.glGetUniformLocation(programId, "render_mod");

        compiled = true;

        DebugManager.info(DebugCategory.RENDER, "GaussianSplatShader compiled successfully");
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
     * Set view matrix
     */
    public void setViewMatrix(Matrix4f matrix) {
        float[] data = new float[16];
        matrix.get(data);
        GL20.glUniformMatrix4fv(u_view_matrix, false, data);
    }

    /**
     * Set projection matrix
     */
    public void setProjectionMatrix(Matrix4f matrix) {
        float[] data = new float[16];
        matrix.get(data);
        GL20.glUniformMatrix4fv(u_projection_matrix, false, data);
    }

    /**
     * Set horizontal/vertical FOV and focal length
     * @param hfovx Horizontal FOV tangent
     * @param hfovy Vertical FOV tangent
     * @param focal Focal length
     */
    public void setHFovxyFocal(float hfovx, float hfovy, float focal) {
        GL20.glUniform3f(u_hfovxy_focal, hfovx, hfovy, focal);
    }

    /**
     * Set camera position
     */
    public void setCameraPos(Vector3f pos) {
        GL20.glUniform3f(u_cam_pos, pos.x, pos.y, pos.z);
    }

    /**
     * Set spherical harmonics dimension
     */
    public void setShDim(int shDim) {
        GL20.glUniform1i(u_sh_dim, shDim);
    }

    /**
     * Set scale modifier
     */
    public void setScaleModifier(float modifier) {
        GL20.glUniform1f(u_scale_modifier, modifier);
    }

    /**
     * Set render mode
     * > 0: render 0-ith SH dim
     * -1: depth visualization
     * -2: billboard mode
     * -3: flat ball
     * -4: gaussian ball
     */
    public void setRenderMod(int mode) {
        GL20.glUniform1i(u_render_mod, mode);
    }

    /**
     * Clean up shader resources
     */
    public void delete() {
        if (compiled) {
            GL20.glDeleteShader(vertexShaderId);
            GL20.glDeleteShader(fragmentShaderId);
            GL20.glDeleteProgram(programId);
            compiled = false;
        }
    }

    /**
     * Load shader source from resource location
     */
    private String loadShaderSource(ResourceLocation location) throws IOException {
        String path = "/assets/" + location.getNamespace() + "/" + location.getPath();
        try (InputStream stream = GaussianSplatShader.class.getResourceAsStream(path)) {
            if (stream == null) {
                throw new IOException("Shader not found: " + path);
            }
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
            String typeName = type == GL20.GL_VERTEX_SHADER ? "vertex" : "fragment";
            throw new RuntimeException("Failed to compile " + typeName + " shader: " + log);
        }

        return shaderId;
    }

    public boolean isCompiled() {
        return compiled;
    }
}
