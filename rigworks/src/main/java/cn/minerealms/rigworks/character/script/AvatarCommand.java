package cn.minerealms.rigworks.character.script;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;

import java.util.Set;

/** Validated command data only. No script object or callable crosses the interpreter boundary. */
public record AvatarCommand(String type,JsonObject args) {
    public AvatarCommand {
        if(type==null||args==null)throw new IllegalArgumentException("Avatar command requires type and arguments");
        args=args.deepCopy();
        switch(type) {
            case "defaultAnimation","defaultMotion","releasePhase","pause","resume","stop","noContact","stopMotion" -> keys(args);
            case "clip" -> {keys(args,"clip","blend");name(args,"clip");range(args,"blend",0,10);}
            case "state" -> {keys(args,"name");name(args,"name");}
            case "phase","layerPhase" -> {keys(args,type.equals("phase")?new String[]{"value"}:new String[]{"id","value"});if(type.equals("layerPhase"))name(args,"id");range(args,"value",0,1);}
            case "playback" -> {keys(args,"speed","loop");range(args,"speed",-10,10);bool(args,"loop");}
            case "layer" -> {
                keys(args,"id","clip","weight","additive","mask");name(args,"id");name(args,"clip");range(args,"weight",0,1);bool(args,"additive");
                JsonObject mask=args.getAsJsonObject("mask");if(mask.size()>256)throw new IllegalArgumentException("Layer mask exceeds 256 bones");
                mask.entrySet().forEach(entry->{id(entry.getKey());number(entry.getValue(),0,1);});
            }
            case "removeLayer","removePose" -> {keys(args,"id");name(args,"id");}
            case "layerWeight" -> {keys(args,"id","weight");name(args,"id");range(args,"weight",0,1);}
            case "morph" -> {keys(args,"name","weight");name(args,"name");range(args,"weight",-10,10);}
            case "ik" -> {keys(args,"name","enabled");name(args,"name");bool(args,"enabled");}
            case "ikTarget" -> {keys(args,"name","x","y","z");name(args,"name");vector(args);}
            case "contact" -> {keys(args,"id","mainHand");name(args,"id");bool(args,"mainHand");}
            case "contactWeight" -> {keys(args,"weight");range(args,"weight",0,1);}
            case "motion" -> {keys(args,"name","loop");name(args,"name");bool(args,"loop");}
            case "pose" -> {keys(args,"id","options");name(args,"id");pose(args.getAsJsonObject("options"));}
            default -> throw new IllegalArgumentException("Unknown avatar command: "+type);
        }
    }
    @Override public JsonObject args(){return args.deepCopy();}
    private static void pose(JsonObject options) {
        keys(options,"bones","weight","space","stage","additive");if(options.has("weight"))range(options,"weight",0,1);
        if(options.has("additive"))bool(options,"additive");
        if(options.has("space")&&!Set.of("local","model").contains(options.get("space").getAsString()))throw new IllegalArgumentException("Invalid pose space");
        if(options.has("stage")&&!Set.of("pre_ik","post_physics").contains(options.get("stage").getAsString()))throw new IllegalArgumentException("Invalid pose stage");
        JsonObject bones=options.getAsJsonObject("bones");if(bones.size()>128)throw new IllegalArgumentException("Pose exceeds 128 bones");
        bones.entrySet().forEach(entry->{id(entry.getKey());var bone=entry.getValue().getAsJsonObject();keys(bone,"rotation","position","scale","weight");
            if(bone.has("weight"))range(bone,"weight",0,1);
            for(String key:new String[]{"rotation","position","scale"})if(bone.has(key)) {
                var array=bone.getAsJsonArray(key);if(array.size()!=3)throw new IllegalArgumentException("Pose vector requires three components");
                for(var value:array)number(value,key.equals("scale")?.001:-1000,key.equals("scale")?100:1000);
            }
        });
    }
    private static void vector(JsonObject args){range(args,"x",-1024,1024);range(args,"y",-1024,1024);range(args,"z",-1024,1024);}
    private static void name(JsonObject args,String key){JsonElement value=args.get(key);if(value==null||!value.isJsonPrimitive()||!value.getAsJsonPrimitive().isString())throw new IllegalArgumentException("Expected command string: "+key);id(value.getAsString());}
    private static void id(String value){if(value==null||value.isBlank()||value.length()>256||value.indexOf('\0')>=0)throw new IllegalArgumentException("Invalid command identifier");}
    private static void range(JsonObject args,String key,double min,double max){number(args.get(key),min,max);}
    private static void number(JsonElement value,double min,double max){if(value==null||!value.isJsonPrimitive()||!value.getAsJsonPrimitive().isNumber())throw new IllegalArgumentException("Expected command number");double number=value.getAsDouble();if(!Double.isFinite(number)||number<min||number>max)throw new IllegalArgumentException("Command number exceeds bounds");}
    private static void bool(JsonObject args,String key){if(!args.has(key)||!args.get(key).isJsonPrimitive()||!args.get(key).getAsJsonPrimitive().isBoolean())throw new IllegalArgumentException("Expected command boolean: "+key);}
    private static void keys(JsonObject args,String... allowed){if(!Set.of(allowed).containsAll(args.keySet()))throw new IllegalArgumentException("Unknown avatar command argument");}
}
