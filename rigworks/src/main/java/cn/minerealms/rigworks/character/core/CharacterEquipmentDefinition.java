package cn.minerealms.rigworks.character.core;

import cn.minerealms.assimploader.animation.PoseSnapshot;
import com.google.gson.JsonElement;
import com.google.gson.JsonArray;
import com.google.gson.JsonNull;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.google.gson.GsonBuilder;
import org.joml.Matrix4f;
import org.joml.Matrix4fc;
import org.joml.Quaternionf;
import org.joml.Vector3f;

import java.io.Reader;
import java.io.Writer;
import java.io.IOException;
import java.util.Objects;
import java.util.Set;

/** Physical-hand sockets. Offsets use metres and do not inherit imported FBX bone scale. */
public record CharacterEquipmentDefinition(boolean enabled, Mount rightHand, Mount leftHand, java.util.List<CharacterGripDefinition> grips,
                                           java.util.Map<String, Mount> sockets, java.util.List<CharacterEquipmentRule> overrides) {
    public CharacterEquipmentDefinition {
        grips=java.util.List.copyOf(grips); sockets=java.util.Map.copyOf(sockets); overrides=java.util.List.copyOf(overrides);
        if(sockets.size()>64 || overrides.size()>64)throw new IllegalArgumentException("At most 64 equipment sockets and rules");
        sockets.forEach((name,mount)->{if(!name.matches("[a-z][a-z0-9_]{0,63}"))throw new IllegalArgumentException("Invalid socket name: "+name);Objects.requireNonNull(mount);});
        for(var rule:overrides) if(rule.socket()!=null && !sockets.containsKey(rule.socket()))throw new IllegalArgumentException("Unknown override socket: "+rule.socket());
    }
    public CharacterEquipmentDefinition(boolean enabled,Mount rightHand,Mount leftHand,java.util.List<CharacterGripDefinition> grips){this(enabled,rightHand,leftHand,grips,java.util.Map.of(),java.util.List.of());}
    public CharacterEquipmentDefinition(boolean enabled,Mount rightHand,Mount leftHand){this(enabled,rightHand,leftHand,java.util.List.of());}

    public CharacterEquipmentDefinition withHands(Mount right, Mount left) {return new CharacterEquipmentDefinition(enabled,right,left,grips,sockets,overrides);}
    public Mount socket(String name) {return sockets.get(name);}
    public Mount select(String arm,String item,Set<String> tags,String state,String useAnimation,String context) {
        var override=override(arm,item,tags,state,useAnimation,context);
        return override!=null?override:arm.equals("left")?leftHand:rightHand;
    }
    public Mount override(String arm,String item,Set<String> tags,String state,String useAnimation,String context) {
        for(var rule:overrides) if(rule.matches(arm,item,tags,state,useAnimation,context)) return rule.mount()!=null?rule.mount():sockets.get(rule.socket());
        return null;
    }
    public record Position(float x, float y, float z) {
        public Position {
            if (!Float.isFinite(x) || !Float.isFinite(y) || !Float.isFinite(z)
                    || Math.abs(x) > 10 || Math.abs(y) > 10 || Math.abs(z) > 10)
                throw new IllegalArgumentException("Equipment offset must be finite metres in [-10,10]");
        }
    }

    public record Mount(String node, Position position, CharacterProfile.Rotation rotation, float scale) {
        public Mount {
            if (node == null || node.isBlank() || node.length() > 256 || node.indexOf('\0') >= 0)
                throw new IllegalArgumentException("Invalid equipment node");
            Objects.requireNonNull(position); Objects.requireNonNull(rotation);
            if (!Float.isFinite(scale) || scale < .001f || scale > 10)
                throw new IllegalArgumentException("Equipment item scale must be in [0.001,10]");
        }

        public Matrix4f transform(PoseSnapshot snapshot) {
            Matrix4f bone = Objects.requireNonNull(snapshot).getNodeTransform(node);
            if (bone == null) throw new IllegalArgumentException("Unknown equipment node: " + node);
            return transform(bone);
        }

        public Matrix4f transform(Matrix4fc bone) {
            if (!bone.isFinite() || bone.determinant3x3() <= 1e-12f)
                throw new IllegalArgumentException("Equipment bone must have a finite nonsingular positive frame");
            Vector3f lengths = bone.getScale(new Vector3f());
            if (Math.min(lengths.x, Math.min(lengths.y, lengths.z)) < 1e-6f)
                throw new IllegalArgumentException("Equipment bone scale is singular");
            Quaternionf orientation = bone.getUnnormalizedRotation(new Quaternionf()).normalize();
            return new Matrix4f().translationRotate(bone.getTranslation(new Vector3f()), orientation)
                    .translate(position.x, position.y, position.z).rotate(rotation.quaternion()).scale(scale);
        }
    }

    public static CharacterEquipmentDefinition defaults(CharacterProfile profile) {
        return new CharacterEquipmentDefinition(true, defaultMount(profile, "right_hand"), defaultMount(profile, "left_hand"));
    }

    private static Mount defaultMount(CharacterProfile profile, String role) {
        var bone = profile.bone(role);
        if(bone==null)return null;
        var rotation=bone.referenceRotation().quaternion().rotateX(-(float)Math.PI/2).rotateY((float)Math.PI);
        return new Mount(bone.node(), new Position(0, 0, 0),
                new CharacterProfile.Rotation(rotation.x,rotation.y,rotation.z,rotation.w), 1);
    }

    public static CharacterEquipmentDefinition read(Reader reader) {
        JsonElement value = JsonParser.parseReader(reader);
        if (!value.isJsonObject()) throw new IllegalArgumentException("Equipment definition must be an object");
        JsonObject root = value.getAsJsonObject();
        keys(root, "version", "enabled", "right_hand", "left_hand", "grips", "sockets", "overrides");
        if (!root.has("version") || number(root.get("version")) != 1)
            throw new IllegalArgumentException("Equipment definition version must be 1");
        boolean enabled = true;
        if (root.has("enabled")) {
            if (!root.get("enabled").isJsonPrimitive() || !root.getAsJsonPrimitive("enabled").isBoolean())
                throw new IllegalArgumentException("Equipment enabled must be boolean");
            enabled = root.get("enabled").getAsBoolean();
        }
        var sockets = new java.util.LinkedHashMap<String,Mount>();
        if(root.has("sockets")) {
            if(!root.get("sockets").isJsonObject())throw new IllegalArgumentException("Equipment sockets must be an object");
            var entries=root.getAsJsonObject("sockets");
            entries.keySet().forEach(name->{var socket=mount(entries,name);if(socket==null)throw new IllegalArgumentException("Named sockets cannot be null");sockets.put(name,socket);});
        }
        return new CharacterEquipmentDefinition(enabled, mount(root, "right_hand"), mount(root, "left_hand"),CharacterGripDefinition.read(root.get("grips")),sockets,CharacterEquipmentRule.read(root.get("overrides")));
    }

    /** Standard resource schema, also used for local editor overrides. */
    public JsonObject toJson() {
        JsonObject root = new JsonObject();
        root.addProperty("version", 1);
        root.addProperty("enabled", enabled);
        root.add("right_hand", mountJson(rightHand));
        root.add("left_hand", mountJson(leftHand));
        JsonArray rules = new JsonArray();
        for (var grip : grips) {
            JsonObject rule = new JsonObject();
            rule.addProperty("id", grip.id());
            rule.add("items", strings(grip.items()));
            rule.add("tags", strings(grip.tags()));
            rule.add("states", strings(grip.states()));
            rule.addProperty("primary_arm", grip.primaryArm());
            rule.add("socket", point(grip.socket()));
            rule.add("pole", point(grip.pole()));
            rule.add("hand_offset", point(grip.handOffset()));
            rule.add("rotation", rotation(grip.rotation()));
            rule.addProperty("min_bend", grip.minimumBend());
            rule.addProperty("max_bend", grip.maximumBend());
            rule.addProperty("weight", grip.weight());
            rule.addProperty("orientation_weight", grip.orientationWeight());
            rule.addProperty("free_support", grip.freeSupport());
            rules.add(rule);
        }
        root.add("grips", rules);
        JsonObject named = new JsonObject();
        sockets.entrySet().stream().sorted(java.util.Map.Entry.comparingByKey()).forEach(entry->named.add(entry.getKey(),mountJson(entry.getValue())));
        root.add("sockets",named);
        JsonArray selections = new JsonArray();
        overrides.forEach(rule->selections.add(rule.toJson()));
        root.add("overrides",selections);
        return root;
    }

    public void write(Writer writer) throws IOException {
        new GsonBuilder().setPrettyPrinting().serializeNulls().create().toJson(toJson(), writer);
        writer.write('\n');
    }

    static JsonElement mountJson(Mount mount) {
        if (mount == null) return JsonNull.INSTANCE;
        JsonObject value = new JsonObject();
        value.addProperty("node", mount.node());
        value.add("position", point(mount.position()));
        value.add("rotation", rotation(mount.rotation()));
        value.addProperty("scale", mount.scale());
        return value;
    }

    private static JsonArray point(Position value) { return numbers(value.x(), value.y(), value.z()); }
    private static JsonArray rotation(CharacterProfile.Rotation value) { return numbers(value.x(), value.y(), value.z(), value.w()); }
    private static JsonArray numbers(float... values) {
        JsonArray array = new JsonArray();
        for (float value : values) array.add(value);
        return array;
    }
    private static JsonArray strings(Set<String> values) {
        JsonArray array = new JsonArray();
        values.stream().sorted().forEach(array::add);
        return array;
    }

    static Mount mount(JsonObject root, String key) {
        if (!root.has(key) || root.get(key).isJsonNull()) return null;
        if (!root.get(key).isJsonObject()) throw new IllegalArgumentException("Expected equipment socket object: " + key);
        JsonObject value = root.getAsJsonObject(key);
        keys(value, "node", "position", "rotation", "scale");
        if (!value.has("node") || !value.get("node").isJsonPrimitive() || !value.getAsJsonPrimitive("node").isString())
            throw new IllegalArgumentException("Equipment socket requires a node string");
        float[] position = array(value, "position", new float[]{0, 0, 0});
        float[] rotation = array(value, "rotation", new float[]{0, 0, 0, 1});
        return new Mount(value.get("node").getAsString(), new Position(position[0], position[1], position[2]),
                new CharacterProfile.Rotation(rotation[0], rotation[1], rotation[2], rotation[3]),
                value.has("scale") ? number(value.get("scale")) : 1);
    }

    private static float[] array(JsonObject object, String key, float[] fallback) {
        if (!object.has(key)) return fallback;
        if (!object.get(key).isJsonArray() || object.getAsJsonArray(key).size() != fallback.length)
            throw new IllegalArgumentException("Invalid equipment " + key + " components");
        float[] result = new float[fallback.length];
        for (int i = 0; i < result.length; i++) result[i] = number(object.getAsJsonArray(key).get(i));
        return result;
    }

    private static float number(JsonElement value) {
        if (!value.isJsonPrimitive() || !value.getAsJsonPrimitive().isNumber() || !Float.isFinite(value.getAsFloat()))
            throw new IllegalArgumentException("Expected finite equipment number");
        return value.getAsFloat();
    }

    private static void keys(JsonObject object, String... allowed) {
        Set<String> keys = Set.of(allowed);
        for (String key : object.keySet()) if (!keys.contains(key)) throw new IllegalArgumentException("Unknown equipment field: " + key);
    }
}
