package cn.minerealms.assimploader.animation;

import cn.minerealms.assimploader.model.AssimpAnimation;
import cn.minerealms.assimploader.model.AssimpNode;
import org.joml.Matrix4f;

import java.util.Map;

/** One independently timed, optionally masked override or bind-relative additive clip. */
public final class AnimationPoseLayer {
    private final AnimationPlayer player;
    private final boolean additive;
    private final Map<String, Float> mask;
    private float weight;

    public AnimationPoseLayer(AssimpAnimation clip, AssimpNode root, float weight,
                              boolean additive, Map<String, Float> mask) {
        this.player = new AnimationPlayer(clip, root);
        this.additive = additive;
        mask.forEach((name, value) -> validateWeight(value));
        this.mask = Map.copyOf(mask);
        setWeight(weight);
        player.play();
    }

    public void setWeight(float weight) { validateWeight(weight); this.weight = weight; }
    public float getWeight() { return weight; }
    public AnimationPlayer getPlayer() { return player; }
    public Map<String, Float> getMask() { return mask; }
    public boolean isAdditive() { return additive; }
    public void update(double delta) { player.update(delta); }

    void apply(Map<String, Matrix4f> locals, Map<String, Matrix4f> bind) {
        if (weight == 0) return;
        player.getBoneTransforms().forEach((node, sampled) -> {
            float effective = weight * (mask.isEmpty() ? 1 : mask.getOrDefault(node, 0f));
            if (effective == 0) return;
            Matrix4f rest = bind.getOrDefault(node, new Matrix4f());
            Matrix4f base = locals.getOrDefault(node, rest);
            if (additive) {
                if (Math.abs(rest.determinant()) < 1e-10f) {
                    throw new IllegalStateException("Additive layer has singular bind transform: " + node);
                }
                Matrix4f delta = new Matrix4f(rest).invert().mul(sampled);
                locals.put(node, new Matrix4f(base).mul(AnimationBlender.blendTransforms(new Matrix4f(), delta, effective)));
            } else {
                locals.put(node, AnimationBlender.blendTransforms(base, sampled, effective));
            }
        });
    }

    private static void validateWeight(float weight) {
        if (!Float.isFinite(weight) || weight < 0 || weight > 1) {
            throw new IllegalArgumentException("Layer weights must be finite and within [0, 1]");
        }
    }
}
