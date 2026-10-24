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
     * Render a single mesh.
     *
     * <p>Allocation free: no per-vertex objects are created and each triangle emits exactly
     * three vertices.
     */
    protected void renderMesh(AssimpMesh mesh, AssimpMaterial material, PoseStack poseStack,
                              VertexConsumer consumer, int packedLight, int packedOverlay) {

        float[] vertices = mesh.getVertices();
        float[] normals = mesh.getNormals();
        float[] texCoords = mesh.getTexCoords();
        int[] indices = mesh.getIndices();

        float red = 1f;
        float green = 1f;
        float blue = 1f;
        float alpha = 1f;

        if (material != null) {
            float[] diffuse = material.getDiffuseColor();
            alpha = diffuse[3];
            // With shaders active the pack computes lighting from normals, so keep the
            // vertex colour white and only pass alpha through.
            if (!OculusCompat.isShadersActive()) {
                red = diffuse[0];
                green = diffuse[1];
                blue = diffuse[2];
            }
        }

        Matrix4f pose = poseStack.last().pose();
        Matrix3f normalMatrix = poseStack.last().normal();

        int vertexCount = vertices.length / 3;

        // Assimp winds counter-clockwise for a right-handed system; Minecraft expects the
        // opposite, so the triangle is emitted in reverse order.
        for (int i = 0; i + 2 < indices.length; i += 3) {
            int i0 = indices[i];
            int i1 = indices[i + 1];
            int i2 = indices[i + 2];

            if (i0 < 0 || i1 < 0 || i2 < 0 || i0 >= vertexCount || i1 >= vertexCount || i2 >= vertexCount) {
                continue;
            }

            addVertex(consumer, pose, normalMatrix, vertices, normals, texCoords, i2,
                    red, green, blue, alpha, packedLight, packedOverlay);
            addVertex(consumer, pose, normalMatrix, vertices, normals, texCoords, i1,
                    red, green, blue, alpha, packedLight, packedOverlay);
            addVertex(consumer, pose, normalMatrix, vertices, normals, texCoords, i0,
                    red, green, blue, alpha, packedLight, packedOverlay);
        }
    }

    /**
     * Emit one vertex without allocating.
     */
    protected void addVertex(VertexConsumer consumer, Matrix4f pose, Matrix3f normalMatrix,
                             float[] vertices, float[] normals, float[] texCoords, int vertexIndex,
                             float red, float green, float blue, float alpha,
                             int packedLight, int packedOverlay) {

        int p = vertexIndex * 3;
        float x = vertices[p];
        float y = vertices[p + 1];
        float z = vertices[p + 2];

        float nx = 0f;
        float ny = 1f;
        float nz = 0f;
        if (normals != null && p + 2 < normals.length) {
            nx = normals[p];
            ny = normals[p + 1];
            nz = normals[p + 2];

            float lengthSq = nx * nx + ny * ny + nz * nz;
            if (lengthSq > 1.0e-6f) {
                float inverseLength = 1.0f / (float) Math.sqrt(lengthSq);
                nx *= inverseLength;
                ny *= inverseLength;
                nz *= inverseLength;
            } else {
                nx = 0f;
                ny = 1f;
                nz = 0f;
            }
        }

        // Apply the pose normal matrix by hand (JOML's Matrix3f#mul allocates nothing but
        // needs a Vector3f, and we are deliberately avoiding those).
        float tnx = normalMatrix.m00() * nx + normalMatrix.m10() * ny + normalMatrix.m20() * nz;
        float tny = normalMatrix.m01() * nx + normalMatrix.m11() * ny + normalMatrix.m21() * nz;
        float tnz = normalMatrix.m02() * nx + normalMatrix.m12() * ny + normalMatrix.m22() * nz;

        float u = 0f;
        float v = 0f;
        if (texCoords != null) {
            int t = vertexIndex * 2;
            if (t + 1 < texCoords.length) {
                u = texCoords[t];
                v = texCoords[t + 1];
            }
        }

        consumer.vertex(pose, x, y, z)
                .color(red, green, blue, alpha)
                .uv(u, v)
                .overlayCoords(packedOverlay)
                .uv2(packedLight)
                .normal(tnx, tny, tnz)
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
