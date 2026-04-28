package cn.minerealms.assimploader.example;

import cn.minerealms.assimploader.animation.AnimationState;
import cn.minerealms.assimploader.animation.BoneHierarchyBuilder;
import cn.minerealms.assimploader.lod.LODManager;
import cn.minerealms.assimploader.model.AssimpScene;
import cn.minerealms.assimploader.model.AssimpMesh;
import cn.minerealms.assimploader.render.FrustumCuller;
import cn.minerealms.assimploader.rendering.SkinnedMeshRenderer;
import cn.minerealms.assimploader.rendering.SimpleMeshRenderer;
import com.mojang.blaze3d.vertex.PoseStack;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.blockentity.BlockEntityRenderer;
import net.minecraft.client.renderer.blockentity.BlockEntityRendererProvider;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import org.joml.Matrix4f;

import java.io.IOException;

/**
 * Renderer for CircleBlockEntity with skeletal animation support.
 */
public class CircleBlockEntityRenderer implements BlockEntityRenderer<CircleBlockEntity> {

    private SkinnedMeshRenderer skinnedRenderer;
    private boolean skinnedRendererInitialized = false;

    public CircleBlockEntityRenderer(BlockEntityRendererProvider.Context context) {
    }

    @Override
    public void render(CircleBlockEntity blockEntity, float partialTick, PoseStack poseStack,
                      MultiBufferSource bufferSource, int packedLight, int packedOverlay) {

        // Performance profiling
        if (cn.minerealms.assimploader.debug.DebugManager.isProfilingEnabled()) {
            cn.minerealms.assimploader.debug.PerformanceProfiler.start("circle_render_total");
        }

        // Frustum culling: check if block entity is visible
        AABB boundingBox = new AABB(
            blockEntity.getBlockPos().getX(),
            blockEntity.getBlockPos().getY(),
            blockEntity.getBlockPos().getZ(),
            blockEntity.getBlockPos().getX() + 1,
            blockEntity.getBlockPos().getY() + 1,
            blockEntity.getBlockPos().getZ() + 1
        );
        if (!FrustumCuller.getInstance().isVisible(boundingBox)) {
            if (cn.minerealms.assimploader.debug.DebugManager.isProfilingEnabled()) {
                cn.minerealms.assimploader.debug.PerformanceProfiler.end("circle_render_total");
            }
            return; // Skip rendering if outside view frustum
        }

        // LOD: Calculate distance and determine LOD level
        Vec3 cameraPos = Minecraft.getInstance().gameRenderer.getMainCamera().getPosition();
        Vec3 entityPos = new Vec3(
            blockEntity.getBlockPos().getX() + 0.5,
            blockEntity.getBlockPos().getY() + 0.5,
            blockEntity.getBlockPos().getZ() + 0.5
        );
        LODManager.LODLevel lodLevel = LODManager.getLODLevel(cameraPos, entityPos);

        // Skip rendering if too far (CULLED level)
        if (lodLevel == LODManager.LODLevel.CULLED) {
            if (cn.minerealms.assimploader.debug.DebugManager.isProfilingEnabled()) {
                cn.minerealms.assimploader.debug.PerformanceProfiler.end("circle_render_total");
            }
            return;
        }

        AnimationState animationState = blockEntity.getAnimationState();
        if (animationState == null || !animationState.isInitialized()) {
            cn.minerealms.assimploader.debug.DebugManager.warn(
                cn.minerealms.assimploader.debug.DebugManager.DebugCategory.RENDER,
                "CircleBlockEntityRenderer: AnimationState is null or not initialized"
            );
            if (cn.minerealms.assimploader.debug.DebugManager.isProfilingEnabled()) {
                cn.minerealms.assimploader.debug.PerformanceProfiler.end("circle_render_total");
            }
            return;
        }

        AssimpScene scene = animationState.getScene();
        if (scene == null) {
            cn.minerealms.assimploader.debug.DebugManager.warn(
                cn.minerealms.assimploader.debug.DebugManager.DebugCategory.RENDER,
                "CircleBlockEntityRenderer: Scene is null"
            );
            if (cn.minerealms.assimploader.debug.DebugManager.isProfilingEnabled()) {
                cn.minerealms.assimploader.debug.PerformanceProfiler.end("circle_render_total");
            }
            return;
        }

        poseStack.pushPose();

        // Center the model in the block
        poseStack.translate(0.5, 0.0, 0.5);

        // Calculate light level (0.0 - 1.0)
        float lightLevel = (packedLight & 0xFFFF) / 240.0f;

        int renderedMeshes = 0;
        int skinnedMeshes = 0;
        int bakedMeshes = 0;

        // Render each mesh
        for (AssimpMesh mesh : scene.getMeshes()) {
            if (mesh.hasBones()) {
                // Skeletal animation path
                skinnedMeshes++;

                if (cn.minerealms.assimploader.debug.DebugManager.isProfilingEnabled()) {
                    cn.minerealms.assimploader.debug.PerformanceProfiler.start("circle_skinned_mesh");
                }

                // Initialize skinned renderer if needed
                if (!skinnedRendererInitialized) {
                    skinnedRenderer = new SkinnedMeshRenderer();
                    try {
                        skinnedRenderer.initialize();
                        skinnedRendererInitialized = true;
                        cn.minerealms.assimploader.debug.DebugManager.info(
                            cn.minerealms.assimploader.debug.DebugManager.DebugCategory.RENDER,
                            "CircleBlockEntityRenderer: SkinnedMeshRenderer initialized"
                        );
                    } catch (IOException e) {
                        throw new RuntimeException("Failed to initialize skinned mesh renderer", e);
                    }
                }

                BoneHierarchyBuilder boneHierarchy = animationState.getBoneHierarchy(mesh);
                if (boneHierarchy != null) {
                    // Get matrices
                    Matrix4f mvp = new Matrix4f(poseStack.last().pose());
                    Matrix4f mv = new Matrix4f(poseStack.last().pose());

                    // Get texture (use embedded texture if available, or default white)
                    ResourceLocation texture = new ResourceLocation("minecraft", "textures/block/white_concrete.png");
                    if (scene.hasEmbeddedTextures()) {
                        texture = new ResourceLocation("assimploader", "embedded/circle_0");
                    }

                    // Render skinned mesh
                    skinnedRenderer.render(mesh, boneHierarchy, mvp, mv, texture,
                            Minecraft.getInstance().getTextureManager(), lightLevel);
                    renderedMeshes++;
                } else {
                    cn.minerealms.assimploader.debug.DebugManager.warn(
                        cn.minerealms.assimploader.debug.DebugManager.DebugCategory.RENDER,
                        "CircleBlockEntityRenderer: BoneHierarchy is null for mesh"
                    );
                }

                if (cn.minerealms.assimploader.debug.DebugManager.isProfilingEnabled()) {
                    cn.minerealms.assimploader.debug.PerformanceProfiler.end("circle_skinned_mesh");
                }
            } else {
                // Baked/vertex animation path - render using Minecraft's buffer system
                bakedMeshes++;

                if (cn.minerealms.assimploader.debug.DebugManager.isProfilingEnabled()) {
                    cn.minerealms.assimploader.debug.PerformanceProfiler.start("circle_baked_mesh");
                }

                // Get texture (use embedded texture if available, or default white)
                ResourceLocation texture = new ResourceLocation("minecraft", "textures/block/white_concrete.png");
                if (scene.hasEmbeddedTextures()) {
                    texture = new ResourceLocation("assimploader", "embedded/circle_0");
                }

                // Get node transforms from animation player (for node animations)
                java.util.Map<String, org.joml.Matrix4f> nodeTransforms = null;
                if (animationState.getCurrentAnimation() != null) {
                    nodeTransforms = animationState.getCurrentAnimation().getBoneTransforms();
                }

                // Render using SimpleMeshRenderer with scene and node transforms
                SimpleMeshRenderer.render(mesh, poseStack, bufferSource, texture, packedLight, packedOverlay, scene, nodeTransforms);
                renderedMeshes++;

                cn.minerealms.assimploader.debug.DebugManager.debug(
                    cn.minerealms.assimploader.debug.DebugManager.DebugCategory.RENDER,
                    "CircleBlockEntityRenderer: Rendered baked animation mesh '{}'",
                    mesh.getName()
                );

                if (cn.minerealms.assimploader.debug.DebugManager.isProfilingEnabled()) {
                    cn.minerealms.assimploader.debug.PerformanceProfiler.end("circle_baked_mesh");
                }
            }
        }

        if (renderedMeshes == 0) {
            cn.minerealms.assimploader.debug.DebugManager.warn(
                cn.minerealms.assimploader.debug.DebugManager.DebugCategory.RENDER,
                "CircleBlockEntityRenderer: No meshes rendered (total: {}, skinned: {}, baked: {})",
                scene.getMeshes().size(), skinnedMeshes, bakedMeshes
            );
        }

        poseStack.popPose();

        if (cn.minerealms.assimploader.debug.DebugManager.isProfilingEnabled()) {
            cn.minerealms.assimploader.debug.PerformanceProfiler.end("circle_render_total");
        }
    }

    @Override
    public int getViewDistance() {
        // Match LOD culling distance (96 blocks)
        return 96;
    }
}
