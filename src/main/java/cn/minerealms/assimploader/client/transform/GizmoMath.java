package cn.minerealms.assimploader.client.transform;

import org.joml.Matrix4fc;
import org.joml.Vector3f;
import org.joml.Vector3fc;
import org.joml.Vector4f;

/** Camera-relative picking and projection used by world transform handles. */
public final class GizmoMath {
    public record Point(float x, float y, float depth) {}
    public record Ray(Vector3f origin, Vector3f direction) {}
    private GizmoMath() {}

    public static Point project(Matrix4fc matrix, Vector3fc point, int width, int height) {
        var clip = matrix.transform(new Vector4f(point, 1));
        if (!clip.isFinite() || clip.w <= 1e-5f) return null;
        float depth = clip.z / clip.w;
        if (depth < -1 || depth > 1) return null;
        return new Point((clip.x / clip.w + 1) * width * .5f, (1 - clip.y / clip.w) * height * .5f, depth);
    }

    public static Ray ray(Matrix4fc inverse, double x, double y, int width, int height) {
        float nx = (float) (2 * x / width - 1), ny = (float) (1 - 2 * y / height);
        var near = inverse.transform(new Vector4f(nx, ny, -1, 1));
        var far = inverse.transform(new Vector4f(nx, ny, 1, 1));
        if (!near.isFinite() || !far.isFinite() || Math.abs(near.w) < 1e-8 || Math.abs(far.w) < 1e-8) return null;
        var origin = new Vector3f(near.x, near.y, near.z).div(near.w);
        var direction = new Vector3f(far.x, far.y, far.z).div(far.w).sub(origin);
        return direction.lengthSquared() < 1e-12f ? null : new Ray(origin, direction.normalize());
    }

    public static float segmentDistance(double x, double y, Point a, Point b) {
        double dx = b.x - a.x, dy = b.y - a.y, length = dx * dx + dy * dy;
        double t = length < 1e-10 ? 0 : Math.max(0, Math.min(1, ((x - a.x) * dx + (y - a.y) * dy) / length));
        return (float) Math.hypot(x - a.x - t * dx, y - a.y - t * dy);
    }

    public static float axisParameter(Ray ray, Vector3fc point, Vector3fc axis) {
        if (ray == null) return Float.NaN;
        var offset = new Vector3f(ray.origin).sub(point);
        float b = ray.direction.dot(axis), d = ray.direction.dot(offset), e = axis.dot(offset);
        float denominator = 1 - b * b;
        return denominator < 1e-4f ? Float.NaN : (e - b * d) / denominator;
    }

    public static Vector3f plane(Ray ray, Vector3fc point, Vector3fc normal) {
        if (ray == null) return null;
        float denominator = normal.dot(ray.direction);
        if (Math.abs(denominator) < 1e-5f) return null;
        float t = normal.dot(new Vector3f(point).sub(ray.origin)) / denominator;
        return t < 0 ? null : new Vector3f(ray.origin).fma(t, ray.direction);
    }

    public static float signedAngle(Vector3fc a, Vector3fc b, Vector3fc normal) {
        return (float) Math.atan2(normal.dot(new Vector3f(a).cross(b)), a.dot(b));
    }
}
