package cn.minerealms.assimploader.collision;

import cn.minerealms.assimploader.animation.PoseSnapshot;
import cn.minerealms.assimploader.animation.physics.SpringBoneModifier;
import org.joml.Matrix4f;
import org.joml.Vector3f;
import org.joml.Vector3fc;

/** A conservative model-space capsule following one snapshot node. */
public final class BoneCollisionProxy {
    private final String node;
    private final Vector3f start, end;
    private final float radius;

    public BoneCollisionProxy(String node, Vector3fc start, Vector3fc end, float radius) {
        this.node = java.util.Objects.requireNonNull(node);
        this.start = Ray.finite(start, "proxy start");
        this.end = Ray.finite(end, "proxy end");
        if (!Float.isFinite(radius) || radius <= 0) throw new IllegalArgumentException("Invalid proxy radius");
        this.radius = radius;
    }

    public CapsuleCollider at(PoseSnapshot snapshot) {
        Matrix4f transform = snapshot.getNodeTransform(node);
        if (transform == null) throw new IllegalArgumentException("Unknown proxy node: " + node);
        float scale = SpringBoneModifier.conservativeScale(transform);
        return new CapsuleCollider(transform.transformPosition(new Vector3f(start)), transform.transformPosition(new Vector3f(end)), radius * scale);
    }
}
