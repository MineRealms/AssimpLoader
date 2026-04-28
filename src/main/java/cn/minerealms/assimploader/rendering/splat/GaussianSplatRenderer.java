package cn.minerealms.assimploader.rendering.splat;

import cn.minerealms.assimploader.debug.DebugManager;
import cn.minerealms.assimploader.debug.DebugManager.DebugCategory;
import cn.minerealms.assimploader.render.GLCapabilityDetector;
import cn.minerealms.assimploader.rendering.splat.data.GaussianSplatData;
import org.joml.Matrix4f;
import org.joml.Vector3f;
import org.lwjgl.opengl.GL11;
import org.lwjgl.opengl.GL31;

import java.io.IOException;
import java.util.HashMap;
import java.util.Map;

/**
 * Main renderer for Gaussian splats.
 *
 * Manages VBO caching, sorting, and instanced rendering.
 * Follows the same pattern as SkinnedMeshRenderer.
 */
public class GaussianSplatRenderer {

    private final GaussianSplatShader shader;
    private final Map<GaussianSplatData, GaussianSplatVBO> vboCache;
    private final GaussianSplatSorter sorter;
    private final GaussianSplatTBO tbo;
    private boolean initialized = false;
    private boolean useSSBO = false;

    public GaussianSplatRenderer() {
        this.shader = new GaussianSplatShader();
        this.vboCache = new HashMap<>();
        this.sorter = new GaussianSplatSorter();
        this.tbo = new GaussianSplatTBO();
    }

    /**
     * Initialize the renderer (compile shaders)
     */
    public void initialize() throws IOException {
        if (initialized) {
            return;
        }

        shader.compile();

        // Check if SSBO is supported
        useSSBO = GLCapabilityDetector.isSSBOSupported();
        if (useSSBO) {
            DebugManager.info(DebugCategory.RENDER, "GaussianSplatRenderer: SSBO support enabled");
        } else {
            DebugManager.info(DebugCategory.RENDER, "GaussianSplatRenderer: Using TBO fallback for Oculus compatibility");
        }

        // Initialize Oculus shader injection
        GaussianSplatOculusInjector.initialize();

        initialized = true;

        DebugManager.info(DebugCategory.RENDER, "GaussianSplatRenderer initialized");
    }

    /**
     * Render Gaussian splats
     *
     * @param data Splat data
     * @param viewMatrix View matrix
     * @param projectionMatrix Projection matrix
     * @param cameraPos Camera position
     * @param fovX Horizontal FOV (radians)
     * @param fovY Vertical FOV (radians)
     * @param focal Focal length
     * @param scaleModifier Scale modifier
     */
    public void render(GaussianSplatData data,
                       Matrix4f viewMatrix,
                       Matrix4f projectionMatrix,
                       Vector3f cameraPos,
                       float fovX, float fovY, float focal,
                       float scaleModifier) {

        if (!initialized) {
            throw new IllegalStateException("Renderer not initialized");
        }

        if (data.getSplatCount() == 0) {
            return;
        }

        // Performance profiling
        if (DebugManager.isProfilingEnabled()) {
            cn.minerealms.assimploader.debug.PerformanceProfiler.start("splat_render");
        }

        // Apply LOD filtering
        if (DebugManager.isProfilingEnabled()) {
            cn.minerealms.assimploader.debug.PerformanceProfiler.start("splat_lod");
        }
        GaussianSplatData lodData = GaussianSplatLOD.applyLOD(data, cameraPos);
        if (DebugManager.isProfilingEnabled()) {
            cn.minerealms.assimploader.debug.PerformanceProfiler.end("splat_lod");
        }

        if (lodData.getSplatCount() == 0) {
            if (DebugManager.isProfilingEnabled()) {
                cn.minerealms.assimploader.debug.PerformanceProfiler.end("splat_render");
            }
            return; // All splats culled
        }

        // Get or create VBO (use original data as key for caching)
        GaussianSplatVBO vbo = vboCache.computeIfAbsent(data, d -> {
            DebugManager.info(DebugCategory.RENDER, "Creating VBO for splat data: {} splats", d.getSplatCount());
            return createVBO(d);
        });

        // Sort splats by depth
        if (DebugManager.isProfilingEnabled()) {
            cn.minerealms.assimploader.debug.PerformanceProfiler.start("splat_sort");
        }
        sorter.sort(lodData.getSplats(), viewMatrix);
        if (DebugManager.isProfilingEnabled()) {
            cn.minerealms.assimploader.debug.PerformanceProfiler.end("splat_sort");
        }

        // Upload sorted indices
        vbo.uploadOrder(sorter.getSortedIndices());

        // Setup render state
        GaussianSplatRenderState.setup();

        // Bind shader and set uniforms
        shader.bind();
        shader.setViewMatrix(viewMatrix);
        shader.setProjectionMatrix(projectionMatrix);
        shader.setHFovxyFocal((float) Math.tan(fovX / 2.0f), (float) Math.tan(fovY / 2.0f), focal);
        shader.setCameraPos(cameraPos);
        shader.setShDim(data.getShDim());
        shader.setScaleModifier(scaleModifier);
        shader.setRenderMod(3); // Full rendering with SH degree 3

        // Render instanced
        vbo.render();

        // Unbind
        shader.unbind();

        // Restore render state
        GaussianSplatRenderState.restore();

        if (DebugManager.isProfilingEnabled()) {
            cn.minerealms.assimploader.debug.PerformanceProfiler.end("splat_render");
        }
    }

    /**
     * Create VBO for splat data
     */
    private GaussianSplatVBO createVBO(GaussianSplatData data) {
        GaussianSplatVBO vbo = new GaussianSplatVBO();
        vbo.upload(data, useSSBO);
        return vbo;
    }

    /**
     * Clear VBO cache
     */
    public void clearCache() {
        for (GaussianSplatVBO vbo : vboCache.values()) {
            vbo.delete();
        }
        vboCache.clear();
        DebugManager.info(DebugCategory.RENDER, "Cleared Gaussian splat VBO cache");
    }

    /**
     * Clean up renderer resources
     */
    public void delete() {
        clearCache();
        shader.delete();
        tbo.cleanup();
        initialized = false;
    }

    public boolean isInitialized() {
        return initialized;
    }
}
