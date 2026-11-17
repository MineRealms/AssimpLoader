package cn.minerealms.assimploader.render;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;

import java.io.Reader;
import java.math.BigDecimal;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Set;

/** Immutable shader parameter schema. Parsing and validation require neither Minecraft nor OpenGL. */
public final class ShaderParameterDefinition {
    public static final double MAX_ABSOLUTE_VALUE = 1_000_000;
    public static final int MAX_UNIFORMS = 64;

    public enum Type {
        INT(1), FLOAT(1), VEC2(2), VEC3(3), VEC4(4);
        private final int components;
        Type(int components) { this.components = components; }
        public int components() { return components; }
    }

    public static final class Uniform {
        private final Type type;
        private final double[] minimum, maximum;
        private final float[] defaults, disabled;

        private Uniform(Type type, double[] minimum, double[] maximum, double[] defaults, double[] disabled) {
            this.type = type;
            this.minimum = minimum.clone();
            this.maximum = maximum.clone();
            this.defaults = validate(defaults);
            // A disabled sentinel may be outside the active range, but must satisfy the type and global bound.
            this.disabled = convert(type, disabled);
        }

        public Type type() { return type; }
        public float[] defaults() { return defaults.clone(); }
        public float[] disabled() { return disabled.clone(); }
        public double[] minimum() { return minimum.clone(); }
        public double[] maximum() { return maximum.clone(); }

        public float[] validate(double[] values) {
            if (values == null || values.length != type.components())
                throw new IllegalArgumentException("Uniform component count does not match " + type);
            for (int index = 0; index < values.length; index++) {
                if (values[index] < minimum[index] || values[index] > maximum[index])
                    throw new IllegalArgumentException("Uniform component outside declared min/max range");
            }
            return convert(type, values);
        }
    }

    private final boolean enabled;
    private final Map<String, Uniform> uniforms;

    private ShaderParameterDefinition(boolean enabled, Map<String, Uniform> uniforms) {
        this.enabled = enabled;
        this.uniforms = Collections.unmodifiableMap(new LinkedHashMap<>(uniforms));
    }

    public boolean enabled() { return enabled; }
    public Map<String, Uniform> uniforms() { return uniforms; }
    public static ShaderParameterDefinition read(Reader reader) { return parse(JsonParser.parseReader(reader)); }

    public static ShaderParameterDefinition parse(JsonElement element) {
        JsonObject root = object(element, "Shader parameter definition");
        keys(root, Set.of("version", "enabled", "uniforms"));
        var version = root.get("version");
        if (version == null || !version.isJsonPrimitive() || !version.getAsJsonPrimitive().isNumber()
                || !version.getAsString().equals("1"))
            throw new IllegalArgumentException("Shader parameter version must be the integer 1");
        boolean enabled = false;
        if (root.has("enabled")) {
            var value = root.get("enabled");
            if (!value.isJsonPrimitive() || !value.getAsJsonPrimitive().isBoolean())
                throw new IllegalArgumentException("Shader parameter enabled must be boolean");
            enabled = value.getAsBoolean();
        }
        JsonObject declarations = object(root.get("uniforms"), "Shader uniforms");
        if (declarations.size() > MAX_UNIFORMS) throw new IllegalArgumentException("Shader uniform limit exceeded");
        var uniforms = new LinkedHashMap<String, Uniform>();
        for (var entry : declarations.entrySet()) {
            if (!entry.getKey().matches("[A-Za-z_][A-Za-z0-9_]{0,95}"))
                throw new IllegalArgumentException("Invalid shader uniform name: " + entry.getKey());
            JsonObject spec = object(entry.getValue(), "Uniform " + entry.getKey());
            keys(spec, Set.of("type", "default", "disabled", "min", "max"));
            var typeValue = spec.get("type");
            if (typeValue == null || !typeValue.isJsonPrimitive() || !typeValue.getAsJsonPrimitive().isString())
                throw new IllegalArgumentException("Uniform requires a string type");
            Type type = switch (typeValue.getAsString()) {
                case "int" -> Type.INT;
                case "float" -> Type.FLOAT;
                case "vec2" -> Type.VEC2;
                case "vec3" -> Type.VEC3;
                case "vec4" -> Type.VEC4;
                default -> throw new IllegalArgumentException("Unsupported shader uniform type");
            };
            double[] minimum = bound(spec, "min", type.components(), -MAX_ABSOLUTE_VALUE);
            double[] maximum = bound(spec, "max", type.components(), MAX_ABSOLUTE_VALUE);
            for (int index = 0; index < minimum.length; index++) {
                if (minimum[index] > maximum[index]) throw new IllegalArgumentException("Uniform min exceeds max");
            }
            double[] defaults = vector(spec.get("default"), type);
            double[] disabled = spec.has("disabled") ? vector(spec.get("disabled"), type) : new double[type.components()];
            uniforms.put(entry.getKey(), new Uniform(type, minimum, maximum, defaults, disabled));
        }
        return new ShaderParameterDefinition(enabled, uniforms);
    }

    private static double[] bound(JsonObject spec, String key, int size, double fallback) {
        double[] result = new double[size];
        if (!spec.has(key)) {
            java.util.Arrays.fill(result, fallback);
            return result;
        }
        JsonElement value = spec.get(key);
        if (value.isJsonArray()) {
            if (value.getAsJsonArray().size() != size) throw new IllegalArgumentException("Uniform range component count");
            for (int index = 0; index < size; index++) result[index] = numeric(value.getAsJsonArray().get(index), false);
        } else java.util.Arrays.fill(result, numeric(value, false));
        return result;
    }

    private static double[] vector(JsonElement value, Type type) {
        int size = type.components();
        if (size == 1 && value != null && value.isJsonPrimitive()) return new double[]{numeric(value, type == Type.INT)};
        if (value == null || !value.isJsonArray() || value.getAsJsonArray().size() != size)
            throw new IllegalArgumentException("Uniform value component count does not match " + type);
        double[] result = new double[size];
        for (int index = 0; index < size; index++) result[index] = numeric(value.getAsJsonArray().get(index), type == Type.INT);
        return result;
    }

    private static double numeric(JsonElement value, boolean integer) {
        if (value == null || !value.isJsonPrimitive() || !value.getAsJsonPrimitive().isNumber())
            throw new IllegalArgumentException("Uniform component must be a JSON number");
        try {
            BigDecimal decimal = value.getAsBigDecimal();
            if (decimal.abs().compareTo(BigDecimal.valueOf(MAX_ABSOLUTE_VALUE)) > 0)
                throw new IllegalArgumentException("Uniform exceeds global numeric bound");
            if (integer) decimal.intValueExact();
            double result = decimal.doubleValue();
            if (!Double.isFinite(result)) throw new IllegalArgumentException("Nonfinite uniform component");
            return result;
        } catch (NumberFormatException | ArithmeticException exception) {
            throw new IllegalArgumentException("Invalid numeric shader component", exception);
        }
    }

    private static float[] convert(Type type, double[] values) {
        if (values == null || values.length != type.components()) throw new IllegalArgumentException("Uniform component count");
        float[] result = new float[values.length];
        for (int index = 0; index < values.length; index++) {
            double value = values[index];
            if (!Double.isFinite(value) || Math.abs(value) > MAX_ABSOLUTE_VALUE)
                throw new IllegalArgumentException("Uniform exceeds global numeric bound");
            // Validate before float conversion: values such as 1.00000001 round to 1f.
            if (type == Type.INT && value != Math.rint(value)) throw new IllegalArgumentException("Integer uniform required");
            result[index] = (float) value;
        }
        return result;
    }

    private static JsonObject object(JsonElement value, String label) {
        if (value == null || !value.isJsonObject()) throw new IllegalArgumentException(label + " must be an object");
        return value.getAsJsonObject();
    }
    private static void keys(JsonObject value, Set<String> allowed) {
        for (String key : value.keySet()) if (!allowed.contains(key)) throw new IllegalArgumentException("Unknown shader schema field: " + key);
    }
}
