package cn.minerealms.rigworks.character.core;

import com.google.gson.Gson;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;

import java.io.Reader;
import java.math.BigDecimal;
import java.util.Map;
import java.util.Set;

/** Sampling switches and local skeletal blend weights; contains no game or rendering state. */
public record VanillaPoseSelection(boolean enabled, boolean attack, boolean items, float root,
                                   float body, float head, float leftArm, float rightArm,
                                   float leftLeg, float rightLeg) {
    public static final VanillaPoseSelection ALL = new VanillaPoseSelection(true, true, true, 1, 1, 1, 1, 1, 1, 1);
    public static final VanillaPoseSelection MOTION = new VanillaPoseSelection(true, false, false, 1, 1, 1, 1, 1, 1, 1);
    private static final Gson GSON = new Gson();
    private static final Set<String> FIELDS = Set.of("version", "enabled", "attack", "items", "root", "body", "head", "leftArm", "rightArm", "leftLeg", "rightLeg");

    public VanillaPoseSelection {
        for (float value : new float[]{root, body, head, leftArm, rightArm, leftLeg, rightLeg})
            if (!Float.isFinite(value) || value < 0 || value > 1) throw new IllegalArgumentException("Vanilla channel weight must be in [0,1]");
    }

    public float weight(String role) {
        return switch (role) {
            case "hips", "spine", "chest", "upper_chest" -> body;
            case "neck", "head" -> head;
            case "left_upper_arm", "left_forearm", "left_hand" -> leftArm;
            case "right_upper_arm", "right_forearm", "right_hand" -> rightArm;
            case "left_thigh", "left_shin", "left_foot", "left_toes" -> leftLeg;
            case "right_thigh", "right_shin", "right_foot", "right_toes" -> rightLeg;
            // Preserve the previous full-pose reset for additional profile roles.
            default -> body == 1 && head == 1 && leftArm == 1 && rightArm == 1 && leftLeg == 1 && rightLeg == 1 ? 1 : 0;
        };
    }

    public static VanillaPoseSelection read(Reader reader) { return parse(JsonParser.parseReader(reader)); }
    public static VanillaPoseSelection fromMap(Map<String, ?> values) {
        if (values == null || values.size() > FIELDS.size()) throw new IllegalArgumentException("Invalid vanilla pose options");
        return parse(GSON.toJsonTree(values));
    }

    public static VanillaPoseSelection parse(JsonElement json) {
        if (json == null || !json.isJsonObject()) throw new IllegalArgumentException("Vanilla pose options must be an object");
        JsonObject object = json.getAsJsonObject();
        for (String key : object.keySet()) if (!FIELDS.contains(key)) throw new IllegalArgumentException("Unknown vanilla pose option: " + key);
        if (object.has("version")) {
            var value = object.get("version");
            if (!value.isJsonPrimitive() || !value.getAsJsonPrimitive().isNumber()
                    || value.getAsBigDecimal().compareTo(BigDecimal.ONE) != 0)
                throw new IllegalArgumentException("Vanilla pose version must be 1");
        }
        return new VanillaPoseSelection(flag(object,"enabled"), flag(object,"attack"), flag(object,"items"),
                weight(object,"root"), weight(object,"body"), weight(object,"head"), weight(object,"leftArm"),
                weight(object,"rightArm"), weight(object,"leftLeg"), weight(object,"rightLeg"));
    }

    private static boolean flag(JsonObject object, String key) {
        if (!object.has(key)) return true;
        var value = object.get(key);
        if (!value.isJsonPrimitive() || !value.getAsJsonPrimitive().isBoolean()) throw new IllegalArgumentException(key + " must be boolean");
        return value.getAsBoolean();
    }
    private static float weight(JsonObject object, String key) {
        if (!object.has(key)) return 1;
        var value = object.get(key);
        if (!value.isJsonPrimitive() || !value.getAsJsonPrimitive().isNumber()) throw new IllegalArgumentException(key + " must be a number");
        double number = value.getAsDouble();
        BigDecimal precise = value.getAsBigDecimal();
        if (!Double.isFinite(number) || precise.signum() < 0 || precise.compareTo(BigDecimal.ONE) > 0)
            throw new IllegalArgumentException(key + " must be in [0,1]");
        return (float) number;
    }
}
