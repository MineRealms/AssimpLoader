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
import java.util.LinkedHashSet;
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
    private final Map<AssimpMesh, float[]> morphWeights;
    private final List<MeshInstance> meshInstances;
    private final AABB bounds;

    private PoseSnapshot(long frameId, AssimpScene scene, Map<String, Matrix4f> overrides, Map<String, Matrix4f> locals,
                         Map<String, Matrix4f> globals, Map<AssimpMesh, Matrix4f[]> palettes,
                         List<MeshInstance> instances, Map<AssimpMesh, float[]> morphWeights, AABB bounds) {
        this.frameId = frameId;
        this.scene = scene;
        this.localOverrides = overrides;
        this.localTransforms = locals;
        this.globalTransforms = globals;
        this.palettes = palettes;
        this.morphWeights = morphWeights;
        this.meshInstances = List.copyOf(instances);
        this.bounds = bounds;
    }

    public static PoseSnapshot capture(long frameId, AssimpScene scene, Map<String, Matrix4f> localOverrides) {
        return capture(frameId, scene, localOverrides, Map.of());
    }

    public static PoseSnapshot capture(long frameId, AssimpScene scene, Map<String, Matrix4f> localOverrides,
                                       Map<AssimpMesh, float[]> morphOverrides) {
        Objects.requireNonNull(scene, "scene");
        Objects.requireNonNull(morphOverrides, "morphOverrides");
        Map<AssimpMesh, float[]> morphWeights = new IdentityHashMap<>();
        for (AssimpMesh mesh : scene.getMeshes()) {
            float[] weights = morphOverrides.containsKey(mesh)
                    ? Objects.requireNonNull(morphOverrides.get(mesh), "morph weights").clone() : mesh.getDefaultMorphWeights();
            if (weights.length != mesh.getMorphTargets().size()) throw new IllegalArgumentException("Morph weight count mismatch for " + mesh.getName());
            for (float weight : weights) if (!Float.isFinite(weight)) throw new IllegalArgumentException("Non-finite morph weight");
            if (mesh.hasMorphTargets()) morphWeights.put(mesh, weights);
        }
        for (AssimpMesh mesh : morphOverrides.keySet())
            if (!scene.getMeshes().contains(mesh)) throw new IllegalArgumentException("Morph mesh does not belong to scene");
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
        return new PoseSnapshot(frameId, scene, overrides, locals, globals, palettes, instances, morphWeights,
                calculateBounds(scene, instances, palettes, morphWeights));
    }

    public long getFrameId() { return frameId; }
    public AssimpScene getScene() { return scene; }

    /** Rebuild globals, palettes and conservative bounds from interpolated local TRS and Morph. */
    static PoseSnapshot interpolate(long frameId, PoseSnapshot from, PoseSnapshot to, float alpha) {
        if (from.scene != to.scene) throw new IllegalArgumentException("Cannot interpolate different scenes");
        if (!Float.isFinite(alpha) || alpha < 0 || alpha > 1) throw new IllegalArgumentException("Invalid interpolation weight");
        if (alpha == 0) return from;
        if (alpha == 1) return to;
        Map<String, Matrix4f> locals = new LinkedHashMap<>();
        var names = new LinkedHashSet<>(from.localOverrides.keySet());
        names.addAll(to.localOverrides.keySet());
        for (String name : names) {
            Matrix4f a = from.localTransforms.get(name);
            Matrix4f b = to.localTransforms.get(name);
            if (a == null) a = new Matrix4f();
            if (b == null) b = new Matrix4f();
            // Static shear and duplicate bind nodes retain their original representation.
            locals.put(name, a.equals(b) ? new Matrix4f(a)
                    : interpolatable(a) && interpolatable(b) ? AnimationBlender.blendTransforms(a, b, alpha)
                    : new Matrix4f(b));
        }
        Map<AssimpMesh, float[]> morphs = new IdentityHashMap<>();
        to.morphWeights.forEach((mesh, weights) -> {
            float[] source = from.morphWeights.get(mesh);
            float[] mixed = new float[weights.length];
            for (int i = 0; i < mixed.length; i++) mixed[i] = source[i] * (1 - alpha) + weights[i] * alpha;
            morphs.put(mesh, mixed);
        });
        return capture(frameId, to.scene, locals, morphs);
    }

    private static boolean interpolatable(Matrix4f matrix) {
        if (!matrix.isFinite() || matrix.m03() != 0 || matrix.m13() != 0 || matrix.m23() != 0 || matrix.m33() != 1)
            return false;
        Vector3f x = matrix.getColumn(0, new Vector3f());
        Vector3f y = matrix.getColumn(1, new Vector3f());
        Vector3f z = matrix.getColumn(2, new Vector3f());
        float xx = x.lengthSquared(), yy = y.lengthSquared(), zz = z.lengthSquared();
        if (!Float.isFinite(xx) || !Float.isFinite(yy) || !Float.isFinite(zz)
                || xx < 1e-12f || yy < 1e-12f || zz < 1e-12f) return false;
        x.normalize(); y.normalize(); z.normalize();
        return Math.abs(x.dot(y)) < 1e-4f && Math.abs(x.dot(z)) < 1e-4f && Math.abs(y.dot(z)) < 1e-4f;
    }

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

    public float[] getMorphWeights(AssimpMesh mesh) {
        float[] weights = morphWeights.get(mesh);
        return weights == null ? new float[0] : weights.clone();
    }

    public boolean hasActiveMorphs(AssimpMesh mesh) { return active(morphWeights.get(mesh)); }
    public boolean hasActiveMorphs() {
        for (float[] weights : morphWeights.values()) if (active(weights)) return true;
        return false;
    }

    private static boolean active(float[] weights) {
        if (weights != null) for (float weight : weights) if (weight != 0) return true;
        return false;
    }

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
                                        Map<AssimpMesh, Matrix4f[]> palettes, Map<AssimpMesh, float[]> morphWeights) {
        Map<AssimpMesh, MeshBounds> layout;
        synchronized (BOUNDS_CACHE) {
            layout = BOUNDS_CACHE.computeIfAbsent(scene, PoseSnapshot::prepareBounds);
        }
        if (!morphWeights.isEmpty()) {
            layout = new IdentityHashMap<>(layout);
            for (var entry : morphWeights.entrySet()) if (active(entry.getValue())) {
                AssimpMesh mesh = entry.getKey();
                layout.put(mesh, prepareMeshBounds(mesh, MorphDeformation.deform(mesh, entry.getValue()).positions()));
            }
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
            layout.put(mesh, prepareMeshBounds(mesh, mesh.getVertices()));
        }
        return layout;
    }

    private static MeshBounds prepareMeshBounds(AssimpMesh mesh, float[] vertices) {
        BoundsAccumulator all = new BoundsAccumulator();
        BoundsAccumulator unweighted = new BoundsAccumulator();
        BoundsAccumulator[] bones = new BoundsAccumulator[mesh.getBoneOffsets().size()];
        for (int i = 0; i < bones.length; i++) bones[i] = new BoundsAccumulator();
        SkinnedMeshProcessor.SkinnedMeshData skin = mesh.getSkinnedData();
        int[] indices = skin == null ? new int[0] : skin.getBoneIndices();
        float[] weights = skin == null ? new float[0] : skin.getBoneWeights();
        int stride = skin == null || skin.getVertexCount() == 0 ? 0 : indices.length / skin.getVertexCount();
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
        return new MeshBounds(all.toAabb(), boneBoxes, unweighted.toAabb());
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
