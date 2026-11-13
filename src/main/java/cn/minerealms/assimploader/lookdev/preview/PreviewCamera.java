package cn.minerealms.assimploader.lookdev.preview;

import org.joml.Matrix4f;
import org.joml.Vector3f;

/** Camera coordinates are relative to the bounds center, avoiding large world translations. */
public final class PreviewCamera {
    private static final float FOV = (float) Math.toRadians(45);
    private float yaw = 35;
    private float pitch = 20;
    private float radius = 1;
    private float distance = 4;
    private float panX;
    private float panY;

    public void reset(float modelRadius, float aspect) {
        yaw = 35;
        pitch = 20;
        fit(modelRadius, aspect);
    }

    public void fit(float modelRadius, float aspect) {
        radius = Float.isFinite(modelRadius) ? Math.max(modelRadius, 0.00001f) : 1;
        float halfFov = limitingHalfFov(aspect);
        distance = radius / (float) Math.sin(halfFov) * 1.12f;
        panX = 0;
        panY = 0;
    }

    private static float limitingHalfFov(float aspect) {
        float safeAspect = Float.isFinite(aspect) ? Math.max(0.001f, aspect) : 1;
        return Math.min(FOV / 2, (float) Math.atan(Math.tan(FOV / 2) * safeAspect));
    }

    public void orbit(double dx, double dy) {
        if (!Double.isFinite(dx) || !Double.isFinite(dy)) return;
        yaw = (float) ((yaw + dx * 0.45) % 360);
        pitch = Math.max(-89, Math.min(89, (float) (pitch + dy * 0.45)));
    }

    public void zoom(double steps) {
        if (!Double.isFinite(steps)) return;
        double next = distance * Math.exp(-Math.max(-100, Math.min(100, steps)) * 0.14);
        distance = (float) Math.max(radius * 0.015, Math.min(radius * 10000, next));
    }

    public void pan(double dx, double dy, int viewportHeight) {
        if (!Double.isFinite(dx) || !Double.isFinite(dy)) return;
        float unitsPerPixel = 2 * distance * (float) Math.tan(FOV / 2) / Math.max(viewportHeight, 1);
        panX -= (float) dx * unitsPerPixel;
        panY += (float) dy * unitsPerPixel;
    }

    public Matrix4f view() {
        float azimuth = (float) Math.toRadians(yaw);
        float elevation = (float) Math.toRadians(pitch);
        Vector3f direction = new Vector3f(
                (float) (Math.cos(elevation) * Math.sin(azimuth)),
                (float) Math.sin(elevation),
                (float) (Math.cos(elevation) * Math.cos(azimuth)));
        Vector3f right = new Vector3f((float) Math.cos(azimuth), 0, -(float) Math.sin(azimuth));
        Vector3f up = new Vector3f(direction).cross(right).normalize();
        Vector3f target = right.mul(panX).add(up.mul(panY));
        return new Matrix4f().lookAt(new Vector3f(direction).mul(distance).add(target), target, new Vector3f(0, 1, 0));
    }

    public Matrix4f projection(float aspect) {
        float near = Math.max(radius * 0.0001f, distance - radius * 2.5f);
        float far = Math.max(near * 2, distance + radius * 4);
        return new Matrix4f().perspective(FOV, Math.max(0.001f, aspect), near, far);
    }

    public float distance() { return distance; }
    public float yaw() { return yaw; }
    public float pitch() { return pitch; }
}
