package cn.minerealms.assimploader.render;

import cn.minerealms.assimploader.model.AssimpMesh;
import cn.minerealms.assimploader.model.AssimpNode;
import cn.minerealms.assimploader.model.AssimpScene;
import com.mojang.blaze3d.vertex.PoseStack;
import net.minecraft.world.phys.AABB;
import org.joml.Matrix3f;
import org.joml.Matrix4f;
import org.joml.Vector3f;

import java.util.ArrayList;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Map;

/** Cached scene instances preserve every imported parent transform, including unit scale. */
public final class SceneTransforms {
    public record Instance(AssimpMesh mesh, Matrix4f transform) {}
    private record Entry(List<Instance> instances, AABB bounds, boolean opaque) {}
    private static final Map<AssimpScene, Entry> CACHE = new IdentityHashMap<>();
    private SceneTransforms() {}

    public static List<Instance> instances(AssimpScene scene) { return entry(scene).instances(); }
    public static AABB bounds(AssimpScene scene) { return entry(scene).bounds(); }
    public static boolean isOpaque(AssimpScene scene) { return entry(scene).opaque(); }
    public static void clear() { CACHE.clear(); }

    public static List<Instance> instances(AssimpScene scene, Map<String, Matrix4f> localOverrides) {
        if (localOverrides == null || localOverrides.isEmpty() || !scene.isDynamic()) return instances(scene);
        List<Instance> instances = new ArrayList<>();
        collect(scene, scene.getRootNode(), new Matrix4f(), localOverrides, instances);
        return instances;
    }

    public static void apply(PoseStack stack, Matrix4f transform) {
        stack.last().pose().mul(transform);
        Matrix3f normal = new Matrix3f(transform);
        if (Math.abs(normal.determinant()) > 1.0e-12f) {
            stack.last().normal().mul(normal.invert().transpose());
        }
    }

    private static Entry entry(AssimpScene scene) {
        return CACHE.computeIfAbsent(scene, value -> {
            List<Instance> instances = new ArrayList<>();
            if (scene.isDynamic() && scene.getRootNode() != null) {
                collect(scene, scene.getRootNode(), new Matrix4f(), Map.of(), instances);
            } else {
                for (AssimpMesh mesh : scene.getMeshes()) instances.add(new Instance(mesh, new Matrix4f()));
            }
            double minX = Double.POSITIVE_INFINITY, minY = minX, minZ = minX;
            double maxX = Double.NEGATIVE_INFINITY, maxY = maxX, maxZ = maxX;
            Vector3f point = new Vector3f();
            for (Instance instance : instances) {
                float[] vertices = instance.mesh().getVertices();
                for (int i = 0; i + 2 < vertices.length; i += 3) {
                    instance.transform().transformPosition(point.set(vertices[i], vertices[i + 1], vertices[i + 2]));
                    if (!Float.isFinite(point.x) || !Float.isFinite(point.y) || !Float.isFinite(point.z)) continue;
                    minX = Math.min(minX, point.x); minY = Math.min(minY, point.y); minZ = Math.min(minZ, point.z);
                    maxX = Math.max(maxX, point.x); maxY = Math.max(maxY, point.y); maxZ = Math.max(maxZ, point.z);
                }
            }
            AABB bounds = Double.isFinite(minX) ? new AABB(minX, minY, minZ, maxX, maxY, maxZ)
                    : new AABB(-0.5, 0, -0.5, 0.5, 1, 0.5);
            boolean opaque = scene.getMaterials().stream().noneMatch(material -> material.isTransparent());
            return new Entry(List.copyOf(instances), bounds, opaque);
        });
    }

    private static void collect(AssimpScene scene, AssimpNode node, Matrix4f parent,
                                Map<String, Matrix4f> overrides, List<Instance> instances) {
        if (node == null) return;
        Matrix4f global = new Matrix4f(parent).mul(overrides.getOrDefault(node.getName(), node.getTransformation()));
        for (int index : node.getMeshIndices()) {
            if (index >= 0 && index < scene.getMeshCount()) instances.add(new Instance(scene.getMeshes().get(index), global));
        }
        for (AssimpNode child : node.getChildren()) collect(scene, child, global, overrides, instances);
    }
}
