package cn.minerealms.assimploader.collision;

import org.joml.Matrix4f;
import org.joml.Matrix4fc;
import org.joml.Vector3f;
import org.joml.Vector3fc;
import java.util.Optional;

/** A local AABB transformed by an invertible affine matrix, including nonuniform scale. */
public final class ObbCollider implements CollisionShape {
    private final AabbCollider box;
    private final Matrix4f transform, inverse;

    public ObbCollider(Vector3fc minimum, Vector3fc maximum, Matrix4fc transform) {
        box = new AabbCollider(minimum, maximum);
        this.transform = new Matrix4f(transform);
        if (!this.transform.isFinite() || Math.abs(this.transform.determinant()) < 1.0e-12f
                || this.transform.m03() != 0 || this.transform.m13() != 0 || this.transform.m23() != 0 || this.transform.m33() != 1)
            throw new IllegalArgumentException("OBB requires an invertible affine transform");
        inverse = new Matrix4f(transform).invert();
    }

    @Override public Optional<RayHit> raycast(Ray ray, float maximumDistance) {
        Ray.distance(maximumDistance);
        Vector3f localDirection = inverse.transformDirection(ray.direction());
        float scale = localDirection.length();
        Ray local = new Ray(inverse.transformPosition(ray.origin()), localDirection);
        float localMaximum = (float) Math.min(Float.MAX_VALUE, (double) maximumDistance * scale);
        return box.raycast(local, localMaximum).map(hit -> {
            Vector3f normal = new org.joml.Matrix3f(inverse).transpose().transform(hit.normal()).normalize();
            return new RayHit(hit.distance() / scale, transform.transformPosition(hit.position()), normal, -1);
        });
    }
}
