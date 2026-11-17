package cn.minerealms.rigworks.character.core;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import org.joml.Vector3f;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;

/** Item-space support contacts. Selection policy is declarative and may be overridden each script tick. */
public record CharacterGripDefinition(String id,Set<String> items,Set<String> tags,Set<String> states,String primaryArm,
        CharacterEquipmentDefinition.Position socket,CharacterEquipmentDefinition.Position pole,
        CharacterEquipmentDefinition.Position handOffset,CharacterProfile.Rotation rotation,
        float minimumBend,float maximumBend,float weight,float orientationWeight,boolean freeSupport) {
    public CharacterGripDefinition {
        if(id==null||!id.matches("[A-Za-z0-9_.:/-]{1,128}"))throw new IllegalArgumentException("Invalid grip ID");
        items=Set.copyOf(items);tags=Set.copyOf(tags);states=Set.copyOf(states);
        if(!Set.of("any","left","right").contains(primaryArm))throw new IllegalArgumentException("Grip primary_arm must be any, left or right");
        if(items.size()+tags.size()>64 || states.isEmpty()||!Set.of("using","charged","held").containsAll(states)
                ||!Float.isFinite(minimumBend)||!Float.isFinite(maximumBend)||minimumBend<0||minimumBend>maximumBend||maximumBend>=180
                ||!Float.isFinite(weight)||weight<0||weight>1||!Float.isFinite(orientationWeight)||orientationWeight<0||orientationWeight>1)
            throw new IllegalArgumentException("Invalid grip bounds or selection");
        for(String selector:java.util.stream.Stream.concat(items.stream(),tags.stream()).toList())
            if(!selector.matches("[a-z0-9_.-]+:[a-z0-9_./-]+"))throw new IllegalArgumentException("Invalid grip item/tag ID");
        java.util.Objects.requireNonNull(socket);java.util.Objects.requireNonNull(pole);
        java.util.Objects.requireNonNull(handOffset);java.util.Objects.requireNonNull(rotation);
        if(orientationWeight!=1 && (handOffset.x()!=0||handOffset.y()!=0||handOffset.z()!=0))
            throw new IllegalArgumentException("A hand contact offset requires full orientation weight");
    }
    public Vector3f socketVector(){return vector(socket);}
    public Vector3f poleVector(){return vector(pole);}
    public Vector3f handOffsetVector(){return vector(handOffset);}
    private static Vector3f vector(CharacterEquipmentDefinition.Position point){return new Vector3f(point.x(),point.y(),point.z());}

    public static List<CharacterGripDefinition> read(JsonElement input) {
        if(input==null)return List.of();
        if(!input.isJsonArray()||input.getAsJsonArray().size()>32)throw new IllegalArgumentException("Equipment grips require at most 32 rules");
        var result=new ArrayList<CharacterGripDefinition>();var ids=new java.util.HashSet<String>();
        for(var element:input.getAsJsonArray()) {
            if(!element.isJsonObject())throw new IllegalArgumentException("Grip must be an object");
            var value=element.getAsJsonObject();
            for(String key:value.keySet())if(!Set.of("id","items","tags","states","primary_arm","socket","pole","hand_offset","rotation","min_bend","max_bend","weight","orientation_weight","free_support").contains(key))
                throw new IllegalArgumentException("Unknown grip field: "+key);
            String id=text(value.get("id"));if(!ids.add(id))throw new IllegalArgumentException("Duplicate grip ID: "+id);
            var q=array(value,"rotation",new float[]{0,0,0,1});
            result.add(new CharacterGripDefinition(id,strings(value,"items",Set.of()),strings(value,"tags",Set.of()),
                    strings(value,"states",Set.of("using")),value.has("primary_arm")?text(value.get("primary_arm")):"any",point(value,"socket",new float[]{0,0,0}),
                    point(value,"pole",new float[]{.3f,-.5f,0}),point(value,"hand_offset",new float[]{0,0,0}),
                    new CharacterProfile.Rotation(q[0],q[1],q[2],q[3]),number(value,"min_bend",2),number(value,"max_bend",160),
                    number(value,"weight",1),number(value,"orientation_weight",0),bool(value,"free_support",false)));
        }
        return List.copyOf(result);
    }
    private static Set<String> strings(JsonObject value,String key,Set<String> fallback) {
        if(!value.has(key))return fallback;
        var input=value.get(key);if(!input.isJsonArray()||input.getAsJsonArray().size()>64)throw new IllegalArgumentException("Grip list required: "+key);
        var values=new java.util.LinkedHashSet<String>();for(var entry:input.getAsJsonArray())if(!values.add(text(entry)))throw new IllegalArgumentException("Duplicate grip selector");return values;
    }
    private static String text(JsonElement value){if(value==null||!value.isJsonPrimitive()||!value.getAsJsonPrimitive().isString())throw new IllegalArgumentException("Grip string required");return value.getAsString();}
    private static float number(JsonObject value,String key,float fallback){return value.has(key)?number(value.get(key)):fallback;}
    private static float number(JsonElement value){if(!value.isJsonPrimitive()||!value.getAsJsonPrimitive().isNumber()||!Float.isFinite(value.getAsFloat()))throw new IllegalArgumentException("Finite grip number required");return value.getAsFloat();}
    private static boolean bool(JsonObject value,String key,boolean fallback){if(!value.has(key))return fallback;var entry=value.get(key);if(!entry.isJsonPrimitive()||!entry.getAsJsonPrimitive().isBoolean())throw new IllegalArgumentException("Grip boolean required");return entry.getAsBoolean();}
    private static float[] array(JsonObject value,String key,float[] fallback){if(!value.has(key))return fallback;var input=value.get(key);if(!input.isJsonArray()||input.getAsJsonArray().size()!=fallback.length)throw new IllegalArgumentException("Grip vector dimension: "+key);float[] result=new float[fallback.length];for(int i=0;i<result.length;i++)result[i]=number(input.getAsJsonArray().get(i));return result;}
    private static CharacterEquipmentDefinition.Position point(JsonObject value,String key,float[] fallback){float[] point=array(value,key,fallback);return new CharacterEquipmentDefinition.Position(point[0],point[1],point[2]);}
}
