package cn.minerealms.assimploader.example;

import cn.minerealms.assimploader.AssimpLoader;
import cn.minerealms.assimploader.core.AssimpModelLoader;
import cn.minerealms.assimploader.model.AssimpMaterial;
import cn.minerealms.assimploader.model.AssimpMesh;
import cn.minerealms.assimploader.model.AssimpScene;
import cn.minerealms.assimploader.model.AssimpTexture;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.blockentity.BlockEntityRenderer;
import net.minecraft.client.renderer.blockentity.BlockEntityRendererProvider;
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
 * Custom renderer for FBX models using BlockEntityRenderer.
 * This allows full control over texture binding and rendering.
 */
public class TestModelBlockEntityRenderer implements BlockEntityRenderer<TestModelBlockEntity> {

    private static final Map<String, ResourceLocation> textureCache = new HashMap<>();

    public TestModelBlockEntityRenderer(BlockEntityRendererProvider.Context context) {
    }

    @Override
    public void render(TestModelBlockEntity blockEntity, float partialTick, PoseStack poseStack,
                      MultiBufferSource bufferSource, int packedLight, int packedOverlay) {

        // Get the cached model (from hot-reload or resource loading)
        String modelName = "cnc"; // TODO: Make this configurable per block
        AssimpScene scene = AssimpModelLoader.getCachedModel(modelName);

        if (scene == null) {
            return;
        }

        // Load embedded textures if not already loaded
        if (scene.hasEmbeddedTextures() && !textureCache.containsKey(modelName + "_0")) {
            loadEmbeddedTextures(scene, modelName);
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

                // Check for embedded texture
                String texPath = material.getDiffuseTexture();
                if (texPath != null && material.isEmbeddedTexture(texPath)) {
                    int texIndex = material.getEmbeddedTextureIndex(texPath);
                    textureLocation = textureCache.get(modelName + "_" + texIndex);
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

            VertexConsumer consumer = bufferSource.getBuffer(renderType);

            // Render the mesh
            renderMesh(mesh, material, poseStack, consumer, packedLight, packedOverlay);
        }

        poseStack.popPose();
    }

    private void renderMesh(AssimpMesh mesh, AssimpMaterial material, PoseStack poseStack,
                           VertexConsumer consumer, int packedLight, int packedOverlay) {

        float[] vertices = mesh.getVertices();
        float[] normals = mesh.getNormals();
        float[] texCoords = mesh.getTexCoords();
        int[] indices = mesh.getIndices();

        if (vertices == null || indices == null) {
            return;
        }

        // Get material color
        Vector4f color = new Vector4f(1, 1, 1, 1);
        if (material != null && material.getDiffuseColor() != null) {
            float[] diffuse = material.getDiffuseColor();
            color.set(diffuse[0], diffuse[1], diffuse[2], diffuse[3]);
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

            // UVs
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
            }

            // Reverse winding order for Minecraft (v2, v1, v0)
            addVertex(consumer, pose, normal, v2, n2, u2, vt2, color, packedLight, packedOverlay);
            addVertex(consumer, pose, normal, v1, n1, u1, vt1, color, packedLight, packedOverlay);
            addVertex(consumer, pose, normal, v0, n0, u0, vt0, color, packedLight, packedOverlay);
            // Duplicate last vertex for quad
            addVertex(consumer, pose, normal, v0, n0, u0, vt0, color, packedLight, packedOverlay);
        }
    }

    private void addVertex(VertexConsumer consumer, Matrix4f pose, Matrix3f normal,
                          Vector3f pos, Vector3f norm, float u, float v,
                          Vector4f color, int packedLight, int packedOverlay) {
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
            DynamicTexture dynamicTexture = texture.createDynamicTexture();

            if (dynamicTexture != null) {
                ResourceLocation textureLoc = new ResourceLocation("assimploader",
                    "embedded/" + modelName + "_" + i);
                textureManager.register(textureLoc, dynamicTexture);
                textureCache.put(modelName + "_" + i, textureLoc);
                AssimpLoader.LOGGER.info("Registered embedded texture for BlockEntityRenderer: {}", textureLoc);
            }
        }
    }
}
