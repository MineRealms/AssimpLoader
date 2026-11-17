package cn.minerealms.assimploader.client.transform;

import org.joml.Vector3d;

/** Orbit math in Minecraft's yaw/pitch convention, independent of client or GL state. */
final class WorldEditCameraOrbit {
    static final double MIN_DISTANCE = .18, MAX_DISTANCE = 16, MAX_PAN = 8;
    private double yaw, pitch = 10, distance = 2.8, frame = .5;
    private double panX, panY, panZ;
    private final Vector3d forward = new Vector3d(), right = new Vector3d(), up = new Vector3d();

    WorldEditCameraOrbit() { updateBasis(); }

    void reset(double bodyYaw) {
        yaw = Double.isFinite(bodyYaw) ? wrap(bodyYaw + 180) : 180;
        pitch = 10;
        distance = 2.8;
        frame = .5;
        focus();
        updateBasis();
    }

    void orbit(double dx, double dy) {
        if (!Double.isFinite(dx) || !Double.isFinite(dy)) return;
        yaw = wrap(yaw + clamp(dx, -3600, 3600) * .3);
        pitch = clamp(pitch + clamp(dy, -3600, 3600) * .3, -85, 85);
        updateBasis();
    }

    void pan(double dx, double dy, double worldPerPixel) {
        if (!Double.isFinite(dx) || !Double.isFinite(dy) || !Double.isFinite(worldPerPixel)
                || worldPerPixel <= 0) return;
        double horizontal = -clamp(dx, -10000, 10000) * Math.min(worldPerPixel, 1);
        double vertical = clamp(dy, -10000, 10000) * Math.min(worldPerPixel, 1);
        panX = clamp(panX + right.x * horizontal + up.x * vertical, -MAX_PAN, MAX_PAN);
        panY = clamp(panY + right.y * horizontal + up.y * vertical, -MAX_PAN, MAX_PAN);
        panZ = clamp(panZ + right.z * horizontal + up.z * vertical, -MAX_PAN, MAX_PAN);
    }

    void zoom(double wheel) {
        if (Double.isFinite(wheel))
            distance = clamp(distance * Math.exp(-clamp(wheel, -100, 100) * .12), MIN_DISTANCE, MAX_DISTANCE);
    }

    void lift(double wheel) {
        if (Double.isFinite(wheel)) panY = clamp(panY + clamp(wheel, -100, 100) * distance * .06, -MAX_PAN, MAX_PAN);
    }

    void frame(double centerX) {
        if (Double.isFinite(centerX)) frame = clamp(centerX, 0, 1);
    }

    void focus() { panX = panY = panZ = 0; }
    double distance() { return distance; }
    float yaw() { return (float) yaw; }
    float pitch() { return (float) pitch; }
    Vector3d forward(Vector3d destination) { return destination.set(forward); }
    Vector3d right(Vector3d destination) { return destination.set(right); }
    Vector3d up(Vector3d destination) { return destination.set(up); }

    Vector3d target(double x, double y, double z, Vector3d destination) {
        return destination.set(x + panX, y + panY, z + panZ);
    }

    Vector3d pivot(double x, double y, double z, double horizontalTangent, Vector3d destination) {
        double tangent = Double.isFinite(horizontalTangent) ? clamp(horizontalTangent, .01, 8) : 1;
        double shift = clamp((frame * 2 - 1) * distance * tangent, -MAX_DISTANCE, MAX_DISTANCE);
        return target(x, y, z, destination).fma(-shift, right);
    }

    Vector3d position(double x, double y, double z, double horizontalTangent, Vector3d destination) {
        return pivot(x, y, z, horizontalTangent, destination).fma(-distance, forward);
    }

    private void updateBasis() {
        double azimuth = Math.toRadians(yaw), elevation = Math.toRadians(pitch);
        double sine = Math.sin(azimuth), cosine = Math.cos(azimuth);
        double pitchSine = Math.sin(elevation), pitchCosine = Math.cos(elevation);
        forward.set(-sine * pitchCosine, -pitchSine, cosine * pitchCosine);
        right.set(-cosine, 0, -sine);
        right.cross(forward, up);
    }

    private static double wrap(double value) { return ((value + 180) % 360 + 360) % 360 - 180; }
    private static double clamp(double value, double min, double max) { return Math.max(min, Math.min(value, max)); }
}
