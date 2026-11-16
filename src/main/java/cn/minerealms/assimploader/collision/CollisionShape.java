package cn.minerealms.assimploader.collision;

import java.util.Optional;

/** Queries are two-sided. A ray starting inside a closed shape returns its exit surface. */
public interface CollisionShape {
    Optional<RayHit> raycast(Ray ray, float maximumDistance);
}
