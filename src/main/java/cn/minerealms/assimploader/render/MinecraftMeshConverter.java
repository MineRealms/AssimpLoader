package cn.minerealms.assimploader.render;

import cn.minerealms.assimploader.model.AssimpMesh;
import cn.minerealms.assimploader.model.AssimpMaterial;
import com.mojang.blaze3d.vertex.DefaultVertexFormat;
import com.mojang.blaze3d.vertex.VertexFormat;
import net.minecraft.client.renderer.block.model.BakedQuad;
import net.minecraft.client.renderer.texture.TextureAtlasSprite;
import net.minecraft.core.Direction;
import net.minecraftforge.client.model.pipeline.QuadBakingVertexConsumer;
import org.joml.Vector3f;
import org.joml.Vector4f;

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
     * @param sprite Texture sprite (can be null)
     * @param material Material for this mesh (for PhongE color)
     * @return List of BakedQuads ready for rendering
     */
    public static List<BakedQuad> convertToQuads(AssimpMesh mesh, TextureAtlasSprite sprite, AssimpMaterial material) {
        List<BakedQuad> quads = new ArrayList<>();

        float[] vertices = mesh.getVertices();
        float[] normals = mesh.getNormals();
        float[] texCoords = mesh.getTexCoords();
        int[] indices = mesh.getIndices();

        if (vertices == null || indices == null) {
            return quads;
        }

        // Get material color (PhongE diffuse color)
        Vector4f materialColor = new Vector4f(1, 1, 1, 1);
        if (material != null && material.getDiffuseColor() != null) {
            float[] diffuse = material.getDiffuseColor();
            materialColor.set(diffuse[0], diffuse[1], diffuse[2], diffuse[3]);
            cn.minerealms.assimploader.AssimpLoader.LOGGER.info("Material color: R={}, G={}, B={}, A={}",
                diffuse[0], diffuse[1], diffuse[2], diffuse[3]);
        } else {
            cn.minerealms.assimploader.AssimpLoader.LOGGER.warn("No material or diffuse color found, using white");
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

            // Get normals (or calculate if missing)
            Vector3f n0, n1, n2;
            if (normals != null) {
                n0 = new Vector3f(normals[idx0], normals[idx0 + 1], normals[idx0 + 2]);
                n1 = new Vector3f(normals[idx1], normals[idx1 + 1], normals[idx1 + 2]);
                n2 = new Vector3f(normals[idx2], normals[idx2 + 1], normals[idx2 + 2]);
            } else {
                // Calculate face normal
                Vector3f edge1 = new Vector3f(v1).sub(v0);
                Vector3f edge2 = new Vector3f(v2).sub(v0);
                Vector3f normal = new Vector3f(edge1).cross(edge2).normalize();
                n0 = n1 = n2 = normal;
            }

            // Get texture coordinates - use sprite center to avoid atlas bleeding
            // Material color is applied via vertex color, not texture
            // We use white_concrete texture, so vertex_color × white = vertex_color
            float u0 = sprite.getU0() + (sprite.getU1() - sprite.getU0()) * 0.5f;
            float vt0 = sprite.getV0() + (sprite.getV1() - sprite.getV0()) * 0.5f;
            float u1 = u0, vt1 = vt0, u2 = u0, vt2 = vt0;

            // Determine face direction from normal
            Direction direction = getDirectionFromNormal(n0);

            // Create quad (triangle as degenerate quad - duplicate last vertex)
            // IMPORTANT: Reverse vertex order (v2, v1, v0) to fix face culling
            // FBX uses clockwise winding, Minecraft uses counter-clockwise
            BakedQuad quad = createQuad(
                    v2, v1, v0, v0, // Reversed order + duplicate v0 for 4th vertex
                    n2, n1, n0, n0, // Reverse normals to match
                    u2, vt2, u1, vt1, u0, vt0, u0, vt0, // Reverse UVs to match
                    sprite,
                    direction,
                    materialColor
            );

            if (quad != null) {
                quads.add(quad);
            }
        }

        return quads;
    }

    /**
     * Create a BakedQuad from vertex data
     */
    private static BakedQuad createQuad(
            Vector3f v0, Vector3f v1, Vector3f v2, Vector3f v3,
            Vector3f n0, Vector3f n1, Vector3f n2, Vector3f n3,
            float u0, float vt0, float u1, float vt1, float u2, float vt2, float u3, float vt3,
            TextureAtlasSprite sprite,
            Direction direction,
            Vector4f color) {

        AtomicReference<BakedQuad> quadRef = new AtomicReference<>();
        QuadBakingVertexConsumer builder = new QuadBakingVertexConsumer(quadRef::set);
        builder.setSprite(sprite);
        builder.setDirection(direction);
        builder.setTintIndex(-1);  // No tint - use vertex color directly
        builder.setShade(true);  // CRITICAL: Enable shading for lighting calculation

        // Vertex 0
        putVertex(builder, v0, n0, u0, vt0, color);
        // Vertex 1
        putVertex(builder, v1, n1, u1, vt1, color);
        // Vertex 2
        putVertex(builder, v2, n2, u2, vt2, color);
        // Vertex 3
        putVertex(builder, v3, n3, u3, vt3, color);

        return quadRef.get();
    }

    /**
     * Put a single vertex into the builder
     */
    private static void putVertex(QuadBakingVertexConsumer builder, Vector3f pos, Vector3f normal, float u, float v, Vector4f color) {
        int r = (int) (color.x * 255);
        int g = (int) (color.y * 255);
        int b = (int) (color.z * 255);
        int a = (int) (color.w * 255);

        builder.vertex(pos.x, pos.y, pos.z)
                .color(r, g, b, a)
                .uv(u, v)
                .uv2(0, 0)  // Lightmap UV - (0,0) means use world lighting
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
