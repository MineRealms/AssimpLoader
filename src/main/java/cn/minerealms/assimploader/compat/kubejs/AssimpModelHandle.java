package cn.minerealms.assimploader.compat.kubejs;

import cn.minerealms.assimploader.animation.AnimationState;
import cn.minerealms.assimploader.animation.InstancePerformanceSettings;
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
    private final WeakReference<AnimationState> reference;
    private final Thread thread=Thread.currentThread();

    AssimpModelHandle(AnimationState state) {reference=new WeakReference<>(state);}

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

    public AssimpModelHandle layer(String id,String clip,double weight,boolean additive,Map<String,Number> mask) {
        Map<String,Float> converted=new LinkedHashMap<>();
        mask.forEach((node,value)->converted.put(node,number(value.doubleValue())));
        live().setLayer(id,clip,number(weight),additive,converted);return this;
    }
    public AssimpModelHandle layerWeight(String id,double weight) {live().setLayerWeight(id,number(weight));return this;}
    public AssimpModelHandle removeLayer(String id) {live().removeLayer(id);return this;}

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
