package cn.minerealms.assimploader.collision;

import org.joml.Vector3f;
import org.joml.Vector3fc;

/** A model-space ray with unit direction; hit distances use model units. */
public final class Ray {
    private final Vector3f origin;
    private final Vector3f direction;

    public Ray(Vector3fc origin, Vector3fc direction) {
        this.origin = finite(origin, "origin");
        this.direction = finite(direction, "direction");
        if (!Float.isFinite(this.direction.lengthSquared()) || this.direction.lengthSquared() < 1.0e-20f)
            throw new IllegalArgumentException("Invalid ray direction magnitude");
        this.direction.normalize();
    }

    public Vector3f origin() { return new Vector3f(origin); }
    public Vector3f direction() { return new Vector3f(direction); }
    public Vector3f at(float distance) { return new Vector3f(direction).mul(distance).add(origin); }

    static Vector3f finite(Vector3fc vector, String name) {
        if (vector == null || !Float.isFinite(vector.x()) || !Float.isFinite(vector.y()) || !Float.isFinite(vector.z()))
            throw new IllegalArgumentException("Non-finite " + name);
        return new Vector3f(vector);
    }

    static void distance(float maximum) {
        if (!Float.isFinite(maximum) || maximum < 0) throw new IllegalArgumentException("Invalid ray distance");
    }
}
