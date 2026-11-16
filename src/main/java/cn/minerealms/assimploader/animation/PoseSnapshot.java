package cn.minerealms.assimploader.animation;

import cn.minerealms.assimploader.model.AssimpMesh;
import cn.minerealms.assimploader.model.AssimpNode;
import cn.minerealms.assimploader.model.AssimpScene;
import net.minecraft.world.phys.AABB;
import org.joml.Matrix4f;
import org.joml.Matrix4fc;
import org.joml.Vector3f;

import java.util.ArrayList;
import java.util.Collections;
import java.util.IdentityHashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.WeakHashMap;

/** A complete pose whose matrices cannot be changed by its producer or consumers. */
public final class PoseSnapshot {
    private static final AABB EMPTY_BOUNDS = new AABB(-0.5, 0, -0.5, 0.5, 1, 0.5);
    private static final Map<AssimpScene, Map<AssimpMesh, MeshBounds>> BOUNDS_CACHE = new WeakHashMap<>();

    private final long frameId;
    private final AssimpScene scene;
    private final Map<String, Matrix4f> localOverrides;
    private final Map<String, Matrix4f> localTransforms;
    private final Map<String, Matrix4f> globalTransforms;
    private final Map<AssimpMesh, Matrix4f[]> palettes;
    private final List<MeshInstance> meshInstances;
    private final AABB bounds;

    private PoseSnapshot(long frameId, AssimpScene scene, Map<String, Matrix4f> overrides, Map<String, Matrix4f> locals,
                         Map<String, Matrix4f> globals, Map<AssimpMesh, Matrix4f[]> palettes,
                         List<MeshInstance> instances, AABB bounds) {
        this.frameId = frameId;
        this.scene = scene;
        this.localOverrides = overrides;
        this.localTransforms = locals;
        this.globalTransforms = globals;
        this.palettes = palettes;
        this.meshInstances = List.copyOf(instances);
        this.bounds = bounds;
    }

    public static PoseSnapshot capture(long frameId, AssimpScene scene, Map<String, Matrix4f> localOverrides) {
        Objects.requireNonNull(scene, "scene");
        Map<String, Matrix4f> overrides = copyMatrices(Objects.requireNonNull(localOverrides, "localOverrides"));
        Map<String, Matrix4f> locals = new LinkedHashMap<>(overrides);
        Map<String, Matrix4f> globals = new LinkedHashMap<>();
        List<MeshInstance> instances = new ArrayList<>();
        collect(scene, scene.getRootNode(), new Matrix4f(), overrides, locals, globals, instances);
        if (!scene.isDynamic() || scene.getRootNode() == null) {
            instances.clear();
            for (AssimpMesh mesh : scene.getMeshes()) instances.add(new MeshInstance(mesh, new Matrix4f()));
        }

        Map<AssimpMesh, Matrix4f[]> palettes = new IdentityHashMap<>();
        for (AssimpMesh mesh : scene.getMeshes()) {
            if (!mesh.hasBones()) continue;
            Matrix4f[] palette = new Matrix4f[mesh.getBoneOffsets().size()];
            int index = 0;
            for (var bone : mesh.getBoneOffsets().entrySet()) {
                // Some importers supply weighted bones outside the node tree.
                Matrix4f global = globals.get(bone.getKey());
                if (global == null) global = locals.get(bone.getKey());
                palette[index++] = (global == null ? new Matrix4f() : new Matrix4f(global)).mul(bone.getValue());
            }
            palettes.put(mesh, palette);
        }
        return new PoseSnapshot(frameId, scene, overrides, locals, globals, palettes, instances,
                calculateBounds(scene, instances, palettes));
    }

    public long getFrameId() { return frameId; }
    public AssimpScene getScene() { return scene; }

    public Map<String, Matrix4f> copyLocalTransforms() {
        return Collections.unmodifiableMap(copyMatrices(localTransforms));
    }

    public Map<String, Matrix4f> copyLocalOverrides() {
        return Collections.unmodifiableMap(copyMatrices(localOverrides));
    }

    /** Instances preserve distinct transforms even when imported node names repeat. */
    public List<MeshInstance> copyMeshInstances() { return meshInstances; }

    public Matrix4f getNodeTransform(String name) {
        Matrix4f transform = globalTransforms.get(name);
        return transform == null ? null : new Matrix4f(transform);
    }

    public Matrix4f getAttachmentTransform(String name, Matrix4fc localOffset) {
        Matrix4f transform = getNodeTransform(name);
        return transform == null ? null : transform.mul(Objects.requireNonNull(localOffset, "localOffset"));
    }

    public Matrix4f[] getBoneTransforms(AssimpMesh mesh) {
        Matrix4f[] palette = palettes.get(mesh);
        if (palette == null) return null;
        Matrix4f[] copy = new Matrix4f[palette.length];
        for (int i = 0; i < palette.length; i++) copy[i] = new Matrix4f(palette[i]);
        return copy;
    }

    public int getBoneCount(AssimpMesh mesh) {
        Matrix4f[] palette = palettes.get(mesh);
        return palette == null ? 0 : palette.length;
    }

    public AABB getBounds() { return bounds; }

    private static Map<String, Matrix4f> copyMatrices(Map<String, Matrix4f> matrices) {
        Map<String, Matrix4f> copy = new LinkedHashMap<>();
        matrices.forEach((name, matrix) -> copy.put(name, new Matrix4f(Objects.requireNonNull(matrix, "transform"))));
        return copy;
    }

    private static void collect(AssimpScene scene, AssimpNode node, Matrix4f parent,
                                Map<String, Matrix4f> overrides, Map<String, Matrix4f> locals, Map<String, Matrix4f> globals,
                                List<MeshInstance> instances) {
        if (node == null) return;
        Matrix4f local = new Matrix4f(overrides.getOrDefault(node.getName(), node.getTransformation()));
        locals.putIfAbsent(node.getName(), local);
        Matrix4f global = new Matrix4f(parent).mul(local);
        globals.putIfAbsent(node.getName(), global);
        for (int index : node.getMeshIndices()) {
            if (index >= 0 && index < scene.getMeshCount()) instances.add(new MeshInstance(scene.getMeshes().get(index), global));
        }
        for (AssimpNode child : node.getChildren()) collect(scene, child, global, overrides, locals, globals, instances);
    }

    private static AABB calculateBounds(AssimpScene scene, List<MeshInstance> instances,
                                        Map<AssimpMesh, Matrix4f[]> palettes) {
        Map<AssimpMesh, MeshBounds> layout;
        synchronized (BOUNDS_CACHE) {
            layout = BOUNDS_CACHE.computeIfAbsent(scene, PoseSnapshot::prepareBounds);
        }
        BoundsAccumulator result = new BoundsAccumulator();
        Map<AssimpMesh, Boolean> visitedSkinned = new IdentityHashMap<>();
        for (MeshInstance instance : instances) {
            MeshBounds mesh = layout.get(instance.mesh());
            Matrix4f[] palette = palettes.get(instance.mesh());
            if (!scene.isDynamic() || palette == null) {
                result.include(mesh.all(), instance.transform);
            } else if (visitedSkinned.put(instance.mesh(), Boolean.TRUE) == null) {
                // A normalized blend is inside the union of its transformed influence boxes.
                for (int i = 0; i < palette.length; i++) result.include(mesh.bones()[i], palette[i]);
                // CPU and GPU skinning use identity for vertices with no valid influence.
                result.include(mesh.unweighted(), new Matrix4f());
            }
        }
        AABB bounds = result.toAabb();
        return bounds == null ? EMPTY_BOUNDS : bounds;
    }

    private static Map<AssimpMesh, MeshBounds> prepareBounds(AssimpScene scene) {
        // Values retain mesh data only, so the weak scene keys remain collectible.
        Map<AssimpMesh, MeshBounds> layout = new IdentityHashMap<>();
        for (AssimpMesh mesh : scene.getMeshes()) {
            BoundsAccumulator all = new BoundsAccumulator();
            BoundsAccumulator unweighted = new BoundsAccumulator();
            BoundsAccumulator[] bones = new BoundsAccumulator[mesh.getBoneOffsets().size()];
            for (int i = 0; i < bones.length; i++) bones[i] = new BoundsAccumulator();
            SkinnedMeshProcessor.SkinnedMeshData skin = mesh.getSkinnedData();
            int[] indices = skin == null ? new int[0] : skin.getBoneIndices();
            float[] weights = skin == null ? new float[0] : skin.getBoneWeights();
            int stride = skin == null || skin.getVertexCount() == 0 ? 0 : indices.length / skin.getVertexCount();
            float[] vertices = mesh.getVertices();
            for (int vertex = 0; vertex < mesh.getVertexCount(); vertex++) {
                int position = vertex * 3;
                float x = vertices[position], y = vertices[position + 1], z = vertices[position + 2];
                all.include(x, y, z);
                float total = 0;
                for (int influence = 0; influence < stride; influence++) {
                    int slot = vertex * stride + influence;
                    if (slot >= indices.length || slot >= weights.length) break;
                    int bone = indices[slot];
                    float weight = weights[slot];
                    if (bone < 0 || bone >= bones.length || !Float.isFinite(weight) || weight <= 0) continue;
                    bones[bone].include(x, y, z);
                    total += weight;
                }
                if (total <= 1.0e-8f) unweighted.include(x, y, z);
            }
            AABB[] boneBoxes = new AABB[bones.length];
            for (int i = 0; i < bones.length; i++) boneBoxes[i] = bones[i].toAabb();
            layout.put(mesh, new MeshBounds(all.toAabb(), boneBoxes, unweighted.toAabb()));
        }
        return layout;
    }

    public record MeshInstance(AssimpMesh mesh, Matrix4f transform) {
        public MeshInstance {
            Objects.requireNonNull(mesh, "mesh");
            transform = new Matrix4f(Objects.requireNonNull(transform, "transform"));
        }

        @Override
        public Matrix4f transform() { return new Matrix4f(transform); }
    }
    private record MeshBounds(AABB all, AABB[] bones, AABB unweighted) {}

    private static final class BoundsAccumulator {
        private double minX = Double.POSITIVE_INFINITY, minY = minX, minZ = minX;
        private double maxX = Double.NEGATIVE_INFINITY, maxY = maxX, maxZ = maxX;

        private void include(float x, float y, float z) {
            if (!Float.isFinite(x) || !Float.isFinite(y) || !Float.isFinite(z)) return;
            minX = Math.min(minX, x); minY = Math.min(minY, y); minZ = Math.min(minZ, z);
            maxX = Math.max(maxX, x); maxY = Math.max(maxY, y); maxZ = Math.max(maxZ, z);
        }

        private void include(AABB box, Matrix4f transform) {
            if (box == null) return;
            Vector3f point = new Vector3f();
            for (int corner = 0; corner < 8; corner++) {
                point.set((float) ((corner & 1) == 0 ? box.minX : box.maxX),
                        (float) ((corner & 2) == 0 ? box.minY : box.maxY),
                        (float) ((corner & 4) == 0 ? box.minZ : box.maxZ));
                transform.transformPosition(point);
                include(point.x, point.y, point.z);
            }
        }

        private AABB toAabb() {
            return Double.isFinite(minX) ? new AABB(minX, minY, minZ, maxX, maxY, maxZ) : null;
        }
    }
}
