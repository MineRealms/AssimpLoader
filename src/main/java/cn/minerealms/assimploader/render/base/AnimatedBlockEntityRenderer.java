package cn.minerealms.assimploader.render.base;

import cn.minerealms.assimploader.animation.AnimationState;
import cn.minerealms.assimploader.animation.BoneHierarchyBuilder;
import cn.minerealms.assimploader.debug.DebugManager;
import cn.minerealms.assimploader.debug.DebugManager.DebugCategory;
import cn.minerealms.assimploader.model.AssimpMesh;
import cn.minerealms.assimploader.model.AssimpScene;
import cn.minerealms.assimploader.rendering.SimpleMeshRenderer;
import cn.minerealms.assimploader.rendering.SkinnedMeshRenderer;
import com.mojang.blaze3d.vertex.PoseStack;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.level.block.entity.BlockEntity;
import org.joml.Matrix4f;

import java.io.IOException;

/**
 * Renderer for animated Assimp models with skeletal animation support.
 *
 * Features:
 * - Skeletal animation (bone-based)
 * - Mixed rendering (skinned + static meshes)
 * - AnimationState management
 * - Automatic renderer initialization
 *
 * Subclasses need to:
 * - Provide model name via getModelName()
 * - Provide AnimationState via getAnimationState()
 * - Optionally override applyTransformations()
 *
 * Example usage:
 * <pre>
 * public class CircleBlockEntityRenderer extends AnimatedBlockEntityRenderer<CircleBlockEntity> {
 *     {@literal @}Override
 *     protected String getModelName() {
 *         return "circle";
 *     }
 *
 *     {@literal @}Override
 *     protected AnimationState getAnimationState(CircleBlockEntity entity) {
 *         return entity.getAnimationState();
 *     }
 *
 *     {@literal @}Override
 *     protected boolean enableLOD() {
 *         return true;
 *     }
 * }
 * </pre>
 *
 * @param <T> BlockEntity type
 */
public abstract class AnimatedBlockEntityRenderer<T extends BlockEntity> extends BaseBlockEntityRenderer<T> {

    private SkinnedMeshRenderer skinnedRenderer;
    private boolean skinnedRendererInitialized = false;

    /**
     * Get the AnimationState for this block entity.
     * This is where the animation data and bone transformations are stored.
     */
    protected abstract AnimationState getAnimationState(T blockEntity);

    @Override
    public void render(T blockEntity, float partialTick, PoseStack poseStack,
                      MultiBufferSource bufferSource, int packedLight, int packedOverlay) {

        String modelName = getModelName();

        // Performance profiling (if enabled)
        if (enableProfiling()) {
            cn.minerealms.assimploader.debug.PerformanceProfiler.start(modelName + "_render_total");
        }

        // Get animation state
        AnimationState animationState = getAnimationState(blockEntity);
        if (animationState == null || !animationState.isInitialized()) {
            DebugManager.warn(DebugCategory.RENDER,
                "{}: AnimationState is null or not initialized", modelName);
            if (enableProfiling()) {
                cn.minerealms.assimploader.debug.PerformanceProfiler.end(modelName + "_render_total");
            }
            return;
        }

        AssimpScene scene = animationState.getScene();
        if (scene == null) {
            DebugManager.warn(DebugCategory.RENDER, "{}: Scene is null", modelName);
            if (enableProfiling()) {
                cn.minerealms.assimploader.debug.PerformanceProfiler.end(modelName + "_render_total");
            }
            return;
        }

        // Check hot-reload
        checkHotReload(scene, modelName);

        // Frustum culling
        if (enableFrustumCulling()) {
            net.minecraft.world.phys.AABB boundingBox = getBoundingBox(blockEntity);
            if (boundingBox != null && !cn.minerealms.assimploader.render.FrustumCuller.getInstance().isVisible(boundingBox)) {
                if (enableProfiling()) {
                    cn.minerealms.assimploader.debug.PerformanceProfiler.end(modelName + "_render_total");
                }
                return;
            }
        }

        // LOD check
        if (enableLOD()) {
            net.minecraft.world.phys.Vec3 cameraPos = net.minecraft.client.Minecraft.getInstance().gameRenderer.getMainCamera().getPosition();
            net.minecraft.world.phys.Vec3 entityPos = new net.minecraft.world.phys.Vec3(
                blockEntity.getBlockPos().getX() + 0.5,
                blockEntity.getBlockPos().getY() + 0.5,
                blockEntity.getBlockPos().getZ() + 0.5
            );
            cn.minerealms.assimploader.lod.LODManager.LODLevel lodLevel = cn.minerealms.assimploader.lod.LODManager.getLODLevel(cameraPos, entityPos);

            if (lodLevel == cn.minerealms.assimploader.lod.LODManager.LODLevel.CULLED) {
                if (enableProfiling()) {
                    cn.minerealms.assimploader.debug.PerformanceProfiler.end(modelName + "_render_total");
                }
                return;
            }
        }

        // Load embedded textures
        loadEmbeddedTextures(scene, modelName);

        poseStack.pushPose();

        // Apply custom transformations
        applyTransformations(poseStack, blockEntity, partialTick);

        // Calculate light level
        float lightLevel = (packedLight & 0xFFFF) / 240.0f;

        int renderedMeshes = 0;
        int skinnedMeshes = 0;
        int staticMeshes = 0;

        // Render each mesh
        for (AssimpMesh mesh : scene.getMeshes()) {
            if (mesh.hasBones()) {
                // Skeletal animation path
                skinnedMeshes++;

                if (enableProfiling()) {
                    cn.minerealms.assimploader.debug.PerformanceProfiler.start(modelName + "_skinned_mesh");
                }

                // Initialize skinned renderer if needed
                if (!skinnedRendererInitialized) {
                    skinnedRenderer = new SkinnedMeshRenderer();
                    try {
                        skinnedRenderer.initialize();
                        skinnedRendererInitialized = true;
                        DebugManager.info(DebugCategory.RENDER,
                            "{}: SkinnedMeshRenderer initialized", modelName);
                    } catch (IOException e) {
                        throw new RuntimeException("Failed to initialize skinned mesh renderer", e);
                    }
                }

                BoneHierarchyBuilder boneHierarchy = animationState.getBoneHierarchy(mesh);
                if (boneHierarchy != null) {
                    // Get matrices
                    Matrix4f mvp = new Matrix4f(poseStack.last().pose());
                    Matrix4f mv = new Matrix4f(poseStack.last().pose());

                    // Get texture
                    ResourceLocation texture = getTextureLocation(scene, mesh, modelName);

                    // Render skinned mesh
                    skinnedRenderer.render(mesh, boneHierarchy, mvp, mv, texture,
                        net.minecraft.client.Minecraft.getInstance().getTextureManager(), lightLevel);

                    renderedMeshes++;
                }

                if (enableProfiling()) {
                    cn.minerealms.assimploader.debug.PerformanceProfiler.end(modelName + "_skinned_mesh");
                }
            } else {
                // Static mesh path (no bones)
                staticMeshes++;

                if (enableProfiling()) {
                    cn.minerealms.assimploader.debug.PerformanceProfiler.start(modelName + "_static_mesh");
                }

                // Get texture
                ResourceLocation texture = getTextureLocation(scene, mesh, modelName);

                // Render static mesh using static method (no initialization needed)
                SimpleMeshRenderer.render(mesh, poseStack, bufferSource, texture, packedLight, packedOverlay);

                renderedMeshes++;

                if (enableProfiling()) {
                    cn.minerealms.assimploader.debug.PerformanceProfiler.end(modelName + "_static_mesh");
                }
            }
        }

        poseStack.popPose();

        if (enableProfiling()) {
            cn.minerealms.assimploader.debug.PerformanceProfiler.end(modelName + "_render_total");
        }

        // Debug logging (first few renders)
        if (isFirstRender(modelName, 3)) {
            DebugManager.info(DebugCategory.RENDER,
                "{}: Rendered {} meshes ({} skinned, {} static)",
                modelName, renderedMeshes, skinnedMeshes, staticMeshes);
        }
    }
}
