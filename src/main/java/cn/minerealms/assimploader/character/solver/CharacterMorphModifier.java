package cn.minerealms.assimploader.character.solver;

import cn.minerealms.assimploader.animation.PoseGraph;
import cn.minerealms.assimploader.animation.PoseModifier;
import cn.minerealms.assimploader.model.AssimpMesh;
import cn.minerealms.assimploader.model.AssimpScene;
import org.joml.Matrix4f;
import org.joml.Quaternionf;
import org.joml.Vector3f;

import java.util.*;

/** Group expansion, bone morph and imported vertex morph weights, owned by one character. */
public final class CharacterMorphModifier implements PoseModifier {
    public record Bone(String node,Vector3f translation,Quaternionf rotation) {
        public Bone {
            translation=new Vector3f(translation);rotation=new Quaternionf(rotation);
            if(node==null || node.isBlank() || !translation.isFinite() || !rotation.isFinite() || rotation.lengthSquared()<1e-10)
                throw new IllegalArgumentException("Invalid bone morph");
            rotation.normalize();
        }
        @Override public Vector3f translation(){return new Vector3f(translation);}
        @Override public Quaternionf rotation(){return new Quaternionf(rotation);}
    }
    private final Map<String,List<Bone>> bones;
    private final Map<String,Map<String,Float>> groups;
    private final Map<String,Map<String,Float>> leaves=new HashMap<>();
    private final Map<String,Float> defaults;
    private final Set<String> known;
    private final Map<String,Float> inputs=new LinkedHashMap<>();
    private Map<String,Float> motionInputs=Map.of();
    private Map<String,Float> expanded=Map.of();
    public CharacterMorphModifier(AssimpScene scene,Map<String,List<Bone>> bones,Map<String,Map<String,Float>> groups) {
        this(scene,bones,groups,Map.of());
    }
    public CharacterMorphModifier(AssimpScene scene,Map<String,List<Bone>> bones,Map<String,Map<String,Float>> groups,Map<String,Float> defaults) {
        if(bones.size()>256 || groups.size()>256 || defaults.size()>256)throw new IllegalArgumentException("Morph definition exceeds capacity");
        var immutable=new LinkedHashMap<String,List<Bone>>();bones.forEach((k,v)->immutable.put(k,List.copyOf(v)));
        this.bones=Map.copyOf(immutable);
        var nested=new LinkedHashMap<String,Map<String,Float>>();groups.forEach((k,v)->nested.put(k,Map.copyOf(v)));
        this.groups=Map.copyOf(nested);
        var names=new HashSet<>(bones.keySet());names.addAll(groups.keySet());
        scene.getMeshes().forEach(m->m.getMorphTargets().forEach(t->names.add(t.getName())));
        scene.getMeshes().forEach(m->names.addAll(m.getMorphTargetAliases().values()));
        known=Set.copyOf(names);
        this.defaults=Map.copyOf(defaults);
        defaults.forEach((name,w)->{if(!known.contains(name)||!Float.isFinite(w)||Math.abs(w)>4)throw new IllegalArgumentException("Invalid morph default: "+name);});
        var graph=new PoseGraph(scene.getRootNode(),new HashMap<>());
        bones.values().forEach(list->list.forEach(b->{if(!graph.contains(b.node))throw new IllegalArgumentException("Unknown morph bone: "+b.node);}));
        groups.forEach((name,children)->{if(children.size()>256 || bones.containsKey(name))throw new IllegalArgumentException("Invalid group morph definition");children.forEach((child,w)->{
            if(!known.contains(child) || !Float.isFinite(w) || Math.abs(w)>4)throw new IllegalArgumentException("Invalid group morph member: "+child);
        });});
        for(String group:groups.keySet())flatten(group,new HashSet<>());
    }
    public Set<String> names(){return known;}
    public void setWeight(String name,float weight) {
        if(!known.contains(name) || !Float.isFinite(weight) || Math.abs(weight)>4)throw new IllegalArgumentException("Invalid character morph: "+name);
        if(weight==0)inputs.remove(name);else inputs.put(name,weight);
    }
    public Map<String,Float> expandedWeights(){return Map.copyOf(expanded);}
    public Map<String,Float> inputWeights(){return Map.copyOf(inputs);}
    void setMotionWeights(Map<String,Float> weights){motionInputs=Map.copyOf(weights);}
    private Map<String,Float> flatten(String name,Set<String> stack) {
        if(leaves.containsKey(name))return leaves.get(name);
        if(!stack.add(name))throw new IllegalArgumentException("Cyclic group morph: "+name);
        if(stack.size()>32)throw new IllegalArgumentException("Group morph nesting exceeds 32");
        var out=new LinkedHashMap<String,Float>();
        var children=groups.get(name);
        if(children==null)out.put(name,1f);
        else children.forEach((child,w)->flatten(child,stack).forEach((leaf,coefficient)->out.merge(leaf,w*coefficient,Float::sum)));
        stack.remove(name);
        if(out.values().stream().anyMatch(w->!Float.isFinite(w)))throw new IllegalArgumentException("Group morph coefficient overflow");
        leaves.put(name,Map.copyOf(out));return leaves.get(name);
    }
    @Override public Category category(){return Category.BONE_MORPH;}
    @Override public void apply(AssimpScene scene,Map<String,Matrix4f> locals,double dt) {
        var values=new LinkedHashMap<String,Float>();
        var combined=new LinkedHashMap<>(motionInputs);combined.putAll(inputs);
        combined.forEach((name,w)->leaves.getOrDefault(name,Map.of(name,1f)).forEach((leaf,coefficient)->values.merge(leaf,w*coefficient,Float::sum)));
        values.replaceAll((name,w)->Math.max(-4,Math.min(4,w)));expanded=Map.copyOf(values);
        var graph=new PoseGraph(scene.getRootNode(),locals);
        values.forEach((name,weight)->{
            for(var bone:bones.getOrDefault(name,List.of())) {
                var local=graph.local(bone.node);
                var rotation=new Quaternionf().slerp(bone.rotation,weight);
                graph.setLocal(bone.node,new Matrix4f().translationRotateScale(
                        local.getTranslation(new Vector3f()).add(new Vector3f(bone.translation).mul(weight)),
                        local.getUnnormalizedRotation(new Quaternionf()).mul(rotation),local.getScale(new Vector3f())));
            }
        });
    }
    @Override public void applyMorphs(AssimpScene scene,Map<AssimpMesh,float[]> weights) {
        for(var mesh:scene.getMeshes()) {
            if(!mesh.hasMorphTargets())continue;
            var values=weights.getOrDefault(mesh,mesh.getDefaultMorphWeights()).clone();
            for(int i=0;i<values.length;i++) {
                String name=mesh.getMorphTargets().get(i).getName();
                String alias=mesh.getMorphTargetAliases().getOrDefault(name,name);
                Float preset=defaults.containsKey(name)?defaults.get(name):defaults.get(alias);
                if(preset!=null)values[i]=preset;
                var value=expanded.containsKey(name)?expanded.get(name):expanded.get(alias);
                if(value!=null)values[i]+=value;
            }
            weights.put(mesh,values);
        }
    }
    @Override public void reset(){inputs.clear();motionInputs=Map.of();expanded=Map.of();}
}
