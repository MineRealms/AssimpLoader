package cn.minerealms.assimploader.collision;

import org.joml.Vector3f;

public record RayHit(float distance, Vector3f position, Vector3f normal, int triangle) {
    public RayHit {
        if (!Float.isFinite(distance) || distance < 0) throw new IllegalArgumentException("Invalid hit distance");
        position = Ray.finite(position, "hit position");
        normal = Ray.finite(normal, "hit normal");
    }
    @Override public Vector3f position() { return new Vector3f(position); }
    @Override public Vector3f normal() { return new Vector3f(normal); }
}
