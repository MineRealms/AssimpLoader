package cn.minerealms.assimploader.rendering;

import cn.minerealms.assimploader.animation.BoneHierarchyBuilder;
import cn.minerealms.assimploader.animation.SkinnedMeshProcessor;
import cn.minerealms.assimploader.animation.MorphDeformation;
import cn.minerealms.assimploader.animation.AnimationState;
import cn.minerealms.assimploader.animation.PoseSnapshot;
import cn.minerealms.assimploader.config.AssimpLoaderConfig;
import cn.minerealms.assimploader.config.RuntimePerformancePolicy;
import cn.minerealms.assimploader.debug.DebugManager;
import cn.minerealms.assimploader.debug.DebugManager.DebugCategory;
import cn.minerealms.assimploader.model.AssimpMesh;
import cn.minerealms.assimploader.render.BoneMatrixTBO;
import cn.minerealms.assimploader.render.GLCapabilityDetector;
import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.vertex.PoseStack;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.client.renderer.texture.TextureManager;
import net.minecraft.resources.ResourceLocation;
import org.joml.Matrix4f;
import org.lwjgl.opengl.GL11;
import org.lwjgl.opengl.GL13;
import org.lwjgl.opengl.GL15;
import org.lwjgl.opengl.GL20;
import org.lwjgl.opengl.GL30;
import org.lwjgl.opengl.GL31;

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
    private final Map<AssimpMesh,MorphTargetTBO> morphCache=new HashMap<>();
    private int maximumMorphTexels;
    private boolean initialized = false;
    private boolean useTBO = false;
    private int maxBones = 64;
    private final Map<AnimationState,Map<AssimpMesh,ComputeSkinning>> computeOwners=new java.util.LinkedHashMap<>(16,.75f,true);
    private long computeBytes;
    private boolean computeFailed;

    public record ComputeStatistics(int owners,long bytes,long dispatches,long hits,boolean failed) {}
    public ComputeStatistics computeStatistics() {
        long dispatches=0,hits=0;
        for(var meshes:computeOwners.values()) for(var output:meshes.values()) {dispatches+=output.dispatches();hits+=output.hits();}
        return new ComputeStatistics(computeOwners.size(),computeBytes,dispatches,hits,computeFailed);
    }

    public CpuSkinning.Geometry readComputeOutput(AnimationState owner,AssimpMesh mesh) {
        var entries=computeOwners.get(owner);
        return entries==null || entries.get(mesh)==null?null:entries.get(mesh).readback();
    }

    public void pruneCompute() {
        boolean disabled=!AssimpLoaderConfig.getOrDefault(AssimpLoaderConfig.CLIENT.computeSkinningEnabled::get,false)
                || cn.minerealms.assimploader.compat.OculusCompat.shouldAvoidCustomShaders()
                || AssimpLoaderConfig.getOrDefault(AssimpLoaderConfig.CLIENT.optimizationLevel::get,
                    AssimpLoaderConfig.OptimizationLevel.AUTO)==AssimpLoaderConfig.OptimizationLevel.FALLBACK;
        long budget=(long)AssimpLoaderConfig.getIntOrDefault(AssimpLoaderConfig.CLIENT.computeSkinningMiB,128)*1024*1024;
        int capacity=AssimpLoaderConfig.getIntOrDefault(AssimpLoaderConfig.CLIENT.computeSkinningOwners,128);
        trimCompute(disabled?0:budget,disabled?0:capacity);
    }

    /** Immediate memory-pressure trim; subsequent draws still obey the configured allocation limits. */
    public void trimCompute(long budget,int capacity) {
        RenderSystem.assertOnRenderThread();
        if(budget<0 || capacity<0) throw new IllegalArgumentException("Negative Compute cache budget");
        var iterator=computeOwners.entrySet().iterator();
        while(iterator.hasNext()) {
            var entry=iterator.next();
            if(entry.getKey().isClosed() || computeBytes>budget || computeOwners.size()>capacity) {
                for(var mesh:entry.getValue().entrySet()) {computeBytes-=ComputeSkinning.requiredBytes(mesh.getKey());mesh.getValue().close();}
                iterator.remove();
            }
        }
    }

    /** Snapshot identity shares deformation across views while transforms and lighting remain per draw. */
    public void renderWithSnapshot(AnimationState owner,PoseSnapshot snapshot,AssimpMesh mesh,PoseStack poses,
            MultiBufferSource buffers,ResourceLocation texture,TextureManager textures,int light,int overlay,float[] tint) {
        var palette=snapshot.getBoneTransforms(mesh);
        var material=cn.minerealms.assimploader.render.ModelTextureCache.material(snapshot.getScene(),mesh);
        boolean doubleSided=material!=null&&material.isDoubleSided();
        if(!computeFailed && ComputeSkinning.available() && owner!=null && !owner.isClosed()
                && AssimpLoaderConfig.getOrDefault(AssimpLoaderConfig.CLIENT.computeSkinningEnabled::get,false)
                && !cn.minerealms.assimploader.compat.OculusCompat.shouldAvoidCustomShaders()) {
            try {
                pruneCompute();
                long required=ComputeSkinning.requiredBytes(mesh);
                long budget=(long)AssimpLoaderConfig.getIntOrDefault(AssimpLoaderConfig.CLIENT.computeSkinningMiB,128)*1024*1024;
                int capacity=AssimpLoaderConfig.getIntOrDefault(AssimpLoaderConfig.CLIENT.computeSkinningOwners,128);
                var meshes=computeOwners.get(owner);
                ComputeSkinning output=meshes==null?null:meshes.get(mesh);
                if(output==null && required<=Math.min(budget,128L*1024*1024)) {
                    var iterator=computeOwners.entrySet().iterator();
                    while(iterator.hasNext() && (computeBytes+required>budget || meshes==null&&computeOwners.size()>=capacity)) {
                        var old=iterator.next();
                        if(old.getKey()==owner) continue;
                        for(var entry:old.getValue().entrySet()) {computeBytes-=ComputeSkinning.requiredBytes(entry.getKey());entry.getValue().close();}
                        iterator.remove();
                    }
                    if(computeBytes+required<=budget) {
                        output=new ComputeSkinning(mesh);
                        if(meshes==null) {meshes=new java.util.IdentityHashMap<>();computeOwners.put(owner,meshes);}
                        meshes.put(mesh,output);computeBytes+=required;
                    }
                }
                if(output!=null) {
                    if(buffers instanceof MultiBufferSource.BufferSource source) source.endBatch();
                    Matrix4f modelView=createModelView(RenderSystem.getModelViewMatrix(),poses.last().pose());
                    try(GpuDrawState drawState=new GpuDrawState()) {
                        drawState.prepare(doubleSided);output.deform(snapshot);
                        renderPacked(mesh,palette,createModelViewProjection(RenderSystem.getProjectionMatrix(),modelView),modelView,
                                texture,textures,light,overlay,tint,null,output);
                    }
                    return;
                }
            } catch(IOException|RuntimeException failure) {
                computeFailed=true;clearCompute();
                cn.minerealms.assimploader.AssimpLoader.LOGGER.warn("Compute skinning failed; using vertex skinning until reload",failure);
            }
        }
        renderWithPose(mesh,palette,poses,buffers,texture,textures,light,overlay,tint,
                snapshot.hasActiveMorphs(mesh)?snapshot.getMorphWeights(mesh):null,doubleSided);
    }

    private void clearCompute() {
        for(var meshes:computeOwners.values()) meshes.values().forEach(ComputeSkinning::close);
        computeOwners.clear();computeBytes=0;
    }

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

        useTBO = tboRequested() && GLCapabilityDetector.isTBOSupported();
        maxBones = useTBO ? GL11.glGetInteger(GL31.GL_MAX_TEXTURE_BUFFER_SIZE) / 4 : 64;
        maximumMorphTexels=GLCapabilityDetector.isTBOSupported()?GL11.glGetInteger(GL31.GL_MAX_TEXTURE_BUFFER_SIZE):0;
        try {
            shader.compile(useTBO);
        } catch (IOException | RuntimeException e) {
            shader.delete();
            throw e;
        }

        // Check if TBO is supported
        if (useTBO) {
            DebugManager.info(DebugCategory.RENDER, "SkinnedMeshRenderer: TBO enabled (up to {} bones)",
                    Math.min(maxBones, AssimpLoaderConfig.getIntOrDefault(AssimpLoaderConfig.CLIENT.maxTBOBones, 1024)));
        } else {
            DebugManager.info(DebugCategory.RENDER, "SkinnedMeshRenderer: Using uniform arrays (max 64 bones)");
        }

        initialized = true;

        DebugManager.info(DebugCategory.RENDER, "SkinnedMeshRenderer initialized");
    }

    /** Draw immediately after pending Minecraft geometry, restoring the bindings used by this renderer. */
    public void renderWithPose(AssimpMesh mesh, BoneHierarchyBuilder boneHierarchy,
                               PoseStack poseStack, MultiBufferSource bufferSource,
                               ResourceLocation texture, TextureManager textureManager, float lightLevel) {
        renderWithPose(mesh, boneHierarchy, poseStack, bufferSource, texture, textureManager, lightLevel,
                new float[]{1, 1, 1, 1});
    }

    public void renderWithPose(AssimpMesh mesh, BoneHierarchyBuilder boneHierarchy,
                               PoseStack poseStack, MultiBufferSource bufferSource,
                               ResourceLocation texture, TextureManager textureManager, float lightLevel, float[] tint) {
        renderWithPose(mesh, boneHierarchy, poseStack, bufferSource, texture, textureManager,
                packedLight(lightLevel), OverlayTexture.NO_OVERLAY, tint);
    }

    public void renderWithPose(AssimpMesh mesh, BoneHierarchyBuilder boneHierarchy,
                               PoseStack poseStack, MultiBufferSource bufferSource,
                               ResourceLocation texture, TextureManager textureManager, int light, int overlay, float[] tint) {
        renderWithPose(mesh, boneHierarchy.getFinalTransforms(), poseStack, bufferSource,
                texture, textureManager, light, overlay, tint);
    }

    /** The palette belongs to the completed pose captured by the caller for this draw. */
    public void renderWithPose(AssimpMesh mesh, Matrix4f[] boneMatrices,
                               PoseStack poseStack, MultiBufferSource bufferSource,
                               ResourceLocation texture, TextureManager textureManager, int light, int overlay, float[] tint) {
        renderWithPose(mesh,boneMatrices,poseStack,bufferSource,texture,textureManager,light,overlay,tint,null);
    }

    public void renderWithPose(AssimpMesh mesh, Matrix4f[] boneMatrices,
                               PoseStack poseStack, MultiBufferSource bufferSource,
                               ResourceLocation texture, TextureManager textureManager, int light, int overlay, float[] tint,float[] morphWeights) {
        renderWithPose(mesh,boneMatrices,poseStack,bufferSource,texture,textureManager,light,overlay,tint,morphWeights,false);
    }

    private void renderWithPose(AssimpMesh mesh, Matrix4f[] boneMatrices,
                               PoseStack poseStack, MultiBufferSource bufferSource,
                               ResourceLocation texture, TextureManager textureManager, int light, int overlay, float[] tint,float[] morphWeights,boolean doubleSided) {
        if (bufferSource instanceof MultiBufferSource.BufferSource source) {
            source.endBatch();
        }

        Matrix4f modelView = createModelView(RenderSystem.getModelViewMatrix(), poseStack.last().pose());
        Matrix4f modelViewProjection = createModelViewProjection(RenderSystem.getProjectionMatrix(), modelView);
        try (GpuDrawState drawState = new GpuDrawState()) {
            drawState.prepare(doubleSided);
            renderPacked(mesh, boneMatrices, modelViewProjection, modelView, texture, textureManager, light, overlay, tint,morphWeights);
        }
    }

    private static int packedLight(float level) {
        int component = Math.round(Math.max(0, Math.min(1, level)) * 15) * 16;
        return component | component << 16;
    }

    static Matrix4f createModelView(Matrix4f view, Matrix4f pose) {
        return new Matrix4f(view).mul(pose);
    }

    static Matrix4f createModelViewProjection(Matrix4f projection, Matrix4f modelView) {
        return new Matrix4f(projection).mul(modelView);
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
        render(mesh, boneHierarchy, modelViewProjection, modelView, texture, textureManager, lightLevel,
                new float[]{1,1,1,1});
    }

    public void render(AssimpMesh mesh, BoneHierarchyBuilder boneHierarchy,
                       Matrix4f modelViewProjection, Matrix4f modelView,
                       ResourceLocation texture, TextureManager textureManager, float lightLevel, float[] tint) {
        render(mesh, boneHierarchy.getFinalTransforms(), modelViewProjection, modelView,
                texture, textureManager, lightLevel, tint);
    }

    public void render(AssimpMesh mesh, Matrix4f[] boneMatrices,
                       Matrix4f modelViewProjection, Matrix4f modelView,
                       ResourceLocation texture, TextureManager textureManager, float lightLevel, float[] tint) {
        try (GpuDrawState drawState = new GpuDrawState()) {
            drawState.prepare();
            renderPacked(mesh, boneMatrices, modelViewProjection, modelView, texture, textureManager,
                    packedLight(lightLevel), OverlayTexture.NO_OVERLAY, tint);
        }
    }

    private void renderPacked(AssimpMesh mesh, Matrix4f[] boneMatrices,
                       Matrix4f modelViewProjection, Matrix4f modelView,
                       ResourceLocation texture, TextureManager textureManager, int light, int overlay, float[] tint) {
        renderPacked(mesh,boneMatrices,modelViewProjection,modelView,texture,textureManager,light,overlay,tint,null);
    }

    private void renderPacked(AssimpMesh mesh, Matrix4f[] boneMatrices,
                       Matrix4f modelViewProjection, Matrix4f modelView,
                       ResourceLocation texture, TextureManager textureManager, int light, int overlay, float[] tint,float[] morphWeights) {
        renderPacked(mesh,boneMatrices,modelViewProjection,modelView,texture,textureManager,light,overlay,tint,morphWeights,null);
    }

    private void renderPacked(AssimpMesh mesh, Matrix4f[] boneMatrices,
                       Matrix4f modelViewProjection, Matrix4f modelView,
                       ResourceLocation texture, TextureManager textureManager, int light, int overlay, float[] tint,float[] morphWeights,ComputeSkinning computed) {

        if (!initialized) {
            throw new IllegalStateException("Renderer not initialized");
        }

        if (!mesh.hasBones()) {
            DebugManager.warn(DebugCategory.RENDER, "Mesh {} has no bones, cannot render as skinned mesh", mesh.getName());
            return;
        }
        if (boneMatrices == null || !supports(boneMatrices.length)) {
            throw new IllegalArgumentException("Bone palette exceeds the selected GPU rendering limits");
        }
        if(morphWeights!=null && !supportsMorph(mesh)) throw new IllegalArgumentException("Morph data exceeds GPU capacity");

        // Performance profiling
        if (DebugManager.isProfilingEnabled()) {
            cn.minerealms.assimploader.debug.PerformanceProfiler.start("skinned_mesh_render");
        }

        // Get or create VBO
        SkinnedMeshVBO vbo = computed==null?vboCache.computeIfAbsent(mesh, m -> {
            DebugManager.info(DebugCategory.RENDER, "Creating VBO for mesh: {}", m.getName());
            return createVBO(m);
        }):null;

        // Bind shader
        shader.bind();
        shader.setTint(tint);
        shader.setBoneCount(computed==null?boneMatrices.length:0);
        // Both sampler uniforms remain active even when the uniform-bone branch is selected.
        shader.setBoneMatricesTBO(3);
        shader.setMinecraftLighting(light, overlay);
        if(morphWeights!=null) {
            float[] effective=MorphDeformation.effectiveWeights(mesh,morphWeights);
            morphCache.computeIfAbsent(mesh,MorphTargetTBO::new).bind();
            shader.setMorphWeights(mesh.getVertexCount(),effective);
        } else shader.setMorphWeights(0,null);

        // Set matrices
        shader.setModelViewProjection(modelViewProjection);
        shader.setModelView(modelView);

        // Set bone matrices
        if (DebugManager.isProfilingEnabled()) {
            cn.minerealms.assimploader.debug.PerformanceProfiler.start("bone_matrix_upload");
        }

        if (computed!=null) {
            shader.setUseTBO(false);
        } else if (useTBO && tboRequested()) {
            // Use TBO for efficient bone matrix upload
            RenderSystem.activeTexture(GL13.GL_TEXTURE3);
            boneMatrixTBO.upload(boneMatrices);
            boneMatrixTBO.bind(3);
            shader.setUseTBO(true);
        } else {
            // Fallback to uniform arrays (max 64 bones)
            shader.setBoneMatrices(packPalette(boneMatrices));
            shader.setUseTBO(false);
        }

        if (DebugManager.isProfilingEnabled()) {
            cn.minerealms.assimploader.debug.PerformanceProfiler.end("bone_matrix_upload");
        }


        // Bind texture
        // setShaderTexture only updates Minecraft's deferred ShaderInstance sampler array.
        RenderSystem.activeTexture(GL13.GL_TEXTURE0);
        textureManager.bindForSetup(texture);
        shader.setTexture(0);

        // Set lighting
        shader.setLightDirection(0.2f, 1.0f, 0.3f); // Default light direction
        shader.setLightColor(1.0f, 1.0f, 1.0f);
        shader.setAmbientColor(0.3f, 0.3f, 0.3f);

        // Render
        if (DebugManager.isProfilingEnabled()) {
            cn.minerealms.assimploader.debug.PerformanceProfiler.start("vbo_draw");
        }

        if(computed!=null) computed.draw();
        else vbo.render(mesh);

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

        // Bone weights were extracted at load time, in the mesh's bone order.
        SkinnedMeshProcessor.SkinnedMeshData skinnedData = mesh.getSkinnedData();

        if (skinnedData == null) {
            throw new IllegalStateException("Failed to extract skinned data from mesh: " + mesh.getName());
        }

        // Create and upload VBO
        SkinnedMeshVBO vbo = new SkinnedMeshVBO();
        try {
            vbo.upload(positions, normals, texCoords, skinnedData, indices);
            return vbo;
        } catch (RuntimeException e) {
            vbo.delete();
            throw e;
        }
    }

    /**
     * Clear VBO cache
     */
    public void clearCache() {
        clearCompute();
        for (SkinnedMeshVBO vbo : vboCache.values()) {
            vbo.delete();
        }
        vboCache.clear();
        morphCache.values().forEach(MorphTargetTBO::delete);
        morphCache.clear();
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

    public boolean supports(BoneHierarchyBuilder hierarchy) {
        return hierarchy != null && supports(hierarchy.getBoneCount());
    }

    public boolean supports(int boneCount) {
        var level = AssimpLoaderConfig.getOrDefault(AssimpLoaderConfig.CLIENT.optimizationLevel::get,
                AssimpLoaderConfig.OptimizationLevel.AUTO);
        if (level == AssimpLoaderConfig.OptimizationLevel.FALLBACK) return false;
        int capacity = useTBO && tboRequested()
                ? Math.min(maxBones, AssimpLoaderConfig.getIntOrDefault(AssimpLoaderConfig.CLIENT.maxTBOBones, 1024)) : 64;
        return boneCount > 0 && boneCount <= capacity;
    }

    public boolean supportsMorph(AssimpMesh mesh) {
        return initialized && MorphTargetTBO.fits(mesh,maximumMorphTexels)
                && RuntimePerformancePolicy.current().permitsGpuMorph(mesh.getMorphTargets().size(), MorphTargetTBO.texels(mesh)*16);
    }

    static float[] packPalette(Matrix4f[] palette) {
        float[] result = new float[Math.multiplyExact(palette.length, 16)];
        for (int bone = 0; bone < palette.length; bone++) palette[bone].get(result, bone * 16);
        return result;
    }

    private static boolean tboRequested() {
        var level = AssimpLoaderConfig.getOrDefault(AssimpLoaderConfig.CLIENT.optimizationLevel::get,
                AssimpLoaderConfig.OptimizationLevel.AUTO);
        return level != AssimpLoaderConfig.OptimizationLevel.LOW && level != AssimpLoaderConfig.OptimizationLevel.FALLBACK
                && AssimpLoaderConfig.getOrDefault(AssimpLoaderConfig.CLIENT.tboEnabled::get, true);
    }
}
