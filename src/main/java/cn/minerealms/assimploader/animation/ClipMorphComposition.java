package cn.minerealms.assimploader.animation;

import cn.minerealms.assimploader.model.AssimpAnimation;
import cn.minerealms.assimploader.model.AssimpMesh;
import cn.minerealms.assimploader.model.AssimpScene;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/** Imported Morph channels resolved once; sampling reads the skeletal layer's existing clock. */
final class ClipMorphComposition {
    private record Target(AssimpMesh mesh, MorphAnimationChannel channel, float mask) {}
    private final List<Target> targets;

    ClipMorphComposition(AssimpScene scene, AssimpAnimation clip, Map<String, Float> mask) {
        var resolved = new LinkedHashMap<AssimpMesh, Target>();
        for (var channel : clip.getMorphChannels()) for (int index = 0; index < scene.getMeshes().size(); index++) {
            AssimpMesh mesh = scene.getMeshes().get(index);
            if (!mesh.hasMorphTargets() || !MorphChannelBinding.matches(scene, channel.getMeshName(), index)) continue;
            float weight = MorphChannelBinding.mask(scene, channel.getMeshName(), index, mask);
            Target previous = resolved.get(mesh);
            if (previous != null) weight = Math.max(weight, previous.mask);
            // Match the base clip sampler: the last imported channel for a mesh supplies its values.
            resolved.put(mesh, new Target(mesh, channel, weight));
        }
        targets = List.copyOf(resolved.values());
    }

    void apply(AnimationPoseLayer layer, Map<AssimpMesh, float[]> weights) {
        for (Target target : targets) {
            float effective = layer.getWeight() * target.mask;
            if (effective == 0) continue;
            AssimpMesh mesh = target.mesh;
            float[] defaults = mesh.getDefaultMorphWeights();
            float[] sampled = target.channel.sample(layer.getPlayer().getCurrentTime(), defaults.length);
            float[] current = weights.getOrDefault(mesh, defaults).clone();
            for (int index = 0; index < current.length; index++) {
                current[index] += (layer.isAdditive() ? sampled[index] - defaults[index] : sampled[index] - current[index]) * effective;
            }
            weights.put(mesh, current);
        }
    }

}
