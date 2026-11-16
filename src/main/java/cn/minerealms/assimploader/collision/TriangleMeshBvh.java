package cn.minerealms.assimploader.collision;

import cn.minerealms.assimploader.model.AssimpMesh;
import org.joml.Vector3f;
import java.util.Arrays;
import java.util.Comparator;
import java.util.Optional;

/** Immutable static triangle acceleration structure. Rebuild explicitly for deformed geometry. */
public final class TriangleMeshBvh implements CollisionShape {
    private final float[] vertices;
    private final int[] indices;
    private final Node root;

    public TriangleMeshBvh(AssimpMesh mesh) { this(mesh.getVertices(), mesh.getIndices()); }

    public TriangleMeshBvh(float[] vertices, int[] indices) {
        if (vertices.length % 3 != 0 || indices.length % 3 != 0) throw new IllegalArgumentException("Triangle layout required");
        this.vertices = vertices.clone();
        this.indices = indices.clone();
        for (float value : vertices) if (!Float.isFinite(value)) throw new IllegalArgumentException("Non-finite mesh vertex");
        for (int index : indices) if (index < 0 || index >= vertices.length / 3) throw new IllegalArgumentException("Invalid triangle index");
        Integer[] triangles = new Integer[indices.length / 3];
        for (int i = 0; i < triangles.length; i++) triangles[i] = i;
        root = triangles.length == 0 ? null : build(triangles, 0, triangles.length);
    }

    private Node build(Integer[] triangles, int from, int to) {
        Vector3f minimum = new Vector3f(Float.POSITIVE_INFINITY), maximum = new Vector3f(Float.NEGATIVE_INFINITY);
        for (int i = from; i < to; i++) for (int corner = 0; corner < 3; corner++) {
            Vector3f vertex = vertex(indices[triangles[i] * 3 + corner]);
            minimum.min(vertex); maximum.max(vertex);
        }
        AabbCollider bounds = new AabbCollider(minimum, maximum);
        if (to - from <= 8) {
            int[] leaf = new int[to - from];
            for (int i = from; i < to; i++) leaf[i - from] = triangles[i];
            return new Node(bounds, null, null, leaf);
        }
        Vector3f size = new Vector3f(maximum).sub(minimum);
        int axis = size.x >= size.y && size.x >= size.z ? 0 : size.y >= size.z ? 1 : 2;
        Arrays.sort(triangles, from, to, Comparator.comparingDouble(triangle -> centroid(triangle, axis)));
        int middle = (from + to) >>> 1;
        return new Node(bounds, build(triangles, from, middle), build(triangles, middle, to), null);
    }

    private float centroid(int triangle, int axis) {
        return (vertices[indices[triangle * 3] * 3 + axis] + vertices[indices[triangle * 3 + 1] * 3 + axis]
                + vertices[indices[triangle * 3 + 2] * 3 + axis]) / 3;
    }

    private Vector3f vertex(int index) { return new Vector3f(vertices[index * 3], vertices[index * 3 + 1], vertices[index * 3 + 2]); }

    @Override public Optional<RayHit> raycast(Ray ray, float maximumDistance) {
        Ray.distance(maximumDistance);
        return Optional.ofNullable(root == null ? null : visit(root, ray, maximumDistance));
    }

    private RayHit visit(Node node, Ray ray, float maximum) {
        if (node.bounds.entry(ray, maximum) == Float.POSITIVE_INFINITY) return null;
        RayHit closest = null;
        if (node.triangles != null) {
            for (int triangle : node.triangles) {
                RayHit hit = triangle(ray, maximum, triangle);
                if (hit != null && (closest == null || hit.distance() < maximum || hit.triangle() < closest.triangle())) {
                    closest = hit; maximum = hit.distance();
                }
            }
            return closest;
        }
        Node first = node.left, second = node.right;
        if (second.bounds.entry(ray, maximum) < first.bounds.entry(ray, maximum)) { first = node.right; second = node.left; }
        closest = visit(first, ray, maximum);
        if (closest != null) maximum = closest.distance();
        RayHit other = visit(second, ray, maximum);
        return other != null && (closest == null || other.distance() < closest.distance()
                || other.distance() == closest.distance() && other.triangle() < closest.triangle()) ? other : closest;
    }

    private RayHit triangle(Ray ray, float maximum, int triangle) {
        Vector3f a = vertex(indices[triangle * 3]);
        Vector3f edge1 = vertex(indices[triangle * 3 + 1]).sub(a), edge2 = vertex(indices[triangle * 3 + 2]).sub(a);
        Vector3f cross = ray.direction().cross(edge2);
        float determinant = edge1.dot(cross);
        if (Math.abs(determinant) <= 1.0e-8f) return null;
        float inverse = 1 / determinant;
        Vector3f offset = ray.origin().sub(a);
        float u = offset.dot(cross) * inverse;
        if (u < 0 || u > 1) return null;
        Vector3f q = offset.cross(edge1);
        float v = ray.direction().dot(q) * inverse;
        if (v < 0 || u + v > 1) return null;
        float distance = edge2.dot(q) * inverse;
        if (distance < 0 || distance > maximum) return null;
        Vector3f normal = edge1.cross(edge2).normalize();
        if (normal.dot(ray.direction()) > 0) normal.negate();
        return new RayHit(distance, ray.at(distance), normal, triangle);
    }

    private record Node(AabbCollider bounds, Node left, Node right, int[] triangles) {}
}
