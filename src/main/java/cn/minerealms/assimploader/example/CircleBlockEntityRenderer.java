package cn.minerealms.assimploader.example;

import cn.minerealms.assimploader.animation.AnimationState;
import cn.minerealms.assimploader.animation.BoneHierarchyBuilder;
import cn.minerealms.assimploader.model.AssimpScene;
import cn.minerealms.assimploader.model.AssimpMesh;
import cn.minerealms.assimploader.rendering.SkinnedMeshRenderer;
import com.mojang.blaze3d.vertex.PoseStack;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.blockentity.BlockEntityRenderer;
import net.minecraft.client.renderer.blockentity.BlockEntityRendererProvider;
import net.minecraft.resources.ResourceLocation;
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

        AnimationState animationState = blockEntity.getAnimationState();
        if (animationState == null || !animationState.isInitialized()) {
            return;
        }

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

        // Center the model in the block
        poseStack.translate(0.5, 0.0, 0.5);

        // Scale if needed (adjust based on your model size)
        // poseStack.scale(0.5f, 0.5f, 0.5f);

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

                    // Get texture (use embedded texture if available, or default white)
                    ResourceLocation texture = new ResourceLocation("minecraft", "textures/block/white_concrete.png");
                    if (scene.hasEmbeddedTextures()) {
                        texture = new ResourceLocation("assimploader", "embedded/circle_0");
                    }

                    // Render skinned mesh
                    skinnedRenderer.render(mesh, boneHierarchy, mvp, mv, texture,
                            Minecraft.getInstance().getTextureManager(), lightLevel);
                }
            }
        }

        poseStack.popPose();
    }

    @Override
    public int getViewDistance() {
        return 64; // Render distance in blocks
    }
}
