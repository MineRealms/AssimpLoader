package cn.minerealms.assimploader.example;

import cn.minerealms.assimploader.animation.AnimationState;
import cn.minerealms.assimploader.animation.BoneHierarchyBuilder;
import cn.minerealms.assimploader.model.AssimpMesh;
import cn.minerealms.assimploader.model.AssimpScene;
import cn.minerealms.assimploader.rendering.SkinnedMeshRenderer;
import com.mojang.blaze3d.vertex.PoseStack;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.blockentity.BlockEntityRendererProvider;
import net.minecraft.resources.ResourceLocation;
import org.joml.Matrix4f;

import java.io.IOException;

/**
 * Custom renderer for TestModelBlock using BlockEntityRenderer.
 * Supports both static and skinned mesh rendering.
 */
public class TestModelBlockEntityRenderer extends AssimpBlockEntityRenderer<TestModelBlockEntity> {

    private SkinnedMeshRenderer skinnedRenderer;
    private boolean skinnedRendererInitialized = false;

    public TestModelBlockEntityRenderer(BlockEntityRendererProvider.Context context) {
    }

    @Override
    protected String getModelName() {
        return "cnc";
    }

    @Override
    public void render(TestModelBlockEntity blockEntity, float partialTick, PoseStack poseStack,
                      MultiBufferSource bufferSource, int packedLight, int packedOverlay) {

        // Get animation state
        AnimationState animationState = blockEntity.getAnimationState();

        // Check if we have skinned meshes
        if (animationState != null && animationState.isInitialized() && animationState.hasSkinnedMeshes()) {
            // Render with skinned mesh renderer
            renderSkinned(blockEntity, animationState, partialTick, poseStack, bufferSource, packedLight, packedOverlay);
        } else {
            // Fallback to static rendering
            super.render(blockEntity, partialTick, poseStack, bufferSource, packedLight, packedOverlay);
        }
    }

    /**
     * Render skinned meshes with bone animation
     */
    private void renderSkinned(TestModelBlockEntity blockEntity, AnimationState animationState,
                               float partialTick, PoseStack poseStack, MultiBufferSource bufferSource,
                               int packedLight, int packedOverlay) {

        // Initialize skinned renderer if needed
        if (!skinnedRendererInitialized) {
            skinnedRenderer = new SkinnedMeshRenderer();
            try {
                skinnedRenderer.initialize();
                skinnedRendererInitialized = true;
            } catch (IOException e) {
                throw new RuntimeException("Failed to initialize skinned mesh renderer", e);
            }
        }

        AssimpScene scene = animationState.getScene();
        if (scene == null) {
            return;
        }

        poseStack.pushPose();
        applyTransformations(poseStack, blockEntity, partialTick);

        // Calculate light level (0.0 - 1.0)
        float lightLevel = (packedLight & 0xFFFF) / 240.0f;

        // Render each skinned mesh
        for (AssimpMesh mesh : scene.getMeshes()) {
            if (mesh.hasBones()) {
                BoneHierarchyBuilder boneHierarchy = animationState.getBoneHierarchy(mesh);
                if (boneHierarchy != null) {
                    // Get matrices
                    Matrix4f mvp = new Matrix4f(poseStack.last().pose());
                    Matrix4f mv = new Matrix4f(poseStack.last().pose());

                    // Get texture (use embedded texture if available)
                    ResourceLocation texture = new ResourceLocation("minecraft", "textures/block/white_concrete.png");
                    if (scene.hasEmbeddedTextures()) {
                        texture = new ResourceLocation("assimploader", "embedded/" + getModelName() + "_0");
                    }

                    // Render skinned mesh
                    skinnedRenderer.render(mesh, boneHierarchy, mvp, mv, texture,
                            Minecraft.getInstance().getTextureManager(), lightLevel);
                }
            }
        }

        poseStack.popPose();
    }
}
