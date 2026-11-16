package cn.minerealms.assimploader.animation;

import cn.minerealms.assimploader.model.AssimpScene;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import org.joml.Matrix4f;
import org.joml.Vector3f;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Set;

/** Parsed once per script command; pose evaluation never calls the JS engine. */
public final class ScriptPoseModifier implements PoseModifier {
    private record Bone(Matrix4f rest, Matrix4f delta, float weight) {}
    private final Map<String, Bone> bones;
    private final boolean additive;
    private final Category stage;

    public ScriptPoseModifier(AssimpScene scene, JsonObject options) {
        keys(options, Set.of("bones", "weight", "additive", "stage"));
        float weight = weight(options, "weight", 1);
        additive = options.has("additive") && options.get("additive").getAsBoolean();
        String requested = options.has("stage") ? options.get("stage").getAsString() : "pre_ik";
        stage = switch (requested) {
            case "pre_ik" -> Category.GENERAL;
            case "post_physics" -> Category.POST_PHYSICS;
            default -> throw new IllegalArgumentException("Pose stage must be pre_ik or post_physics");
        };
        if (!options.has("bones") || !options.get("bones").isJsonObject())
            throw new IllegalArgumentException("Pose requires a bones object");
        JsonObject entries = options.getAsJsonObject("bones");
        if (entries.size() > 256) throw new IllegalArgumentException("Script pose exceeds 256 bones");
        Map<String, Bone> parsed = new LinkedHashMap<>();
        entries.entrySet().forEach(entry -> {
            var node = scene.findNodeByName(entry.getKey());
            if (node == null) throw new IllegalArgumentException("Unknown pose bone: " + entry.getKey());
            JsonObject bone = entry.getValue().getAsJsonObject();
            keys(bone, Set.of("rotation", "position", "scale", "weight"));
            Vector3f rotation = vector(bone, "rotation", new Vector3f()).mul((float)(Math.PI / 180));
            Vector3f position = vector(bone, "position", new Vector3f());
            Vector3f scale = vector(bone, "scale", new Vector3f(1));
            if (scale.x <= 0 || scale.y <= 0 || scale.z <= 0)
                throw new IllegalArgumentException("Pose scale must be positive");
            Matrix4f delta = new Matrix4f().translation(position).rotateXYZ(rotation.x, rotation.y, rotation.z).scale(scale);
            parsed.put(entry.getKey(), new Bone(new Matrix4f(node.getTransformation()), delta, weight * weight(bone, "weight", 1)));
        });
        bones = java.util.Collections.unmodifiableMap(parsed);
    }

    @Override public Category category() { return stage; }
    @Override public Set<String> controlledBones() { return bones.keySet(); }
    @Override public void apply(AssimpScene scene, Map<String, Matrix4f> locals, double deltaSeconds) {
        bones.forEach((name, bone) -> {
            if (bone.weight == 0) return;
            Matrix4f base = locals.getOrDefault(name, bone.rest);
            Matrix4f target = new Matrix4f(additive ? base : bone.rest).mul(bone.delta);
            locals.put(name, AnimationBlender.blendTransforms(base, target, bone.weight));
        });
    }

    private static void keys(JsonObject object, Set<String> allowed) {
        for (String key : object.keySet()) if (!allowed.contains(key))
            throw new IllegalArgumentException("Unknown script pose option: " + key);
    }
    private static float weight(JsonObject object, String key, float fallback) {
        float value = object.has(key) ? finite(object.get(key)) : fallback;
        if (value < 0 || value > 1) throw new IllegalArgumentException("Pose weight must be in [0,1]");
        return value;
    }
    private static Vector3f vector(JsonObject object, String key, Vector3f fallback) {
        if (!object.has(key)) return fallback;
        var array = object.getAsJsonArray(key);
        if (array.size() != 3) throw new IllegalArgumentException(key + " requires three numbers");
        return new Vector3f(finite(array.get(0)), finite(array.get(1)), finite(array.get(2)));
    }
    private static float finite(JsonElement element) {
        if (!element.isJsonPrimitive() || !element.getAsJsonPrimitive().isNumber())
            throw new IllegalArgumentException("Pose value must be a number");
        float value = element.getAsFloat();
        if (!Float.isFinite(value)) throw new IllegalArgumentException("Pose value must be finite");
        return value;
    }
}
