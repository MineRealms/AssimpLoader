package cn.minerealms.assimploader.animation;

import cn.minerealms.assimploader.model.AssimpScene;
import org.joml.Matrix4f;

import java.util.Map;

/** Client-thread stage after clip/layer composition, before immutable publication. */
public interface PoseModifier extends AutoCloseable {
    enum Category { GENERAL, IK, SPRING }
    default Category category() { return Category.GENERAL; }
    void apply(AssimpScene scene, Map<String, Matrix4f> localOverrides, double deltaSeconds);
    default void reset() {}
    @Override default void close() { reset(); }
}
