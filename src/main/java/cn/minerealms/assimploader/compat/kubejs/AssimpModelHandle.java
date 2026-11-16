package cn.minerealms.assimploader.compat.kubejs;

import cn.minerealms.assimploader.animation.AnimationState;
import cn.minerealms.assimploader.animation.InstancePerformanceSettings;
import cn.minerealms.assimploader.animation.ScriptPoseModifier;
import cn.minerealms.assimploader.animation.AnimationPlayer;
import cn.minerealms.assimploader.animation.ik.ChainIkModifier;
import cn.minerealms.assimploader.animation.ik.LookAtModifier;
import cn.minerealms.assimploader.collision.ModelCollision;
import cn.minerealms.assimploader.collision.Ray;
import cn.minerealms.assimploader.model.AssimpMesh;
import com.google.gson.Gson;
import org.joml.Matrix4f;
import org.joml.Vector3f;

import java.lang.ref.WeakReference;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/** Weak, generation-bound script handle. No script callback runs inside a pose or vertex loop. */
public final class AssimpModelHandle {
    private static final Gson JSON=new Gson();
    private static final Map<AnimationState,java.util.Set<String>> CONTROLS=new java.util.WeakHashMap<>();
    private final WeakReference<AnimationState> reference;
    private final Thread thread=Thread.currentThread();

    public AssimpModelHandle(AnimationState state) {reference=new WeakReference<>(java.util.Objects.requireNonNull(state));}

    public boolean isValid() {var state=reference.get();return state!=null&&!state.isClosed();}
    public boolean sameInstance(AssimpModelHandle other) {return other!=null&&isValid()&&reference.get()==other.reference.get();}
    public String model() {return live().getModelName();}
    public String[] animations() {return live().getAvailableAnimations();}
    public List<String> states() {return new ArrayList<>(live().getStateMachine().getBindings().keySet());}
    public String currentState() {return live().getStateMachine().getCurrentStateName();}
    public boolean state(String name) {return live().getStateMachine().request(name);}
    public boolean play(String clip,double blendSeconds) {return live().transitionTo(clip,blendSeconds);}
    public AssimpModelHandle pause() {live().pause();return this;}
    public AssimpModelHandle resume() {live().resume();return this;}
    public AssimpModelHandle stop() {live().stop();return this;}
    public AssimpModelHandle resetDynamics() {live().resetDynamics();return this;}

    public List<String> bones() {
        var names=new ArrayList<String>(); collect(live().getScene().getRootNode(),names); return names;
    }
    public Map<String,Object> playback() {
        var player=player(); var clip=player.getAnimation();
        return Map.of("clip",clip.getName(),"time",player.getCurrentTime()/clip.getTicksPerSecond(),
                "duration",clip.getDuration()/clip.getTicksPerSecond(),"speed",player.getSpeed(),
                "playing",player.isPlaying(),"looping",player.isLooping(),"synchronized",player.hasSynchronizedPhase());
    }
    public AssimpModelHandle playback(double speed,boolean looping) {var p=player();p.setSpeed(number(speed));p.setLooping(looping);return this;}
    /** Native accepted attack phase or a script-owned timeline, normalized [0,1]. */
    public AssimpModelHandle phase(double progress) {player().synchronizePhase(progress);return this;}
    public AssimpModelHandle releasePhase() {player().releaseSynchronizedPhase();return this;}

    /** Rest-relative Euler XYZ degrees; local translations use imported node units. */
    public AssimpModelHandle pose(String id,Map<String,Object> options) {
        var state=live(); var key=control(id,"pose:");
        var modifier=new ScriptPoseModifier(state.getScene(),JSON.toJsonTree(options).getAsJsonObject());
        state.addModifier(key,modifier); return this;
    }
    public AssimpModelHandle removePose(String id) {var state=live();String key=key(id,"pose:");state.removeModifier(key);forget(state,key);return this;}

    public AssimpModelHandle layer(String id,String clip,double weight,boolean additive,Map<String,Number> mask) {
        Map<String,Float> converted=new LinkedHashMap<>();
        mask.forEach((node,value)->converted.put(node,number(value.doubleValue())));
        live().setLayer(control(id,"layer:"),clip,number(weight),additive,converted);return this;
    }
    public AssimpModelHandle layerWeight(String id,double weight) {live().setLayerWeight(key(id,"layer:"),number(weight));return this;}
    public AssimpModelHandle removeLayer(String id) {var state=live();String key=key(id,"layer:");state.removeLayer(key);forget(state,key);return this;}
    public AssimpModelHandle layerPhase(String id,double phase) {layerPlayer(id).synchronizePhase(phase);return this;}
    public AssimpModelHandle layerPlayback(String id,double speed,boolean looping) {var p=layerPlayer(id);p.releaseSynchronizedPhase();p.setSpeed(number(speed));p.setLooping(looping);return this;}
    public Map<String,Object> layerPlayback(String id) {
        var p=layerPlayer(id);var clip=p.getAnimation();
        return Map.of("clip",clip.getName(),"time",p.getCurrentTime()/clip.getTicksPerSecond(),
                "duration",clip.getDuration()/clip.getTicksPerSecond(),"speed",p.getSpeed(),
                "playing",p.isPlaying(),"looping",p.isLooping(),"synchronized",p.hasSynchronizedPhase());
    }
    public AssimpModelHandle layerReleasePhase(String id) {layerPlayer(id).releaseSynchronizedPhase();return this;}
    public AssimpModelHandle clearScriptControls() {
        var state=live();var keys=CONTROLS.remove(state);
        if(keys!=null) for(String key:keys) {
            if(key.startsWith("kubejs:pose:"))state.removeModifier(key);else state.removeLayer(key);
        }
        var p=state.getCurrentAnimation();if(p!=null){p.releaseSynchronizedPhase();p.setSpeed(1);}
        state.resetMorphWeights();
        return this;
    }

    public List<Map<String,Object>> meshes() {
        return new ArrayList<>(live().getScene().getMeshes().stream().map(mesh->Map.<String,Object>of("name",mesh.getName(),
                "vertices",mesh.getVertexCount(),"bones",mesh.getBoneNames().size(),
                "morphTargets",new ArrayList<>(mesh.getMorphTargets().stream().map(target->target.getName()).toList()))).toList());
    }
    public AssimpModelHandle morph(int meshIndex,double[] weights) {
        var state=live();var mesh=mesh(state,meshIndex);
        if(weights.length!=mesh.getMorphTargets().size()) throw new IllegalArgumentException("Morph weight count mismatch");
        float[] values=new float[weights.length];
        for(int index=0;index<weights.length;index++) values[index]=number(weights[index]);
        state.setMorphWeights(mesh,values);return this;
    }
    public AssimpModelHandle resetMorphs() {live().resetMorphWeights();return this;}

    /** Strict sidecar keys; unspecified fields retain the current asset settings. */
    public AssimpModelHandle performance(Map<String,Object> options) {
        var state=live();
        var value=JSON.toJsonTree(state.getPerformanceSettings()).getAsJsonObject();
        options.forEach((key,entry)->value.add(key,JSON.toJsonTree(entry)));
        state.setPerformanceSettings(InstancePerformanceSettings.read(value));return this;
    }
    public Map<String,Object> performance() {
        var value=live().getPerformanceSettings();
        return Map.of("updateIntervalTicks",value.updateIntervalTicks(),"renderInterpolation",value.renderInterpolation(),
                "ik",value.ik(),"springBones",value.springBones(),"particles",value.particles(),"trails",value.trails(),
                "maxParticleEmissions",value.maxParticleEmissions(),"maxTrailSamples",value.maxTrailSamples());
    }
    public AssimpModelHandle effects(boolean enabled) {configured().setWorldEffectsEnabled(enabled);return this;}
    public AssimpModelHandle groundPlacement(boolean enabled) {configured().setGroundPlacementEnabled(enabled);return this;}

    /** Update an existing JSON-defined IK/LookAt target in model space. */
    public AssimpModelHandle target(String modifier,double x,double y,double z) {
        var value=live().getModifier(modifier);var point=new Vector3f(number(x),number(y),number(z));
        if(value instanceof ChainIkModifier ik) ik.setTarget(point);
        else if(value instanceof LookAtModifier look) look.setTarget(point);
        else throw new IllegalArgumentException("Expected an existing IK or LookAt modifier: "+modifier);
        return this;
    }

    public Map<String,Double> bounds() {
        var box=live().getPoseSnapshot().getBounds();
        return Map.of("minX",box.minX,"minY",box.minY,"minZ",box.minZ,"maxX",box.maxX,"maxY",box.maxY,"maxZ",box.maxZ);
    }
    public Map<String,Double> node(String node) {
        var matrix=live().getPoseSnapshot().getNodeTransform(node);
        if(matrix==null) throw new IllegalArgumentException("Unknown node: "+node);
        return position(matrix.getTranslation(new Vector3f()));
    }
    public Map<String,Double> attachment(String name) {
        var matrix=configured().attachmentTransform(name,new Matrix4f());
        if(matrix==null) throw new IllegalArgumentException("Unknown attachment: "+name);
        return position(matrix.getTranslation(new Vector3f()));
    }

    /** Model-space visual query only. Exact mesh queries are explicit and limited to 100k vertices. */
    public Map<String,Object> raycast(String mode,double ox,double oy,double oz,double dx,double dy,double dz,double distance) {
        var state=live();var selected=ModelCollision.Mode.valueOf(mode.toUpperCase(Locale.ROOT));
        if(selected==ModelCollision.Mode.MESH && state.getScene().getMeshes().stream().mapToLong(AssimpMesh::getVertexCount).sum()>100_000)
            throw new IllegalArgumentException("Script mesh raycast exceeds 100,000 vertices; use BOUNDS, BONE or OBB");
        var ray=new Ray(new Vector3f(number(ox),number(oy),number(oz)),new Vector3f(number(dx),number(dy),number(dz)));
        var hit=configured().collision().raycast(state.getPoseSnapshot(),ray,number(distance),selected);
        return hit.map(value->Map.<String,Object>of("distance",value.distance(),"position",position(value.position()),
                "normal",position(value.normal()),"triangle",value.triangle())).orElse(null);
    }

    private cn.minerealms.assimploader.animation.ModelInstance configured() {
        var configured=live().getModelInstance();
        if(configured==null) throw new IllegalStateException("This model has no instance sidecar");
        return configured;
    }
    private AnimationState live() {
        if(Thread.currentThread()!=thread) throw new IllegalStateException("Assimp handle used off its client thread");
        var state=reference.get();
        if(state==null||state.isClosed()) throw new IllegalStateException("Assimp instance expired; reacquire after reload or owner removal");
        return state;
    }
    private AnimationPlayer player() {
        var player=live().getCurrentAnimation();
        if(player==null)throw new IllegalStateException("Play a base clip before controlling its clock");return player;
    }
    private AnimationPlayer layerPlayer(String id) {
        var layer=live().getLayer(key(id,"layer:"));
        if(layer==null)throw new IllegalArgumentException("Unknown script layer: "+id);return layer.getPlayer();
    }
    private String control(String id,String kind) {
        String key=key(id,kind); var owned=CONTROLS.computeIfAbsent(live(),ignored->new java.util.LinkedHashSet<>());
        if(!owned.contains(key)&&owned.size()>=64)throw new IllegalArgumentException("Script controls exceed 64 layers/poses");
        owned.add(key);return key;
    }
    private static String key(String id,String kind) {
        if(id==null||id.isBlank()||id.length()>128)throw new IllegalArgumentException("Script control id requires 1..128 characters");
        return "kubejs:"+kind+id;
    }
    private static void forget(AnimationState state,String key) {var keys=CONTROLS.get(state);if(keys!=null)keys.remove(key);}
    private static void collect(cn.minerealms.assimploader.model.AssimpNode node,List<String> names) {
        if(node==null)return;names.add(node.getName());node.getChildren().forEach(child->collect(child,names));
    }
    private static AssimpMesh mesh(AnimationState state,int index) {
        var meshes=state.getScene().getMeshes();
        if(index<0||index>=meshes.size()) throw new IllegalArgumentException("Invalid mesh index: "+index);
        return meshes.get(index);
    }
    private static float number(double value) {
        if(!Double.isFinite(value)||!Float.isFinite((float)value)) throw new IllegalArgumentException("Expected a finite float-range number");
        return (float)value;
    }
    private static Map<String,Double> position(Vector3f value) {return Map.of("x",(double)value.x,"y",(double)value.y,"z",(double)value.z);}
}
