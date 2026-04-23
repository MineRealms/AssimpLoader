package cn.minerealms.assimploader.example;

import cn.minerealms.assimploader.AssimpLoader;
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
import net.minecraft.client.renderer.blockentity.BlockEntityRendererProvider;
import net.minecraft.client.renderer.texture.AbstractTexture;
import net.minecraft.client.renderer.texture.DynamicTexture;
import net.minecraft.client.renderer.texture.TextureManager;
import net.minecraft.resources.ResourceLocation;
import org.joml.Matrix3f;
import org.joml.Matrix4f;
import org.joml.Vector3f;
import org.joml.Vector4f;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.HashMap;
import java.util.Map;

/**
 * Custom renderer for FBX models using BlockEntityRenderer.
 * This allows full control over texture binding and rendering.
 */
public class TestModelBlockEntityRenderer implements BlockEntityRenderer<TestModelBlockEntity> {

    private static final Map<String, ResourceLocation> textureCache = new HashMap<>();
    private static boolean textureDebugLogged = false;  // Only log texture registration once
    private static int renderCount = 0;  // Track render calls

    public TestModelBlockEntityRenderer(BlockEntityRendererProvider.Context context) {
    }

    @Override
    public void render(TestModelBlockEntity blockEntity, float partialTick, PoseStack poseStack,
                      MultiBufferSource bufferSource, int packedLight, int packedOverlay) {

        renderCount++;
        boolean isFirstRender = renderCount <= 3;  // Log first 3 renders

        if (isFirstRender) {
            AssimpLoader.LOGGER.info("DEBUG: ========== BlockEntityRenderer.render() called #{} ==========", renderCount);
        }

        // Get the cached model (from hot-reload or resource loading)
        String modelName = "cnc"; // TODO: Make this configurable per block
        AssimpScene scene = AssimpModelLoader.getCachedModel(modelName);

        if (scene == null) {
            if (isFirstRender) {
                AssimpLoader.LOGGER.error("DEBUG: Scene is NULL! Model not loaded.");
            }
            return;
        }

        if (isFirstRender) {
            AssimpLoader.LOGGER.info("DEBUG: Scene loaded, has embedded textures: {}", scene.hasEmbeddedTextures());
            AssimpLoader.LOGGER.info("DEBUG: Scene texture count: {}", scene.getTextureCount());
            AssimpLoader.LOGGER.info("DEBUG: Scene object: {}", System.identityHashCode(scene));
            AssimpLoader.LOGGER.info("DEBUG: Texture cache contains 'cnc_0': {}", textureCache.containsKey(modelName + "_0"));
            AssimpLoader.LOGGER.info("DEBUG: Texture cache keys: {}", textureCache.keySet());
        }

        // Load embedded textures if not already loaded
        if (scene.hasEmbeddedTextures() && !textureCache.containsKey(modelName + "_0")) {
            if (isFirstRender) {
                AssimpLoader.LOGGER.info("DEBUG: Loading embedded textures NOW...");
            }
            loadEmbeddedTextures(scene, modelName);
        } else if (isFirstRender) {
            AssimpLoader.LOGGER.warn("DEBUG: Skipping texture load - hasEmbedded: {}, cacheContains: {}",
                scene.hasEmbeddedTextures(), textureCache.containsKey(modelName + "_0"));
        }

        poseStack.pushPose();

        // Center the model
        poseStack.translate(0.5, 0, 0.5);

        // Render each mesh
        for (int meshIndex = 0; meshIndex < scene.getMeshes().size(); meshIndex++) {
            AssimpMesh mesh = scene.getMeshes().get(meshIndex);

            // Get material
            AssimpMaterial material = null;
            ResourceLocation textureLocation = null;

            if (mesh.getMaterialIndex() >= 0 && mesh.getMaterialIndex() < scene.getMaterials().size()) {
                material = scene.getMaterials().get(mesh.getMaterialIndex());

                // CRITICAL: If scene has embedded textures, use them regardless of material's texture path
                // FBX materials often reference external file paths even when textures are embedded
                if (scene.hasEmbeddedTextures()) {
                    // Use first embedded texture (index 0)
                    // TODO: Support multiple textures per material
                    String cacheKey = modelName + "_0";
                    textureLocation = textureCache.get(cacheKey);

                    if (isFirstRender) {
                        AssimpLoader.LOGGER.info("DEBUG: Mesh {} using embedded texture, cache key: {}, texture: {}",
                            meshIndex, cacheKey, textureLocation);

                        if (textureLocation != null) {
                            TextureManager tm = Minecraft.getInstance().getTextureManager();
                            AbstractTexture tex = tm.getTexture(textureLocation);
                            AssimpLoader.LOGGER.info("DEBUG: Texture exists in TextureManager: {}", tex != null);
                        } else {
                            AssimpLoader.LOGGER.error("DEBUG: Texture NOT in cache! Keys: {}", textureCache.keySet());
                        }
                    }
                } else if (isFirstRender) {
                    AssimpLoader.LOGGER.info("DEBUG: Mesh {} has no embedded textures, using material color only", meshIndex);
                }
            } else if (isFirstRender) {
                AssimpLoader.LOGGER.warn("DEBUG: Mesh {} has invalid material index: {}", meshIndex, mesh.getMaterialIndex());
            }

            if (isFirstRender) {
                AssimpLoader.LOGGER.info("DEBUG: Mesh {} material index: {}, texture: {}",
                    meshIndex, mesh.getMaterialIndex(), textureLocation);
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

            if (isFirstRender) {
                AssimpLoader.LOGGER.info("DEBUG: Using RenderType: {}, texture: {}",
                    material != null && material.isTransparent() ? "entityTranslucent" : "entityCutout",
                    textureLocation);
            }

            VertexConsumer consumer = bufferSource.getBuffer(renderType);

            // Render the mesh
            renderMesh(mesh, material, poseStack, consumer, packedLight, packedOverlay, isFirstRender);
        }

        if (isFirstRender) {
            AssimpLoader.LOGGER.info("DEBUG: ========== Render complete ==========");
        }
        poseStack.popPose();
    }

    private void renderMesh(AssimpMesh mesh, AssimpMaterial material, PoseStack poseStack,
                           VertexConsumer consumer, int packedLight, int packedOverlay, boolean isFirstRender) {

        float[] vertices = mesh.getVertices();
        float[] normals = mesh.getNormals();
        float[] texCoords = mesh.getTexCoords();
        int[] indices = mesh.getIndices();

        if (vertices == null || indices == null) {
            return;
        }

        // Get material color (for models without textures)
        Vector4f color = new Vector4f(1, 1, 1, 1);
        if (material != null && material.getDiffuseColor() != null) {
            float[] diffuse = material.getDiffuseColor();
            color.set(diffuse[0], diffuse[1], diffuse[2], diffuse[3]);
        }

        if (isFirstRender) {
            AssimpLoader.LOGGER.info("DEBUG: Material diffuse color: ({}, {}, {}, {})",
                color.x, color.y, color.z, color.w);
            AssimpLoader.LOGGER.info("DEBUG: Mesh has {} vertices, {} indices, {} triangles",
                vertices.length / 3, indices.length, indices.length / 3);
            AssimpLoader.LOGGER.info("DEBUG: Has texCoords: {}, length: {}",
                texCoords != null, texCoords != null ? texCoords.length : 0);
        }

        Matrix4f pose = poseStack.last().pose();
        Matrix3f normal = poseStack.last().normal();

        // Render triangles
        for (int i = 0; i < indices.length; i += 3) {
            int idx0 = indices[i] * 3;
            int idx1 = indices[i + 1] * 3;
            int idx2 = indices[i + 2] * 3;

            // Vertices
            Vector3f v0 = new Vector3f(vertices[idx0], vertices[idx0 + 1], vertices[idx0 + 2]);
            Vector3f v1 = new Vector3f(vertices[idx1], vertices[idx1 + 1], vertices[idx1 + 2]);
            Vector3f v2 = new Vector3f(vertices[idx2], vertices[idx2 + 1], vertices[idx2 + 2]);

            // Normals
            Vector3f n0, n1, n2;
            if (normals != null) {
                n0 = new Vector3f(normals[idx0], normals[idx0 + 1], normals[idx0 + 2]);
                n1 = new Vector3f(normals[idx1], normals[idx1 + 1], normals[idx1 + 2]);
                n2 = new Vector3f(normals[idx2], normals[idx2 + 1], normals[idx2 + 2]);
            } else {
                Vector3f edge1 = new Vector3f(v1).sub(v0);
                Vector3f edge2 = new Vector3f(v2).sub(v0);
                Vector3f faceNormal = new Vector3f(edge1).cross(edge2).normalize();
                n0 = n1 = n2 = faceNormal;
            }

            // TEST 2: Try both original UV and flipped UV
            float u0 = 0, vt0 = 0, u1 = 0, vt1 = 0, u2 = 0, vt2 = 0;
            if (texCoords != null && texCoords.length > 0) {
                int uvIdx0 = indices[i] * 2;
                int uvIdx1 = indices[i + 1] * 2;
                int uvIdx2 = indices[i + 2] * 2;

                u0 = texCoords[uvIdx0];
                vt0 = texCoords[uvIdx0 + 1];
                u1 = texCoords[uvIdx1];
                vt1 = texCoords[uvIdx1 + 1];
                u2 = texCoords[uvIdx2];
                vt2 = texCoords[uvIdx2 + 1];

                if (isFirstRender && i == 0) {
                    AssimpLoader.LOGGER.info("DEBUG: First triangle UV coords:");
                    AssimpLoader.LOGGER.info("  v0: ({}, {})", u0, vt0);
                    AssimpLoader.LOGGER.info("  v1: ({}, {})", u1, vt1);
                    AssimpLoader.LOGGER.info("  v2: ({}, {})", u2, vt2);
                }
            }

            // Reverse winding order for Minecraft (v2, v1, v0)
            addVertex(consumer, pose, normal, v2, n2, u2, vt2, color, packedLight, packedOverlay, isFirstRender && i == 0);
            addVertex(consumer, pose, normal, v1, n1, u1, vt1, color, packedLight, packedOverlay, false);
            addVertex(consumer, pose, normal, v0, n0, u0, vt0, color, packedLight, packedOverlay, false);
            // Duplicate last vertex for quad
            addVertex(consumer, pose, normal, v0, n0, u0, vt0, color, packedLight, packedOverlay, false);
        }
    }

    private void addVertex(VertexConsumer consumer, Matrix4f pose, Matrix3f normal,
                          Vector3f pos, Vector3f norm, float u, float v,
                          Vector4f color, int packedLight, int packedOverlay, boolean logThis) {

        if (logThis) {
            AssimpLoader.LOGGER.info("DEBUG: addVertex called with UV: ({}, {}), color: ({}, {}, {}, {})",
                u, v, color.x, color.y, color.z, color.w);
        }

        consumer.vertex(pose, pos.x, pos.y, pos.z)
                .color(color.x, color.y, color.z, color.w)
                .uv(u, v)
                .overlayCoords(packedOverlay)
                .uv2(packedLight)
                .normal(normal, norm.x, norm.y, norm.z)
                .endVertex();
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

                AssimpLoader.LOGGER.info("DEBUG: ========== Texture Registration ==========");
                AssimpLoader.LOGGER.info("DEBUG: About to register texture:");
                AssimpLoader.LOGGER.info("  ResourceLocation: {}", textureLoc);
                AssimpLoader.LOGGER.info("  Cache key: {}", modelName + "_" + i);
                AssimpLoader.LOGGER.info("  Image size: {}x{}", image.getWidth(), image.getHeight());

                // Save original texture snapshot to disk
                try {
                    Path debugPath = Paths.get("H:/MinecraftMods/ImprovedMobs/debug/embedded_texture_" + modelName + "_" + i + ".png");
                    Files.createDirectories(debugPath.getParent());
                    image.writeToFile(debugPath);
                    AssimpLoader.LOGGER.info("DEBUG: Saved embedded texture snapshot to: {}", debugPath);
                } catch (IOException e) {
                    AssimpLoader.LOGGER.error("Failed to save texture snapshot: {}", e.getMessage());
                }

                textureManager.register(textureLoc, dynamicTexture);
                textureCache.put(modelName + "_" + i, textureLoc);

                // CRITICAL: Verify texture was actually registered
                AbstractTexture registeredTexture = textureManager.getTexture(textureLoc);
                AssimpLoader.LOGGER.info("DEBUG: Texture registered successfully: {}", registeredTexture != null);

                if (registeredTexture instanceof DynamicTexture) {
                    DynamicTexture dt = (DynamicTexture) registeredTexture;
                    NativeImage verifyImage = dt.getPixels();
                    if (verifyImage != null) {
                        AssimpLoader.LOGGER.info("DEBUG: Verified texture in TextureManager: {}x{}",
                            verifyImage.getWidth(), verifyImage.getHeight());

                        // Save the verified texture from Minecraft to disk
                        try {
                            Path verifyPath = Paths.get("H:/MinecraftMods/ImprovedMobs/debug/minecraft_verified_" + modelName + "_" + i + ".png");
                            Files.createDirectories(verifyPath.getParent());
                            verifyImage.writeToFile(verifyPath);
                            AssimpLoader.LOGGER.info("DEBUG: Saved verified texture from Minecraft to: {}", verifyPath);
                        } catch (IOException e) {
                            AssimpLoader.LOGGER.error("Failed to save verified texture: {}", e.getMessage());
                        }
                    } else {
                        AssimpLoader.LOGGER.error("DEBUG: DynamicTexture.getPixels() returned null!");
                    }
                } else {
                    AssimpLoader.LOGGER.error("DEBUG: Registered texture is not a DynamicTexture! Type: {}",
                        registeredTexture != null ? registeredTexture.getClass().getName() : "null");
                }

                AssimpLoader.LOGGER.info("DEBUG: ==========================================");
            } catch (Exception e) {
                AssimpLoader.LOGGER.error("Failed to load embedded texture {}: {}", i, e.getMessage(), e);
            }
        }
    }
}
