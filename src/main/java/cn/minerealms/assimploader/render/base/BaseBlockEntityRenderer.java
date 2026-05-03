package cn.minerealms.assimploader.render.base;

import cn.minerealms.assimploader.core.AssimpModelLoader;
import cn.minerealms.assimploader.debug.DebugManager;
import cn.minerealms.assimploader.debug.DebugManager.DebugCategory;
import cn.minerealms.assimploader.lod.LODManager;
import cn.minerealms.assimploader.model.AssimpMesh;
import cn.minerealms.assimploader.model.AssimpScene;
import cn.minerealms.assimploader.render.FrustumCuller;
import com.mojang.blaze3d.vertex.PoseStack;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.blockentity.BlockEntityRenderer;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

import java.util.HashMap;
import java.util.Map;

/**
 * Base class for BlockEntity renderers using Assimp models.
 *
 * Features:
 * - Dynamic AABB calculation with caching
 * - Frustum culling
 * - LOD support (optional)
 * - Performance profiling (optional)
 * - Hot-reload support
 *
 * Subclasses only need to:
 * - Provide model name via getModelName()
 * - Optionally override applyTransformations()
 * - Optionally enable LOD/profiling
 *
 * @param <T> BlockEntity type
 */
public abstract class BaseBlockEntityRenderer<T extends BlockEntity> extends BaseAssimpRenderer implements BlockEntityRenderer<T> {

    // AABB cache: stores relative bounding boxes (without world position)
    protected static final Map<String, AABB> boundingBoxCache = new HashMap<>();

    @Override
    public void render(T blockEntity, float partialTick, PoseStack poseStack,
                      MultiBufferSource bufferSource, int packedLight, int packedOverlay) {

        String modelName = getModelName();

        // Performance profiling (if enabled)
        if (enableProfiling()) {
            cn.minerealms.assimploader.debug.PerformanceProfiler.start(modelName + "_render_total");
        }

        // Track render count for debug logging
        int renderCount = incrementRenderCount(modelName);
        boolean isFirstRender = renderCount <= 3;

        if (isFirstRender) {
            DebugManager.info(DebugCategory.CORE, "========== {} Render #{} ==========", modelName, renderCount);
        }

        // Get the cached model
        AssimpScene scene = AssimpModelLoader.getCachedModel(modelName);

        if (scene == null) {
            if (isFirstRender) {
                DebugManager.error(DebugCategory.CORE, "Scene is NULL! Model not loaded for {}.", modelName);
            }
            if (enableProfiling()) {
                cn.minerealms.assimploader.debug.PerformanceProfiler.end(modelName + "_render_total");
            }
            return;
        }

        // Check hot-reload
        checkHotReload(scene, modelName);

        // Frustum culling: check if block entity is visible
        if (enableFrustumCulling()) {
            AABB boundingBox = getBoundingBox(blockEntity);
            if (boundingBox != null && !FrustumCuller.getInstance().isVisible(boundingBox)) {
                if (enableProfiling()) {
                    cn.minerealms.assimploader.debug.PerformanceProfiler.end(modelName + "_render_total");
                }
                return; // Skip rendering if outside view frustum
            }
        }

        // LOD: Calculate distance and determine LOD level
        if (enableLOD()) {
            Vec3 cameraPos = Minecraft.getInstance().gameRenderer.getMainCamera().getPosition();
            Vec3 entityPos = new Vec3(
                blockEntity.getBlockPos().getX() + 0.5,
                blockEntity.getBlockPos().getY() + 0.5,
                blockEntity.getBlockPos().getZ() + 0.5
            );
            LODManager.LODLevel lodLevel = LODManager.getLODLevel(cameraPos, entityPos);

            // Skip rendering if too far (CULLED level)
            if (lodLevel == LODManager.LODLevel.CULLED) {
                if (enableProfiling()) {
                    cn.minerealms.assimploader.debug.PerformanceProfiler.end(modelName + "_render_total");
                }
                return;
            }
        }

        // Load embedded textures if not already loaded
        loadEmbeddedTextures(scene, modelName);

        // Log import mode once per model
        if (!modeLoggedMap.containsKey(modelName)) {
            DebugManager.info(DebugCategory.CORE, "Model '{}' import mode: {} (isDynamic: {})",
                modelName, scene.getImportMode(), scene.isDynamic());
            modeLoggedMap.put(modelName, true);
        }

        poseStack.pushPose();

        // Allow subclasses to apply custom transformations
        applyTransformations(poseStack, blockEntity, partialTick);

        // Choose rendering path based on import mode
        if (scene.isDynamic()) {
            // DYNAMIC mode: Traverse scene graph to preserve node transformations
            renderSceneGraph(scene, poseStack, bufferSource, packedLight, packedOverlay, modelName, isFirstRender);
        } else {
            // STATIC mode: Direct mesh iteration (transformations already baked)
            renderMeshList(scene, poseStack, bufferSource, packedLight, packedOverlay, modelName, isFirstRender);
        }

        poseStack.popPose();

        if (enableProfiling()) {
            cn.minerealms.assimploader.debug.PerformanceProfiler.end(modelName + "_render_total");
        }
    }

    /**
     * Get the bounding box for frustum culling.
     * Override this to provide a custom bounding box.
     * Default: Dynamically computed from model vertices with caching
     */
    protected AABB getBoundingBox(T blockEntity) {
        String modelName = getModelName();

        // Check cache first
        if (boundingBoxCache.containsKey(modelName)) {
            AABB cachedBox = boundingBoxCache.get(modelName);
            // Transform cached relative box to world space
            return cachedBox.move(
                blockEntity.getBlockPos().getX(),
                blockEntity.getBlockPos().getY(),
                blockEntity.getBlockPos().getZ()
            );
        }

        // Compute bounding box from model vertices
        AssimpScene scene = AssimpModelLoader.getCachedModel(modelName);
        if (scene == null) {
            return null; // No culling if model not loaded
        }

        float minX = Float.POSITIVE_INFINITY;
        float minY = Float.POSITIVE_INFINITY;
        float minZ = Float.POSITIVE_INFINITY;
        float maxX = Float.NEGATIVE_INFINITY;
        float maxY = Float.NEGATIVE_INFINITY;
        float maxZ = Float.NEGATIVE_INFINITY;

        // Iterate through all meshes to find bounds
        for (AssimpMesh mesh : scene.getMeshes()) {
            float[] vertices = mesh.getVertices();
            for (int i = 0; i < vertices.length; i += 3) {
                minX = Math.min(minX, vertices[i]);
                minY = Math.min(minY, vertices[i + 1]);
                minZ = Math.min(minZ, vertices[i + 2]);
                maxX = Math.max(maxX, vertices[i]);
                maxY = Math.max(maxY, vertices[i + 1]);
                maxZ = Math.max(maxZ, vertices[i + 2]);
            }
        }

        // Handle empty models
        if (Float.isInfinite(minX) || Float.isInfinite(maxX)) {
            DebugManager.warn(DebugCategory.CORE, "Model '{}' has no valid vertices, using default 1x1x1 bounding box", modelName);
            AABB defaultBox = new AABB(0, 0, 0, 1, 1, 1);
            boundingBoxCache.put(modelName, defaultBox);
            return defaultBox.move(
                blockEntity.getBlockPos().getX(),
                blockEntity.getBlockPos().getY(),
                blockEntity.getBlockPos().getZ()
            );
        }

        // Cache the relative bounding box (without world position)
        AABB relativeBox = new AABB(minX, minY, minZ, maxX, maxY, maxZ);
        boundingBoxCache.put(modelName, relativeBox);

        DebugManager.info(DebugCategory.CORE, "Computed bounding box for '{}': min({}, {}, {}), max({}, {}, {})",
            modelName, minX, minY, minZ, maxX, maxY, maxZ);

        // Return world-space bounding box
        return relativeBox.move(
            blockEntity.getBlockPos().getX(),
            blockEntity.getBlockPos().getY(),
            blockEntity.getBlockPos().getZ()
        );
    }

    /**
     * Apply custom transformations to the pose stack.
     * Override this method to add rotations, translations, or scaling.
     * Default: no transformations
     */
    protected void applyTransformations(PoseStack poseStack, T blockEntity, float partialTick) {
        // Default: no transformations
    }

    /**
     * Enable frustum culling.
     * Default: true
     */
    protected boolean enableFrustumCulling() {
        return true;
    }

    /**
     * Enable LOD (Level of Detail) system.
     * Default: false
     */
    protected boolean enableLOD() {
        return false;
    }

    /**
     * Enable performance profiling.
     * Default: false
     */
    protected boolean enableProfiling() {
        return false;
    }

    @Override
    public int getViewDistance() {
        return 256; // Default render distance
    }
}
