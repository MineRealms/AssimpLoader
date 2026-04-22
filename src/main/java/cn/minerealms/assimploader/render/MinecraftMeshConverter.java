package cn.minerealms.assimploader.render;

import cn.minerealms.assimploader.AssimpLoader;
import cn.minerealms.assimploader.model.AssimpMaterial;
import cn.minerealms.assimploader.model.AssimpMesh;
import com.mojang.blaze3d.vertex.DefaultVertexFormat;
import com.mojang.blaze3d.vertex.VertexFormat;
import net.minecraft.client.renderer.block.model.BakedQuad;
import net.minecraft.client.renderer.texture.TextureAtlasSprite;
import net.minecraft.core.Direction;
import net.minecraftforge.client.model.pipeline.QuadBakingVertexConsumer;
import org.joml.Vector3f;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.AtomicReference;

/**
 * Converts Assimp mesh data to Minecraft BakedQuad format.
 *
 * Handles:
 * - Vertex position transformation
 * - Normal vector conversion
 * - UV coordinate mapping
 * - Triangle to quad conversion (when possible)
 * - Vertex format adaptation
 */
public class MinecraftMeshConverter {

    /**
     * Convert AssimpMesh to list of BakedQuads for Minecraft rendering
     *
     * @param mesh Assimp mesh data
     * @param material Material for this mesh (can be null)
     * @param sprite Texture sprite (can be null)
     * @return List of BakedQuads ready for rendering
     */
    public static List<BakedQuad> convertToQuads(AssimpMesh mesh, AssimpMaterial material, TextureAtlasSprite sprite) {
        AssimpLoader.LOGGER.debug("convertToQuads - vertices: {}, indices: {}",
            mesh.getVertices() != null ? mesh.getVertices().length / 3 : 0,
            mesh.getIndices() != null ? mesh.getIndices().length : 0);

        List<BakedQuad> quads = new ArrayList<>();

        float[] vertices = mesh.getVertices();
        float[] normals = mesh.getNormals();
        float[] texCoords = mesh.getTexCoords();
        float[] vertexColors = mesh.getVertexColors();
        int[] indices = mesh.getIndices();

        if (vertices == null || indices == null) {
            AssimpLoader.LOGGER.warn("Mesh has null vertices or indices");
            return quads;
        }

        // Get material diffuse color if available
        float[] materialColor = null;
        if (material != null) {
            materialColor = material.getDiffuseColor();
            AssimpLoader.LOGGER.debug("Material color: [{}, {}, {}, {}]",
                materialColor[0], materialColor[1], materialColor[2], materialColor[3]);
        }

        // Process triangles (indices come in groups of 3)
        for (int i = 0; i < indices.length; i += 3) {
            int idx0 = indices[i] * 3;
            int idx1 = indices[i + 1] * 3;
            int idx2 = indices[i + 2] * 3;

            // Get vertex positions
            Vector3f v0 = new Vector3f(vertices[idx0], vertices[idx0 + 1], vertices[idx0 + 2]);
            Vector3f v1 = new Vector3f(vertices[idx1], vertices[idx1 + 1], vertices[idx1 + 2]);
            Vector3f v2 = new Vector3f(vertices[idx2], vertices[idx2 + 1], vertices[idx2 + 2]);

            // Check for degenerate triangles (zero area)
            Vector3f edge1 = new Vector3f(v1).sub(v0);
            Vector3f edge2 = new Vector3f(v2).sub(v0);
            Vector3f cross = new Vector3f(edge1).cross(edge2);
            float area = cross.length();

            if (area < 0.000001f) {
                // Skip degenerate triangles
                AssimpLoader.LOGGER.debug("Skipping degenerate triangle at index {}", i);
                continue;
            }

            // Get normals (or calculate if missing)
            Vector3f n0, n1, n2;
            if (normals != null) {
                n0 = new Vector3f(normals[idx0], normals[idx0 + 1], normals[idx0 + 2]);
                n1 = new Vector3f(normals[idx1], normals[idx1 + 1], normals[idx1 + 2]);
                n2 = new Vector3f(normals[idx2], normals[idx2 + 1], normals[idx2 + 2]);
            } else {
                // Calculate face normal
                Vector3f normal = cross.normalize();
                n0 = n1 = n2 = normal;
            }

            // Get vertex colors (priority: vertex colors > material color > white)
            int color0, color1, color2;
            if (vertexColors != null) {
                int colorIdx0 = indices[i] * 4;
                int colorIdx1 = indices[i + 1] * 4;
                int colorIdx2 = indices[i + 2] * 4;
                color0 = floatColorToInt(vertexColors[colorIdx0], vertexColors[colorIdx0 + 1],
                                        vertexColors[colorIdx0 + 2], vertexColors[colorIdx0 + 3]);
                color1 = floatColorToInt(vertexColors[colorIdx1], vertexColors[colorIdx1 + 1],
                                        vertexColors[colorIdx1 + 2], vertexColors[colorIdx1 + 3]);
                color2 = floatColorToInt(vertexColors[colorIdx2], vertexColors[colorIdx2 + 1],
                                        vertexColors[colorIdx2 + 2], vertexColors[colorIdx2 + 3]);
            } else if (materialColor != null) {
                int matColor = floatColorToInt(materialColor[0], materialColor[1], materialColor[2], materialColor[3]);
                color0 = color1 = color2 = matColor;
            } else {
                color0 = color1 = color2 = 0xFFFFFFFF; // White
            }

            // Get texture coordinates
            float u0, vt0, u1, vt1, u2, vt2;

            // Check if material has actual texture or just color
            boolean hasTexture = material != null && material.hasTexture();

            if (hasTexture && texCoords != null) {
                // Use actual UV coordinates for textured materials
                int uvIdx0 = indices[i] * 2;
                int uvIdx1 = indices[i + 1] * 2;
                int uvIdx2 = indices[i + 2] * 2;

                u0 = texCoords[uvIdx0];
                vt0 = texCoords[uvIdx0 + 1];
                u1 = texCoords[uvIdx1];
                vt1 = texCoords[uvIdx1 + 1];
                u2 = texCoords[uvIdx2];
                vt2 = texCoords[uvIdx2 + 1];
            } else {
                // For pure color materials (no texture), use sprite center to avoid sampling atlas
                // This prevents showing random textures from the atlas
                float centerU = (sprite.getU0() + sprite.getU1()) / 2.0f;
                float centerV = (sprite.getV0() + sprite.getV1()) / 2.0f;
                u0 = u1 = u2 = centerU;
                vt0 = vt1 = vt2 = centerV;
            }

            // Determine face direction from normal
            Direction direction = getDirectionFromNormal(n0);

            // For BakedQuad, we need 4 vertices
            // Since we have a triangle, duplicate the last vertex
            // This creates a degenerate quad (triangle rendered as quad)
            Vector3f v3 = new Vector3f(v2);
            Vector3f n3 = new Vector3f(n2);
            float u3 = u2;
            float vt3 = vt2;
            int color3 = color2;

            BakedQuad quad = createQuad(
                    v0, v1, v2, v3,
                    n0, n1, n2, n3,
                    u0, vt0, u1, vt1, u2, vt2, u3, vt3,
                    color0, color1, color2, color3,
                    sprite,
                    direction
            );

            if (quad != null) {
                quads.add(quad);
            }
        }

        return quads;
    }

    /**
     * Convert float color [0,1] to int ARGB format
     */
    private static int floatColorToInt(float r, float g, float b, float a) {
        int ri = (int) (r * 255.0f) & 0xFF;
        int gi = (int) (g * 255.0f) & 0xFF;
        int bi = (int) (b * 255.0f) & 0xFF;
        int ai = (int) (a * 255.0f) & 0xFF;
        return (ai << 24) | (ri << 16) | (gi << 8) | bi;
    }

    /**
     * Blend two ARGB colors (average)
     */
    private static int blendColors(int color1, int color2) {
        int a1 = (color1 >> 24) & 0xFF;
        int r1 = (color1 >> 16) & 0xFF;
        int g1 = (color1 >> 8) & 0xFF;
        int b1 = color1 & 0xFF;

        int a2 = (color2 >> 24) & 0xFF;
        int r2 = (color2 >> 16) & 0xFF;
        int g2 = (color2 >> 8) & 0xFF;
        int b2 = color2 & 0xFF;

        int a = (a1 + a2) / 2;
        int r = (r1 + r2) / 2;
        int g = (g1 + g2) / 2;
        int b = (b1 + b2) / 2;

        return (a << 24) | (r << 16) | (g << 8) | b;
    }

    /**
     * Create a BakedQuad from vertex data
     */
    private static BakedQuad createQuad(
            Vector3f v0, Vector3f v1, Vector3f v2, Vector3f v3,
            Vector3f n0, Vector3f n1, Vector3f n2, Vector3f n3,
            float u0, float vt0, float u1, float vt1, float u2, float vt2, float u3, float vt3,
            int color0, int color1, int color2, int color3,
            TextureAtlasSprite sprite,
            Direction direction) {

        AtomicReference<BakedQuad> quadRef = new AtomicReference<>();
        QuadBakingVertexConsumer builder = new QuadBakingVertexConsumer(quadRef::set);
        builder.setSprite(sprite);
        builder.setDirection(direction);
        builder.setTintIndex(-1);

        // Vertex 0
        putVertex(builder, v0, n0, u0, vt0, color0);
        // Vertex 1
        putVertex(builder, v1, n1, u1, vt1, color1);
        // Vertex 2
        putVertex(builder, v2, n2, u2, vt2, color2);
        // Vertex 3
        putVertex(builder, v3, n3, u3, vt3, color3);

        return quadRef.get();
    }

    /**
     * Put a single vertex into the builder
     */
    private static void putVertex(QuadBakingVertexConsumer builder, Vector3f pos, Vector3f normal, float u, float v, int color) {
        // Extract ARGB components
        int a = (color >> 24) & 0xFF;
        int r = (color >> 16) & 0xFF;
        int g = (color >> 8) & 0xFF;
        int b = color & 0xFF;

        builder.vertex(pos.x, pos.y, pos.z)
                .color(r, g, b, a)
                .uv(u, v)
                .normal(normal.x, normal.y, normal.z)
                .endVertex();
    }

    /**
     * Determine face direction from normal vector
     */
    private static Direction getDirectionFromNormal(Vector3f normal) {
        float absX = Math.abs(normal.x);
        float absY = Math.abs(normal.y);
        float absZ = Math.abs(normal.z);

        if (absY > absX && absY > absZ) {
            return normal.y > 0 ? Direction.UP : Direction.DOWN;
        } else if (absX > absZ) {
            return normal.x > 0 ? Direction.EAST : Direction.WEST;
        } else {
            return normal.z > 0 ? Direction.SOUTH : Direction.NORTH;
        }
    }
}
