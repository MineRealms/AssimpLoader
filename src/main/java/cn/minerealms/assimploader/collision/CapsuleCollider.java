package cn.minerealms.assimploader.collision;

import org.joml.Vector3f;
import org.joml.Vector3fc;
import java.util.Optional;

/** A segment swept by a sphere. Coincident endpoints form a sphere. */
public final class CapsuleCollider implements CollisionShape {
    private final Vector3f start, end;
    private final float radius;

    public CapsuleCollider(Vector3fc start, Vector3fc end, float radius) {
        this.start = Ray.finite(start, "capsule start");
        this.end = Ray.finite(end, "capsule end");
        if (!Float.isFinite(radius) || radius <= 0) throw new IllegalArgumentException("Invalid capsule radius");
        this.radius = radius;
    }

    public float radius() { return radius; }
    public Vector3f closestPoint(Vector3fc point) {
        Vector3f segment = new Vector3f(end).sub(start);
        float length = segment.lengthSquared();
        float t = length > 1.0e-20f ? new Vector3f(point).sub(start).dot(segment) / length : 0;
        return segment.mul(Math.max(0, Math.min(1, t))).add(start);
    }

    /** Projects a particle outside the capsule plus its own radius. */
    public Vector3f project(Vector3fc point, float particleRadius, Vector3fc fallbackDirection) {
        Ray.finite(point, "particle");
        if (!Float.isFinite(particleRadius) || particleRadius < 0) throw new IllegalArgumentException("Invalid particle radius");
        Vector3f center = closestPoint(point), delta = new Vector3f(point).sub(center);
        float required = radius + particleRadius;
        if (delta.lengthSquared() >= required * required) return new Vector3f(point);
        if (delta.lengthSquared() < 1.0e-20f) {
            delta = Ray.finite(fallbackDirection, "fallback direction");
            if (delta.lengthSquared() < 1.0e-20f) delta.set(1, 0, 0);
        }
        return delta.normalize(required).add(center);
    }

    @Override public Optional<RayHit> raycast(Ray ray, float maximumDistance) {
        Ray.distance(maximumDistance);
        Vector3f segment = new Vector3f(end).sub(start), direction = ray.direction(), offset = ray.origin().sub(start);
        float length2 = segment.lengthSquared(), best = Float.POSITIVE_INFINITY;
        if (length2 > 1.0e-20f) {
            float along = segment.dot(direction), axialOrigin = segment.dot(offset);
            float a = length2 - along * along;
            float b = length2 * offset.dot(direction) - axialOrigin * along;
            float c = length2 * (offset.lengthSquared() - radius * radius) - axialOrigin * axialOrigin;
            float discriminant = b * b - a * c;
            if (a > 1.0e-12f && discriminant >= 0) {
                float root = (float) Math.sqrt(discriminant);
                for (float candidate : new float[]{(-b - root) / a, (-b + root) / a}) {
                    float axial = axialOrigin + candidate * along;
                    if (candidate >= 0 && axial >= 0 && axial <= length2) best = Math.min(best, candidate);
                }
            }
        }
        best = Math.min(best, sphereHit(ray, start, segment, length2, false));
        best = Math.min(best, sphereHit(ray, end, segment, length2, true));
        if (!Float.isFinite(best) || best > maximumDistance) return Optional.empty();
        Vector3f point = ray.at(best), normal = new Vector3f(point).sub(closestPoint(point)).normalize();
        return Optional.of(new RayHit(best, point, normal, -1));
    }

    private float sphereHit(Ray ray, Vector3f center, Vector3f segment, float length2, boolean upper) {
        Vector3f offset = ray.origin().sub(center);
        float b = offset.dot(ray.direction()), c = offset.lengthSquared() - radius * radius;
        float discriminant = b * b - c;
        if (discriminant < 0) return Float.POSITIVE_INFINITY;
        float root = (float) Math.sqrt(discriminant), best = Float.POSITIVE_INFINITY;
        for (float candidate : new float[]{-b - root, -b + root}) {
            if (candidate < 0) continue;
            float axial = ray.at(candidate).sub(start).dot(segment);
            if (length2 <= 1.0e-20f || (upper ? axial >= length2 : axial <= 0)) best = Math.min(best, candidate);
        }
        return best;
    }
}
