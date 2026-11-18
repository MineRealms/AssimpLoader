package cn.minerealms.rigworks.character.script;

import cn.minerealms.assimploader.animation.AnimationState;
import cn.minerealms.assimploader.animation.ScriptPoseModifier;
import cn.minerealms.assimploader.character.solver.CharacterRigDefinition;
import cn.minerealms.rigworks.character.client.CharacterHandContacts;
import com.google.gson.JsonObject;
import org.joml.Vector3f;

import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Set;

/** Client-tick command consumer. Its Java engine objects are never handed to the interpreter. */
public final class AvatarCommandApplier implements AutoCloseable {
    private static final String PREFIX="rigworks:avatar:";
    private final AnimationState animation;
    private final CharacterRigDefinition.Installed rig;
    private final CharacterHandContacts contacts;
    private final Runnable nativeAnimation,nativeMotion;
    private final Thread owner=Thread.currentThread();
    private final Set<String> layers=new HashSet<>(),poses=new HashSet<>();
    private final Map<String,Boolean> ikEnabled=new HashMap<>();
    private final Map<String,Vector3f> ikTargets=new HashMap<>();
    private final Map<String,Float> morphs=new HashMap<>();
    private boolean controlled;

    public AvatarCommandApplier(AnimationState animation,CharacterRigDefinition.Installed rig,CharacterHandContacts contacts,
                                Runnable nativeAnimation,Runnable nativeMotion) {
        this.animation=java.util.Objects.requireNonNull(animation);this.rig=rig;this.contacts=contacts;
        this.nativeAnimation=java.util.Objects.requireNonNull(nativeAnimation);this.nativeMotion=java.util.Objects.requireNonNull(nativeMotion);
    }
    public boolean apply(AvatarScriptRuntime.Result result) {
        thread();
        if(!result.handled()){reset();return false;}
        try{preflight(result);}catch(RuntimeException failure){reset();throw failure;}
        if(!controlled) {
            if(rig!=null){rig.ik().forEach((id,ik)->{ikEnabled.put(id,ik.isEnabled());ikTargets.put(id,ik.target());});morphs.putAll(rig.morphs().inputWeights());}
            controlled=true;
        }
        animation.beginPoseEdit();
        try {
            for(var command:result.commands())apply(command);
            return true;
        } catch(RuntimeException failure){reset();throw failure;}
        finally{animation.endPoseEdit(false);}
    }
    private void preflight(AvatarScriptRuntime.Result result) {
        Set<String> existing=new HashSet<>(layers),existingPoses=new HashSet<>(poses);
        for(var command:result.commands()) {
            var args=command.args();
            switch(command.type()) {
                case "clip" -> clip(text(args,"clip"));
                case "state" -> {if(!animation.getStateMachine().getBindings().containsKey(text(args,"name")))throw new IllegalArgumentException("Unknown avatar animation state");}
                case "layer" -> {
                    clip(text(args,"clip"));existing.add(text(args,"id"));
                    args.getAsJsonObject("mask").keySet().forEach(node->{if(animation.getScene().findNodeByName(node)==null)throw new IllegalArgumentException("Unknown avatar mask bone: "+node);});
                }
                case "removeLayer" -> existing.remove(text(args,"id"));
                case "layerWeight","layerPhase" -> {if(!existing.contains(text(args,"id")))throw new IllegalArgumentException("Unknown avatar layer");}
                case "pose" -> {new ScriptPoseModifier(animation.getScene(),args.getAsJsonObject("options")).close();existingPoses.add(text(args,"id"));}
                case "removePose" -> existingPoses.remove(text(args,"id"));
                case "morph" -> {if(rig==null||!rig.morphs().names().contains(text(args,"name")))throw new IllegalArgumentException("Unknown avatar morph");}
                case "ik","ikTarget" -> {if(rig==null||!rig.ik().containsKey(text(args,"name")))throw new IllegalArgumentException("Unknown avatar IK chain");}
                case "motion" -> {if(rig==null||!rig.motions().names().contains(text(args,"name")))throw new IllegalArgumentException("Unknown avatar motion");}
                case "contact" -> {if(contacts==null||!contacts.rules().contains(text(args,"id")))throw new IllegalArgumentException("Unknown avatar hand contact");}
                case "contactWeight" -> {if(contacts==null)throw new IllegalArgumentException("Avatar has no hand contacts");}
                default -> {}
            }
        }
        if(existing.size()>32)throw new IllegalArgumentException("At most 32 avatar animation layers");
        if(existingPoses.size()>32)throw new IllegalArgumentException("At most 32 avatar pose modifiers");
    }
    private void apply(AvatarCommand command) {
        JsonObject a=command.args();
        switch(command.type()) {
            case "defaultAnimation" -> nativeAnimation.run();
            case "defaultMotion" -> nativeMotion.run();
            case "clip" -> animation.transitionTo(text(a,"clip"),a.get("blend").getAsDouble());
            case "state" -> animation.getStateMachine().request(text(a,"name"));
            case "phase" -> {var player=animation.getCurrentAnimation();if(player!=null)player.synchronizePhase(a.get("value").getAsDouble());}
            case "releasePhase" -> {var player=animation.getCurrentAnimation();if(player!=null)player.releaseSynchronizedPhase();}
            case "playback" -> {var player=animation.getCurrentAnimation();if(player!=null){player.setSpeed(number(a,"speed"));player.setLooping(a.get("loop").getAsBoolean());}}
            case "pause" -> animation.pause();
            case "resume" -> animation.resume();
            case "stop" -> animation.stop();
            case "layer" -> {
                String id=text(a,"id"),clip=text(a,"clip");var mask=new LinkedHashMap<String,Float>();
                a.getAsJsonObject("mask").entrySet().forEach(entry->mask.put(entry.getKey(),entry.getValue().getAsFloat()));
                boolean additive=a.get("additive").getAsBoolean();var layer=animation.getLayer(PREFIX+id);
                if(layer==null||!layer.getPlayer().getAnimation().getName().equals(clip)||layer.isAdditive()!=additive||!layer.getMask().equals(mask))
                    animation.setLayer(PREFIX+id,clip,number(a,"weight"),additive,mask);
                else animation.setLayerWeight(PREFIX+id,number(a,"weight"));
                layers.add(id);
            }
            case "removeLayer" -> {animation.removeLayer(PREFIX+text(a,"id"));layers.remove(text(a,"id"));}
            case "layerWeight" -> animation.setLayerWeight(PREFIX+text(a,"id"),number(a,"weight"));
            case "layerPhase" -> animation.getLayer(PREFIX+text(a,"id")).getPlayer().synchronizePhase(a.get("value").getAsDouble());
            case "pose" -> {String id=text(a,"id");animation.addModifier(PREFIX+id,new ScriptPoseModifier(animation.getScene(),a.getAsJsonObject("options")));poses.add(id);}
            case "removePose" -> {animation.removeModifier(PREFIX+text(a,"id"));poses.remove(text(a,"id"));}
            case "morph" -> rig.morphs().setWeight(text(a,"name"),number(a,"weight"));
            case "ik" -> rig.ik().get(text(a,"name")).setEnabled(a.get("enabled").getAsBoolean());
            case "ikTarget" -> rig.ik().get(text(a,"name")).setTarget(new Vector3f(number(a,"x"),number(a,"y"),number(a,"z")));
            case "contact" -> contacts.select(text(a,"id"),a.get("mainHand").getAsBoolean());
            case "contactWeight" -> contacts.weight(a.get("weight").getAsDouble());
            case "noContact" -> {if(contacts!=null)contacts.disable();}
            case "motion" -> {String name=text(a,"name");if(!name.equals(rig.motions().currentName()))rig.motions().play(name,a.get("loop").getAsBoolean());}
            case "stopMotion" -> {if(rig!=null)rig.motions().reset();}
            default -> throw new IllegalArgumentException("Unknown avatar command");
        }
    }
    public void reset() {
        thread();
        if(controlled&&!animation.isClosed()) {
            animation.beginPoseEdit();
            try {
                for(String id:layers)animation.removeLayer(PREFIX+id);
                for(String id:poses)animation.removeModifier(PREFIX+id);
                var player=animation.getCurrentAnimation();if(player!=null){player.releaseSynchronizedPhase();player.setSpeed(1);
                    var binding=animation.getStateMachine().getBindings().get(animation.getStateMachine().getCurrentStateName());
                    player.setLooping(binding==null||!binding.clip().equals(player.getAnimation().getName())||binding.looping());player.play();}
                animation.resume();
                if(rig!=null){rig.motions().reset();rig.morphs().reset();morphs.forEach(rig.morphs()::setWeight);
                    ikEnabled.forEach((id,value)->rig.ik().get(id).setEnabled(value));ikTargets.forEach((id,value)->rig.ik().get(id).setTarget(value));}
                if(contacts!=null)contacts.reset();
            } finally{animation.endPoseEdit(false);}
        }
        controlled=false;layers.clear();poses.clear();ikEnabled.clear();ikTargets.clear();morphs.clear();
    }
    @Override public void close(){reset();}
    private void clip(String name){if(animation.getScene().getAnimations().stream().noneMatch(clip->clip.getName().equals(name)))throw new IllegalArgumentException("Unknown avatar clip: "+name);}
    private void thread(){if(Thread.currentThread()!=owner)throw new IllegalStateException("Avatar commands require their owner client tick thread");}
    private static String text(JsonObject value,String key){return value.get(key).getAsString();}
    private static float number(JsonObject value,String key){return value.get(key).getAsFloat();}
}
