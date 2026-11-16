package cn.minerealms.assimploader.animation;

import cn.minerealms.assimploader.animation.ik.ChainIkModifier;
import cn.minerealms.assimploader.animation.ik.LookAtModifier;
import cn.minerealms.assimploader.animation.physics.SpringBoneModifier;
import cn.minerealms.assimploader.model.AssimpScene;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import org.joml.Matrix4f;
import org.joml.Vector3f;

import java.io.Reader;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

/** Versioned, declarative configuration. Each instantiation owns all mutable playback and modifiers. */
public final class ModelInstanceDefinition {
    private final JsonObject root;
    private final String model;

    public ModelInstanceDefinition(JsonObject root) {
        this.root = root.deepCopy();
        keys(this.root, "version", "model", "initial", "states", "transitions", "layers", "attachments",
                "lookAt", "ik", "morphs", "springs", "colliders", "materials", "trails", "hitboxes", "particles");
        if (number(this.root, "version", 0) != 1) throw new IllegalArgumentException("Instance version must be 1");
        model = text(this.root, "model", null);
        if (model == null || model.isBlank()) throw new IllegalArgumentException("Instance model is required");
    }

    public static ModelInstanceDefinition read(Reader reader) {
        return new ModelInstanceDefinition(JsonParser.parseReader(reader).getAsJsonObject());
    }

    public String getModel() { return model; }

    public ModelInstance instantiate(AssimpScene scene) {
        AnimationState state = new AnimationState(model, java.util.Objects.requireNonNull(scene));
        try {
            if (!scene.isDynamic()) throw new IllegalArgumentException("Configured instances require a dynamic scene import");
            state.initialize();
            configure(state);
            return new ModelInstance(state, attachments(scene), root);
        } catch (RuntimeException failure) {
            state.close();
            throw failure;
        }
    }

    private void configure(AnimationState state) {
        JsonObject states = object(root, "states");
        states.entrySet().forEach(entry -> {
            JsonObject value = entry.getValue().getAsJsonObject();
            keys(value, "clip", "loop", "speed", "transition");
            state.getStateMachine().addState(entry.getKey(), clip(state, text(value, "clip", entry.getKey())),
                    bool(value, "loop", true), number(value, "speed", 1), number(value, "transition", .25f));
        });
        for (JsonElement element : array(root, "transitions")) {
            JsonObject value = element.getAsJsonObject();
            keys(value, "from", "to", "duration");
            state.getStateMachine().addTransition(text(value, "from", ""), text(value, "to", ""), number(value, "duration", .25f));
        }
        String initial = text(root, "initial", null);
        if (initial != null && !state.getStateMachine().request(initial)) throw new IllegalArgumentException("Unknown initial state: " + initial);
        object(root, "layers").entrySet().forEach(entry -> {
            JsonObject value = entry.getValue().getAsJsonObject();
            keys(value, "clip", "weight", "additive", "mask", "loop", "speed");
            Map<String, Float> mask = new LinkedHashMap<>();
            object(value, "mask").entrySet().forEach(maskEntry -> {
                if (state.getScene().getMeshes().stream().noneMatch(mesh -> mesh.getName().equals(maskEntry.getKey())))
                    requireNode(state.getScene(), maskEntry.getKey());
                mask.put(maskEntry.getKey(), finite(maskEntry.getValue()));
            });
            AnimationPoseLayer layer = state.setLayer(entry.getKey(), clip(state, text(value, "clip", entry.getKey())),
                    number(value, "weight", 1), bool(value, "additive", false), mask);
            layer.getPlayer().setLooping(bool(value, "loop", true));
            layer.getPlayer().setSpeed(number(value, "speed", 1));
        });
        object(root, "lookAt").entrySet().forEach(entry -> {
            JsonObject value = entry.getValue().getAsJsonObject();
            keys(value, "node", "forward", "target", "weight", "maxAngle");
            String node = text(value, "node", "");
            requireNode(state.getScene(), node);
            state.addModifier(entry.getKey(), new LookAtModifier(node, vector(value, "forward", new Vector3f(0, 0, 1)),
                    vector(value, "target", new Vector3f()), number(value, "weight", 1), number(value, "maxAngle", (float) Math.PI)));
        });
        object(root, "ik").entrySet().forEach(entry -> {
            JsonObject value = entry.getValue().getAsJsonObject();
            keys(value, "chain", "target", "weight", "iterations", "tolerance");
            List<String> chain = new ArrayList<>();
            for (JsonElement node : array(value, "chain")) { requireNode(state.getScene(), node.getAsString()); chain.add(node.getAsString()); }
            float iterations = number(value, "iterations", 16);
            if (iterations != (int) iterations) throw new IllegalArgumentException("IK iterations must be an integer");
            state.addModifier(entry.getKey(), new ChainIkModifier(chain, vector(value, "target", new Vector3f()),
                    number(value, "weight", 1), (int) iterations, number(value, "tolerance", .001f)));
        });
        object(root, "morphs").entrySet().forEach(entry -> {
            var meshes = state.getScene().getMeshes().stream().filter(mesh -> mesh.getName().equals(entry.getKey())).toList();
            if (meshes.size() != 1) throw new IllegalArgumentException("Morph mesh must resolve uniquely: " + entry.getKey());
            JsonArray values = entry.getValue().getAsJsonArray();
            float[] weights = new float[values.size()];
            for (int i = 0; i < weights.length; i++) weights[i] = finite(values.get(i));
            state.setMorphWeights(meshes.get(0), weights);
        });
        List<SpringBoneModifier.Collider> colliders = new ArrayList<>();
        object(root, "colliders").entrySet().forEach(entry -> {
            JsonObject value = entry.getValue().getAsJsonObject();
            keys(value, "node", "type", "center", "start", "end", "radius");
            String node = text(value, "node", null);
            if (node != null) requireNode(state.getScene(), node);
            String type = text(value, "type", "sphere");
            if (type.equals("sphere")) colliders.add(new SpringBoneModifier.Sphere(node,
                    vector(value, "center", new Vector3f()), number(value, "radius", .1f)));
            else if (type.equals("capsule")) colliders.add(new SpringBoneModifier.Capsule(node,
                    vector(value, "start", new Vector3f()), vector(value, "end", new Vector3f(0,1,0)), number(value, "radius", .1f)));
            else throw new IllegalArgumentException("Unknown spring collider type: " + type);
        });
        object(root, "springs").entrySet().forEach(entry -> {
            JsonObject value = entry.getValue().getAsJsonObject();
            keys(value, "node", "tail", "stiffness", "damping", "gravity", "radius", "fixedStep", "maxSubsteps", "teleportDistance");
            String node = text(value, "node", "");
            requireNode(state.getScene(), node);
            float substeps = number(value, "maxSubsteps", 8);
            if (substeps != (int) substeps) throw new IllegalArgumentException("Spring substeps must be an integer");
            state.addModifier(entry.getKey(), new SpringBoneModifier(new SpringBoneModifier.Settings(node,
                    vector(value, "tail", new Vector3f(0,1,0)), number(value, "stiffness", 30), number(value, "damping", 5),
                    vector(value, "gravity", new Vector3f(0,-9.81f,0)), number(value, "radius", .02f),
                    number(value, "fixedStep", 1f/120), (int)substeps, number(value, "teleportDistance", 4)), colliders));
        });
    }

    private Map<String, ModelInstance.Attachment> attachments(AssimpScene scene) {
        Map<String, ModelInstance.Attachment> result = new LinkedHashMap<>();
        object(root, "attachments").entrySet().forEach(entry -> {
            JsonObject value = entry.getValue().getAsJsonObject();
            keys(value, "node", "translation", "rotation", "scale");
            String node = text(value, "node", "");
            requireNode(scene, node);
            Vector3f rotation = vector(value, "rotation", new Vector3f());
            Vector3f scale = vector(value, "scale", new Vector3f(1));
            if (scale.x == 0 || scale.y == 0 || scale.z == 0) throw new IllegalArgumentException("Attachment scale is singular");
            Matrix4f offset = new Matrix4f().translation(vector(value, "translation", new Vector3f()))
                    .rotateXYZ(rotation.x, rotation.y, rotation.z).scale(scale);
            result.put(entry.getKey(), new ModelInstance.Attachment(node, offset));
        });
        return result;
    }

    private static String clip(AnimationState state, String requested) {
        List<String> matches = java.util.Arrays.stream(state.getAvailableAnimations())
                .filter(name -> name.equals(requested) || name.endsWith("|" + requested)).toList();
        if (matches.contains(requested)) return requested;
        if (matches.size() != 1) throw new IllegalArgumentException("Clip must resolve uniquely: " + requested);
        return matches.get(0);
    }

    static void requireNode(AssimpScene scene, String node) {
        if (scene.getRootNode() == null || scene.getRootNode().find(node) == null) throw new IllegalArgumentException("Unknown node: " + node);
    }

    static JsonObject object(JsonObject object, String name) { return object.has(name) ? object.getAsJsonObject(name) : new JsonObject(); }
    static JsonArray array(JsonObject object, String name) { return object.has(name) ? object.getAsJsonArray(name) : new JsonArray(); }
    static String text(JsonObject object, String name, String fallback) { return object.has(name) ? object.get(name).getAsString() : fallback; }
    static boolean bool(JsonObject object, String name, boolean fallback) { return object.has(name) ? object.get(name).getAsBoolean() : fallback; }
    static float number(JsonObject object, String name, float fallback) { return object.has(name) ? finite(object.get(name)) : fallback; }
    static float finite(JsonElement element) {
        float value = element.getAsFloat();
        if (!Float.isFinite(value)) throw new IllegalArgumentException("Expected finite number");
        return value;
    }
    static Vector3f vector(JsonObject object, String name, Vector3f fallback) {
        if (!object.has(name)) return new Vector3f(fallback);
        JsonArray values = object.getAsJsonArray(name);
        if (values.size() != 3) throw new IllegalArgumentException("Expected 3 components: " + name);
        return new Vector3f(finite(values.get(0)), finite(values.get(1)), finite(values.get(2)));
    }
    static void keys(JsonObject value, String... allowed) {
        Set<String> names = Set.of(allowed);
        for (String key : value.keySet()) if (!names.contains(key)) throw new IllegalArgumentException("Unknown instance field: " + key);
    }
}
