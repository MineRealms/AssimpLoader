package cn.minerealms.rigworks.character.core;

import cn.minerealms.assimploader.character.physics.CharacterPhysicsDefinition;
import cn.minerealms.assimploader.character.solver.CharacterRigDefinition;
import com.google.gson.*;
import com.google.gson.stream.JsonReader;
import com.google.gson.stream.JsonToken;
import org.joml.Matrix4f;

import java.io.IOException;
import java.io.StringReader;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;
import java.util.Objects;
import java.util.Set;

/** Declarative visual state only. Model bytes and executable scripts are never accepted here. */
public final class CharacterVisualConfig {
    public static final int MAX_BYTES = 131072;
    private static final Set<String> FIELDS = Set.of("version", "modelHash", "profile", "equipment", "root", "rig", "physics", "options");
    public record Options(boolean handContacts,boolean physics,boolean groundPlacement,boolean effects) {
        public static final Options DEFAULT=new Options(false,true,true,false);
        JsonObject json() {
            var value=new JsonObject();value.addProperty("handContacts",handContacts);value.addProperty("physics",physics);
            value.addProperty("groundPlacement",groundPlacement);value.addProperty("effects",effects);return value;
        }
        static Options parse(JsonObject value) {
            if(!Set.of("handContacts","physics","groundPlacement","effects").containsAll(value.keySet()))throw new IllegalArgumentException("Unknown character visual option");
            for(var e:value.entrySet())if(!e.getValue().isJsonPrimitive()||!e.getValue().getAsJsonPrimitive().isBoolean())throw new IllegalArgumentException("Character visual options require booleans");
            return new Options(value.has("handContacts")&&value.get("handContacts").getAsBoolean(),!value.has("physics")||value.get("physics").getAsBoolean(),
                    !value.has("groundPlacement")||value.get("groundPlacement").getAsBoolean(),value.has("effects")&&value.get("effects").getAsBoolean());
        }
    }
    public record Transform(float x, float y, float z, float rx, float ry, float rz, float sx, float sy, float sz) {
        public static final Transform IDENTITY = new Transform(0,0,0,0,0,0,1,1,1);
        public Transform {
            for (float value : new float[]{x,y,z,rx,ry,rz,sx,sy,sz})
                if (!Float.isFinite(value)) throw new IllegalArgumentException("Non-finite visual root transform");
            if (Math.max(Math.max(Math.abs(x),Math.abs(y)),Math.abs(z)) > 10
                    || Math.max(Math.max(Math.abs(rx),Math.abs(ry)),Math.abs(rz)) > 3600
                    || Math.min(Math.min(sx,sy),sz) < .001f || Math.max(Math.max(sx,sy),sz) > 10)
                throw new IllegalArgumentException("Visual root position/rotation/scale exceeds bounds");
        }
        public Matrix4f matrix() {
            return new Matrix4f().translation(x,y,z).rotateXYZ((float)Math.toRadians(rx),(float)Math.toRadians(ry),(float)Math.toRadians(rz)).scale(sx,sy,sz);
        }
        JsonArray json() { JsonArray a=new JsonArray(); for(float v:new float[]{x,y,z,rx,ry,rz,sx,sy,sz})a.add(v);return a; }
        static Transform parse(JsonArray a) {
            if(a.size()!=9)throw new IllegalArgumentException("Visual root needs 9 components");
            float[] v=new float[9];for(int i=0;i<9;i++) {
                if(!a.get(i).isJsonPrimitive()||!a.get(i).getAsJsonPrimitive().isNumber())throw new IllegalArgumentException("Visual root requires numeric components");
                v[i]=a.get(i).getAsFloat();
            }
            return new Transform(v[0],v[1],v[2],v[3],v[4],v[5],v[6],v[7],v[8]);
        }
    }
    private final CharacterProfile profile;
    private final CharacterEquipmentDefinition equipment;
    private final Transform transform;
    private final Options options;
    private final String modelHash, rig, physics, json, digest;

    public CharacterVisualConfig(CharacterProfile profile, CharacterEquipmentDefinition equipment, Transform transform,
                                 String modelHash, String rig, String physics) {
        this(profile,equipment,transform,modelHash,rig,physics,Options.DEFAULT);
    }
    public CharacterVisualConfig(CharacterProfile profile, CharacterEquipmentDefinition equipment, Transform transform,
                                 String modelHash, String rig, String physics,Options options) {
        this.profile=Objects.requireNonNull(profile);this.equipment=Objects.requireNonNull(equipment);
        this.transform=Objects.requireNonNull(transform);
        this.options=Objects.requireNonNull(options);
        if(modelHash==null||!modelHash.matches("[a-f0-9]{64}"))throw new IllegalArgumentException("Expected SHA-256 model identity");
        this.modelHash=modelHash;
        JsonObject solver=rig==null?null:object(parseBounded(rig));
        if(solver!=null)validateRig(solver);
        JsonObject bodies=physics==null?null:object(parseBounded(physics));
        if(bodies!=null) {
            var canonical=bodies.deepCopy();
            if(canonical.has("joints"))for(var entry:canonical.getAsJsonArray("joints"))entry.getAsJsonObject().remove("anchorBone");
            CharacterPhysicsDefinition.parse(canonical);
        }
        this.rig=solver==null?null:canonical(solver).toString();this.physics=bodies==null?null:canonical(bodies).toString();
        JsonObject root=new JsonObject();root.addProperty("version",1);root.addProperty("modelHash",modelHash);
        root.add("profile",profile.toJson());root.add("equipment",equipment.toJson());root.add("root",transform.json());root.add("options",options.json());
        if(solver!=null)root.add("rig",solver);if(bodies!=null)root.add("physics",bodies);
        json=canonical(root).toString();checkSize(json);digest=hash(json.getBytes(StandardCharsets.UTF_8));
    }
    public CharacterProfile profile(){return profile;}
    public CharacterEquipmentDefinition equipment(){return equipment;}
    public Transform transform(){return transform;}
    public Options options(){return options;}
    public String modelHash(){return modelHash;}
    public String rig(){return rig;}
    public String physics(){return physics;}
    public String json(){return json;}
    public String digest(){return digest;}
    public CharacterVisualConfig withEquipment(CharacterEquipmentDefinition value) {
        return new CharacterVisualConfig(profile,value,transform,modelHash,rig,physics,options);
    }
    public CharacterVisualConfig withOptions(Options value){return new CharacterVisualConfig(profile,equipment,transform,modelHash,rig,physics,value);}
    public static CharacterVisualConfig parse(String json) {
        JsonObject root=object(parseBounded(json));
        if(!FIELDS.containsAll(root.keySet())||!root.has("version")||!root.get("version").isJsonPrimitive()
                ||!root.getAsJsonPrimitive("version").isNumber()||root.get("version").getAsDouble()!=1)
            throw new IllegalArgumentException("Unsupported shared visual schema");
        return new CharacterVisualConfig(CharacterProfile.parse(object(root.get("profile"))),
                CharacterEquipmentDefinition.read(new StringReader(object(root.get("equipment")).toString())),
                root.has("root")?Transform.parse(root.getAsJsonArray("root")):Transform.IDENTITY,
                root.get("modelHash").getAsString(),root.has("rig")?root.get("rig").toString():null,
                root.has("physics")?root.get("physics").toString():null,root.has("options")?Options.parse(object(root.get("options"))):Options.DEFAULT);
    }
    public static JsonElement parseBounded(String text) {
        checkSize(text);
        try (JsonReader reader=new JsonReader(new StringReader(text))) {
            reader.setLenient(false);
            JsonElement value=read(reader,0,new int[1]);
            if(reader.peek()!=JsonToken.END_DOCUMENT)throw new IllegalArgumentException("Trailing shared configuration data");
            return value;
        } catch(IOException|IllegalStateException e) {throw new IllegalArgumentException("Invalid shared configuration",e);}
    }
    public static String canonicalResource(String text) {return canonical(parseBounded(text)).toString();}
    private static JsonElement read(JsonReader reader,int depth,int[] nodes)throws IOException {
        if(depth>24||++nodes[0]>12000)throw new IllegalArgumentException("Shared configuration complexity limit");
        return switch(reader.peek()) {
            case BEGIN_OBJECT -> {
                reader.beginObject();JsonObject value=new JsonObject();
                while(reader.hasNext()) {
                    String key=reader.nextName();
                    if(key.length()>256||value.has(key)||value.size()>=256)throw new IllegalArgumentException("Invalid or duplicate configuration field");
                    value.add(key,read(reader,depth+1,nodes));
                }
                reader.endObject();yield value;
            }
            case BEGIN_ARRAY -> {
                reader.beginArray();JsonArray value=new JsonArray();
                while(reader.hasNext()){if(value.size()>=256)throw new IllegalArgumentException("Shared configuration array limit");value.add(read(reader,depth+1,nodes));}
                reader.endArray();yield value;
            }
            case STRING -> {String value=reader.nextString();if(value.length()>1024||value.indexOf('\0')>=0)throw new IllegalArgumentException("Invalid configuration string");yield new JsonPrimitive(value);}
            case BOOLEAN -> new JsonPrimitive(reader.nextBoolean());
            case NULL -> {reader.nextNull();yield JsonNull.INSTANCE;}
            case NUMBER -> {double value=reader.nextDouble();if(!Double.isFinite(value)||Math.abs(value)>1e8)throw new IllegalArgumentException("Invalid configuration number");yield new JsonPrimitive(value);}
            default -> throw new IllegalArgumentException("Unexpected configuration token");
        };
    }
    private static void validateRig(JsonObject rig) {
        CharacterRigDefinition.read(new StringReader(rig.toString()));
        int work=0;
        if(rig.has("ik")&&rig.getAsJsonObject("ik").size()>16)throw new IllegalArgumentException("At most 16 shared IK chains");
        if(rig.has("ik"))for(var entry:rig.getAsJsonObject("ik").entrySet()) {
            JsonObject ik=object(entry.getValue());
            if(ik.has("iterations")&&(ik.get("iterations").getAsDouble()!=ik.get("iterations").getAsInt()
                    ||ik.get("iterations").getAsInt()<1||ik.get("iterations").getAsInt()>64))
                throw new IllegalArgumentException("Shared IK iterations must be in [1,64]");
            if(ik.has("chain")&&ik.getAsJsonArray("chain").size()>32)throw new IllegalArgumentException("Shared IK chain limit");
            work+=(ik.has("iterations")?ik.get("iterations").getAsInt():32)*(ik.has("chain")?ik.getAsJsonArray("chain").size():0);
            if(work>4096)throw new IllegalArgumentException("Shared IK solver work budget exceeded");
        }
    }
    private static JsonObject object(JsonElement value) {
        if(value==null||!value.isJsonObject())throw new IllegalArgumentException("Expected shared configuration object");return value.getAsJsonObject();
    }
    private static JsonElement canonical(JsonElement value) {
        if(value.isJsonObject()) {JsonObject o=new JsonObject();value.getAsJsonObject().entrySet().stream().sorted(java.util.Map.Entry.comparingByKey()).forEach(e->o.add(e.getKey(),canonical(e.getValue())));return o;}
        if(value.isJsonArray()){JsonArray a=new JsonArray();value.getAsJsonArray().forEach(e->a.add(canonical(e)));return a;}return value.deepCopy();
    }
    private static void checkSize(String value) {
        if(value==null||value.length()>MAX_BYTES||value.getBytes(StandardCharsets.UTF_8).length>MAX_BYTES)
            throw new IllegalArgumentException("Shared visual configuration exceeds 128 KiB");
    }
    public static String hash(byte[] value) {
        try{return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(value));}
        catch(NoSuchAlgorithmException e){throw new IllegalStateException(e);}
    }
    @Override public boolean equals(Object value){return value instanceof CharacterVisualConfig config&&json.equals(config.json);}
    @Override public int hashCode(){return json.hashCode();}
}
