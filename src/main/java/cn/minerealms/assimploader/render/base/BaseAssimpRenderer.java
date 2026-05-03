package cn.minerealms.assimploader.render.base;

import cn.minerealms.assimploader.compat.OculusCompat;
import cn.minerealms.assimploader.compat.OculusPBRSupport;
import cn.minerealms.assimploader.core.AssimpModelLoader;
import cn.minerealms.assimploader.debug.DebugManager;
import cn.minerealms.assimploader.debug.DebugManager.DebugCategory;
import cn.minerealms.assimploader.model.AssimpMaterial;
import cn.minerealms.assimploader.model.AssimpMesh;
import cn.minerealms.assimploader.model.AssimpScene;
import cn.minerealms.assimploader.model.AssimpTexture;
import com.mojang.blaze3d.platform.NativeImage;
import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.texture.DynamicTexture;
import net.minecraft.client.renderer.texture.TextureManager;
import net.minecraft.resources.ResourceLocation;
import org.joml.Matrix3f;
import org.joml.Matrix4f;
import org.joml.Vector3f;
import org.joml.Vector4f;

import java.util.HashMap;
import java.util.Map;

/**
 * Base class for all Assimp-based renderers.
 *
 * Provides common functionality:
 * - Texture management (embedded textures, caching)
 * - Hot-reload detection (scene hash tracking)
 * - Material system (PBR support, transparency)
 * - RenderType selection (shader-aware)
 * - Vertex rendering logic
 *
 * Subclasses only need to provide:
 * - Model name
 * - Custom transformations (optional)
 * - Rendering context (Block/Item/Entity)
 */
public abstract class BaseAssimpRenderer {

    // Shared caches across all renderer instances
    protected static final Map<String, ResourceLocation> textureCache = new HashMap<>();
    protected static final Map<String, Integer> sceneHashCache = new HashMap<>();
    protected static final Map<String, Boolean> textureDebugLogged = new HashMap<>();
    protected static final Map<String, Integer> renderCountMap = new HashMap<>();
    protected static final Map<String, Boolean> modeLoggedMap = new HashMap<>();

    /**
     * Get the model name for this renderer.
     * This should match the cache key used in AssimpModelLoader.
     */
    protected abstract String getModelName();

    /**
     * Check if hot-reload occurred and clear caches if needed.
     * Returns true if scene was reloaded.
     */
    protected boolean checkHotReload(AssimpScene scene, String modelName) {
        int currentSceneHash = System.identityHashCode(scene);
        Integer cachedSceneHash = sceneHashCache.get(modelName);

        if (cachedSceneHash == null || cachedSceneHash != currentSceneHash) {
            // Scene changed! Clear texture cache for this model
            textureCache.entrySet().removeIf(entry -> entry.getKey().startsWith(modelName + "_"));
            sceneHashCache.put(modelName, currentSceneHash);
            textureDebugLogged.put(modelName, false);
            return true;
        }
        return false;
    }

    /**
     * Load embedded textures from scene if not already loaded.
     */
    protected void loadEmbeddedTextures(AssimpScene scene, String modelName) {
        if (!scene.hasEmbeddedTextures()) {
            return;
        }

        TextureManager textureManager = Minecraft.getInstance().getTextureManager();

        for (int i = 0; i < scene.getTextures().size(); i++) {
            String cacheKey = modelName + "_" + i;
            if (textureCache.containsKey(cacheKey)) {
                continue; // Already loaded
            }

            AssimpTexture texture = scene.getTextures().get(i);

            try {
                NativeImage image = texture.loadAsNativeImage();

                // Apply material opacity to texture if material is transparent
                for (AssimpMaterial material : scene.getMaterials()) {
                    if (material.isTransparent()) {
                        float opacity = material.getOpacity();
                        applyOpacityToTexture(image, opacity);
                        DebugManager.info(DebugCategory.CORE, "Applied opacity {} to texture {} for model {}",
                            opacity, i, modelName);
                        break; // Only apply once
                    }
                }

                DynamicTexture dynamicTexture = new DynamicTexture(image);
                ResourceLocation textureLoc = new ResourceLocation("assimploader",
                    "embedded/" + modelName + "_" + i);

                textureManager.register(textureLoc, dynamicTexture);
                textureCache.put(cacheKey, textureLoc);

                if (!textureDebugLogged.getOrDefault(modelName, false)) {
                    DebugManager.info(DebugCategory.CORE, "Loaded embedded texture {} for {}: {}", i, modelName, textureLoc);
                }
            } catch (Exception e) {
                DebugManager.error(DebugCategory.CORE, "Failed to load embedded texture {} for {}", i, modelName, e);
            }
        }

        textureDebugLogged.put(modelName, true);
    }

    /**
     * Apply material opacity to all pixels in the texture.
     */
    protected void applyOpacityToTexture(NativeImage image, float opacity) {
        int width = image.getWidth();
        int height = image.getHeight();

        for (int y = 0; y < height; y++) {
            for (int x = 0; x < width; x++) {
                int pixel = image.getPixelRGBA(x, y);

                // Extract RGBA components (ABGR format in NativeImage)
                int a = (pixel >> 24) & 0xFF;
                int b = (pixel >> 16) & 0xFF;
                int g = (pixel >> 8) & 0xFF;
                int r = pixel & 0xFF;

                // Apply material opacity to alpha channel
                int newAlpha = (int)(a * opacity);

                // Reconstruct pixel with new alpha
                int newPixel = (newAlpha << 24) | (b << 16) | (g << 8) | r;
                image.setPixelRGBA(x, y, newPixel);
            }
        }
    }

    /**
     * Get the appropriate render type based on material properties and shader status.
     */
    protected RenderType getRenderType(AssimpMaterial material, ResourceLocation texture) {
        ResourceLocation textureLocation = texture != null ? texture :
            new ResourceLocation("minecraft", "textures/block/white_concrete.png");

        boolean shadersActive = OculusCompat.isShadersActive();
        boolean isTransparent = material != null && material.isTransparent();

        if (shadersActive) {
            // Shader-enhanced rendering
            if (isTransparent) {
                return RenderType.entityTranslucentCull(textureLocation);
            } else {
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

    /**
     * Get texture location for a mesh.
     */
    protected ResourceLocation getTextureLocation(AssimpScene scene, AssimpMesh mesh, String modelName) {
        // If scene has embedded textures, use them
        if (scene.hasEmbeddedTextures() && textureCache.containsKey(modelName + "_0")) {
            return textureCache.get(modelName + "_0");
        }

        // Default white texture
        return new ResourceLocation("minecraft", "textures/block/white_concrete.png");
    }

    /**
     * Render a mesh in STATIC mode (direct mesh iteration).
     */
    protected void renderMeshList(AssimpScene scene, PoseStack poseStack, MultiBufferSource bufferSource,
                                   int packedLight, int packedOverlay, String modelName, boolean isFirstRender) {
        for (int meshIndex = 0; meshIndex < scene.getMeshes().size(); meshIndex++) {
            AssimpMesh mesh = scene.getMeshes().get(meshIndex);

            // Get material
            AssimpMaterial material = null;
            ResourceLocation textureLocation = null;

            if (mesh.getMaterialIndex() >= 0 && mesh.getMaterialIndex() < scene.getMaterials().size()) {
                material = scene.getMaterials().get(mesh.getMaterialIndex());
                textureLocation = getTextureLocation(scene, mesh, modelName);
            }

            // Choose render type
            RenderType renderType = getRenderType(material, textureLocation);

            // Enable blending for transparent materials
            if (material != null && material.isTransparent()) {
                RenderSystem.enableBlend();
                RenderSystem.defaultBlendFunc();
            }

            VertexConsumer consumer = bufferSource.getBuffer(renderType);

            // Register PBR textures if shaders support it
            if (textureLocation != null && material != null) {
                OculusPBRSupport.registerPBRTextures(textureLocation, material);
            }

            // Render the mesh
            renderMesh(mesh, material, poseStack, consumer, packedLight, packedOverlay);
        }
    }

    /**
     * Render a mesh in DYNAMIC mode (traverse scene graph).
     */
    protected void renderSceneGraph(AssimpScene scene, PoseStack poseStack, MultiBufferSource bufferSource,
                                    int packedLight, int packedOverlay, String modelName, boolean isFirstRender) {
        cn.minerealms.assimploader.model.AssimpNode rootNode = scene.getRootNode();
        if (rootNode != null) {
            renderNode(scene, rootNode, poseStack, bufferSource, packedLight, packedOverlay, modelName, isFirstRender, 0);
        }
    }

    /**
     * Recursively render a node and its children in the scene graph.
     */
    protected void renderNode(AssimpScene scene, cn.minerealms.assimploader.model.AssimpNode node,
                              PoseStack poseStack, MultiBufferSource bufferSource,
                              int packedLight, int packedOverlay, String modelName, boolean isFirstRender, int depth) {
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
                    textureLocation = getTextureLocation(scene, mesh, modelName);
                }

                // Choose render type
                RenderType renderType = getRenderType(material, textureLocation);

                // Enable blending for transparent materials
                if (material != null && material.isTransparent()) {
                    RenderSystem.enableBlend();
                    RenderSystem.defaultBlendFunc();
                }

                VertexConsumer consumer = bufferSource.getBuffer(renderType);

                // Register PBR textures if shaders support it
                if (textureLocation != null && material != null) {
                    OculusPBRSupport.registerPBRTextures(textureLocation, material);
                }

                // Render the mesh
                renderMesh(mesh, material, poseStack, consumer, packedLight, packedOverlay);
            }
        }

        // Recursively render child nodes
        for (cn.minerealms.assimploader.model.AssimpNode child : node.getChildren()) {
            renderNode(scene, child, poseStack, bufferSource, packedLight, packedOverlay, modelName, isFirstRender, depth + 1);
        }

        poseStack.popPose();
    }

    /**
     * Render a single mesh with vertex data.
     */
    protected void renderMesh(AssimpMesh mesh, AssimpMaterial material, PoseStack poseStack,
                              VertexConsumer consumer, int packedLight, int packedOverlay) {

        float[] vertices = mesh.getVertices();
        float[] normals = mesh.getNormals();
        float[] texCoords = mesh.getTexCoords();
        int[] indices = mesh.getIndices();

        float[] diffuseColor = material != null ? material.getDiffuseColor() : new float[]{1.0f, 1.0f, 1.0f, 1.0f};

        // When shaders are active, use white color and let shader handle lighting
        boolean shadersActive = OculusCompat.isShadersActive();
        Vector4f color;
        if (shadersActive) {
            color = new Vector4f(1.0f, 1.0f, 1.0f, diffuseColor[3]);
        } else {
            color = new Vector4f(diffuseColor[0], diffuseColor[1], diffuseColor[2], diffuseColor[3]);
        }

        Matrix4f pose = poseStack.last().pose();
        Matrix3f normal = poseStack.last().normal();

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
                Vector3f defaultNorm = new Vector3f(0.0f, 1.0f, 0.0f);
                n0 = n1 = n2 = defaultNorm;
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

    /**
     * Add a single vertex to the vertex consumer.
     */
    protected void addVertex(VertexConsumer consumer, Matrix4f pose, Matrix3f normal,
                             Vector3f position, Vector3f norm, float u, float v,
                             Vector4f color, int packedLight, int packedOverlay) {
        // Normalize the normal vector to ensure correct lighting
        Vector3f normalizedNorm = new Vector3f(norm);
        normalizedNorm.normalize();

        // Transform normal to world space using the normal matrix
        normalizedNorm.mul(normal);

        consumer.vertex(pose, position.x, position.y, position.z)
            .color(color.x, color.y, color.z, color.w)
            .uv(u, v)
            .overlayCoords(packedOverlay)
            .uv2(packedLight)
            .normal(normalizedNorm.x, normalizedNorm.y, normalizedNorm.z)
            .endVertex();
    }

    /**
     * Track render count for debug logging.
     */
    protected int incrementRenderCount(String modelName) {
        int count = renderCountMap.getOrDefault(modelName, 0) + 1;
        renderCountMap.put(modelName, count);
        return count;
    }

    /**
     * Check if this is one of the first few renders (for debug logging).
     */
    protected boolean isFirstRender(String modelName, int maxCount) {
        return renderCountMap.getOrDefault(modelName, 0) <= maxCount;
    }
}
