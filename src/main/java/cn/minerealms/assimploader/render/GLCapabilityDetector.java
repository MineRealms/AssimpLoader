package cn.minerealms.assimploader.render;

import cn.minerealms.assimploader.AssimpLoader;
import org.lwjgl.opengl.GL;
import org.lwjgl.opengl.GLCapabilities;
import org.lwjgl.opengl.GL11;
import org.lwjgl.opengl.GL20;
import org.lwjgl.opengl.GL30;
import org.lwjgl.opengl.GL31;
import org.lwjgl.opengl.GL33;
import org.lwjgl.opengl.GL43;

/**
 * Detects OpenGL capabilities for optimization features.
 * Used to determine which rendering optimizations are available.
 */
public class GLCapabilityDetector {
    private static Boolean computeShaderSupported = null;
    private static Boolean ssboSupported = null;
    private static Boolean tboSupported = null;
    private static Boolean uboSupported = null;
    private static Boolean instancingSupported = null;
    private static String glVersion = null;
    private static String glRenderer = null;
    private static int glVersionMajor = 0;
    private static int glVersionMinor = 0;

    /**
     * Detect all GPU capabilities
     */
    public static void detect() {
        try {
            GLCapabilities caps = GL.getCapabilities();

            glVersion = GL11.glGetString(GL11.GL_VERSION);
            glRenderer = GL11.glGetString(GL11.GL_RENDERER);

            // Parse OpenGL version
            parseGLVersion(glVersion);

            // Detect features
            computeShaderSupported = caps.OpenGL43 || caps.GL_ARB_compute_shader;
            ssboSupported = caps.OpenGL43 || caps.GL_ARB_shader_storage_buffer_object;
            tboSupported = caps.OpenGL31 || caps.GL_ARB_texture_buffer_object;
            uboSupported = caps.OpenGL31 || caps.GL_ARB_uniform_buffer_object;
            instancingSupported = caps.OpenGL33 || caps.GL_ARB_instanced_arrays;

            AssimpLoader.LOGGER.info("=== GPU Capabilities Detected ===");
            AssimpLoader.LOGGER.info("OpenGL Version: {}", glVersion);
            AssimpLoader.LOGGER.info("OpenGL Renderer: {}", glRenderer);
            AssimpLoader.LOGGER.info("OpenGL Version Parsed: {}.{}", glVersionMajor, glVersionMinor);
            AssimpLoader.LOGGER.info("Compute Shader: {}", computeShaderSupported);
            AssimpLoader.LOGGER.info("SSBO (Shader Storage Buffer Object): {}", ssboSupported);
            AssimpLoader.LOGGER.info("TBO (Texture Buffer Object): {}", tboSupported);
            AssimpLoader.LOGGER.info("UBO (Uniform Buffer Object): {}", uboSupported);
            AssimpLoader.LOGGER.info("Instanced Rendering: {}", instancingSupported);

            if (computeShaderSupported) {
                int maxWorkGroupSize = GL11.glGetInteger(GL43.GL_MAX_COMPUTE_WORK_GROUP_INVOCATIONS);
                int maxWorkGroupCountX = GL11.glGetInteger(GL43.GL_MAX_COMPUTE_WORK_GROUP_COUNT);
                AssimpLoader.LOGGER.info("Max Compute Work Group Invocations: {}", maxWorkGroupSize);
                AssimpLoader.LOGGER.info("Max Compute Work Group Count (X): {}", maxWorkGroupCountX);
            }

            if (uboSupported) {
                int maxUBOSize = GL11.glGetInteger(GL31.GL_MAX_UNIFORM_BLOCK_SIZE);
                AssimpLoader.LOGGER.info("Max UBO Size: {} bytes ({} KB)", maxUBOSize, maxUBOSize / 1024);
            }

            // Log optimization recommendations
            logOptimizationRecommendations();

        } catch (Exception e) {
            AssimpLoader.LOGGER.error("Failed to detect GPU capabilities", e);
            // Set safe defaults
            computeShaderSupported = false;
            ssboSupported = false;
            tboSupported = false;
            uboSupported = false;
            instancingSupported = false;
        }
    }

    private static void parseGLVersion(String version) {
        try {
            // Version format: "4.6.0 NVIDIA 536.23" or "3.3.0"
            String[] parts = version.split("\\s+");
            String versionNumber = parts[0];
            String[] versionParts = versionNumber.split("\\.");

            if (versionParts.length >= 2) {
                glVersionMajor = Integer.parseInt(versionParts[0]);
                glVersionMinor = Integer.parseInt(versionParts[1]);
            }
        } catch (Exception e) {
            AssimpLoader.LOGGER.warn("Failed to parse OpenGL version: {}", version);
            glVersionMajor = 3;
            glVersionMinor = 3;
        }
    }

    private static void logOptimizationRecommendations() {
        AssimpLoader.LOGGER.info("=== Optimization Recommendations ===");

        if (glVersionMajor >= 4 && glVersionMinor >= 3) {
            AssimpLoader.LOGGER.info("OpenGL 4.3+: All optimizations available (Compute Shader, SSBO, TBO, UBO, Instancing)");
        } else if (glVersionMajor >= 3 && glVersionMinor >= 3) {
            AssimpLoader.LOGGER.info("OpenGL 3.3+: TBO, UBO, and Instancing available. Compute Shader not supported.");
        } else if (glVersionMajor >= 3 && glVersionMinor >= 1) {
            AssimpLoader.LOGGER.info("OpenGL 3.1+: TBO and UBO available. Instancing and Compute Shader not supported.");
        } else {
            AssimpLoader.LOGGER.warn("OpenGL < 3.1: Limited optimization support. Consider upgrading GPU drivers.");
        }

        if (!instancingSupported) {
            AssimpLoader.LOGGER.warn("Instanced rendering not supported - Phase 4 optimizations will be disabled");
        }

        if (!tboSupported) {
            AssimpLoader.LOGGER.warn("TBO not supported - will use uniform arrays for bone matrices (limited to ~80 bones)");
        }

        if (!computeShaderSupported) {
            AssimpLoader.LOGGER.info("Compute shaders not supported - will use vertex shader skinning (still efficient)");
        }
    }

    public static boolean isComputeShaderSupported() {
        if (computeShaderSupported == null) detect();
        return computeShaderSupported;
    }

    public static boolean isSSBOSupported() {
        if (ssboSupported == null) detect();
        return ssboSupported;
    }

    public static boolean isTBOSupported() {
        if (tboSupported == null) detect();
        return tboSupported;
    }

    public static boolean isUBOSupported() {
        if (uboSupported == null) detect();
        return uboSupported;
    }

    public static boolean isInstancingSupported() {
        if (instancingSupported == null) detect();
        return instancingSupported;
    }

    public static String getGLVersion() {
        if (glVersion == null) detect();
        return glVersion;
    }

    public static String getGLRenderer() {
        if (glRenderer == null) detect();
        return glRenderer;
    }

    public static int getGLVersionMajor() {
        if (glVersion == null) detect();
        return glVersionMajor;
    }

    public static int getGLVersionMinor() {
        if (glVersion == null) detect();
        return glVersionMinor;
    }

    /**
     * Get recommended optimization level based on GPU capabilities
     */
    public static OptimizationLevel getRecommendedOptimizationLevel() {
        if (glVersion == null) detect();

        if (computeShaderSupported && ssboSupported && instancingSupported) {
            return OptimizationLevel.MAXIMUM;
        } else if (tboSupported && uboSupported && instancingSupported) {
            return OptimizationLevel.HIGH;
        } else if (tboSupported && uboSupported) {
            return OptimizationLevel.MEDIUM;
        } else {
            return OptimizationLevel.LOW;
        }
    }

    public enum OptimizationLevel {
        MAXIMUM("All optimizations enabled (Compute Shader + SSBO + Instancing)"),
        HIGH("High optimizations (TBO + UBO + Instancing)"),
        MEDIUM("Medium optimizations (TBO + UBO)"),
        LOW("Basic optimizations (Uniform arrays only)");

        private final String description;

        OptimizationLevel(String description) {
            this.description = description;
        }

        public String getDescription() {
            return description;
        }
    }
}
