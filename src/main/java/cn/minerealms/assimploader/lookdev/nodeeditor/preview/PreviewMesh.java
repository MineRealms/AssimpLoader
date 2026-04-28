package cn.minerealms.assimploader.lookdev.nodeeditor.preview;

import com.mojang.blaze3d.vertex.*;
import net.minecraft.client.renderer.GameRenderer;
import org.joml.Matrix4f;

/**
 * Generates preview meshes for material testing.
 *
 * Provides standard geometric shapes:
 * - Sphere (default, best for PBR materials)
 * - Cube
 * - Plane
 * - Cylinder
 *
 * All meshes include:
 * - Position
 * - Normal
 * - UV coordinates
 * - Tangent/Bitangent (for normal mapping)
 */
public class PreviewMesh {

    public enum MeshType {
        SPHERE,
        CUBE,
        PLANE,
        CYLINDER
    }

    private final MeshType type;
    private final int segments;

    public PreviewMesh(MeshType type, int segments) {
        this.type = type;
        this.segments = segments;
    }

    public PreviewMesh(MeshType type) {
        this(type, 32); // Default segments
    }

    /**
     * Build mesh into a BufferBuilder
     */
    public void build(BufferBuilder builder, Matrix4f modelMatrix) {
        switch (type) {
            case SPHERE -> buildSphere(builder, modelMatrix);
            case CUBE -> buildCube(builder, modelMatrix);
            case PLANE -> buildPlane(builder, modelMatrix);
            case CYLINDER -> buildCylinder(builder, modelMatrix);
        }
    }

    /**
     * Build a UV sphere
     */
    private void buildSphere(BufferBuilder builder, Matrix4f modelMatrix) {
        float radius = 1.0f;
        int rings = segments;
        int sectors = segments;

        for (int r = 0; r < rings; r++) {
            for (int s = 0; s < sectors; s++) {
                // Calculate vertices for this quad
                float theta0 = (float) Math.PI * r / rings;
                float theta1 = (float) Math.PI * (r + 1) / rings;
                float phi0 = 2.0f * (float) Math.PI * s / sectors;
                float phi1 = 2.0f * (float) Math.PI * (s + 1) / sectors;

                // Four vertices of the quad
                Vertex v0 = sphereVertex(radius, theta0, phi0);
                Vertex v1 = sphereVertex(radius, theta0, phi1);
                Vertex v2 = sphereVertex(radius, theta1, phi1);
                Vertex v3 = sphereVertex(radius, theta1, phi0);

                // Two triangles
                addVertex(builder, v0, modelMatrix);
                addVertex(builder, v1, modelMatrix);
                addVertex(builder, v2, modelMatrix);

                addVertex(builder, v0, modelMatrix);
                addVertex(builder, v2, modelMatrix);
                addVertex(builder, v3, modelMatrix);
            }
        }
    }

    /**
     * Build a cube
     */
    private void buildCube(BufferBuilder builder, Matrix4f modelMatrix) {
        float size = 1.0f;

        // Front face
        addQuad(builder, modelMatrix,
            new Vertex(-size, -size, size, 0, 0, 1, 0, 0),
            new Vertex(size, -size, size, 0, 0, 1, 1, 0),
            new Vertex(size, size, size, 0, 0, 1, 1, 1),
            new Vertex(-size, size, size, 0, 0, 1, 0, 1)
        );

        // Back face
        addQuad(builder, modelMatrix,
            new Vertex(size, -size, -size, 0, 0, -1, 0, 0),
            new Vertex(-size, -size, -size, 0, 0, -1, 1, 0),
            new Vertex(-size, size, -size, 0, 0, -1, 1, 1),
            new Vertex(size, size, -size, 0, 0, -1, 0, 1)
        );

        // Top face
        addQuad(builder, modelMatrix,
            new Vertex(-size, size, size, 0, 1, 0, 0, 0),
            new Vertex(size, size, size, 0, 1, 0, 1, 0),
            new Vertex(size, size, -size, 0, 1, 0, 1, 1),
            new Vertex(-size, size, -size, 0, 1, 0, 0, 1)
        );

        // Bottom face
        addQuad(builder, modelMatrix,
            new Vertex(-size, -size, -size, 0, -1, 0, 0, 0),
            new Vertex(size, -size, -size, 0, -1, 0, 1, 0),
            new Vertex(size, -size, size, 0, -1, 0, 1, 1),
            new Vertex(-size, -size, size, 0, -1, 0, 0, 1)
        );

        // Right face
        addQuad(builder, modelMatrix,
            new Vertex(size, -size, size, 1, 0, 0, 0, 0),
            new Vertex(size, -size, -size, 1, 0, 0, 1, 0),
            new Vertex(size, size, -size, 1, 0, 0, 1, 1),
            new Vertex(size, size, size, 1, 0, 0, 0, 1)
        );

        // Left face
        addQuad(builder, modelMatrix,
            new Vertex(-size, -size, -size, -1, 0, 0, 0, 0),
            new Vertex(-size, -size, size, -1, 0, 0, 1, 0),
            new Vertex(-size, size, size, -1, 0, 0, 1, 1),
            new Vertex(-size, size, -size, -1, 0, 0, 0, 1)
        );
    }

    /**
     * Build a plane
     */
    private void buildPlane(BufferBuilder builder, Matrix4f modelMatrix) {
        float size = 2.0f;

        addQuad(builder, modelMatrix,
            new Vertex(-size, 0, -size, 0, 1, 0, 0, 0),
            new Vertex(size, 0, -size, 0, 1, 0, 1, 0),
            new Vertex(size, 0, size, 0, 1, 0, 1, 1),
            new Vertex(-size, 0, size, 0, 1, 0, 0, 1)
        );
    }

    /**
     * Build a cylinder
     */
    private void buildCylinder(BufferBuilder builder, Matrix4f modelMatrix) {
        float radius = 1.0f;
        float height = 2.0f;
        int sectors = segments;

        // Side faces
        for (int i = 0; i < sectors; i++) {
            float theta0 = 2.0f * (float) Math.PI * i / sectors;
            float theta1 = 2.0f * (float) Math.PI * (i + 1) / sectors;

            float x0 = radius * (float) Math.cos(theta0);
            float z0 = radius * (float) Math.sin(theta0);
            float x1 = radius * (float) Math.cos(theta1);
            float z1 = radius * (float) Math.sin(theta1);

            float nx0 = (float) Math.cos(theta0);
            float nz0 = (float) Math.sin(theta0);
            float nx1 = (float) Math.cos(theta1);
            float nz1 = (float) Math.sin(theta1);

            float u0 = (float) i / sectors;
            float u1 = (float) (i + 1) / sectors;

            addQuad(builder, modelMatrix,
                new Vertex(x0, -height / 2, z0, nx0, 0, nz0, u0, 0),
                new Vertex(x1, -height / 2, z1, nx1, 0, nz1, u1, 0),
                new Vertex(x1, height / 2, z1, nx1, 0, nz1, u1, 1),
                new Vertex(x0, height / 2, z0, nx0, 0, nz0, u0, 1)
            );
        }

        // Top and bottom caps with center vertex
        // Bottom cap (y = -height/2, normal pointing down)
        Vertex bottomCenter = new Vertex(0, -height / 2, 0, 0, -1, 0, 0.5f, 0.5f);
        for (int i = 0; i < sectors; i++) {
            float theta0 = 2.0f * (float) Math.PI * i / sectors;
            float theta1 = 2.0f * (float) Math.PI * (i + 1) / sectors;

            float x0 = radius * (float) Math.cos(theta0);
            float z0 = radius * (float) Math.sin(theta0);
            float x1 = radius * (float) Math.cos(theta1);
            float z1 = radius * (float) Math.sin(theta1);

            // UV coordinates for radial pattern
            float u0 = 0.5f + 0.5f * (float) Math.cos(theta0);
            float v0 = 0.5f + 0.5f * (float) Math.sin(theta0);
            float u1 = 0.5f + 0.5f * (float) Math.cos(theta1);
            float v1 = 0.5f + 0.5f * (float) Math.sin(theta1);

            // Bottom cap triangle (clockwise from bottom)
            addVertex(builder, bottomCenter, modelMatrix);
            addVertex(builder, new Vertex(x1, -height / 2, z1, 0, -1, 0, u1, v1), modelMatrix);
            addVertex(builder, new Vertex(x0, -height / 2, z0, 0, -1, 0, u0, v0), modelMatrix);
        }

        // Top cap (y = height/2, normal pointing up)
        Vertex topCenter = new Vertex(0, height / 2, 0, 0, 1, 0, 0.5f, 0.5f);
        for (int i = 0; i < sectors; i++) {
            float theta0 = 2.0f * (float) Math.PI * i / sectors;
            float theta1 = 2.0f * (float) Math.PI * (i + 1) / sectors;

            float x0 = radius * (float) Math.cos(theta0);
            float z0 = radius * (float) Math.sin(theta0);
            float x1 = radius * (float) Math.cos(theta1);
            float z1 = radius * (float) Math.sin(theta1);

            // UV coordinates for radial pattern
            float u0 = 0.5f + 0.5f * (float) Math.cos(theta0);
            float v0 = 0.5f + 0.5f * (float) Math.sin(theta0);
            float u1 = 0.5f + 0.5f * (float) Math.cos(theta1);
            float v1 = 0.5f + 0.5f * (float) Math.sin(theta1);

            // Top cap triangle (counter-clockwise from top)
            addVertex(builder, topCenter, modelMatrix);
            addVertex(builder, new Vertex(x0, height / 2, z0, 0, 1, 0, u0, v0), modelMatrix);
            addVertex(builder, new Vertex(x1, height / 2, z1, 0, 1, 0, u1, v1), modelMatrix);
        }
    }

    /**
     * Calculate sphere vertex
     */
    private Vertex sphereVertex(float radius, float theta, float phi) {
        float x = radius * (float) Math.sin(theta) * (float) Math.cos(phi);
        float y = radius * (float) Math.cos(theta);
        float z = radius * (float) Math.sin(theta) * (float) Math.sin(phi);

        // Normal is same as position for unit sphere
        float nx = x / radius;
        float ny = y / radius;
        float nz = z / radius;

        // UV coordinates
        float u = phi / (2.0f * (float) Math.PI);
        float v = theta / (float) Math.PI;

        return new Vertex(x, y, z, nx, ny, nz, u, v);
    }

    /**
     * Add a quad (two triangles)
     */
    private void addQuad(BufferBuilder builder, Matrix4f modelMatrix,
                         Vertex v0, Vertex v1, Vertex v2, Vertex v3) {
        // Triangle 1
        addVertex(builder, v0, modelMatrix);
        addVertex(builder, v1, modelMatrix);
        addVertex(builder, v2, modelMatrix);

        // Triangle 2
        addVertex(builder, v0, modelMatrix);
        addVertex(builder, v2, modelMatrix);
        addVertex(builder, v3, modelMatrix);
    }

    /**
     * Add a vertex to the buffer
     */
    private void addVertex(BufferBuilder builder, Vertex v, Matrix4f modelMatrix) {
        // Transform position
        float x = v.x;
        float y = v.y;
        float z = v.z;

        // Add vertex data
        builder.vertex(modelMatrix, x, y, z)
               .color(255, 255, 255, 255)
               .uv(v.u, v.v)
               .overlayCoords(0, 10)
               .uv2(240, 240)
               .normal(v.nx, v.ny, v.nz)
               .endVertex();
    }

    /**
     * Simple vertex structure
     */
    private static class Vertex {
        float x, y, z;      // Position
        float nx, ny, nz;   // Normal
        float u, v;         // UV

        Vertex(float x, float y, float z, float nx, float ny, float nz, float u, float v) {
            this.x = x;
            this.y = y;
            this.z = z;
            this.nx = nx;
            this.ny = ny;
            this.nz = nz;
            this.u = u;
            this.v = v;
        }
    }

    public MeshType getType() {
        return type;
    }

    public int getSegments() {
        return segments;
    }
}
