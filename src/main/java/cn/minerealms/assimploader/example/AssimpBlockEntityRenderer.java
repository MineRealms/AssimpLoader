package cn.minerealms.assimploader.example;

import cn.minerealms.assimploader.AssimpLoader;
import cn.minerealms.assimploader.compat.OculusCompat;
import cn.minerealms.assimploader.core.AssimpModelLoader;
import cn.minerealms.assimploader.model.AssimpMaterial;
import cn.minerealms.assimploader.model.AssimpMesh;
import cn.minerealms.assimploader.model.AssimpScene;
import cn.minerealms.assimploader.model.AssimpTexture;
import com.mojang.blaze3d.platform.NativeImage;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.blockentity.BlockEntityRenderer;
import net.minecraft.client.renderer.texture.DynamicTexture;
import net.minecraft.client.renderer.texture.TextureManager;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.level.block.entity.BlockEntity;
import org.joml.Matrix3f;
import org.joml.Matrix4f;
import org.joml.Vector3f;
import org.joml.Vector4f;

import java.util.HashMap;
import java.util.Map;

/**
 * Generic BlockEntityRenderer for Assimp models.
 * Subclasses only need to provide the model name.
 */
public abstract class AssimpBlockEntityRenderer<T extends BlockEntity> implements BlockEntityRenderer<T> {

    private static final Map<String, ResourceLocation> textureCache = new HashMap<>();
    private static final Map<String, Integer> sceneHashCache = new HashMap<>();
    private static final Map<String, Boolean> textureDebugLogged = new HashMap<>();
    private static final Map<String, Integer> renderCountMap = new HashMap<>();

    /**
     * Get the model name for this block entity.
     * This should match the cache key used in AssimpModelLoader.
     */
    protected abstract String getModelName();

    @Override
    public void render(T blockEntity, float partialTick, PoseStack poseStack,
                      MultiBufferSource bufferSource, int packedLight, int packedOverlay) {

        String modelName = getModelName();

        // Track render count for debug logging
        int renderCount = renderCountMap.getOrDefault(modelName, 0) + 1;
        renderCountMap.put(modelName, renderCount);
        boolean isFirstRender = renderCount <= 3;

        if (isFirstRender) {
            AssimpLoader.LOGGER.info("DEBUG: ========== AssimpBlockEntityRenderer.render() for {} #{} ==========", modelName, renderCount);
        }

        // Get the cached model
        AssimpScene scene = AssimpModelLoader.getCachedModel(modelName);

        if (scene == null) {
            if (isFirstRender) {
                AssimpLoader.LOGGER.error("DEBUG: Scene is NULL! Model not loaded for {}.", modelName);
            }
            return;
        }

        if (isFirstRender) {
            AssimpLoader.LOGGER.info("DEBUG: {} Scene loaded, has embedded textures: {}", modelName, scene.hasEmbeddedTextures());
            AssimpLoader.LOGGER.info("DEBUG: {} Scene texture count: {}", modelName, scene.getTextureCount());
        }

        // Detect if scene has changed (hot-reload) and clear texture cache
        int currentSceneHash = System.identityHashCode(scene);
        Integer cachedSceneHash = sceneHashCache.get(modelName);

        if (cachedSceneHash == null || cachedSceneHash != currentSceneHash) {
            textureCache.remove(modelName + "_0");
            sceneHashCache.put(modelName, currentSceneHash);
            textureDebugLogged.put(modelName, false);
        }

        // Load embedded textures if not already loaded
        if (scene.hasEmbeddedTextures() && !textureCache.containsKey(modelName + "_0")) {
            loadEmbeddedTextures(scene, modelName);
        }

        poseStack.pushPose();

        // Allow subclasses to apply custom transformations
        applyTransformations(poseStack, blockEntity, partialTick);

        // Render each mesh
        for (int meshIndex = 0; meshIndex < scene.getMeshes().size(); meshIndex++) {
            AssimpMesh mesh = scene.getMeshes().get(meshIndex);

            // Get material
            AssimpMaterial material = null;
            ResourceLocation textureLocation = null;

            if (mesh.getMaterialIndex() >= 0 && mesh.getMaterialIndex() < scene.getMaterials().size()) {
                material = scene.getMaterials().get(mesh.getMaterialIndex());

                // If scene has embedded textures, use them
                if (scene.hasEmbeddedTextures() && textureCache.containsKey(modelName + "_0")) {
                    textureLocation = textureCache.get(modelName + "_0");
                }
            }

            // Choose render type based on material and shader status
            RenderType renderType = getRenderType(material, textureLocation);

            VertexConsumer consumer = bufferSource.getBuffer(renderType);

            // Render the mesh
            renderMesh(mesh, material, poseStack, consumer, packedLight, packedOverlay);
        }

        poseStack.popPose();
    }

    /**
     * Apply custom transformations to the pose stack.
     * Override this method to add rotations, translations, or scaling.
     */
    protected void applyTransformations(PoseStack poseStack, T blockEntity, float partialTick) {
        // Default: no transformations
    }

    /**
     * Get the appropriate render type based on material properties and shader status.
     * When Oculus shaders are active, uses shader-compatible render types.
     */
    protected RenderType getRenderType(AssimpMaterial material, ResourceLocation texture) {
        ResourceLocation textureLocation = texture != null ? texture :
            new ResourceLocation("minecraft", "textures/block/white_concrete.png");

        boolean shadersActive = OculusCompat.isShadersActive();
        boolean isTransparent = material != null && material.isTransparent();

        if (shadersActive) {
            // Shader-enhanced rendering
            if (isTransparent) {
                return RenderType.entityTranslucent(textureLocation);
            } else {
                // Use cutout for better shader compatibility (supports alpha testing)
                return RenderType.entityCutout(textureLocation);
            }
        } else {
            // Vanilla rendering
            if (isTransparent) {
                return RenderType.entityTranslucent(textureLocation);
            } else {
                return RenderType.entityCutout(textureLocation);
            }
        }
    }

    private void loadEmbeddedTextures(AssimpScene scene, String modelName) {
        TextureManager textureManager = Minecraft.getInstance().getTextureManager();

        for (int i = 0; i < scene.getTextures().size(); i++) {
            AssimpTexture texture = scene.getTextures().get(i);

            try {
                NativeImage image = texture.loadAsNativeImage();
                DynamicTexture dynamicTexture = new DynamicTexture(image);

                ResourceLocation textureLoc = new ResourceLocation("assimploader",
                    "embedded/" + modelName + "_" + i);

                textureManager.register(textureLoc, dynamicTexture);
                textureCache.put(modelName + "_" + i, textureLoc);

                if (!textureDebugLogged.getOrDefault(modelName, false)) {
                    AssimpLoader.LOGGER.info("Loaded embedded texture {} for {}: {}", i, modelName, textureLoc);
                }
            } catch (Exception e) {
                AssimpLoader.LOGGER.error("Failed to load embedded texture {} for {}", i, modelName, e);
            }
        }

        textureDebugLogged.put(modelName, true);
    }

    private void renderMesh(AssimpMesh mesh, AssimpMaterial material, PoseStack poseStack,
                           VertexConsumer consumer, int packedLight, int packedOverlay) {

        float[] vertices = mesh.getVertices();
        float[] normals = mesh.getNormals();
        float[] texCoords = mesh.getTexCoords();
        int[] indices = mesh.getIndices();

        float[] diffuseColor = material != null ? material.getDiffuseColor() : new float[]{1.0f, 1.0f, 1.0f, 1.0f};

        Matrix4f pose = poseStack.last().pose();
        Matrix3f normal = poseStack.last().normal();

        Vector3f pos = new Vector3f();
        Vector3f norm = new Vector3f();
        Vector4f color = new Vector4f(diffuseColor[0], diffuseColor[1], diffuseColor[2], diffuseColor[3]);

        // Render triangles with reversed winding order for Minecraft
        for (int i = 0; i < indices.length; i += 3) {
            int idx0 = indices[i];
            int idx1 = indices[i + 1];
            int idx2 = indices[i + 2];

            int vIdx0 = idx0 * 3;
            int vIdx1 = idx1 * 3;
            int vIdx2 = idx2 * 3;

            int tIdx0 = idx0 * 2;
            int tIdx1 = idx1 * 2;
            int tIdx2 = idx2 * 2;

            // Vertices
            Vector3f v0 = new Vector3f(vertices[vIdx0], vertices[vIdx0 + 1], vertices[vIdx0 + 2]);
            Vector3f v1 = new Vector3f(vertices[vIdx1], vertices[vIdx1 + 1], vertices[vIdx1 + 2]);
            Vector3f v2 = new Vector3f(vertices[vIdx2], vertices[vIdx2 + 1], vertices[vIdx2 + 2]);

            // Normals
            Vector3f n0, n1, n2;
            if (normals != null && vIdx0 + 2 < normals.length && vIdx1 + 2 < normals.length && vIdx2 + 2 < normals.length) {
                n0 = new Vector3f(normals[vIdx0], normals[vIdx0 + 1], normals[vIdx0 + 2]);
                n1 = new Vector3f(normals[vIdx1], normals[vIdx1 + 1], normals[vIdx1 + 2]);
                n2 = new Vector3f(normals[vIdx2], normals[vIdx2 + 1], normals[vIdx2 + 2]);
            } else {
                norm.set(0.0f, 1.0f, 0.0f);
                n0 = n1 = n2 = norm;
            }

            // UV coordinates
            float u0 = texCoords != null && tIdx0 + 1 < texCoords.length ? texCoords[tIdx0] : 0.0f;
            float vt0 = texCoords != null && tIdx0 + 1 < texCoords.length ? texCoords[tIdx0 + 1] : 0.0f;
            float u1 = texCoords != null && tIdx1 + 1 < texCoords.length ? texCoords[tIdx1] : 0.0f;
            float vt1 = texCoords != null && tIdx1 + 1 < texCoords.length ? texCoords[tIdx1 + 1] : 0.0f;
            float u2 = texCoords != null && tIdx2 + 1 < texCoords.length ? texCoords[tIdx2] : 0.0f;
            float vt2 = texCoords != null && tIdx2 + 1 < texCoords.length ? texCoords[tIdx2 + 1] : 0.0f;

            // Reverse winding order for Minecraft: v2, v1, v0, v0 (degenerate quad)
            addVertex(consumer, pose, normal, v2, n2, u2, vt2, color, packedLight, packedOverlay);
            addVertex(consumer, pose, normal, v1, n1, u1, vt1, color, packedLight, packedOverlay);
            addVertex(consumer, pose, normal, v0, n0, u0, vt0, color, packedLight, packedOverlay);
            addVertex(consumer, pose, normal, v0, n0, u0, vt0, color, packedLight, packedOverlay); // Duplicate last vertex
        }
    }

    private void addVertex(VertexConsumer consumer, Matrix4f pose, Matrix3f normal,
                          Vector3f position, Vector3f norm, float u, float v,
                          Vector4f color, int packedLight, int packedOverlay) {
        consumer.vertex(pose, position.x, position.y, position.z)
            .color(color.x, color.y, color.z, color.w)
            .uv(u, v)
            .overlayCoords(packedOverlay)
            .uv2(packedLight)
            .normal(normal, norm.x, norm.y, norm.z)
            .endVertex();
    }
}
