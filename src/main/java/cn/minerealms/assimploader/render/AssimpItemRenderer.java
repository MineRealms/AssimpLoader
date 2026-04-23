package cn.minerealms.assimploader.render;

import cn.minerealms.assimploader.AssimpLoader;
import cn.minerealms.assimploader.core.AssimpModelLoader;
import cn.minerealms.assimploader.model.AssimpMaterial;
import cn.minerealms.assimploader.model.AssimpMesh;
import cn.minerealms.assimploader.model.AssimpScene;
import cn.minerealms.assimploader.model.AssimpTexture;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import com.mojang.blaze3d.platform.NativeImage;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.BlockEntityWithoutLevelRenderer;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.blockentity.BlockEntityRenderDispatcher;
import net.minecraft.client.renderer.texture.DynamicTexture;
import net.minecraft.client.renderer.texture.TextureManager;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.ItemDisplayContext;
import net.minecraftforge.registries.ForgeRegistries;
import net.minecraft.world.item.ItemStack;
import org.jetbrains.annotations.NotNull;
import org.joml.Matrix3f;
import org.joml.Matrix4f;
import org.joml.Vector3f;
import org.joml.Vector4f;

import java.util.HashMap;
import java.util.Map;

/**
 * Custom item renderer for FBX models with embedded textures.
 * Renders items in hand, inventory, and GUI using the same logic as BlockEntityRenderer.
 */
public class AssimpItemRenderer extends BlockEntityWithoutLevelRenderer {

    private final Map<String, Map<Integer, ResourceLocation>> textureCache = new HashMap<>();
    private final Map<String, Integer> sceneHashCache = new HashMap<>();
    private final Map<String, Boolean> loggedItems = new HashMap<>(); // Track which items we've logged

    public AssimpItemRenderer(BlockEntityRenderDispatcher dispatcher) {
        super(dispatcher, Minecraft.getInstance().getEntityModels());
    }

    @Override
    public void renderByItem(@NotNull ItemStack stack, @NotNull ItemDisplayContext displayContext,
                             @NotNull PoseStack poseStack, @NotNull MultiBufferSource buffer,
                             int combinedLight, int combinedOverlay) {
        // Get the model name - try multiple formats
        String itemName = ForgeRegistries.ITEMS.getKey(stack.getItem()).getPath();
        String simpleModelName = itemName.replace("_machine", "").replace("_item", "");
        String fullModelName = "models/block/" + simpleModelName;

        // Only log once per item type
        if (!loggedItems.containsKey(itemName)) {
            AssimpLoader.LOGGER.info("AssimpItemRenderer: Rendering item '{}' (model: {} or {})",
                itemName, simpleModelName, fullModelName);
            loggedItems.put(itemName, true);
        }

        // Try to load the model - try simple name first, then full path
        AssimpScene scene = AssimpModelLoader.getCachedModel(simpleModelName);
        String modelName = simpleModelName;

        if (scene == null) {
            scene = AssimpModelLoader.getCachedModel(fullModelName);
            modelName = fullModelName;
        }

        if (scene == null) {
            if (!loggedItems.containsKey(itemName + "_error")) {
                AssimpLoader.LOGGER.warn("Model not found for item. Tried: {} and {}", simpleModelName, fullModelName);
                loggedItems.put(itemName + "_error", true);
            }
            return; // Model not found
        }

        // Detect if scene has changed (hot-reload) and clear texture cache
        int currentSceneHash = System.identityHashCode(scene);
        Integer cachedSceneHash = sceneHashCache.get(modelName);

        if (cachedSceneHash == null || cachedSceneHash != currentSceneHash) {
            // Scene changed! Clear texture cache for this model
            textureCache.remove(modelName);
            sceneHashCache.put(modelName, currentSceneHash);
        }

        // Load embedded textures if not already loaded
        if (!textureCache.containsKey(modelName)) {
            loadEmbeddedTextures(scene, modelName);
        }

        poseStack.pushPose();

        // Apply item-specific transforms based on display context
        applyItemTransforms(poseStack, displayContext);

        // Render each mesh
        for (int meshIndex = 0; meshIndex < scene.getMeshes().size(); meshIndex++) {
            AssimpMesh mesh = scene.getMeshes().get(meshIndex);

            // Get material
            AssimpMaterial material = null;
            ResourceLocation textureLocation = null;

            if (mesh.getMaterialIndex() >= 0 && mesh.getMaterialIndex() < scene.getMaterials().size()) {
                material = scene.getMaterials().get(mesh.getMaterialIndex());

                // If scene has embedded textures, use them
                if (scene.hasEmbeddedTextures() && textureCache.containsKey(modelName)) {
                    Map<Integer, ResourceLocation> modelTextures = textureCache.get(modelName);
                    if (!modelTextures.isEmpty()) {
                        textureLocation = modelTextures.get(0); // Use first texture
                    }
                }
            }

            // Choose render type based on material
            RenderType renderType;
            if (material != null && material.isTransparent()) {
                renderType = RenderType.entityTranslucent(
                    textureLocation != null ? textureLocation : new ResourceLocation("minecraft", "textures/block/white_concrete.png")
                );
            } else {
                renderType = RenderType.entityCutout(
                    textureLocation != null ? textureLocation : new ResourceLocation("minecraft", "textures/block/white_concrete.png")
                );
            }

            VertexConsumer consumer = buffer.getBuffer(renderType);

            // Render the mesh
            renderMesh(mesh, material, poseStack, consumer, combinedLight, combinedOverlay);
        }

        poseStack.popPose();
    }

    private void loadEmbeddedTextures(AssimpScene scene, String modelName) {
        TextureManager textureManager = Minecraft.getInstance().getTextureManager();
        textureCache.put(modelName, new HashMap<>());

        // Normalize model name for texture cache key (replace / with _)
        String normalizedName = modelName.replace("/", "_").replace(".fbx", "");

        for (int i = 0; i < scene.getTextures().size(); i++) {
            AssimpTexture texture = scene.getTextures().get(i);

            // Use the same ResourceLocation format as AssimpBakedModel
            ResourceLocation textureLoc = new ResourceLocation("assimploader",
                "embedded/" + normalizedName + "_" + i);

            // Check if texture is already registered by AssimpBakedModel
            if (textureManager.getTexture(textureLoc) != null) {
                // Texture already registered, just cache the location
                textureCache.get(modelName).put(i, textureLoc);
                continue;
            }

            // Texture not registered yet, load it now
            try {
                NativeImage image = texture.loadAsNativeImage();
                DynamicTexture dynamicTexture = new DynamicTexture(image);

                textureManager.register(textureLoc, dynamicTexture);
                textureCache.get(modelName).put(i, textureLoc);

                AssimpLoader.LOGGER.info("Loaded embedded texture {} for item model {}", i, modelName);
            } catch (Exception e) {
                AssimpLoader.LOGGER.error("Failed to load embedded texture {} for item model {}", i, modelName, e);
            }
        }
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
            // Reverse vertex order: v2, v1, v0 (instead of v0, v1, v2)
            for (int j = 2; j >= 0; j--) {
                int idx = indices[i + j];
                int vIdx = idx * 3;
                int tIdx = idx * 2;

                pos.set(vertices[vIdx], vertices[vIdx + 1], vertices[vIdx + 2]);

                if (normals != null && vIdx + 2 < normals.length) {
                    norm.set(normals[vIdx], normals[vIdx + 1], normals[vIdx + 2]);
                } else {
                    norm.set(0.0f, 1.0f, 0.0f);
                }

                float u = texCoords != null && tIdx + 1 < texCoords.length ? texCoords[tIdx] : 0.0f;
                float v = texCoords != null && tIdx + 1 < texCoords.length ? texCoords[tIdx + 1] : 0.0f;

                consumer.vertex(pose, pos.x, pos.y, pos.z)
                    .color(color.x, color.y, color.z, color.w)
                    .uv(u, v)
                    .overlayCoords(packedOverlay)
                    .uv2(packedLight)
                    .normal(normal, norm.x, norm.y, norm.z)
                    .endVertex();
            }
        }
    }

    private void applyItemTransforms(PoseStack poseStack, ItemDisplayContext context) {
        switch (context) {
            case FIRST_PERSON_LEFT_HAND:
            case FIRST_PERSON_RIGHT_HAND:
                // First person hand transforms
                poseStack.translate(0.5, 0.5, 0.5);
                poseStack.scale(0.5f, 0.5f, 0.5f);
                break;
            case THIRD_PERSON_LEFT_HAND:
            case THIRD_PERSON_RIGHT_HAND:
                // Third person hand transforms
                poseStack.translate(0.5, 0.5, 0.5);
                poseStack.scale(0.375f, 0.375f, 0.375f);
                break;
            case GUI:
                // GUI/inventory transforms
                poseStack.translate(0.5, 0.5, 0.5);
                poseStack.scale(0.625f, 0.625f, 0.625f);
                break;
            case GROUND:
                // Dropped item transforms
                poseStack.translate(0.5, 0.25, 0.5);
                poseStack.scale(0.25f, 0.25f, 0.25f);
                break;
            case FIXED:
                // Item frame transforms
                poseStack.translate(0.5, 0.5, 0.5);
                poseStack.scale(0.5f, 0.5f, 0.5f);
                break;
            case HEAD:
                // Head/helmet transforms
                poseStack.translate(0.5, 0.5, 0.5);
                poseStack.scale(1.0f, 1.0f, 1.0f);
                break;
            default:
                poseStack.translate(0.5, 0.5, 0.5);
                poseStack.scale(0.5f, 0.5f, 0.5f);
                break;
        }
    }
}
