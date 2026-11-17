package cn.minerealms.assimploader.animation;

import cn.minerealms.assimploader.animation.ik.ChainIkModifier;
import cn.minerealms.assimploader.model.AssimpScene;
import cn.minerealms.assimploader.model.AssimpMesh;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import org.joml.Matrix4f;
import org.joml.Vector3f;

import java.io.IOException;
import java.io.Reader;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/** Cached instance-owned skeletal clip/pose composition after native MOTION, with a separately scheduled IK stage. */
public final class ModelAnimationProcessor implements PoseModifier {
    public static final int MAX_DEFINITION_CHARS = 65_536;
    private static final double MAX_NUMBER = 1_000_000;
    private abstract static class Step {
        final String name;
        boolean enabled;
        float weight;
        Step(String name, JsonObject spec) { this.name=name;enabled=bool(spec,"enabled",true);weight=weight(spec,"weight",1); }
        abstract Set<String> bones();
        abstract void apply(AssimpScene scene,Map<String,Matrix4f> locals,double delta);
        void reset() {}
        void close() {}
    }
    private final class Clip extends Step {
        final AnimationPoseLayer layer;
        final ClipMorphComposition morphs;
        Clip(String name,JsonObject spec) {
            super(name,spec);
            keys(spec,Set.of("clip","weight","enabled","additive","mask","speed","loop"));
            String clipName=text(spec.get("clip"),"clip");
            var animation=scene.getAnimations().stream().filter(clip->clip.getName().equals(clipName)).findFirst()
                    .orElseThrow(()->new IllegalArgumentException("Unknown processor clip: "+clipName));
            var mask=mask(spec,true);
            layer=new AnimationPoseLayer(animation,scene.getRootNode(),weight,bool(spec,"additive",false),mask);
            morphs=new ClipMorphComposition(scene,animation,mask);
            layer.getPlayer().setSpeed((float)number(spec,"speed",1,-16,16));
            layer.getPlayer().setLooping(bool(spec,"loop",true));
        }
        @Override Set<String> bones() {
            var names=new LinkedHashSet<String>();
            layer.getPlayer().getAnimation().getChannels().forEach(channel->names.add(channel.getBoneName()));
            return names;
        }
        @Override void apply(AssimpScene scene,Map<String,Matrix4f> locals,double delta) {
            layer.setWeight(weight);layer.update(delta);layer.apply(locals,bind);
        }
        @Override void reset() {layer.getPlayer().stop();layer.getPlayer().play();}
        @Override void close() {layer.getPlayer().pause();}
    }
    private final class Pose extends Step {
        final ScriptPoseModifier modifier;
        final Map<String,Float> mask;
        Pose(String name,JsonObject spec) {
            super(name,spec);
            keys(spec,Set.of("bones","weight","enabled","additive","mask","space"));
            bool(spec,"additive",false);
            if(spec.has("space")&&!Set.of("local","model").contains(text(spec.get("space"),"space")))
                throw new IllegalArgumentException("Processor pose space must be local or model");
            JsonObject bones=object(spec.get("bones"),"Pose bones");
            if(bones.size()>256)throw new IllegalArgumentException("Processor pose bone limit");
            for(var entry:bones.entrySet()) {
                requireNode(entry.getKey());
                JsonObject bone=object(entry.getValue(),"Pose bone");
                keys(bone,Set.of("rotation","position","scale","weight"));
                weight(bone,"weight",1);
                if(bone.has("rotation"))vector(bone.get("rotation"),-36_000,36_000);
                if(bone.has("position"))vector(bone.get("position"),-MAX_NUMBER,MAX_NUMBER);
                if(bone.has("scale"))vector(bone.get("scale"),.001,1_000);
            }
            mask=mask(spec);
            JsonObject options=spec.deepCopy();options.remove("enabled");options.remove("mask");options.addProperty("weight",1);
            modifier=new ScriptPoseModifier(scene,options);
        }
        @Override Set<String> bones() {return modifier.controlledBones();}
        @Override void apply(AssimpScene scene,Map<String,Matrix4f> locals,double delta) {
            if(weight==1&&mask.isEmpty()){modifier.apply(scene,locals,delta);return;}
            var before=capture(locals,bones());
            modifier.apply(scene,locals,delta);
            for(String bone:bones()) {
                float effective=weight*(mask.isEmpty()?1:mask.getOrDefault(bone,0f));
                if(effective==0)locals.put(bone,before.get(bone));
                else if(effective<1)locals.put(bone,AnimationBlender.blendTransforms(before.get(bone),locals.get(bone),effective));
            }
        }
    }
    private final class Ik extends Step {
        final List<String> chain;
        final ChainIkModifier modifier;
        Ik(String name,JsonObject spec) {
            super(name,spec);
            keys(spec,Set.of("chain","target","weight","enabled","iterations","tolerance"));
            JsonElement value=spec.get("chain");
            if(value==null||!value.isJsonArray()||value.getAsJsonArray().size()<2||value.getAsJsonArray().size()>32)
                throw new IllegalArgumentException("Processor IK chain requires 2..32 nodes");
            var nodes=new ArrayList<String>();
            for(var node:value.getAsJsonArray()) {String id=text(node,"IK node");requireNode(id);nodes.add(id);}
            if(nodes.stream().distinct().count()!=nodes.size())throw new IllegalArgumentException("Duplicate processor IK node");
            chain=List.copyOf(nodes);
            var graph=new PoseGraph(scene.getRootNode(),bind);
            for(int index=1;index<chain.size();index++)if(!graph.isAncestor(chain.get(index-1),chain.get(index)))
                throw new IllegalArgumentException("Processor IK nodes are not an ordered descendant chain");
            double iterations=number(spec,"iterations",16,1,128);
            if(iterations!=Math.rint(iterations))throw new IllegalArgumentException("Processor IK iterations must be integer");
            float[] target=vector(spec.get("target"),-MAX_NUMBER,MAX_NUMBER);
            modifier=new ChainIkModifier(chain,new Vector3f(target),1,(int)iterations,(float)number(spec,"tolerance",.001,1e-6,100));
        }
        @Override Set<String> bones() {return Set.copyOf(chain);}
        @Override void apply(AssimpScene scene,Map<String,Matrix4f> locals,double delta) {
            var before=weight<1?capture(locals,bones()):null;
            modifier.apply(scene,locals,delta);
            if(before!=null)for(String bone:chain)locals.put(bone,AnimationBlender.blendTransforms(before.get(bone),locals.getOrDefault(bone,bind.get(bone)),weight));
        }
    }

    private final AssimpScene scene;
    private final String source;
    private final Map<String,Matrix4f> bind=new LinkedHashMap<>();
    private final Map<String,Step> controls=new LinkedHashMap<>();
    private final List<Step> composition=new ArrayList<>();
    private final List<Ik> ik=new ArrayList<>();
    private final Set<String> dependencies;
    private String mainStageId;
    private boolean enabled,closed;
    private final PoseModifier ikStage=new PoseModifier() {
        @Override public Category category(){return Category.IK;}
        @Override public Set<String> dependencies(){return mainStageId==null?Set.of():Set.of(mainStageId);}
        @Override public Set<String> controlledBones(){return ModelAnimationProcessor.this.controlledBones();}
        @Override public void apply(AssimpScene scene,Map<String,Matrix4f> locals,double delta) {
            if(closed||!enabled)return;
            for(var step:ik)if(step.enabled&&step.weight>0)step.apply(scene,locals,delta);
        }
    };

    private ModelAnimationProcessor(AssimpScene scene,String source,JsonObject definition) {
        this.scene=java.util.Objects.requireNonNull(scene);this.source=source;
        bind.putAll(SkinBindPose.locals(scene));
        keys(definition,Set.of("version","enabled","clips","poses","ik","after"));
        var after=new LinkedHashSet<String>();
        if(definition.has("after")) {
            var values=definition.get("after");
            if(!values.isJsonArray()||values.getAsJsonArray().size()>64)throw new IllegalArgumentException("Processor after requires at most 64 modifier IDs");
            for(var value:values.getAsJsonArray())after.add(text(value,"after modifier ID"));
        }
        dependencies=Set.copyOf(after);
        var version=definition.get("version");
        if(version==null||!version.isJsonPrimitive()||!version.getAsJsonPrimitive().isNumber()
                ||version.getAsBigDecimal().compareTo(java.math.BigDecimal.ONE)!=0)
            throw new IllegalArgumentException("Processor version must be integer 1");
        enabled=bool(definition,"enabled",true);
        for(String section:List.of("clips","poses","ik")) {
            if(!definition.has(section))continue;
            JsonObject entries=object(definition.get(section),section);
            if(section.equals("ik")&&entries.size()>8)throw new IllegalArgumentException("Processor IK chain limit");
            for(var entry:entries.entrySet()) {
                String name=entry.getKey();
                if(!name.matches("[A-Za-z0-9_.:/-]{1,128}")||controls.containsKey(name)||controls.size()>=64)
                    throw new IllegalArgumentException("Invalid, duplicate or excessive processor control name: "+name);
                var spec=object(entry.getValue(),"Processor control");
                Step step=switch(section){case "clips"->new Clip(name,spec);case "poses"->new Pose(name,spec);default->new Ik(name,spec);};
                controls.put(name,step);
                if(step instanceof Ik value)ik.add(value);else composition.add(step);
            }
        }
        if(controls.values().stream().mapToInt(step->step.bones().size()).sum()>1024)
            throw new IllegalArgumentException("Processor exceeds 1024 controlled bone operations");
    }

    public static ModelAnimationProcessor compile(AssimpScene scene,JsonObject definition) {
        return new ModelAnimationProcessor(scene,null,java.util.Objects.requireNonNull(definition));
    }
    public static ModelAnimationProcessor read(AssimpScene scene,String source,Reader reader) throws IOException {
        var text=new StringBuilder();char[] buffer=new char[4096];int size;
        while((size=reader.read(buffer))!=-1) {
            if(text.length()+size>MAX_DEFINITION_CHARS)throw new IllegalArgumentException("Processor definition exceeds 64 KiB");
            text.append(buffer,0,size);
        }
        return new ModelAnimationProcessor(scene,source,object(JsonParser.parseString(text.toString()),"Processor definition"));
    }
    public String source(){return source;}
    public boolean isClosed(){return closed;}
    public List<String> controls(){checkOpen();return cn.minerealms.assimploader.util.ReadOnlyList.copyOf(controls.keySet());}
    public void enabled(boolean value){checkOpen();enabled=value;}
    public boolean enabled(){checkOpen();return enabled;}
    public void enabled(String name,boolean value){control(name).enabled=value;}
    public void weight(String name,double value){control(name).weight=boundedWeight(value);}
    public void phase(String name,double value) {
        Step step=control(name);
        if(!(step instanceof Clip clip))throw new IllegalArgumentException("Processor control is not a clip: "+name);
        boundedWeight(value);clip.layer.getPlayer().synchronizePhase(value);
    }
    public void releasePhase(String name) {
        Step step=control(name);
        if(!(step instanceof Clip clip))throw new IllegalArgumentException("Processor control is not a clip: "+name);
        clip.layer.getPlayer().releaseSynchronizedPhase();
    }
    public void target(String name,double x,double y,double z) {
        Step step=control(name);
        if(!(step instanceof Ik value))throw new IllegalArgumentException("Processor control is not IK: "+name);
        value.modifier.setTarget(new Vector3f((float)finite(x,-MAX_NUMBER,MAX_NUMBER),(float)finite(y,-MAX_NUMBER,MAX_NUMBER),(float)finite(z,-MAX_NUMBER,MAX_NUMBER)));
    }
    public PoseModifier ikStage(){return ikStage;}
    /** Bind the companion stage to its full instance modifier ID before atomic installation. */
    public PoseModifier ikStage(String mainModifierId) {
        checkOpen();
        if(mainModifierId==null||mainModifierId.isBlank())throw new IllegalArgumentException("Processor main modifier ID required");
        if(mainStageId!=null&&!mainStageId.equals(mainModifierId))throw new IllegalStateException("Processor already bound to another modifier ID");
        mainStageId=mainModifierId;return ikStage;
    }
    @Override public Category category(){return Category.GENERAL;}
    @Override public Set<String> dependencies(){return dependencies;}
    @Override public Set<String> controlledBones() {
        var names=new LinkedHashSet<String>();controls.values().forEach(step->names.addAll(step.bones()));return Collections.unmodifiableSet(names);
    }
    @Override public void apply(AssimpScene scene,Map<String,Matrix4f> locals,double delta) {
        if(closed||!enabled)return;
        if(!Double.isFinite(delta)||delta<0)throw new IllegalArgumentException("Invalid processor delta");
        for(var step:composition)if(step.enabled) {
            if(step instanceof Clip||step.weight>0)step.apply(scene,locals,delta);
        }
    }
    @Override public void applyMorphs(AssimpScene scene,Map<AssimpMesh,float[]> weights) {
        if(closed||!enabled)return;
        for(var step:composition)if(step instanceof Clip clip&&clip.enabled&&clip.weight>0) {
            clip.layer.setWeight(clip.weight);
            clip.morphs.apply(clip.layer,weights);
        }
    }
    @Override public void reset(){if(!closed)controls.values().forEach(Step::reset);}
    @Override public void close(){if(!closed){closed=true;controls.values().forEach(Step::close);}}

    private Step control(String name){checkOpen();var value=controls.get(name);if(value==null)throw new IllegalArgumentException("Unknown processor control: "+name);return value;}
    private void checkOpen(){if(closed)throw new IllegalStateException("Animation processor expired");}
    private void requireNode(String name){if(!bind.containsKey(name))throw new IllegalArgumentException("Unknown processor node: "+name);}
    private Map<String,Float> mask(JsonObject spec) {
        return mask(spec,false);
    }
    private Map<String,Float> mask(JsonObject spec,boolean allowMesh) {
        if(!spec.has("mask"))return Map.of();
        var values=object(spec.get("mask"),"Processor mask");
        if(values.size()>256)throw new IllegalArgumentException("Processor mask bone limit");
        var result=new LinkedHashMap<String,Float>();
        values.entrySet().forEach(entry->{
            if(!allowMesh||scene.getMeshes().stream().noneMatch(mesh->mesh.getName().equals(entry.getKey())))requireNode(entry.getKey());
            result.put(entry.getKey(),boundedWeight(numeric(entry.getValue())));
        });
        return Map.copyOf(result);
    }
    private Map<String,Matrix4f> capture(Map<String,Matrix4f> locals,Set<String> names) {
        var result=new LinkedHashMap<String,Matrix4f>();
        for(String name:names)result.put(name,new Matrix4f(locals.getOrDefault(name,bind.get(name))));
        return result;
    }
    private static JsonObject object(JsonElement value,String label){if(value==null||!value.isJsonObject())throw new IllegalArgumentException(label+" must be an object");return value.getAsJsonObject();}
    private static void keys(JsonObject value,Set<String> allowed){for(String key:value.keySet())if(!allowed.contains(key))throw new IllegalArgumentException("Unknown processor field: "+key);}
    private static String text(JsonElement value,String label){if(value==null||!value.isJsonPrimitive()||!value.getAsJsonPrimitive().isString()||value.getAsString().isBlank()||value.getAsString().length()>256)throw new IllegalArgumentException("Invalid processor "+label);return value.getAsString();}
    private static boolean bool(JsonObject value,String key,boolean fallback){if(!value.has(key))return fallback;var entry=value.get(key);if(!entry.isJsonPrimitive()||!entry.getAsJsonPrimitive().isBoolean())throw new IllegalArgumentException(key+" must be boolean");return entry.getAsBoolean();}
    private static double numeric(JsonElement value){if(value==null||!value.isJsonPrimitive()||!value.getAsJsonPrimitive().isNumber())throw new IllegalArgumentException("Processor number required");return finite(value.getAsDouble(),-MAX_NUMBER,MAX_NUMBER);}
    private static double number(JsonObject value,String key,double fallback,double min,double max){return value.has(key)?finite(numeric(value.get(key)),min,max):fallback;}
    private static double finite(double value,double min,double max){if(!Double.isFinite(value)||value<min||value>max)throw new IllegalArgumentException("Processor number outside range ["+min+","+max+"]");return value;}
    private static float boundedWeight(double value){return(float)finite(value,0,1);}
    private static float weight(JsonObject value,String key,float fallback){return value.has(key)?boundedWeight(numeric(value.get(key))):fallback;}
    private static float[] vector(JsonElement value,double min,double max){if(value==null||!value.isJsonArray()||value.getAsJsonArray().size()!=3)throw new IllegalArgumentException("Processor vector requires 3 numbers");float[] result=new float[3];for(int i=0;i<3;i++)result[i]=(float)finite(numeric(value.getAsJsonArray().get(i)),min,max);return result;}
}
