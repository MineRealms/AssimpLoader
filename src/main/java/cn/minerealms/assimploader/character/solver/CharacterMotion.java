package cn.minerealms.assimploader.character.solver;

import com.google.gson.*;
import org.joml.Quaternionf;
import org.joml.Vector3f;

import java.util.*;

/** Immutable motion in seconds. Curves belong to the destination key, including the first interval. */
public final class CharacterMotion {
    public record Curves(BezierTiming x,BezierTiming y,BezierTiming z,BezierTiming rotation) {
        public static final Curves LINEAR=new Curves(BezierTiming.LINEAR,BezierTiming.LINEAR,BezierTiming.LINEAR,BezierTiming.LINEAR);
        public Curves { Objects.requireNonNull(x);Objects.requireNonNull(y);Objects.requireNonNull(z);Objects.requireNonNull(rotation); }
    }
    public record BoneKey(double time,Vector3f translation,Quaternionf rotation,Curves curves) {
        public BoneKey {
            validTime(time);translation=new Vector3f(translation);rotation=new Quaternionf(rotation);
            if(!translation.isFinite() || !rotation.isFinite() || rotation.lengthSquared()<1e-10f)throw new IllegalArgumentException("Invalid motion transform");
            rotation.normalize();Objects.requireNonNull(curves);
        }
        @Override public Vector3f translation(){return new Vector3f(translation);}
        @Override public Quaternionf rotation(){return new Quaternionf(rotation);}
    }
    public record MorphKey(double time,float weight) {
        public MorphKey { validTime(time);if(!Float.isFinite(weight)||Math.abs(weight)>4)throw new IllegalArgumentException("Invalid motion morph weight"); }
    }
    public record IkKey(double time,boolean enabled) { public IkKey { validTime(time); } }
    public record Transform(Vector3f translation,Quaternionf rotation) {}
    private final double duration;
    private final Map<String,List<BoneKey>> bones;
    private final Map<String,List<MorphKey>> morphs;
    private final Map<String,List<IkKey>> ik;

    public CharacterMotion(double duration,Map<String,List<BoneKey>> bones,Map<String,List<MorphKey>> morphs,Map<String,List<IkKey>> ik) {
        validTime(duration);
        this.bones=copy(bones,BoneKey::time,duration);this.morphs=copy(morphs,MorphKey::time,duration);this.ik=copy(ik,IkKey::time,duration);
        int count=this.bones.values().stream().mapToInt(List::size).sum()+this.morphs.values().stream().mapToInt(List::size).sum()+this.ik.values().stream().mapToInt(List::size).sum();
        if(count>16384)throw new IllegalArgumentException("Motion exceeds 16384 keys");
        this.duration=duration;
    }
    private static <T> Map<String,List<T>> copy(Map<String,List<T>> tracks,java.util.function.ToDoubleFunction<T> time,double duration) {
        if(tracks.size()>256)throw new IllegalArgumentException("Motion exceeds 256 tracks per type");
        var result=new LinkedHashMap<String,List<T>>();
        tracks.forEach((name,keys)->{
            if(name==null||name.isBlank()||name.length()>256||keys.isEmpty()||keys.size()>4096)throw new IllegalArgumentException("Invalid motion track");
            double previous=-1;
            for(T key:keys){double t=time.applyAsDouble(key);if(t<=previous||t>duration)throw new IllegalArgumentException("Motion keys must increase within duration");previous=t;}
            result.put(name,List.copyOf(keys));
        });
        return Collections.unmodifiableMap(result);
    }
    public double duration(){return duration;}
    public Set<String> boneNames(){return bones.keySet();}
    public Set<String> morphNames(){return morphs.keySet();}
    public Set<String> ikNames(){return ik.keySet();}
    public Transform sampleBone(String node,double seconds) {
        validSample(seconds);var keys=Objects.requireNonNull(bones.get(node),"Unknown motion bone");
        int right=right(keys,seconds,BoneKey::time);BoneKey to=keys.get(right);
        if(right==0 || seconds>=to.time())return new Transform(to.translation(),to.rotation());
        BoneKey from=keys.get(right-1);double t=(seconds-from.time())/(to.time()-from.time());Curves c=to.curves();
        Vector3f a=from.translation,b=to.translation;
        var p=new Vector3f((float)(a.x+(b.x-a.x)*c.x.sample(t)),(float)(a.y+(b.y-a.y)*c.y.sample(t)),(float)(a.z+(b.z-a.z)*c.z.sample(t)));
        return new Transform(p,new Quaternionf(from.rotation).slerp(to.rotation,(float)c.rotation.sample(t)));
    }
    public float sampleMorph(String name,double seconds) {
        validSample(seconds);var keys=Objects.requireNonNull(morphs.get(name),"Unknown motion morph");int right=right(keys,seconds,MorphKey::time);var to=keys.get(right);
        if(right==0 || seconds>=to.time())return to.weight();
        var from=keys.get(right-1);return (float)(from.weight()+(to.weight()-from.weight())*(seconds-from.time())/(to.time()-from.time()));
    }
    /** Before the first switch, the installed IK default is retained. */
    public Optional<Boolean> sampleIk(String name,double seconds) {
        validSample(seconds);var keys=Objects.requireNonNull(ik.get(name),"Unknown motion IK");
        if(seconds<keys.get(0).time())return Optional.empty();
        int right=right(keys,seconds,IkKey::time);if(keys.get(right).time()>seconds)right--;
        return Optional.of(keys.get(right).enabled());
    }
    private static <T> int right(List<T> keys,double time,java.util.function.ToDoubleFunction<T> at) {
        int low=0,high=keys.size()-1;while(low<high){int mid=(low+high)>>>1;if(at.applyAsDouble(keys.get(mid))<time)low=mid+1;else high=mid;}return low;
    }
    private static void validTime(double time){if(!Double.isFinite(time)||time<0||time>86400)throw new IllegalArgumentException("Motion time must be within one day");}
    private static void validSample(double time){if(!Double.isFinite(time))throw new IllegalArgumentException("Non-finite motion sample");}

    public static CharacterMotion parse(JsonObject root) {
        keys(root,"duration","bones","morphs","ik");double duration=number(root,"duration");
        var bones=new LinkedHashMap<String,List<BoneKey>>();
        object(root,"bones").entrySet().forEach(entry->{var values=new ArrayList<BoneKey>();for(var e:entry.getValue().getAsJsonArray()){
            var key=e.getAsJsonObject();keys(key,"time","translation","rotation","curves");
            var p=vector(key,"translation",3);var q=vector(key,"rotation",4);var c=object(key,"curves");keys(c,"x","y","z","rotation");
            values.add(new BoneKey(number(key,"time"),new Vector3f(p[0],p[1],p[2]),new Quaternionf(q[0],q[1],q[2],q[3]),
                    new Curves(curve(c,"x"),curve(c,"y"),curve(c,"z"),curve(c,"rotation"))));
        }bones.put(entry.getKey(),values);});
        var morphs=new LinkedHashMap<String,List<MorphKey>>();
        object(root,"morphs").entrySet().forEach(entry->{var values=new ArrayList<MorphKey>();for(var e:entry.getValue().getAsJsonArray()){
            var key=e.getAsJsonObject();keys(key,"time","weight");values.add(new MorphKey(number(key,"time"),(float)number(key,"weight")));
        }morphs.put(entry.getKey(),values);});
        var ik=new LinkedHashMap<String,List<IkKey>>();
        object(root,"ik").entrySet().forEach(entry->{var values=new ArrayList<IkKey>();for(var e:entry.getValue().getAsJsonArray()){
            var key=e.getAsJsonObject();keys(key,"time","enabled");var enabled=key.get("enabled");
            if(enabled==null||!enabled.isJsonPrimitive()||!enabled.getAsJsonPrimitive().isBoolean())throw new IllegalArgumentException("Expected motion IK boolean");
            values.add(new IkKey(number(key,"time"),enabled.getAsBoolean()));
        }ik.put(entry.getKey(),values);});
        return new CharacterMotion(duration,bones,morphs,ik);
    }
    private static void keys(JsonObject root,String... allowed){var set=Set.of(allowed);for(String key:root.keySet())if(!set.contains(key))throw new IllegalArgumentException("Unknown motion field: "+key);}
    private static JsonObject object(JsonObject root,String name){return root.has(name)?root.getAsJsonObject(name):new JsonObject();}
    private static double number(JsonObject root,String name){var v=root.get(name);if(v==null||!v.isJsonPrimitive()||!v.getAsJsonPrimitive().isNumber()||!Double.isFinite(v.getAsDouble()))throw new IllegalArgumentException("Expected finite motion number: "+name);return v.getAsDouble();}
    private static float[] vector(JsonObject root,String name,int length){var a=root.getAsJsonArray(name);if(a==null||a.size()!=length)throw new IllegalArgumentException("Invalid motion vector: "+name);var result=new float[length];for(int i=0;i<length;i++){var v=a.get(i);if(!v.isJsonPrimitive()||!v.getAsJsonPrimitive().isNumber()||!Float.isFinite(v.getAsFloat()))throw new IllegalArgumentException("Non-finite motion vector");result[i]=v.getAsFloat();}return result;}
    private static BezierTiming curve(JsonObject root,String name){if(!root.has(name))return BezierTiming.LINEAR;var v=vector(root,name,4);return new BezierTiming(v[0],v[1],v[2],v[3]);}
}
