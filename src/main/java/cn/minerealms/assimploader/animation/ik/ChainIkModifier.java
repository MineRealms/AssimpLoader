package cn.minerealms.assimploader.animation.ik;

import cn.minerealms.assimploader.animation.AnimationBlender;
import cn.minerealms.assimploader.animation.PoseGraph;
import cn.minerealms.assimploader.animation.PoseModifier;
import cn.minerealms.assimploader.model.AssimpScene;
import org.joml.Matrix4f;
import org.joml.Quaternionf;
import org.joml.Vector3f;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/** Bounded CCD adapter operating directly on the instance pose with arbitrary bone axes. */
public final class ChainIkModifier implements PoseModifier {
    @Override public Category category() { return Category.IK; }
    private final List<String> chain;
    private final Vector3f target = new Vector3f();
    private final float weight;
    private final int iterations;
    private final float tolerance;

    public ChainIkModifier(List<String> chain, Vector3f target, float weight, int iterations, float tolerance) {
        if (chain.size() < 2 || chain.size() > 256 || chain.stream().distinct().count() != chain.size()
                || !Float.isFinite(weight) || weight < 0 || weight > 1 || iterations < 1 || iterations > 128
                || !Float.isFinite(tolerance) || tolerance <= 0) throw new IllegalArgumentException("Invalid IK settings");
        this.chain = List.copyOf(chain);
        this.weight = weight;
        this.iterations = iterations;
        this.tolerance = tolerance;
        setTarget(target);
    }

    public void setTarget(Vector3f value) {
        if (!value.isFinite()) throw new IllegalArgumentException("Non-finite IK target");
        target.set(value);
    }

    @Override public void apply(AssimpScene scene, Map<String, Matrix4f> locals, double deltaSeconds) {
        if (weight == 0) return;
        PoseGraph graph = new PoseGraph(scene.getRootNode(), locals);
        Map<String, Matrix4f> before = new LinkedHashMap<>();
        for (int i = 0; i < chain.size(); i++) {
            String node = chain.get(i);
            before.put(node, graph.local(node));
            if (i > 0 && !graph.isAncestor(chain.get(i - 1), node)) {
                throw new IllegalArgumentException("IK nodes are not an ordered descendant chain");
            }
        }
        String tip = chain.get(chain.size() - 1);
        for (int iteration = 0; iteration < iterations; iteration++) {
            if (graph.position(tip).distanceSquared(target) <= tolerance * tolerance) break;
            for (int i = chain.size() - 2; i >= 0; i--) {
                String node = chain.get(i);
                Vector3f origin = graph.position(node);
                Vector3f end = graph.position(tip).sub(origin);
                Vector3f desired = new Vector3f(target).sub(origin);
                if (end.lengthSquared() < 1e-10f || desired.lengthSquared() < 1e-10f) continue;
                graph.rotateModel(node, new Quaternionf().rotationTo(end.normalize(), desired.normalize()));
            }
        }
        if (weight < 1) for (int i = 0; i < chain.size() - 1; i++) {
            String node = chain.get(i);
            graph.setLocal(node, AnimationBlender.blendTransforms(before.get(node), graph.local(node), weight));
        }
    }
}
