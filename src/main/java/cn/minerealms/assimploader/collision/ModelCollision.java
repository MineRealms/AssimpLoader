package cn.minerealms.assimploader.collision;

import cn.minerealms.assimploader.animation.PoseSnapshot;
import cn.minerealms.assimploader.model.AssimpMesh;
import cn.minerealms.assimploader.rendering.CpuSkinning;
import org.joml.Matrix3f;
import org.joml.Matrix4f;
import org.joml.Vector3f;

import java.util.IdentityHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.WeakHashMap;

/** Explicit query cost: bounds, bone proxies, or exact current-pose triangles with snapshot caching. */
public final class ModelCollision {
    public enum Mode { BOUNDS, BONE, MESH }
    private final List<BoneCollisionProxy> proxies;
    private final Map<AssimpMesh, TriangleMeshBvh> staticMeshes = new IdentityHashMap<>();
    private final Map<PoseSnapshot, Map<AssimpMesh, TriangleMeshBvh>> deformedMeshes = new WeakHashMap<>();

    public ModelCollision(List<BoneCollisionProxy> proxies) { this.proxies = List.copyOf(proxies); }

    /** Ray and distances are model-space. Caller transforms world input at its authoritative tick. */
    public Optional<RayHit> raycast(PoseSnapshot snapshot, Ray ray, float maximumDistance, Mode mode) {
        Ray.distance(maximumDistance);
        var bounds = snapshot.getBounds();
        AabbCollider box = new AabbCollider(new Vector3f((float)bounds.minX,(float)bounds.minY,(float)bounds.minZ),
                new Vector3f((float)bounds.maxX,(float)bounds.maxY,(float)bounds.maxZ));
        if (mode == Mode.BOUNDS) return box.raycast(ray, maximumDistance);
        if (mode == Mode.MESH && box.entry(ray,maximumDistance) == Float.POSITIVE_INFINITY) return Optional.empty();
        RayHit closest = null;
        if (mode == Mode.BONE) {
            for (BoneCollisionProxy proxy : proxies) {
                RayHit hit = proxy.at(snapshot).raycast(ray,maximumDistance).orElse(null);
                if (hit != null && (closest == null || hit.distance() < closest.distance())) { closest=hit; maximumDistance=hit.distance(); }
            }
        } else {
            for (PoseSnapshot.MeshInstance instance : snapshot.copyMeshInstances()) {
                AssimpMesh mesh = instance.mesh();
                boolean deformed = mesh.hasBones() || snapshot.hasActiveMorphs(mesh);
                TriangleMeshBvh bvh = deformed ? deformedMeshes.computeIfAbsent(snapshot, ignored -> new IdentityHashMap<>())
                        .computeIfAbsent(mesh, value -> new TriangleMeshBvh(CpuSkinning.geometry(snapshot,value).positions(),value.getIndices()))
                        : staticMeshes.computeIfAbsent(mesh,TriangleMeshBvh::new);
                Matrix4f transform = mesh.hasBones() ? new Matrix4f() : instance.transform();
                RayHit hit = transformed(bvh, transform, ray, maximumDistance);
                if (hit != null && (closest == null || hit.distance() < closest.distance())) { closest=hit; maximumDistance=hit.distance(); }
            }
        }
        return Optional.ofNullable(closest);
    }

    private static RayHit transformed(CollisionShape shape, Matrix4f transform, Ray ray, float maximum) {
        if (Math.abs(transform.determinant()) < 1e-10f) return null;
        Matrix4f inverse = new Matrix4f(transform).invert();
        Vector3f direction = inverse.transformDirection(ray.direction());
        float scale = direction.length();
        if (!Float.isFinite(scale) || scale < 1e-10f) return null;
        RayHit hit = shape.raycast(new Ray(inverse.transformPosition(ray.origin()),direction),maximum*scale).orElse(null);
        if (hit == null) return null;
        return new RayHit(hit.distance()/scale,transform.transformPosition(hit.position()),
                new Matrix3f(inverse).transpose().transform(hit.normal()).normalize(),hit.triangle());
    }

    public void clear() { staticMeshes.clear(); deformedMeshes.clear(); }
}
