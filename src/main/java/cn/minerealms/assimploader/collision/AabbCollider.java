package cn.minerealms.assimploader.collision;

import org.joml.Vector3f;
import org.joml.Vector3fc;
import java.util.Optional;

public final class AabbCollider implements CollisionShape {
    private final Vector3f minimum;
    private final Vector3f maximum;

    public AabbCollider(Vector3fc minimum, Vector3fc maximum) {
        this.minimum = Ray.finite(minimum, "minimum");
        this.maximum = Ray.finite(maximum, "maximum");
        if (minimum.x() > maximum.x() || minimum.y() > maximum.y() || minimum.z() > maximum.z())
            throw new IllegalArgumentException("Reversed box extents");
    }

    public Vector3f minimum() { return new Vector3f(minimum); }
    public Vector3f maximum() { return new Vector3f(maximum); }

    /** Entry bound used by the BVH, including rays originating within this box. */
    float entry(Ray ray, float maximumDistance) {
        float[] interval = interval(ray);
        return interval == null || interval[1] < 0 || interval[0] > maximumDistance
                ? Float.POSITIVE_INFINITY : Math.max(0, interval[0]);
    }

    private float[] interval(Ray ray) {
        Vector3f origin = ray.origin(), direction = ray.direction();
        float near = Float.NEGATIVE_INFINITY, far = Float.POSITIVE_INFINITY;
        for (int axis = 0; axis < 3; axis++) {
            float o = origin.get(axis), d = direction.get(axis);
            if (Math.abs(d) < 1.0e-20f) {
                if (o < minimum.get(axis) || o > maximum.get(axis)) return null;
                continue;
            }
            float a = (minimum.get(axis) - o) / d, b = (maximum.get(axis) - o) / d;
            near = Math.max(near, Math.min(a, b));
            far = Math.min(far, Math.max(a, b));
            if (near > far) return null;
        }
        return new float[]{near, far};
    }

    @Override public Optional<RayHit> raycast(Ray ray, float maximumDistance) {
        Ray.distance(maximumDistance);
        float[] interval = interval(ray);
        if (interval == null) return Optional.empty();
        float distance = interval[0] >= 0 ? interval[0] : interval[1];
        if (!Float.isFinite(distance) || distance < 0 || distance > maximumDistance) return Optional.empty();
        Vector3f point = ray.at(distance), normal = new Vector3f();
        float closest = Float.POSITIVE_INFINITY;
        for (int axis = 0; axis < 3; axis++) {
            float lower = Math.abs(point.get(axis) - minimum.get(axis));
            float upper = Math.abs(point.get(axis) - maximum.get(axis));
            if (lower < closest) { closest = lower; normal.zero().setComponent(axis, -1); }
            if (upper < closest) { closest = upper; normal.zero().setComponent(axis, 1); }
        }
        return Optional.of(new RayHit(distance, point, normal, -1));
    }
}
