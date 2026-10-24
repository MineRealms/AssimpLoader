package cn.minerealms.assimploader.render.base;

import cn.minerealms.assimploader.animation.AnimationState;
import cn.minerealms.assimploader.animation.BoneHierarchyBuilder;
import cn.minerealms.assimploader.config.AssimpLoaderConfig;
import cn.minerealms.assimploader.debug.DebugManager;
import cn.minerealms.assimploader.debug.DebugManager.DebugCategory;
import cn.minerealms.assimploader.model.AssimpMesh;
import cn.minerealms.assimploader.model.AssimpScene;
import cn.minerealms.assimploader.rendering.SimpleMeshRenderer;
import cn.minerealms.assimploader.rendering.SkinnedMeshRenderer;
import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.vertex.PoseStack;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.level.block.entity.BlockEntity;
import org.joml.Matrix4f;
import org.lwjgl.opengl.GL11;
import org.lwjgl.opengl.GL20;
import org.lwjgl.opengl.GL30;

import javax.annotation.Nullable;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

/**
 * Renderer for Assimp models that may contain skinned meshes.
 *
 * <p>Meshes without bones are drawn through Minecraft's batching pipeline
 * ({@link SimpleMeshRenderer}); meshes with bones are drawn immediately with the GPU
 * skinning shader, driven by the {@link AnimationState} supplied by the block entity.
 *
 * <p>When the block entity has no animation state (model loaded in STATIC mode, or no
 * skeleton at all) this falls back to {@link BaseBlockEntityRenderer} so a single renderer
 * class works for both static and animated models.
 */
public abstract class AnimatedBlockEntityRenderer<T extends BlockEntity> extends BaseBlockEntityRenderer<T> {

    @Nullable
    private SkinnedMeshRenderer skinnedRenderer;

    private boolean skinnedRendererFailed = false;

    /**
     * Animation state for this block entity, or {@code null} when the model is not animated.
     */
    @Nullable
    protected abstract AnimationState getAnimationState(T blockEntity);

    @Override
    public void render(T blockEntity, float partialTick, PoseStack poseStack,
                       MultiBufferSource bufferSource, int packedLight, int packedOverlay) {

        AnimationState animationState = getAnimationState(blockEntity);
        if (animationState == null || !animationState.isInitialized() || !animationState.hasSkinnedMeshes()
                || !AssimpLoaderConfig.CLIENT.gpuSkinningEnabled.get()) {
            super.render(blockEntity, partialTick, poseStack, bufferSource, packedLight, packedOverlay);
            return;
        }

        String modelName = getModelName();
        AssimpScene scene = animationState.getScene();
        if (scene == null) {
            super.render(blockEntity, partialTick, poseStack, bufferSource, packedLight, packedOverlay);
            return;
        }

        checkHotReload(scene, modelName);

        if (enableFrustumCulling()) {
            net.minecraft.world.phys.AABB boundingBox = getBoundingBox(blockEntity);
            if (boundingBox != null
                    && !cn.minerealms.assimploader.render.FrustumCuller.getInstance().isVisible(boundingBox)) {
                return;
            }
        }

        loadEmbeddedTextures(scene, modelName);

        List<AssimpMesh> skinned = new ArrayList<>(scene.getMeshes().size());
        List<AssimpMesh> staticMeshes = new ArrayList<>(scene.getMeshes().size());
        for (AssimpMesh mesh : scene.getMeshes()) {
            if (mesh.hasBones()) {
                skinned.add(mesh);
            } else {
                staticMeshes.add(mesh);
            }
        }

        poseStack.pushPose();
        applyTransformations(poseStack, blockEntity, partialTick);

        // Pass 1 - static geometry goes through Minecraft's batching pipeline.
        for (AssimpMesh mesh : staticMeshes) {
            SimpleMeshRenderer.render(mesh, poseStack, bufferSource,
                    getTextureLocation(scene, mesh, modelName), packedLight, packedOverlay);
        }

        // Pass 2 - flush the batch, then draw the skinned meshes with our own program.
        if (!skinned.isEmpty()) {
            SkinnedMeshRenderer renderer = getSkinnedRenderer();
            if (renderer != null) {
                if (bufferSource instanceof MultiBufferSource.BufferSource bufferSourceImpl) {
                    bufferSourceImpl.endBatch();
                }
                drawSkinnedMeshes(renderer, scene, skinned, animationState, poseStack, modelName);
            }
        }

        poseStack.popPose();
    }

    private void drawSkinnedMeshes(SkinnedMeshRenderer renderer, AssimpScene scene, List<AssimpMesh> meshes,
                                   AnimationState animationState, PoseStack poseStack, String modelName) {

        Matrix4f modelView = new Matrix4f(RenderSystem.getModelViewMatrix()).mul(poseStack.last().pose());
        Matrix4f modelViewProjection = new Matrix4f(RenderSystem.getProjectionMatrix()).mul(modelView);
        float lightLevel = 1.0f;

        // Save the GL state we are about to disturb.
        int previousProgram = GL11.glGetInteger(GL20.GL_CURRENT_PROGRAM);
        int previousVao = GL11.glGetInteger(GL30.GL_VERTEX_ARRAY_BINDING);

        try {
            for (AssimpMesh mesh : meshes) {
                BoneHierarchyBuilder boneHierarchy = animationState.getBoneHierarchy(mesh);
                if (boneHierarchy == null) {
                    DebugManager.warn(DebugCategory.RENDER,
                            "{}: no bone hierarchy for skinned mesh '{}'", modelName, mesh.getName());
                    continue;
                }

                ResourceLocation texture = getTextureLocation(scene, mesh, modelName);
                renderer.render(mesh, boneHierarchy, modelViewProjection, modelView, texture,
                        net.minecraft.client.Minecraft.getInstance().getTextureManager(), lightLevel);
            }
        } finally {
            GL30.glBindVertexArray(previousVao);
            GL20.glUseProgram(previousProgram);
            RenderSystem.setShaderColor(1f, 1f, 1f, 1f);
        }
    }

    @Nullable
    private SkinnedMeshRenderer getSkinnedRenderer() {
        if (skinnedRenderer != null) {
            return skinnedRenderer;
        }
        if (skinnedRendererFailed) {
            return null;
        }

        SkinnedMeshRenderer renderer = new SkinnedMeshRenderer();
        try {
            renderer.initialize();
            skinnedRenderer = renderer;
            return renderer;
        } catch (IOException | RuntimeException e) {
            skinnedRendererFailed = true;
            DebugManager.error(DebugCategory.RENDER,
                    "Failed to initialise the GPU skinning renderer, falling back to static rendering", e);
            return null;
        }
    }
}
