package cn.minerealms.assimploader.lookdev.preview;

import cn.minerealms.assimploader.model.AssimpMesh;
import cn.minerealms.assimploader.model.AssimpNode;
import cn.minerealms.assimploader.model.AssimpScene;
import org.joml.Matrix4f;
import org.joml.Vector3f;

import java.util.ArrayList;
import java.util.List;

/** Immutable draw plan built once when a scene is selected, including repeated mesh instances. */
public final class PreviewSceneData {
    public record Draw(int meshIndex, Matrix4f transform) {}
    private final List<Draw> draws;
    private final Vector3f center;
    private final Vector3f size;
    private final float radius;
    private final long triangles;

    public PreviewSceneData(AssimpScene scene) {
        List<Draw> plan = new ArrayList<>();
        if (scene.isDynamic() && scene.getRootNode() != null) {
            collect(scene.getRootNode(), new Matrix4f(), scene.getMeshCount(), plan);
        } else {
            for (int i = 0; i < scene.getMeshCount(); i++) plan.add(new Draw(i, new Matrix4f()));
        }
        draws = List.copyOf(plan);
        Vector3f min = new Vector3f(Float.POSITIVE_INFINITY);
        Vector3f max = new Vector3f(Float.NEGATIVE_INFINITY);
        Vector3f point = new Vector3f();
        long triangleCount = 0;
        for (Draw draw : draws) {
            AssimpMesh mesh = scene.getMeshes().get(draw.meshIndex());
            triangleCount += mesh.getIndices().length / 3;
            float[] vertices = mesh.getVertices();
            for (int i = 0; i + 2 < vertices.length; i += 3) {
                draw.transform().transformPosition(point.set(vertices[i], vertices[i + 1], vertices[i + 2]));
                if (point.isFinite()) {
                    min.min(point);
                    max.max(point);
                }
            }
        }
        if (!min.isFinite() || !max.isFinite()) {
            min.set(-0.5f);
            max.set(0.5f);
        }
        center = new Vector3f(min).lerp(max, 0.5f);
        size = new Vector3f(max).sub(min);
        radius = Math.max(size.length() * 0.5f, 0.00001f);
        triangles = triangleCount;
    }

    private static void collect(AssimpNode node, Matrix4f parent, int count, List<Draw> target) {
        Matrix4f transform = new Matrix4f(parent).mul(node.getTransformation());
        for (int index : node.getMeshIndices()) {
            if (index >= 0 && index < count) target.add(new Draw(index, new Matrix4f(transform)));
        }
        for (AssimpNode child : node.getChildren()) collect(child, transform, count, target);
    }

    public List<Draw> draws() { return draws; }
    public Vector3f center() { return new Vector3f(center); }
    public Vector3f size() { return new Vector3f(size); }
    public float radius() { return radius; }
    public long triangles() { return triangles; }
}
