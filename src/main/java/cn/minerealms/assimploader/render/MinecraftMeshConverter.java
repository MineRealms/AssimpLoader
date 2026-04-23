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

        // Calculate bounding box to detect if model needs scaling
        float minX = Float.MAX_VALUE, minY = Float.MAX_VALUE, minZ = Float.MAX_VALUE;
        float maxX = Float.MIN_VALUE, maxY = Float.MIN_VALUE, maxZ = Float.MIN_VALUE;

        for (int i = 0; i < vertices.length; i += 3) {
            minX = Math.min(minX, vertices[i]);
            minY = Math.min(minY, vertices[i + 1]);
            minZ = Math.min(minZ, vertices[i + 2]);
            maxX = Math.max(maxX, vertices[i]);
            maxY = Math.max(maxY, vertices[i + 1]);
            maxZ = Math.max(maxZ, vertices[i + 2]);
        }

        float sizeX = maxX - minX;
        float sizeY = maxY - minY;
        float sizeZ = maxZ - minZ;

        cn.minerealms.assimploader.AssimpLoader.LOGGER.info("Mesh bounding box: ({}, {}, {}) to ({}, {}, {}), size: ({}, {}, {})",
            minX, minY, minZ, maxX, maxY, maxZ, sizeX, sizeY, sizeZ);

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

            // Get texture coordinates
            float u0, vt0, u1, vt1, u2, vt2;
            if (texCoords != null && texCoords.length > 0) {
                // Use actual UV coordinates from mesh
                int uvIdx0 = indices[i] * 2;
                int uvIdx1 = indices[i + 1] * 2;
                int uvIdx2 = indices[i + 2] * 2;

                // Map UV coordinates to sprite atlas coordinates
                u0 = sprite.getU(texCoords[uvIdx0]);
                vt0 = sprite.getV(texCoords[uvIdx0 + 1]);
                u1 = sprite.getU(texCoords[uvIdx1]);
                vt1 = sprite.getV(texCoords[uvIdx1 + 1]);
                u2 = sprite.getU(texCoords[uvIdx2]);
                vt2 = sprite.getV(texCoords[uvIdx2 + 1]);
            } else {
                // Fallback: use sprite center to avoid atlas bleeding
                u0 = sprite.getU0() + (sprite.getU1() - sprite.getU0()) * 0.5f;
                vt0 = sprite.getV0() + (sprite.getV1() - sprite.getV0()) * 0.5f;
                u1 = u0; vt1 = vt0; u2 = u0; vt2 = vt0;
            }

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
        builder.setShade(false);  // CRITICAL: Disable face-based shading for smooth curved surfaces
                                   // We apply lighting via vertex colors and normals instead

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
        // Apply simple diffuse lighting based on normal direction
        // This gives smoother lighting for curved surfaces
        float diffuse = calculateDiffuseLighting(normal);

        int r = (int) (color.x * 255 * diffuse);
        int g = (int) (color.y * 255 * diffuse);
        int b = (int) (color.z * 255 * diffuse);
        int a = (int) (color.w * 255);

        // Clamp to valid range
        r = Math.max(0, Math.min(255, r));
        g = Math.max(0, Math.min(255, g));
        b = Math.max(0, Math.min(255, b));

        // Use default lightmap (0, 0) to let world lighting apply naturally
        // Combined with setShade(false), this gives smooth vertex-based lighting
        builder.vertex(pos.x, pos.y, pos.z)
                .color(r, g, b, a)
                .uv(u, v)
                .uv2(0, 0)  // Use world lighting
                .normal(normal.x, normal.y, normal.z)
                .endVertex();
    }

    /**
     * Calculate diffuse lighting factor based on normal direction
     * Simulates light coming from above and slightly from the front
     */
    private static float calculateDiffuseLighting(Vector3f normal) {
        // Light direction: slightly from above and front (like Minecraft's default lighting)
        Vector3f lightDir = new Vector3f(0.2f, 1.0f, 0.3f);
        lightDir.normalize();

        // Dot product gives us the angle between normal and light
        float dotProduct = normal.dot(lightDir);

        // Remap from [-1, 1] to [0.5, 1.0] for ambient + diffuse
        // 0.5 = minimum ambient light, 1.0 = fully lit
        return 0.5f + (dotProduct * 0.5f);
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
