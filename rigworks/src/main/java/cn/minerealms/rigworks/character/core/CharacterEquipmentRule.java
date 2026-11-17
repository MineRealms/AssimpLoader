package cn.minerealms.rigworks.character.core;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;

/** First matching rule wins; all selectors describe physical hands, independent of main-hand preference. */
public record CharacterEquipmentRule(String arm, Set<String> items, Set<String> tags, Set<String> states,
                                     Set<String> useAnimations, Set<String> contexts, String socket,
                                     CharacterEquipmentDefinition.Mount mount) {
    public CharacterEquipmentRule {
        if(!Set.of("any","left","right").contains(arm))throw new IllegalArgumentException("Equipment arm must be any, left or right");
        items=Set.copyOf(items);tags=Set.copyOf(tags);states=Set.copyOf(states);useAnimations=Set.copyOf(useAnimations);contexts=Set.copyOf(contexts);
        if(items.size()+tags.size()>64 || !Set.of("held","using","charged").containsAll(states))throw new IllegalArgumentException("Invalid equipment selectors");
        for(String id:java.util.stream.Stream.concat(items.stream(),tags.stream()).toList())if(!id.matches("[a-z0-9_.-]+:[a-z0-9_./-]+"))throw new IllegalArgumentException("Invalid equipment resource ID");
        if((socket==null)==(mount==null))throw new IllegalArgumentException("Equipment rule requires exactly one socket or mount");
        if(socket!=null&&!socket.matches("[a-z][a-z0-9_]{0,63}"))throw new IllegalArgumentException("Invalid equipment socket");
        if(!Set.of("none","eat","drink","block","bow","spear","crossbow","spyglass","toot_horn","brush").containsAll(useAnimations))throw new IllegalArgumentException("Unknown use animation");
        if(!Set.of("third_person_left_hand","third_person_right_hand","first_person_left_hand","first_person_right_hand","head").containsAll(contexts))throw new IllegalArgumentException("Unknown item display context");
    }
    public boolean matches(String hand,String item,Set<String> itemTags,String state,String useAnimation,String context) {
        return (arm.equals("any")||arm.equals(hand)) && (items.isEmpty()&&tags.isEmpty()||items.contains(item)||itemTags.stream().anyMatch(tags::contains))
                && (states.isEmpty()||states.contains(state)) && (useAnimations.isEmpty()||useAnimations.contains(useAnimation))
                && (contexts.isEmpty()||contexts.contains(context));
    }
    static List<CharacterEquipmentRule> read(JsonElement input) {
        if(input==null)return List.of();
        if(!input.isJsonArray()||input.getAsJsonArray().size()>64)throw new IllegalArgumentException("Equipment overrides require at most 64 rules");
        var result=new ArrayList<CharacterEquipmentRule>();
        for(var element:input.getAsJsonArray()) {
            if(!element.isJsonObject())throw new IllegalArgumentException("Equipment override must be an object");
            var value=element.getAsJsonObject();
            for(String key:value.keySet())if(!Set.of("arm","items","tags","states","use_animations","contexts","socket","mount").contains(key))throw new IllegalArgumentException("Unknown equipment override field: "+key);
            result.add(new CharacterEquipmentRule(value.has("arm")?text(value.get("arm")):"any",strings(value,"items"),strings(value,"tags"),strings(value,"states"),
                    strings(value,"use_animations"),strings(value,"contexts"),value.has("socket")?text(value.get("socket")):null,CharacterEquipmentDefinition.mount(value,"mount")));
        }
        return List.copyOf(result);
    }
    JsonObject toJson() {
        var value=new JsonObject();value.addProperty("arm",arm);value.add("items",array(items));value.add("tags",array(tags));value.add("states",array(states));
        value.add("use_animations",array(useAnimations));value.add("contexts",array(contexts));
        if(socket!=null)value.addProperty("socket",socket);else value.add("mount",CharacterEquipmentDefinition.mountJson(mount));return value;
    }
    private static JsonArray array(Set<String> values){var out=new JsonArray();values.stream().sorted().forEach(out::add);return out;}
    private static Set<String> strings(JsonObject value,String key) {
        if(!value.has(key))return Set.of();var input=value.get(key);
        if(!input.isJsonArray()||input.getAsJsonArray().size()>64)throw new IllegalArgumentException("Equipment selector array required: "+key);
        var out=new java.util.LinkedHashSet<String>();for(var entry:input.getAsJsonArray())if(!out.add(text(entry)))throw new IllegalArgumentException("Duplicate equipment selector");return out;
    }
    private static String text(JsonElement value){if(value==null||!value.isJsonPrimitive()||!value.getAsJsonPrimitive().isString())throw new IllegalArgumentException("Equipment string required");return value.getAsString();}
}
