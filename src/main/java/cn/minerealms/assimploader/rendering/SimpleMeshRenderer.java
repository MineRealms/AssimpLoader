package cn.minerealms.assimploader.rendering;

import cn.minerealms.assimploader.debug.DebugManager;
import cn.minerealms.assimploader.debug.DebugManager.DebugCategory;
import cn.minerealms.assimploader.model.AssimpMaterial;
import cn.minerealms.assimploader.model.AssimpMesh;
import cn.minerealms.assimploader.model.AssimpScene;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.resources.ResourceLocation;
import org.joml.Matrix3f;
import org.joml.Matrix4f;

import javax.annotation.Nullable;
import java.util.Map;

/**
 * Simple mesh renderer for non-skinned meshes (static or baked animation).
 * Uses Minecraft's BufferBuilder system for rendering.
 */
public class SimpleMeshRenderer {

    /**
     * Render a simple mesh without bone animation
     *
     * @param mesh The mesh to render
     * @param poseStack Pose stack for transformations
     * @param bufferSource Buffer source for vertex data
     * @param texture Texture resource location
     * @param packedLight Minecraft packed light value
     * @param packedOverlay Minecraft packed overlay value
     */
    public static void render(AssimpMesh mesh, PoseStack poseStack, MultiBufferSource bufferSource,
                              ResourceLocation texture, int packedLight, int packedOverlay) {
        render(mesh, poseStack, bufferSource, texture, packedLight, packedOverlay, null, null);
    }

    /**
     * Render a simple mesh with optional node animation transforms
     *
     * @param mesh The mesh to render
     * @param poseStack Pose stack for transformations
     * @param bufferSource Buffer source for vertex data
     * @param texture Texture resource location
     * @param packedLight Minecraft packed light value
     * @param packedOverlay Minecraft packed overlay value
     * @param scene The scene containing the mesh (used to find node name)
     * @param nodeTransforms Optional map of node name to transform matrix (for node animations)
     */
    public static void render(AssimpMesh mesh, PoseStack poseStack, MultiBufferSource bufferSource,
                              ResourceLocation texture, int packedLight, int packedOverlay,
                              @Nullable AssimpScene scene,
                              @Nullable Map<String, Matrix4f> nodeTransforms) {

        // Get vertex data
        float[] vertices = mesh.getVertices();
        float[] normals = mesh.getNormals();
        float[] texCoords = mesh.getTexCoords();
        int[] indices = mesh.getIndices();

        if (vertices == null || indices == null) {
            DebugManager.warn(DebugCategory.RENDER, "SimpleMeshRenderer: Mesh '{}' has no vertex data", mesh.getName());
            return;
        }

        // Check if we have a node transform for this mesh
        Matrix4f nodeTransform = null;
        if (nodeTransforms != null && !nodeTransforms.isEmpty() && scene != null) {
            // Use scene graph to find the correct node name for this mesh
            String nodeName = scene.getNodeNameForMesh(mesh);

            if (nodeName != null) {
                nodeTransform = nodeTransforms.get(nodeName);
                DebugManager.debug(DebugCategory.RENDER,
                    "SimpleMeshRenderer: Found node '{}' for mesh '{}' (index {})",
                    nodeName, mesh.getName(), mesh.getIndex());
            } else {
                DebugManager.debug(DebugCategory.RENDER,
                    "SimpleMeshRenderer: No node found for mesh '{}' (index {})",
                    mesh.getName(), mesh.getIndex());
            }

            if (nodeTransform == null && !nodeTransforms.isEmpty()) {
                // Fallback: use first available transform (for single-node animations)
                nodeName = nodeTransforms.keySet().iterator().next();
                nodeTransform = nodeTransforms.get(nodeName);
                DebugManager.debug(DebugCategory.RENDER,
                    "SimpleMeshRenderer: Using fallback node '{}' for mesh '{}'",
                    nodeName, mesh.getName());
            }

            if (nodeTransform != null) {
                DebugManager.debug(DebugCategory.RENDER,
                    "SimpleMeshRenderer: Applying node transform to mesh '{}'", mesh.getName());
                // Apply node transform to pose stack
                poseStack.pushPose();
                poseStack.last().pose().mul(nodeTransform);
            }
        }

        // Get material color
        int r = 255, g = 255, b = 255, a = 255;
        if (scene != null && mesh.getMaterialIndex() >= 0 && mesh.getMaterialIndex() < scene.getMaterials().size()) {
            AssimpMaterial material = scene.getMaterials().get(mesh.getMaterialIndex());
            float[] diffuseColor = material.getDiffuseColor();
            if (diffuseColor != null && diffuseColor.length >= 3) {
                r = (int) (diffuseColor[0] * 255);
                g = (int) (diffuseColor[1] * 255);
                b = (int) (diffuseColor[2] * 255);
                if (diffuseColor.length >= 4) {
                    a = (int) (diffuseColor[3] * 255);
                }
            }
        }

        // Debug: Log mesh data (only once per mesh)
        DebugManager.debug(DebugCategory.RENDER,
            "SimpleMeshRenderer: Rendering mesh '{}' - vertices: {}, indices: {}, normals: {}, texCoords: {}, texture: {}, hasNodeTransform: {}, color: ({},{},{})",
            mesh.getName(), vertices.length / 3, indices.length,
            normals != null ? normals.length / 3 : 0,
            texCoords != null ? texCoords.length / 2 : 0,
            texture, nodeTransform != null, r, g, b);

        // Get render type - use entitySolid for opaque meshes
        // entityCutout has alpha testing which might cause issues
        RenderType renderType = RenderType.entitySolid(texture);
        VertexConsumer vertexConsumer = bufferSource.getBuffer(renderType);

        // Get transformation matrices
        Matrix4f pose = poseStack.last().pose();
        Matrix3f normal = poseStack.last().normal();

        int vertexCount = vertices.length / 3;
        boolean hasNormals = normals != null && normals.length == vertices.length;
        boolean hasTexCoords = texCoords != null && texCoords.length == vertexCount * 2;

        // Render triangles with reversed winding order for Minecraft (same as AssimpBlockEntityRenderer)
        // Use degenerate quads: v2, v1, v0, v0
        for (int i = 0; i < indices.length; i += 3) {
            int i0 = indices[i];
            int i1 = indices[i + 1];
            int i2 = indices[i + 2];

            // Reverse winding order: i2, i1, i0, i0 (degenerate quad)
            addVertex(vertexConsumer, pose, normal, vertices, normals, texCoords,
                    i2, hasNormals, hasTexCoords, packedLight, packedOverlay, r, g, b, a);

            addVertex(vertexConsumer, pose, normal, vertices, normals, texCoords,
                    i1, hasNormals, hasTexCoords, packedLight, packedOverlay, r, g, b, a);

            addVertex(vertexConsumer, pose, normal, vertices, normals, texCoords,
                    i0, hasNormals, hasTexCoords, packedLight, packedOverlay, r, g, b, a);

            addVertex(vertexConsumer, pose, normal, vertices, normals, texCoords,
                    i0, hasNormals, hasTexCoords, packedLight, packedOverlay, r, g, b, a);
        }

        DebugManager.debug(DebugCategory.RENDER, "SimpleMeshRenderer: Rendered mesh '{}' with {} triangles",
                mesh.getName(), indices.length / 3);

        // Pop pose stack if we applied a node transform
        if (nodeTransform != null) {
            poseStack.popPose();
        }
    }

    private static void addVertex(VertexConsumer consumer, Matrix4f pose, Matrix3f normal,
                                  float[] vertices, float[] normals, float[] texCoords,
                                  int index, boolean hasNormals, boolean hasTexCoords,
                                  int packedLight, int packedOverlay, int r, int g, int b, int a) {

        // Position
        float x = vertices[index * 3];
        float y = vertices[index * 3 + 1];
        float z = vertices[index * 3 + 2];

        // Normal
        float nx = 0, ny = 1, nz = 0;
        if (hasNormals) {
            nx = normals[index * 3];
            ny = normals[index * 3 + 1];
            nz = normals[index * 3 + 2];
        }

        // Texture coordinates
        float u = 0, v = 0;
        if (hasTexCoords) {
            u = texCoords[index * 2];
            v = texCoords[index * 2 + 1];
        }

        // Add vertex using Minecraft 1.20.1 API with material color
        consumer.vertex(pose, x, y, z)
            .color(r, g, b, a)
            .uv(u, v)
            .overlayCoords(packedOverlay)
            .uv2(packedLight)
            .normal(normal, nx, ny, nz)
            .endVertex();
    }
}
