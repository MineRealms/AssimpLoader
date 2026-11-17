package cn.minerealms.rigworks.character.core;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import org.joml.Quaternionf;
import org.joml.Vector3f;

import java.io.Reader;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;
import java.util.Set;

/** Immutable resource metadata; reading a profile never loads a model, accesses a world or allocates GL resources. */
public record CharacterProfile(String model, double scale, Map<String, HumanoidBone> humanoid,
                               Map<String, String> animations, String physicsDefinition,
                               double blendSeconds, double movementThreshold, HeadLook headLook,
                               String solverDefinition, float modelYawDegrees, String equipmentDefinition, Timing timing) {
    private static final Set<String> ANIMATIONS = Set.of("idle", "walk", "run", "walk_backward", "strafe_left", "strafe_right",
            "crouch", "crouch_walk", "jump", "fall", "swim", "fly", "elytra", "ride", "sleep", "dead", "use_item", "swing");

    public record Rotation(float x, float y, float z, float w) {
        public static final Rotation IDENTITY = new Rotation(0, 0, 0, 1);
        public Rotation {
            double length = Math.sqrt((double)x*x + (double)y*y + (double)z*z + (double)w*w);
            if (!Double.isFinite(length) || length < 1e-8) throw new IllegalArgumentException("Invalid reference rotation");
            x /= length; y /= length; z /= length; w /= length;
        }
        public Quaternionf quaternion() { return new Quaternionf(x, y, z, w); }
    }

    /** Local bone-axis correction metadata, not an automatic whole-rig retarget operation. */
    public record HumanoidBone(String node, Rotation referenceRotation) {
        public HumanoidBone { name(node, "Humanoid node"); Objects.requireNonNull(referenceRotation); }
    }

    public record Direction(float x, float y, float z) {
        public Direction {
            double length = Math.sqrt((double)x*x + (double)y*y + (double)z*z);
            if (!Double.isFinite(length) || length < 1e-8) throw new IllegalArgumentException("Invalid head forward axis");
            x /= length; y /= length; z /= length;
        }
        public Vector3f vector() { return new Vector3f(x, y, z); }
    }

    public record HeadLook(boolean enabled, Direction forward, float maxYaw, float maxPitch) {
        public HeadLook {
            Objects.requireNonNull(forward);
            if (!Float.isFinite(maxYaw) || maxYaw < 0 || maxYaw > 180
                    || !Float.isFinite(maxPitch) || maxPitch < 0 || maxPitch > 90)
                throw new IllegalArgumentException("Head look limits must be degrees in [0,180] yaw / [0,90] pitch");
        }
        public static HeadLook defaults() { return new HeadLook(true, new Direction(0, 0, -1), 75, 60); }
    }

    /** Stride is metres per full authored cycle; playback limits do not limit synchronized swing phase. */
    public record Timing(boolean swingSync, boolean movementSync, double walkStride, double runStride,
                         double crouchStride, float minRate, float maxRate) {
        public static final Timing DEFAULT = new Timing(true,true,2.4,3.2,1.4,.25f,12);
        public Timing {
            if (!Double.isFinite(walkStride) || walkStride < .01 || walkStride > 32
                    || !Double.isFinite(runStride) || runStride < .01 || runStride > 32
                    || !Double.isFinite(crouchStride) || crouchStride < .01 || crouchStride > 32
                    || !Float.isFinite(minRate) || !Float.isFinite(maxRate) || minRate < .01f || maxRate > 64 || minRate > maxRate)
                throw new IllegalArgumentException("Invalid character action timing");
        }
    }

    public CharacterProfile {
        resource(model, "model");
        if (!Double.isFinite(scale) || scale < .001 || scale > 100)
            throw new IllegalArgumentException("Character scale must be in [0.001,100]");
        if (!Double.isFinite(blendSeconds) || blendSeconds < 0 || blendSeconds > 10)
            throw new IllegalArgumentException("Character blendSeconds must be in [0,10]");
        if (!Double.isFinite(movementThreshold) || movementThreshold < 0 || movementThreshold > 10)
            throw new IllegalArgumentException("Character movementThreshold must be in [0,10]");
        Objects.requireNonNull(humanoid); Objects.requireNonNull(animations); Objects.requireNonNull(headLook); Objects.requireNonNull(timing);
        if (humanoid.size() > 64) throw new IllegalArgumentException("At most 64 humanoid roles");
        humanoid.forEach((role, bone) -> {
            if (role == null || !role.matches("[a-z][a-z0-9_]{0,63}")) throw new IllegalArgumentException("Invalid humanoid role: " + role);
            Objects.requireNonNull(bone, "Humanoid bone");
        });
        animations.forEach((intent, target) -> {
            if (!ANIMATIONS.contains(intent)) throw new IllegalArgumentException("Unknown character animation intent: " + intent);
            name(target, "Animation state or clip");
        });
        humanoid = Map.copyOf(humanoid); animations = Map.copyOf(animations);
        if (physicsDefinition != null) resource(physicsDefinition, "physicsDefinition");
        if (solverDefinition != null) resource(solverDefinition, "solverDefinition");
        if (equipmentDefinition != null) resource(equipmentDefinition, "equipmentDefinition");
        if (!Float.isFinite(modelYawDegrees)) throw new IllegalArgumentException("Non-finite model yaw");
    }

    public CharacterProfile(String model, double scale, Map<String, HumanoidBone> humanoid,
                            Map<String, String> animations, String physicsDefinition,
                            double blendSeconds, double movementThreshold, HeadLook headLook) {
        this(model,scale,humanoid,animations,physicsDefinition,blendSeconds,movementThreshold,headLook,null,180,null,Timing.DEFAULT);
    }

    public CharacterProfile(String model, double scale, Map<String, HumanoidBone> humanoid,
                            Map<String, String> animations, String physicsDefinition,
                            double blendSeconds, double movementThreshold, HeadLook headLook,
                            String solverDefinition, float modelYawDegrees) {
        this(model,scale,humanoid,animations,physicsDefinition,blendSeconds,movementThreshold,headLook,solverDefinition,modelYawDegrees,null,Timing.DEFAULT);
    }

    public CharacterProfile(String model, double scale, Map<String, HumanoidBone> humanoid,
                            Map<String, String> animations, String physicsDefinition,
                            double blendSeconds, double movementThreshold, HeadLook headLook,
                            String solverDefinition, float modelYawDegrees, String equipmentDefinition) {
        this(model,scale,humanoid,animations,physicsDefinition,blendSeconds,movementThreshold,headLook,
                solverDefinition,modelYawDegrees,equipmentDefinition,Timing.DEFAULT);
    }

    public String animation(String intent) { return animations.getOrDefault(intent, intent); }
    public HumanoidBone bone(String role) { return humanoid.get(role); }

    public static CharacterProfile read(Reader source) {
        Objects.requireNonNull(source, "Character profile reader");
        JsonElement value = JsonParser.parseReader(source);
        if (!value.isJsonObject()) throw new IllegalArgumentException("Character profile must be a JSON object");
        return parse(value.getAsJsonObject());
    }

    public static CharacterProfile parse(JsonObject root) {
        Objects.requireNonNull(root, "Character profile");
        keys(root, "version", "model", "scale", "humanoid", "animations", "physicsDefinition", "blendSeconds", "movementThreshold", "headLook", "solverDefinition", "modelYawDegrees", "equipmentDefinition", "timing");
        if (number(root, "version", 0) != 1) throw new IllegalArgumentException("Character profile version must be 1");
        Map<String, HumanoidBone> humanoid = new LinkedHashMap<>();
        object(root, "humanoid").entrySet().forEach(entry -> {
            JsonElement value = entry.getValue();
            if (value.isJsonPrimitive() && value.getAsJsonPrimitive().isString())
                humanoid.put(entry.getKey(), new HumanoidBone(value.getAsString(), Rotation.IDENTITY));
            else {
                if (!value.isJsonObject()) throw new IllegalArgumentException("Humanoid binding must be a node string or object: " + entry.getKey());
                JsonObject bone = value.getAsJsonObject(); keys(bone, "node", "referenceRotation");
                Rotation rotation = Rotation.IDENTITY;
                if (bone.has("referenceRotation")) {
                    JsonArray values = array(bone, "referenceRotation", 4);
                    rotation = new Rotation(finite(values.get(0)), finite(values.get(1)), finite(values.get(2)), finite(values.get(3)));
                }
                humanoid.put(entry.getKey(), new HumanoidBone(text(bone, "node", null), rotation));
            }
        });
        Map<String, String> animations = new LinkedHashMap<>();
        object(root, "animations").entrySet().forEach(entry -> animations.put(entry.getKey(), string(entry.getValue(), entry.getKey())));
        JsonObject look = object(root, "headLook"); keys(look, "enabled", "forward", "maxYaw", "maxPitch");
        Direction forward = new Direction(0, 0, -1);
        if (look.has("forward")) {
            JsonArray values = array(look, "forward", 3);
            forward = new Direction(finite(values.get(0)), finite(values.get(1)), finite(values.get(2)));
        }
        JsonObject timing = object(root,"timing");
        keys(timing,"swingSync","movementSync","walkStride","runStride","crouchStride","minRate","maxRate");
        Timing defaults = Timing.DEFAULT;
        return new CharacterProfile(text(root, "model", null), number(root, "scale", 1), humanoid, animations,
                text(root, "physicsDefinition", null), number(root, "blendSeconds", .15), number(root, "movementThreshold", .02),
                new HeadLook(bool(look, "enabled", true), forward, (float)number(look, "maxYaw", 75), (float)number(look, "maxPitch", 60)),
                text(root,"solverDefinition",null),(float)number(root,"modelYawDegrees",180),text(root,"equipmentDefinition",null),
                new Timing(bool(timing,"swingSync",defaults.swingSync()),bool(timing,"movementSync",defaults.movementSync()),
                        number(timing,"walkStride",defaults.walkStride()),number(timing,"runStride",defaults.runStride()),
                        number(timing,"crouchStride",defaults.crouchStride()),(float)number(timing,"minRate",defaults.minRate()),
                        (float)number(timing,"maxRate",defaults.maxRate())));
    }

    private static void resource(String value, String field) {
        if (value == null || value.length() > 512 || !value.matches("[a-z0-9_.-]+:[a-z0-9_./-]+")
                || value.substring(value.indexOf(':') + 1).startsWith("/")
                || java.util.Arrays.asList(value.substring(value.indexOf(':') + 1).split("/", -1)).contains(".."))
            throw new IllegalArgumentException("Expected a namespaced resource ID for " + field);
    }
    private static void name(String value, String field) {
        if (value == null || value.isBlank() || value.length() > 256 || value.indexOf('\0') >= 0)
            throw new IllegalArgumentException(field + " must be a non-empty name of at most 256 characters");
    }
    private static void keys(JsonObject root, String... allowed) {
        Set<String> names = Set.of(allowed);
        for (String key : root.keySet()) if (!names.contains(key)) throw new IllegalArgumentException("Unknown character field: " + key);
    }
    private static JsonObject object(JsonObject parent, String key) {
        if (!parent.has(key)) return new JsonObject();
        if (!parent.get(key).isJsonObject()) throw new IllegalArgumentException("Expected object: " + key);
        return parent.getAsJsonObject(key);
    }
    private static JsonArray array(JsonObject parent, String key, int size) {
        if (!parent.get(key).isJsonArray() || parent.getAsJsonArray(key).size() != size)
            throw new IllegalArgumentException("Expected " + size + " components: " + key);
        return parent.getAsJsonArray(key);
    }
    private static String string(JsonElement value, String key) {
        if (!value.isJsonPrimitive() || !value.getAsJsonPrimitive().isString()) throw new IllegalArgumentException("Expected string: " + key);
        return value.getAsString();
    }
    private static String text(JsonObject root, String key, String fallback) { return root.has(key) ? string(root.get(key), key) : fallback; }
    private static double number(JsonObject root, String key, double fallback) {
        if (!root.has(key)) return fallback;
        JsonElement value = root.get(key);
        if (!value.isJsonPrimitive() || !value.getAsJsonPrimitive().isNumber() || !Double.isFinite(value.getAsDouble()))
            throw new IllegalArgumentException("Expected finite number: " + key);
        return value.getAsDouble();
    }
    private static float finite(JsonElement value) {
        if (!value.isJsonPrimitive() || !value.getAsJsonPrimitive().isNumber() || !Float.isFinite(value.getAsFloat()))
            throw new IllegalArgumentException("Expected finite float component");
        return value.getAsFloat();
    }
    private static boolean bool(JsonObject root, String key, boolean fallback) {
        if (!root.has(key)) return fallback;
        JsonElement value = root.get(key);
        if (!value.isJsonPrimitive() || !value.getAsJsonPrimitive().isBoolean()) throw new IllegalArgumentException("Expected boolean: " + key);
        return value.getAsBoolean();
    }
}
