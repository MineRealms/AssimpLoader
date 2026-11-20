package cn.minerealms.assimploader.animation;

import cn.minerealms.assimploader.model.AssimpNode;
import cn.minerealms.assimploader.model.AssimpScene;

import java.util.Locale;
import java.util.Map;

/** Resolves imported mesh channels consistently for playback, processors, and previews. */
public final class MorphChannelBinding {
    private MorphChannelBinding() {}

    public static boolean matches(AssimpScene scene, String channel, int meshIndex) {
        return matches(scene.getRootNode(), scene.getMeshes().get(meshIndex).getName(), meshIndex, channel,
                scene.getSourceFormat().toLowerCase(Locale.ROOT).contains("fbx"));
    }

    static boolean matches(AssimpNode root, String meshName, int meshIndex, String channel, boolean fbx) {
        if (meshName.equals(channel) || owns(root, channel, meshIndex)) return true;
        if (!fbx || !channel.endsWith("*0")) return false;
        // Assimp FBX identifies a geometry as <owning node>*<geometry ordinal>.
        // Material splitting makes ordinals ambiguous; only resolve a single-mesh owner.
        AssimpNode owner = root == null ? null : root.find(channel.substring(0, channel.length() - 2));
        return owner != null && owner.getMeshIndices().length == 1 && owner.getMeshIndices()[0] == meshIndex;
    }

    public static float mask(AssimpScene scene, String channel, int meshIndex, Map<String, Float> mask) {
        if (mask.isEmpty()) return 1;
        float weight = Math.max(mask.getOrDefault(scene.getMeshes().get(meshIndex).getName(), 0f),
                ownerMask(scene.getRootNode(), meshIndex, mask));
        return Math.max(weight, mask.getOrDefault(channel, 0f));
    }

    private static boolean owns(AssimpNode node, String name, int meshIndex) {
        if (node == null) return false;
        if (node.getName().equals(name)) for (int index : node.getMeshIndices()) if (index == meshIndex) return true;
        for (AssimpNode child : node.getChildren()) if (owns(child, name, meshIndex)) return true;
        return false;
    }

    private static float ownerMask(AssimpNode node, int meshIndex, Map<String, Float> mask) {
        if (node == null) return 0;
        float weight = 0;
        for (int index : node.getMeshIndices()) if (index == meshIndex) weight = mask.getOrDefault(node.getName(), 0f);
        for (AssimpNode child : node.getChildren()) weight = Math.max(weight, ownerMask(child, meshIndex, mask));
        return weight;
    }
}
