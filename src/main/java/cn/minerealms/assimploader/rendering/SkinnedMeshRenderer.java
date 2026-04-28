package cn.minerealms.assimploader.rendering;

import cn.minerealms.assimploader.animation.BoneHierarchyBuilder;
import cn.minerealms.assimploader.animation.SkinnedMeshProcessor;
import cn.minerealms.assimploader.debug.DebugManager;
import cn.minerealms.assimploader.debug.DebugManager.DebugCategory;
import cn.minerealms.assimploader.model.AssimpMesh;
import cn.minerealms.assimploader.render.BoneMatrixTBO;
import cn.minerealms.assimploader.render.GLCapabilityDetector;
import com.mojang.blaze3d.systems.RenderSystem;
import net.minecraft.client.renderer.texture.TextureManager;
import net.minecraft.resources.ResourceLocation;
import org.joml.Matrix4f;

import java.io.IOException;
import java.util.HashMap;
import java.util.Map;

/**
 * Renderer for skinned meshes with bone animation support.
 *
 * Each instance manages its own shader, VBO, and rendering state.
 * Supports per-mesh animation with independent bone hierarchies.
 */
public class SkinnedMeshRenderer {

    private final SkinnedMeshShader shader;
    private final Map<AssimpMesh, SkinnedMeshVBO> vboCache;
    private final BoneMatrixTBO boneMatrixTBO;
    private boolean initialized = false;
    private boolean useTBO = false;

    public SkinnedMeshRenderer() {
        this.shader = new SkinnedMeshShader();
        this.vboCache = new HashMap<>();
        this.boneMatrixTBO = new BoneMatrixTBO();
    }

    /**
     * Initialize the renderer (compile shaders)
     */
    public void initialize() throws IOException {
        if (initialized) {
            return;
        }

        shader.compile();

        // Check if TBO is supported
        useTBO = GLCapabilityDetector.isTBOSupported();
        if (useTBO) {
            DebugManager.info(DebugCategory.RENDER, "SkinnedMeshRenderer: TBO support enabled (up to 16384 bones)");
        } else {
            DebugManager.info(DebugCategory.RENDER, "SkinnedMeshRenderer: Using uniform arrays (max 64 bones)");
        }

        initialized = true;

        DebugManager.info(DebugCategory.RENDER, "SkinnedMeshRenderer initialized");
    }

    /**
     * Render a skinned mesh with bone animation
     *
     * @param mesh The mesh to render
     * @param boneHierarchy Bone hierarchy with updated transforms
     * @param modelViewProjection MVP matrix
     * @param modelView MV matrix
     * @param texture Texture resource location
     * @param textureManager Minecraft texture manager
     * @param lightLevel Minecraft light level (0.0 - 1.0)
     */
    public void render(AssimpMesh mesh, BoneHierarchyBuilder boneHierarchy,
                       Matrix4f modelViewProjection, Matrix4f modelView,
                       ResourceLocation texture, TextureManager textureManager,
                       float lightLevel) {

        if (!initialized) {
            throw new IllegalStateException("Renderer not initialized");
        }

        if (!mesh.hasBones()) {
            DebugManager.warn(DebugCategory.RENDER, "Mesh {} has no bones, cannot render as skinned mesh", mesh.getName());
            return;
        }

        // Performance profiling
        if (DebugManager.isProfilingEnabled()) {
            cn.minerealms.assimploader.debug.PerformanceProfiler.start("skinned_mesh_render");
        }

        // Get or create VBO
        SkinnedMeshVBO vbo = vboCache.computeIfAbsent(mesh, m -> {
            DebugManager.info(DebugCategory.RENDER, "Creating VBO for mesh: {}", m.getName());
            return createVBO(m);
        });

        // Bind shader
        shader.bind();

        // Set matrices
        shader.setModelViewProjection(modelViewProjection);
        shader.setModelView(modelView);

        // Set bone matrices
        if (DebugManager.isProfilingEnabled()) {
            cn.minerealms.assimploader.debug.PerformanceProfiler.start("bone_matrix_upload");
        }

        if (useTBO) {
            // Use TBO for efficient bone matrix upload
            Matrix4f[] boneMatrices = boneHierarchy.getFinalTransforms();
            boneMatrixTBO.upload(boneMatrices);
            boneMatrixTBO.bind(1); // Bind to texture unit 1
            shader.setUseTBO(true);
            DebugManager.debug(DebugCategory.RENDER, "Uploaded {} bone matrices via TBO", boneMatrices.length);
        } else {
            // Fallback to uniform arrays (max 64 bones)
            float[] boneMatrices = boneHierarchy.getFinalTransformsFlat();
            int boneCount = Math.min(boneMatrices.length / 16, 64);
            shader.setBoneMatrices(boneMatrices);
            shader.setUseTBO(false);
            DebugManager.debug(DebugCategory.RENDER, "Uploaded {} bone matrices via uniforms", boneCount);
        }

        if (DebugManager.isProfilingEnabled()) {
            cn.minerealms.assimploader.debug.PerformanceProfiler.end("bone_matrix_upload");
        }

        int boneCount = boneHierarchy.getBoneCount();
        DebugManager.debug(DebugCategory.RENDER, "Rendering skinned mesh '{}' with {} bones (TBO: {})",
            mesh.getName(), boneCount, useTBO);

        // Bind texture
        RenderSystem.setShaderTexture(0, texture);
        shader.setTexture(0);

        // Set lighting
        shader.setLightDirection(0.2f, 1.0f, 0.3f); // Default light direction
        shader.setLightColor(1.0f, 1.0f, 1.0f);
        shader.setAmbientColor(0.3f, 0.3f, 0.3f);
        shader.setLightLevel(lightLevel);

        // Render
        if (DebugManager.isProfilingEnabled()) {
            cn.minerealms.assimploader.debug.PerformanceProfiler.start("vbo_draw");
        }

        vbo.render();

        if (DebugManager.isProfilingEnabled()) {
            cn.minerealms.assimploader.debug.PerformanceProfiler.end("vbo_draw");
        }

        // Unbind
        shader.unbind();

        if (DebugManager.isProfilingEnabled()) {
            cn.minerealms.assimploader.debug.PerformanceProfiler.end("skinned_mesh_render");
        }
    }

    /**
     * Create VBO for a mesh
     */
    private SkinnedMeshVBO createVBO(AssimpMesh mesh) {
        DebugManager.info(DebugCategory.RENDER, "Creating VBO for skinned mesh: {}", mesh.getName());

        // Get vertex data
        float[] positions = mesh.getVertices();
        float[] normals = mesh.getNormals();
        float[] texCoords = mesh.getTexCoords();
        int[] indices = mesh.getIndices();

        // Get bone data - need to build bone name to index map first
        Map<String, Integer> boneNameToIndexMap = new HashMap<>();
        // This will be populated by BoneHierarchyBuilder
        // For now, we'll extract it from the mesh
        org.lwjgl.assimp.AIMesh aiMesh = mesh.getNativeMesh();
        for (int i = 0; i < aiMesh.mNumBones(); i++) {
            org.lwjgl.assimp.AIBone aiBone = org.lwjgl.assimp.AIBone.create(aiMesh.mBones().get(i));
            String boneName = aiBone.mName().dataString();
            boneNameToIndexMap.put(boneName, i);
        }

        SkinnedMeshProcessor.SkinnedMeshData skinnedData = mesh.getSkinnedData(boneNameToIndexMap);

        if (skinnedData == null) {
            throw new IllegalStateException("Failed to extract skinned data from mesh: " + mesh.getName());
        }

        // Create and upload VBO
        SkinnedMeshVBO vbo = new SkinnedMeshVBO();
        vbo.upload(positions, normals, texCoords, skinnedData, indices);

        return vbo;
    }

    /**
     * Clear VBO cache
     */
    public void clearCache() {
        for (SkinnedMeshVBO vbo : vboCache.values()) {
            vbo.delete();
        }
        vboCache.clear();
        DebugManager.debug(DebugCategory.RENDER, "Cleared VBO cache");
    }

    /**
     * Clean up resources
     */
    public void cleanup() {
        clearCache();
        shader.delete();
        boneMatrixTBO.cleanup();
        initialized = false;
        DebugManager.info(DebugCategory.RENDER, "SkinnedMeshRenderer cleaned up");
    }

    public boolean isInitialized() {
        return initialized;
    }
}
