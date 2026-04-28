package cn.minerealms.assimploader.render;

import cn.minerealms.assimploader.animation.AnimationState;
import cn.minerealms.assimploader.animation.BoneHierarchyBuilder;
import cn.minerealms.assimploader.animation.ClientAnimationManager;
import cn.minerealms.assimploader.debug.DebugManager;
import cn.minerealms.assimploader.debug.DebugManager.DebugCategory;
import cn.minerealms.assimploader.compat.OculusCompat;
import cn.minerealms.assimploader.compat.OculusPBRSupport;
import cn.minerealms.assimploader.core.AssimpModelLoader;
import cn.minerealms.assimploader.model.AssimpMaterial;
import cn.minerealms.assimploader.model.AssimpMesh;
import cn.minerealms.assimploader.model.AssimpScene;
import cn.minerealms.assimploader.model.AssimpTexture;
import cn.minerealms.assimploader.rendering.SimpleMeshRenderer;
import cn.minerealms.assimploader.rendering.SkinnedMeshRenderer;
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

import java.io.IOException;
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
    private final Map<String, Boolean> modeLoggedMap = new HashMap<>();
    private SkinnedMeshRenderer skinnedRenderer;
    private boolean skinnedRendererInitialized = false;

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
            DebugManager.info(DebugCategory.RENDER, "AssimpItemRenderer: Rendering item '{}' (model: {} or {})",
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
                DebugManager.warn(DebugCategory.RENDER, "Model not found for item. Tried: {} and {}", simpleModelName, fullModelName);
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

        // Log import mode once per model
        if (!modeLoggedMap.containsKey(modelName)) {
            DebugManager.info(DebugCategory.RENDER, "Item '{}' import mode: {} (isDynamic: {})",
                modelName, scene.getImportMode(), scene.isDynamic());
            modeLoggedMap.put(modelName, true);
        }

        poseStack.pushPose();

        // Apply item-specific transforms based on display context
        applyItemTransforms(poseStack, displayContext);

        // Get animation state from ClientAnimationManager
        AnimationState animationState = ClientAnimationManager.getInstance().getOrCreateAnimationState(modelName);

        // Choose rendering path based on import mode and mesh type
        if (scene.isDynamic()) {
            // DYNAMIC mode: Check if we need skeletal or node animation
            renderDynamicMode(scene, animationState, poseStack, buffer, combinedLight, combinedOverlay, modelName);
        } else {
            // STATIC mode: Direct mesh iteration (transformations already baked)
            renderMeshList(scene, poseStack, buffer, combinedLight, combinedOverlay, modelName);
        }

        poseStack.popPose();
    }

    /**
     * Render in STATIC mode - direct mesh iteration with baked transformations
     */
    private void renderMeshList(AssimpScene scene, PoseStack poseStack, MultiBufferSource buffer,
                                int combinedLight, int combinedOverlay, String modelName) {
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

            // Choose render type based on material and shader status
            RenderType renderType = getRenderType(material, textureLocation);

            VertexConsumer consumer = buffer.getBuffer(renderType);

            // Register PBR textures if shaders support it
            if (textureLocation != null && material != null) {
                OculusPBRSupport.registerPBRTextures(textureLocation, material);
            }

            // Render the mesh
            renderMesh(mesh, material, poseStack, consumer, combinedLight, combinedOverlay);
        }
    }

    /**
     * Render in DYNAMIC mode - supports both skeletal and node animations
     */
    private void renderDynamicMode(AssimpScene scene, AnimationState animationState, PoseStack poseStack,
                                   MultiBufferSource buffer, int combinedLight, int combinedOverlay, String modelName) {
        if (animationState == null || !animationState.isInitialized()) {
            // Fallback to static scene graph rendering
            renderSceneGraph(scene, poseStack, buffer, combinedLight, combinedOverlay, modelName);
            return;
        }

        // Get texture
        ResourceLocation texture = getTextureForModel(scene, modelName);

        // Render each mesh with appropriate method
        for (AssimpMesh mesh : scene.getMeshes()) {
            if (mesh.hasBones()) {
                // Skeletal animation path
                renderSkinnedMesh(mesh, animationState, poseStack, texture, combinedLight);
            } else {
                // Node animation path
                renderNodeAnimatedMesh(mesh, animationState, scene, poseStack, buffer, texture, combinedLight, combinedOverlay);
            }
        }
    }

    /**
     * Render skeletal animated mesh using SkinnedMeshRenderer
     */
    private void renderSkinnedMesh(AssimpMesh mesh, AnimationState animationState, PoseStack poseStack,
                                   ResourceLocation texture, int combinedLight) {
        // Initialize skinned renderer if needed
        if (!skinnedRendererInitialized) {
            skinnedRenderer = new SkinnedMeshRenderer();
            try {
                skinnedRenderer.initialize();
                skinnedRendererInitialized = true;
                DebugManager.info(DebugCategory.RENDER, "AssimpItemRenderer: SkinnedMeshRenderer initialized");
            } catch (IOException e) {
                DebugManager.error(DebugCategory.RENDER, "Failed to initialize SkinnedMeshRenderer", e);
                return;
            }
        }

        BoneHierarchyBuilder boneHierarchy = animationState.getBoneHierarchy(mesh);
        if (boneHierarchy != null) {
            // Get matrices
            Matrix4f mvp = new Matrix4f(poseStack.last().pose());
            Matrix4f mv = new Matrix4f(poseStack.last().pose());

            // Calculate light level
            float lightLevel = (combinedLight & 0xFFFF) / 240.0f;

            // Render skinned mesh
            skinnedRenderer.render(mesh, boneHierarchy, mvp, mv, texture,
                    Minecraft.getInstance().getTextureManager(), lightLevel);
        }
    }

    /**
     * Render node-animated mesh using SimpleMeshRenderer
     */
    private void renderNodeAnimatedMesh(AssimpMesh mesh, AnimationState animationState, AssimpScene scene,
                                       PoseStack poseStack, MultiBufferSource buffer, ResourceLocation texture,
                                       int combinedLight, int combinedOverlay) {
        // Get node transforms from animation player
        Map<String, Matrix4f> nodeTransforms = null;
        if (animationState.getCurrentAnimation() != null) {
            nodeTransforms = animationState.getCurrentAnimation().getBoneTransforms();
        }

        // Render using SimpleMeshRenderer
        SimpleMeshRenderer.render(mesh, poseStack, buffer, texture, combinedLight, combinedOverlay, scene, nodeTransforms);
    }

    /**
     * Get texture for model (embedded or default)
     */
    private ResourceLocation getTextureForModel(AssimpScene scene, String modelName) {
        ResourceLocation texture = new ResourceLocation("minecraft", "textures/block/white_concrete.png");
        if (scene.hasEmbeddedTextures() && textureCache.containsKey(modelName)) {
            Map<Integer, ResourceLocation> modelTextures = textureCache.get(modelName);
            if (!modelTextures.isEmpty()) {
                texture = modelTextures.get(0); // Use first texture
            }
        }
        return texture;
    }

    /**
     * Render in DYNAMIC mode - traverse scene graph to apply node transformations (fallback for non-animated)
     */
    private void renderSceneGraph(AssimpScene scene, PoseStack poseStack, MultiBufferSource buffer,
                                  int combinedLight, int combinedOverlay, String modelName) {
        cn.minerealms.assimploader.model.AssimpNode rootNode = scene.getRootNode();
        if (rootNode != null) {
            // Only log once per model
            if (!loggedItems.containsKey(modelName + "_dynamic")) {
                DebugManager.info(DebugCategory.RENDER, "Item '{}' rendering in DYNAMIC mode, root node: {}",
                    modelName, rootNode.getName());
                loggedItems.put(modelName + "_dynamic", true);
            }
            renderNode(scene, rootNode, poseStack, buffer, combinedLight, combinedOverlay, modelName, 0);
        }
    }

    /**
     * Recursively render a node and its children in the scene graph
     */
    private void renderNode(AssimpScene scene, cn.minerealms.assimploader.model.AssimpNode node,
                           PoseStack poseStack, MultiBufferSource buffer,
                           int combinedLight, int combinedOverlay, String modelName, int depth) {
        // Log node structure once (only first 5 levels)
        String logKey = modelName + "_node_" + node.getName();
        if (!loggedItems.containsKey(logKey) && depth < 5) {
            DebugManager.info(DebugCategory.RENDER, "Item '{}' node[depth={}]: '{}', meshes: {}, children: {}",
                modelName, depth, node.getName(), node.getMeshIndices().length, node.getChildren().size());
            loggedItems.put(logKey, true);
        }

        // Apply this node's transformation
        poseStack.pushPose();
        poseStack.last().pose().mul(node.getTransformation());

        // Render all meshes attached to this node
        for (int meshIndex : node.getMeshIndices()) {
            if (meshIndex >= 0 && meshIndex < scene.getMeshes().size()) {
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

                // Choose render type based on material and shader status
                RenderType renderType = getRenderType(material, textureLocation);

                VertexConsumer consumer = buffer.getBuffer(renderType);

                // Register PBR textures if shaders support it
                if (textureLocation != null && material != null) {
                    OculusPBRSupport.registerPBRTextures(textureLocation, material);
                }

                // Render the mesh
                renderMesh(mesh, material, poseStack, consumer, combinedLight, combinedOverlay);
            }
        }

        // Recursively render child nodes
        for (cn.minerealms.assimploader.model.AssimpNode child : node.getChildren()) {
            renderNode(scene, child, poseStack, buffer, combinedLight, combinedOverlay, modelName, depth + 1);
        }

        poseStack.popPose();
    }

    private void loadEmbeddedTextures(AssimpScene scene, String modelName) {
        TextureManager textureManager = Minecraft.getInstance().getTextureManager();
        textureCache.put(modelName, new HashMap<>());

        // Skip if no embedded textures
        if (!scene.hasEmbeddedTextures() || scene.getTextures().isEmpty()) {
            return;
        }

        // Normalize model name for texture cache key (replace / with _)
        String normalizedName = modelName.replace("/", "_").replace(".fbx", "");

        for (int i = 0; i < scene.getTextures().size(); i++) {
            AssimpTexture texture = scene.getTextures().get(i);

            // Use the same ResourceLocation format as AssimpBakedModel
            ResourceLocation textureLoc = new ResourceLocation("assimploader",
                "embedded/" + normalizedName + "_" + i);

            // Load and register the texture
            try {
                NativeImage image = texture.loadAsNativeImage();
                DynamicTexture dynamicTexture = new DynamicTexture(image);

                textureManager.register(textureLoc, dynamicTexture);
                textureCache.get(modelName).put(i, textureLoc);

                DebugManager.info(DebugCategory.RENDER, "Loaded embedded texture {} for item model {}", i, modelName);
            } catch (Exception e) {
                DebugManager.error(DebugCategory.RENDER, "Failed to load embedded texture {} for item model {}", i, modelName, e);
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
            consumer.vertex(pose, v2.x, v2.y, v2.z)
                .color(color.x, color.y, color.z, color.w)
                .uv(u2, vt2)
                .overlayCoords(packedOverlay)
                .uv2(packedLight)
                .normal(normal, n2.x, n2.y, n2.z)
                .endVertex();

            consumer.vertex(pose, v1.x, v1.y, v1.z)
                .color(color.x, color.y, color.z, color.w)
                .uv(u1, vt1)
                .overlayCoords(packedOverlay)
                .uv2(packedLight)
                .normal(normal, n1.x, n1.y, n1.z)
                .endVertex();

            consumer.vertex(pose, v0.x, v0.y, v0.z)
                .color(color.x, color.y, color.z, color.w)
                .uv(u0, vt0)
                .overlayCoords(packedOverlay)
                .uv2(packedLight)
                .normal(normal, n0.x, n0.y, n0.z)
                .endVertex();

            // Duplicate last vertex for degenerate quad
            consumer.vertex(pose, v0.x, v0.y, v0.z)
                .color(color.x, color.y, color.z, color.w)
                .uv(u0, vt0)
                .overlayCoords(packedOverlay)
                .uv2(packedLight)
                .normal(normal, n0.x, n0.y, n0.z)
                .endVertex();
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

    /**
     * Get the appropriate render type based on material properties and shader status.
     * When Oculus shaders are active, uses shader-compatible render types.
     */
    private RenderType getRenderType(AssimpMaterial material, ResourceLocation texture) {
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
}
