package cn.minerealms.assimploader.example;

import cn.minerealms.assimploader.AssimpLoader;
import cn.minerealms.assimploader.model.AssimpMaterial;
import cn.minerealms.assimploader.model.AssimpMesh;
import cn.minerealms.assimploader.model.AssimpScene;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.blockentity.BlockEntityRenderer;
import net.minecraft.client.renderer.blockentity.BlockEntityRendererProvider;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.resources.ResourceLocation;
import org.joml.Matrix3f;
import org.joml.Matrix4f;
import org.joml.Vector3f;

/**
 * Renderer for CNC Machine BlockEntity.
 * Directly renders FBX model triangles using VertexConsumer.
 */
public class CNCMachineRenderer implements BlockEntityRenderer<CNCMachineBlockEntity> {

    private static final float SCALE = 3.0f;

    public CNCMachineRenderer(BlockEntityRendererProvider.Context context) {
    }

    @Override
    public void render(CNCMachineBlockEntity blockEntity, float partialTick, PoseStack poseStack,
                       MultiBufferSource bufferSource, int packedLight, int packedOverlay) {

        AssimpScene scene = blockEntity.getScene();
        if (scene == null) {
            return;
        }

        poseStack.pushPose();

        // Apply transformations
        poseStack.scale(SCALE, SCALE, SCALE);

        // Get matrices
        Matrix4f pose = poseStack.last().pose();
        Matrix3f normal = poseStack.last().normal();

        // Render each mesh
        for (AssimpMesh mesh : scene.getMeshes()) {
            renderMesh(mesh, scene, pose, normal, bufferSource, packedLight, packedOverlay);
        }

        poseStack.popPose();
    }

    private void renderMesh(AssimpMesh mesh, AssimpScene scene, Matrix4f pose, Matrix3f normal,
                           MultiBufferSource bufferSource, int packedLight, int packedOverlay) {

        // Get material
        AssimpMaterial material = null;
        if (mesh.getMaterialIndex() >= 0 && mesh.getMaterialIndex() < scene.getMaterials().size()) {
            material = scene.getMaterials().get(mesh.getMaterialIndex());
        }

        // Get render type
        RenderType renderType = getRenderType(material);
        VertexConsumer consumer = bufferSource.getBuffer(renderType);

        // Get mesh data
        float[] positions = mesh.getVertices();
        float[] normals = mesh.getNormals();
        float[] texCoords = mesh.getTexCoords();
        int[] indices = mesh.getIndices();

        // Render triangles
        for (int i = 0; i < indices.length; i += 3) {
            for (int j = 0; j < 3; j++) {
                int idx = indices[i + j];

                // Position
                float x = positions[idx * 3];
                float y = positions[idx * 3 + 1];
                float z = positions[idx * 3 + 2];

                // Normal
                float nx = normals != null && normals.length > idx * 3 ? normals[idx * 3] : 0;
                float ny = normals != null && normals.length > idx * 3 + 1 ? normals[idx * 3 + 1] : 1;
                float nz = normals != null && normals.length > idx * 3 + 2 ? normals[idx * 3 + 2] : 0;

                // Transform normal
                Vector3f transformedNormal = new Vector3f(nx, ny, nz);
                transformedNormal.mul(normal);
                transformedNormal.normalize();

                // Texture coordinates
                float u = texCoords != null && texCoords.length > idx * 2 ? texCoords[idx * 2] : 0;
                float v = texCoords != null && texCoords.length > idx * 2 + 1 ? texCoords[idx * 2 + 1] : 0;

                // Add vertex
                consumer.vertex(pose, x, y, z)
                    .color(255, 255, 255, 255)
                    .uv(u, v)
                    .overlayCoords(packedOverlay)
                    .uv2(packedLight)
                    .normal(transformedNormal.x, transformedNormal.y, transformedNormal.z)
                    .endVertex();
            }
        }
    }

    private RenderType getRenderType(AssimpMaterial material) {
        if (material != null && material.getDiffuseTexture() != null) {
            try {
                ResourceLocation texture = new ResourceLocation(material.getDiffuseTexture());
                return RenderType.entityCutout(texture);
            } catch (Exception e) {
                AssimpLoader.LOGGER.warn("Invalid texture path: {}", material.getDiffuseTexture());
            }
        }

        // Fallback to solid rendering
        return RenderType.solid();
    }
}
