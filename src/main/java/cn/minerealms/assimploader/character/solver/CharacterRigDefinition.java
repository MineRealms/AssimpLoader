package cn.minerealms.assimploader.character.solver;

import cn.minerealms.assimploader.animation.AnimationState;
import cn.minerealms.assimploader.model.AssimpScene;
import com.google.gson.*;
import org.joml.Quaternionf;
import org.joml.Vector3f;

import java.io.Reader;
import java.util.*;

/** Versioned format-neutral solver sidecar. Unknown semantics fail with a diagnostic. */
public final class CharacterRigDefinition {
    private final JsonObject root;
    private CharacterRigDefinition(JsonObject root) {
        this.root=root.deepCopy();keys(root,"version","append","ik","boneMorphs","groups","defaults","motions");
        if(number(root,"version",0)!=1)throw new IllegalArgumentException("Character rig version must be 1");
    }
    public static CharacterRigDefinition read(Reader reader){return new CharacterRigDefinition(JsonParser.parseReader(reader).getAsJsonObject());}
    public record Installed(CharacterMorphModifier morphs,Map<String,LimitedIkModifier> ik,CharacterMotionPlayer motions) {}
    public Installed install(AnimationState state) {
        AssimpScene scene=state.getScene();
        var grants=new ArrayList<AppendTransformModifier.Grant>();
        for(var element:array(root,"append")) {
            var g=element.getAsJsonObject();keys(g,"source","target","rotation","translation","afterPhysics");
            grants.add(new AppendTransformModifier.Grant(text(g,"source"),text(g,"target"),number(g,"rotation",0),number(g,"translation",0),bool(g,"afterPhysics",false)));
        }
        var bones=new LinkedHashMap<String,List<CharacterMorphModifier.Bone>>();
        object(root,"boneMorphs").entrySet().forEach(entry->{
            var values=new ArrayList<CharacterMorphModifier.Bone>();
            if(entry.getValue().getAsJsonArray().size()>256)throw new IllegalArgumentException("Bone morph too large");
            for(var element:entry.getValue().getAsJsonArray()) {
                var value=element.getAsJsonObject();keys(value,"node","translation","rotation");
                var rotation=value.has("rotation")?vector4(value.getAsJsonArray("rotation")):new Quaternionf();
                values.add(new CharacterMorphModifier.Bone(text(value,"node"),vector(value,"translation",new Vector3f()),rotation));
            }
            bones.put(entry.getKey(),List.copyOf(values));
        });
        var groups=new LinkedHashMap<String,Map<String,Float>>();
        object(root,"groups").entrySet().forEach(entry->{
            var children=new LinkedHashMap<String,Float>();
            entry.getValue().getAsJsonObject().entrySet().forEach(child->children.put(child.getKey(),finite(child.getValue())));
            groups.put(entry.getKey(),children);
        });
        var defaults=new LinkedHashMap<String,Float>();
        object(root,"defaults").entrySet().forEach(entry->defaults.put(entry.getKey(),finite(entry.getValue())));
        var morph=new CharacterMorphModifier(scene,bones,groups,defaults);
        var pre=new AppendTransformModifier(scene,grants,false);
        var post=new AppendTransformModifier(scene,grants,true);
        var chains=new LinkedHashMap<String,LimitedIkModifier>();
        object(root,"ik").entrySet().forEach(entry->{
            var value=entry.getValue().getAsJsonObject();keys(value,"chain","target","limits","iterations","tolerance","maxAngle","weight","afterPhysics","enabled");
            var chain=new ArrayList<String>();for(var n:array(value,"chain"))chain.add(n.getAsString());
            var limits=new LinkedHashMap<String,LimitedIkModifier.Limit>();
            object(value,"limits").entrySet().forEach(limit->{
                var l=limit.getValue().getAsJsonObject();keys(l,"lower","upper","hinge");
                limits.put(limit.getKey(),new LimitedIkModifier.Limit(vector(l,"lower",new Vector3f(-(float)Math.PI)),
                        vector(l,"upper",new Vector3f((float)Math.PI)),l.has("hinge")?vector(l,"hinge",null):null));
            });
            float iterations=number(value,"iterations",32);
            if(iterations!=(int)iterations)throw new IllegalArgumentException("IK iterations must be an integer");
            var ik=new LimitedIkModifier(chain,vector(value,"target",new Vector3f()),limits,(int)iterations,
                    number(value,"tolerance",.001f),number(value,"maxAngle",.35f),number(value,"weight",1),bool(value,"afterPhysics",false));
            var graph=new cn.minerealms.assimploader.animation.PoseGraph(scene.getRootNode(),new HashMap<>());
            for(int i=0;i<chain.size();i++) {
                if(!graph.contains(chain.get(i)) || i>0 && !graph.isAncestor(chain.get(i-1),chain.get(i)))throw new IllegalArgumentException("Invalid IK chain: "+entry.getKey());
            }
            ik.setEnabled(bool(value,"enabled",true));chains.put(entry.getKey(),ik);
        });
        var motions=new LinkedHashMap<String,CharacterMotion>();
        object(root,"motions").entrySet().forEach(entry->motions.put(entry.getKey(),CharacterMotion.parse(entry.getValue().getAsJsonObject())));
        var motionPlayer=new CharacterMotionPlayer(scene,motions,morph,chains);
        state.addModifier("character:motion",motionPlayer);
        state.addModifier("character:morph",morph);
        state.addModifier("character:append",pre);
        chains.forEach((name,ik)->state.addModifier("character:ik/"+name,ik));
        state.addModifier("character:post-append",post);
        return new Installed(morph,Map.copyOf(chains),motionPlayer);
    }
    private static void keys(JsonObject object,String... allowed) {
        var names=Set.of(allowed);for(var key:object.keySet())if(!names.contains(key))throw new IllegalArgumentException("Unknown character rig field: "+key);
        if(object.size()>256)throw new IllegalArgumentException("Character rig object exceeds 256 entries");
    }
    private static JsonObject object(JsonObject parent,String key) {
        var value=parent.has(key)?parent.getAsJsonObject(key):new JsonObject();
        if(value.size()>256)throw new IllegalArgumentException("Character rig object exceeds 256 entries");return value;
    }
    private static JsonArray array(JsonObject parent,String key) {
        var value=parent.has(key)?parent.getAsJsonArray(key):new JsonArray();
        if(value.size()>256)throw new IllegalArgumentException("Character rig array exceeds 256 entries");return value;
    }
    private static float finite(JsonElement value) {
        if(!value.isJsonPrimitive() || !value.getAsJsonPrimitive().isNumber() || !Float.isFinite(value.getAsFloat()))throw new IllegalArgumentException("Expected finite solver number");return value.getAsFloat();
    }
    private static float number(JsonObject object,String key,float fallback){return object.has(key)?finite(object.get(key)):fallback;}
    private static boolean bool(JsonObject object,String key,boolean fallback) {
        if(!object.has(key))return fallback;
        if(!object.get(key).isJsonPrimitive() || !object.getAsJsonPrimitive(key).isBoolean())throw new IllegalArgumentException("Expected boolean: "+key);
        return object.get(key).getAsBoolean();
    }
    private static String text(JsonObject object,String key){
        var v=object.get(key);if(v==null || !v.isJsonPrimitive() || !v.getAsJsonPrimitive().isString() || v.getAsString().isBlank() || v.getAsString().length()>256)throw new IllegalArgumentException("Expected solver name: "+key);return v.getAsString();
    }
    private static Vector3f vector(JsonObject object,String key,Vector3f fallback) {
        if(!object.has(key))return fallback;
        var a=object.getAsJsonArray(key);if(a.size()!=3)throw new IllegalArgumentException("Expected 3 components: "+key);
        return new Vector3f(finite(a.get(0)),finite(a.get(1)),finite(a.get(2)));
    }
    private static Quaternionf vector4(JsonArray a) {
        if(a.size()!=4)throw new IllegalArgumentException("Expected quaternion xyzw");
        return new Quaternionf(finite(a.get(0)),finite(a.get(1)),finite(a.get(2)),finite(a.get(3)));
    }
}
