package cn.minerealms.assimploader.animation;

import cn.minerealms.assimploader.model.AssimpScene;
import org.joml.Matrix4f;

import java.util.Map;

/** Client-thread stage after clip/layer composition, before immutable publication. */
public interface PoseModifier extends AutoCloseable {
    enum Category { MOTION, BONE_MORPH, GENERAL, GROUND, IK, PHYSICS, SPRING, POST_PHYSICS }
    default Category category() { return Category.GENERAL; }
    /** Full modifier IDs that must run first. Validated and snapshotted when the instance plan changes. */
    default java.util.Set<String> dependencies() { return java.util.Set.of(); }
    /** Declared bones prevent conflicting rigid-body and Spring Bone writers in one instance. */
    default java.util.Set<String> controlledBones() { return java.util.Set.of(); }
    void apply(AssimpScene scene, Map<String, Matrix4f> localOverrides, double deltaSeconds);
    default void applyMorphs(AssimpScene scene, Map<cn.minerealms.assimploader.model.AssimpMesh, float[]> weights) {}
    default void reset() {}
    @Override default void close() { reset(); }
}
