package cn.minerealms.assimploader.util;

import cn.minerealms.assimploader.model.AssimpMesh;
import org.joml.Matrix3f;
import org.joml.Matrix4f;
import org.joml.Vector3f;
import org.joml.Vector4f;

/**
 * Vertex transformation pipeline for converting mesh data between coordinate systems.
 *
 * Handles proper transformation of:
 * - Vertex positions (affected by translation, rotation, scale)
 * - Normals (affected by rotation, inverse transpose for non-uniform scale)
 * - Tangents and bitangents (affected by rotation)
 * - Texture coordinates (may need flipping)
 *
 * Key principles:
 * 1. Positions: Transform with full 4x4 matrix (includes translation)
 * 2. Normals: Transform with inverse transpose of 3x3 rotation/scale matrix
 * 3. Tangents: Transform with 3x3 rotation/scale matrix
 * 4. UVs: May need V-flip depending on coordinate system
 *
 * References:
 * - https://www.khronos.org/opengl/wiki/General_OpenGL:_Transformations
 * - https://stackoverflow.com/questions/75207068/unexpected-transformationmatrix-and-offset-matrix-with-skeletal-animation
 */
public class VertexTransformPipeline {

    /**
     * Transform all vertices in a mesh to target coordinate system
     */
    public static void transformMesh(AssimpMesh mesh, Matrix4f transformMatrix) {
        // Extract 3x3 rotation/scale matrix for normals
        Matrix3f normalMatrix = new Matrix3f();
        transformMatrix.get3x3(normalMatrix);
        normalMatrix.invert().transpose(); // Inverse transpose for correct normal transformation

        // Transform positions
        float[] vertices = mesh.getVertices();
        if (vertices != null) {
            transformPositions(vertices, transformMatrix);
        }

        // Transform normals
        float[] normals = mesh.getNormals();
        if (normals != null) {
            transformNormals(normals, normalMatrix);
        }

        // Transform tangents
        float[] tangents = mesh.getTangents();
        if (tangents != null) {
            Matrix3f tangentMatrix = new Matrix3f();
            transformMatrix.get3x3(tangentMatrix);
            transformDirections(tangents, tangentMatrix);
        }

        // Transform bitangents
        float[] bitangents = mesh.getBitangents();
        if (bitangents != null) {
            Matrix3f tangentMatrix = new Matrix3f();
            transformMatrix.get3x3(tangentMatrix);
            transformDirections(bitangents, tangentMatrix);
        }

        // Note: Texture coordinates usually don't need transformation
        // unless there's a specific UV mapping change required
    }

    /**
     * Transform vertex positions with full 4x4 matrix
     */
    private static void transformPositions(float[] positions, Matrix4f matrix) {
        Vector4f vertex = new Vector4f();

        for (int i = 0; i < positions.length; i += 3) {
            vertex.set(positions[i], positions[i + 1], positions[i + 2], 1.0f);
            matrix.transform(vertex);

            positions[i] = vertex.x;
            positions[i + 1] = vertex.y;
            positions[i + 2] = vertex.z;
        }
    }

    /**
     * Transform normals with inverse transpose of 3x3 matrix
     * This ensures normals remain perpendicular to surfaces even with non-uniform scaling
     */
    private static void transformNormals(float[] normals, Matrix3f normalMatrix) {
        Vector3f normal = new Vector3f();

        for (int i = 0; i < normals.length; i += 3) {
            normal.set(normals[i], normals[i + 1], normals[i + 2]);
            normalMatrix.transform(normal);
            normal.normalize(); // Renormalize after transformation

            normals[i] = normal.x;
            normals[i + 1] = normal.y;
            normals[i + 2] = normal.z;
        }
    }

    /**
     * Transform direction vectors (tangents, bitangents) with 3x3 matrix
     */
    private static void transformDirections(float[] directions, Matrix3f matrix) {
        Vector3f direction = new Vector3f();

        for (int i = 0; i < directions.length; i += 3) {
            direction.set(directions[i], directions[i + 1], directions[i + 2]);
            matrix.transform(direction);
            direction.normalize(); // Renormalize after transformation

            directions[i] = direction.x;
            directions[i + 1] = direction.y;
            directions[i + 2] = direction.z;
        }
    }

    /**
     * Flip texture coordinate V axis (for OpenGL vs DirectX conventions)
     */
    public static void flipTextureCoordinatesV(float[] texCoords) {
        if (texCoords == null) return;

        for (int i = 1; i < texCoords.length; i += 2) {
            texCoords[i] = 1.0f - texCoords[i];
        }
    }

    /**
     * Reverse winding order of triangles (for left-handed vs right-handed)
     * This ensures correct face culling after coordinate system conversion
     */
    public static void reverseWindingOrder(int[] indices) {
        if (indices == null) return;

        // Swap every triangle's second and third vertex
        for (int i = 0; i < indices.length; i += 3) {
            int temp = indices[i + 1];
            indices[i + 1] = indices[i + 2];
            indices[i + 2] = temp;
        }
    }

    /**
     * Apply coordinate system conversion to mesh
     */
    public static void convertMeshCoordinateSystem(
            AssimpMesh mesh,
            CoordinateSystemConverter.CoordinateSystem from,
            CoordinateSystemConverter.CoordinateSystem to) {

        Matrix4f conversionMatrix = CoordinateSystemConverter.getConversionMatrix(from, to);
        transformMesh(mesh, conversionMatrix);

        // If converting between left-handed and right-handed, reverse winding order
        if (isHandednessChange(from, to)) {
            reverseWindingOrder(mesh.getIndices());
        }
    }

    /**
     * Check if conversion changes handedness (left ↔ right)
     */
    private static boolean isHandednessChange(
            CoordinateSystemConverter.CoordinateSystem from,
            CoordinateSystemConverter.CoordinateSystem to) {

        boolean fromLeftHanded = (from == CoordinateSystemConverter.CoordinateSystem.MINECRAFT_LEFT_HANDED);
        boolean toLeftHanded = (to == CoordinateSystemConverter.CoordinateSystem.MINECRAFT_LEFT_HANDED);

        return fromLeftHanded != toLeftHanded;
    }

    /**
     * Validate mesh data after transformation
     * Checks for NaN, infinite values, and degenerate triangles
     */
    public static boolean validateMesh(AssimpMesh mesh) {
        float[] vertices = mesh.getVertices();
        if (vertices != null) {
            for (float v : vertices) {
                if (Float.isNaN(v) || Float.isInfinite(v)) {
                    return false;
                }
            }
        }

        float[] normals = mesh.getNormals();
        if (normals != null) {
            for (float n : normals) {
                if (Float.isNaN(n) || Float.isInfinite(n)) {
                    return false;
                }
            }
        }

        return true;
    }

    /**
     * Calculate bounding box after transformation
     */
    public static BoundingBox calculateBoundingBox(float[] vertices) {
        if (vertices == null || vertices.length == 0) {
            return new BoundingBox();
        }

        float minX = Float.POSITIVE_INFINITY;
        float minY = Float.POSITIVE_INFINITY;
        float minZ = Float.POSITIVE_INFINITY;
        float maxX = Float.NEGATIVE_INFINITY;
        float maxY = Float.NEGATIVE_INFINITY;
        float maxZ = Float.NEGATIVE_INFINITY;

        for (int i = 0; i < vertices.length; i += 3) {
            float x = vertices[i];
            float y = vertices[i + 1];
            float z = vertices[i + 2];

            minX = Math.min(minX, x);
            minY = Math.min(minY, y);
            minZ = Math.min(minZ, z);
            maxX = Math.max(maxX, x);
            maxY = Math.max(maxY, y);
            maxZ = Math.max(maxZ, z);
        }

        return new BoundingBox(
                new Vector3f(minX, minY, minZ),
                new Vector3f(maxX, maxY, maxZ)
        );
    }

    /**
     * Simple bounding box class
     */
    public static class BoundingBox {
        public final Vector3f min;
        public final Vector3f max;

        public BoundingBox() {
            this.min = new Vector3f();
            this.max = new Vector3f();
        }

        public BoundingBox(Vector3f min, Vector3f max) {
            this.min = min;
            this.max = max;
        }

        public Vector3f getCenter() {
            return new Vector3f(min).add(max).mul(0.5f);
        }

        public Vector3f getSize() {
            return new Vector3f(max).sub(min);
        }
    }
}
