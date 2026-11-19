package cn.minerealms.assimploader.rendering.splat;

import net.minecraft.resources.ResourceLocation;
import org.joml.Matrix4f;
import org.joml.Vector3f;
import org.lwjgl.opengl.GL20;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.stream.Collectors;
import cn.minerealms.assimploader.util.ResourceLocations;
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
    private final float[] matrixData = new float[16];

    public GaussianSplatShader() {
    }

    /**
     * Compile and link the shader program
     */
    public void compile() throws IOException {
        compile(org.lwjgl.opengl.GL.getCapabilities().OpenGL43);
    }

    public void compile(boolean useSSBO) throws IOException {
        if (compiled) {
            return;
        }

        // Load shader sources
        String vertexSource = loadShaderSource(ResourceLocations.of("assimploader", "shaders/core/gaussian_splat.vert"));
        String fragmentSource = loadShaderSource(ResourceLocations.of("assimploader", "shaders/core/gaussian_splat.frag"));
        if (useSSBO) vertexSource = vertexSource.replace("#version 330 core", "#version 430 core\n#define SPLAT_SSBO");

        // Compile shaders
        try {
            vertexShaderId = compileShader(GL20.GL_VERTEX_SHADER, vertexSource);
            fragmentShaderId = compileShader(GL20.GL_FRAGMENT_SHADER, fragmentSource);

            // Link program
            programId = GL20.glCreateProgram();
            GL20.glAttachShader(programId, vertexShaderId);
            GL20.glAttachShader(programId, fragmentShaderId);
            GL20.glLinkProgram(programId);

            if (GL20.glGetProgrami(programId, GL20.GL_LINK_STATUS) == GL20.GL_FALSE) {
                String log = GL20.glGetProgramInfoLog(programId);
                throw new RuntimeException("Failed to link Gaussian splat shader: " + log);
            }

            u_view_matrix = GL20.glGetUniformLocation(programId, "view_matrix");
            u_projection_matrix = GL20.glGetUniformLocation(programId, "projection_matrix");
            u_hfovxy_focal = GL20.glGetUniformLocation(programId, "hfovxy_focal");
            u_cam_pos = GL20.glGetUniformLocation(programId, "cam_pos");
            u_sh_dim = GL20.glGetUniformLocation(programId, "sh_dim");
            u_scale_modifier = GL20.glGetUniformLocation(programId, "scale_modifier");
            u_render_mod = GL20.glGetUniformLocation(programId, "render_mod");
            int previous = org.lwjgl.opengl.GL11.glGetInteger(GL20.GL_CURRENT_PROGRAM);
            GL20.glUseProgram(programId);
            try {
                GL20.glUniform1i(GL20.glGetUniformLocation(programId, "gaussianDataTBO"), 0);
                GL20.glUniform1i(GL20.glGetUniformLocation(programId, "gaussianOrderTBO"), 1);
            } finally {
                GL20.glUseProgram(previous);
            }

            compiled = true;
        } catch (RuntimeException | Error failure) {
            delete();
            throw failure;
        } finally {
            if (vertexShaderId != 0) GL20.glDeleteShader(vertexShaderId);
            if (fragmentShaderId != 0) GL20.glDeleteShader(fragmentShaderId);
            vertexShaderId = fragmentShaderId = 0;
        }
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
        matrix.get(matrixData);
        GL20.glUniformMatrix4fv(u_view_matrix, false, matrixData);
    }

    /**
     * Set projection matrix
     */
    public void setProjectionMatrix(Matrix4f matrix) {
        matrix.get(matrixData);
        GL20.glUniformMatrix4fv(u_projection_matrix, false, matrixData);
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
        if (vertexShaderId != 0) GL20.glDeleteShader(vertexShaderId);
        if (fragmentShaderId != 0) GL20.glDeleteShader(fragmentShaderId);
        if (programId != 0) GL20.glDeleteProgram(programId);
        vertexShaderId = fragmentShaderId = programId = 0;
        compiled = false;
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
            try (BufferedReader reader = new BufferedReader(new InputStreamReader(stream, StandardCharsets.UTF_8))) {
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
            GL20.glDeleteShader(shaderId);
            throw new RuntimeException("Failed to compile " + typeName + " shader: " + log);
        }

        return shaderId;
    }

    public boolean isCompiled() {
        return compiled;
    }
}
