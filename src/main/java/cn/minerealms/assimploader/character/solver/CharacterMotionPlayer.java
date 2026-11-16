package cn.minerealms.assimploader.character.solver;

import cn.minerealms.assimploader.animation.PoseModifier;
import cn.minerealms.assimploader.model.AssimpScene;
import org.joml.Matrix4f;
import org.joml.Quaternionf;
import org.joml.Vector3f;

import java.util.*;

/** One character's optional semantic motion, evaluated before bone Morph, IK and physics. */
public final class CharacterMotionPlayer implements PoseModifier {
    private final Map<String,CharacterMotion> motions;
    private final Map<String,Matrix4f> rest=new HashMap<>();
    private final CharacterMorphModifier morphs;
    private final Map<String,LimitedIkModifier> ik;
    private final Map<String,Boolean> previousIk=new HashMap<>();
    private CharacterMotion active;
    private String name;
    private double seconds;
    private boolean looping,paused;

    public CharacterMotionPlayer(AssimpScene scene,Map<String,CharacterMotion> motions,CharacterMorphModifier morphs,Map<String,LimitedIkModifier> ik) {
        this.motions=Map.copyOf(motions);this.morphs=Objects.requireNonNull(morphs);this.ik=Map.copyOf(ik);
        if(motions.size()>256)throw new IllegalArgumentException("Too many character motions");
        motions.forEach((motionName,motion)->{
            if(motionName==null||motionName.isBlank()||motionName.length()>256)throw new IllegalArgumentException("Invalid motion name");
            for(String node:motion.boneNames()){
                var n=scene.findNodeByName(node);if(n==null)throw new IllegalArgumentException("Unknown motion bone: "+node);
                rest.put(node,new Matrix4f(n.getTransformation()));
            }
            for(String target:motion.morphNames())if(!morphs.names().contains(target))throw new IllegalArgumentException("Unknown motion morph: "+target);
            for(String target:motion.ikNames())if(!ik.containsKey(target))throw new IllegalArgumentException("Unknown motion IK: "+target);
        });
    }
    public Set<String> names(){return motions.keySet();}
    public String currentName(){return name;}
    public double time(){return seconds;}
    public boolean isPaused(){return paused;}
    public void play(String name,boolean loop) {
        var motion=motions.get(name);if(motion==null)throw new IllegalArgumentException("Unknown character motion: "+name);
        reset();this.active=motion;this.name=name;this.looping=loop;
        motion.ikNames().forEach(target->previousIk.put(target,ik.get(target).isEnabled()));
    }
    public void pause(){paused=true;}
    public void resume(){paused=false;}
    public void seek(double time) {
        if(!Double.isFinite(time)||time<0)throw new IllegalArgumentException("Invalid motion seek");
        if(active==null)throw new IllegalStateException("No active character motion");
        seconds=normalized(time);
    }
    private double normalized(double time){return active.duration()==0?0:looping?time%active.duration():Math.min(time,active.duration());}
    @Override public Category category(){return Category.MOTION;}
    @Override public void apply(AssimpScene scene,Map<String,Matrix4f> locals,double dt) {
        if(!Double.isFinite(dt)||dt<0)throw new IllegalArgumentException("Invalid motion delta");
        if(active==null)return;
        if(!paused)seconds=normalized(seconds+dt);
        for(String node:active.boneNames()) {
            var sample=active.sampleBone(node,seconds);var base=rest.get(node);
            locals.put(node,new Matrix4f().translationRotateScale(base.getTranslation(new Vector3f()).add(sample.translation()),
                    base.getUnnormalizedRotation(new Quaternionf()).mul(sample.rotation()),base.getScale(new Vector3f())));
        }
        var weights=new LinkedHashMap<String,Float>();
        active.morphNames().forEach(target->weights.put(target,active.sampleMorph(target,seconds)));morphs.setMotionWeights(weights);
        active.ikNames().forEach(target->ik.get(target).setEnabled(active.sampleIk(target,seconds).orElse(previousIk.get(target))));
    }
    @Override public void reset() {
        previousIk.forEach((target,enabled)->ik.get(target).setEnabled(enabled));previousIk.clear();
        active=null;name=null;seconds=0;paused=false;morphs.setMotionWeights(Map.of());
    }
}
