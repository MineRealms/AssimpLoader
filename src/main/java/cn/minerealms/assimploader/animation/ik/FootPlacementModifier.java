package cn.minerealms.assimploader.animation.ik;

import cn.minerealms.assimploader.animation.PoseGraph;
import cn.minerealms.assimploader.animation.PoseModifier;
import cn.minerealms.assimploader.model.AssimpScene;
import org.joml.Matrix4f;
import org.joml.Vector3f;

import java.util.List;
import java.util.Map;
import java.util.Optional;

/** Tick-only world ground sampling with explicit model/world conversion and bounded reach. */
public final class FootPlacementModifier implements PoseModifier {
    @Override public Category category() { return Category.IK; }
    @FunctionalInterface public interface GroundSampler { Optional<Vector3f> ground(Vector3f origin, float downDistance); }
    private final List<String> chain;
    private GroundSampler sampler;
    private final Matrix4f modelToWorld = new Matrix4f();
    private final float rise, drop, sole, weight;
    private final ChainIkModifier solver;
    private Vector3f lastTarget;

    public FootPlacementModifier(List<String> chain, GroundSampler sampler, float rise, float drop, float sole, float weight) {
        if (chain.size()<2 || chain.size()>16 || !Float.isFinite(rise) || rise<0 || !Float.isFinite(drop) || drop<=0
                || !Float.isFinite(sole) || !Float.isFinite(weight) || weight<0 || weight>1) throw new IllegalArgumentException("Invalid foot placement");
        this.chain=List.copyOf(chain); this.sampler=sampler;
        this.rise=rise; this.drop=drop; this.sole=sole; this.weight=weight;
        solver=new ChainIkModifier(chain,new Vector3f(),weight,24,.0005f);
    }
    public void setGroundSampler(GroundSampler sampler) { this.sampler=sampler; }
    public Vector3f getLastTarget() { return lastTarget==null?null:new Vector3f(lastTarget); }
    public void setModelToWorld(Matrix4f matrix) {
        if (!matrix.isFinite() || Math.abs(matrix.determinant())<1e-10f) throw new IllegalArgumentException("Invalid foot placement anchor");
        modelToWorld.set(matrix);
    }
    @Override public void apply(AssimpScene scene, Map<String, Matrix4f> locals, double deltaSeconds) {
        if (deltaSeconds == 0 || weight == 0) return;
        lastTarget=null;
        if (sampler==null) return;
        PoseGraph graph = new PoseGraph(scene.getRootNode(),locals);
        Vector3f foot = modelToWorld.transformPosition(graph.position(chain.get(chain.size()-1)));
        Optional<Vector3f> ground = sampler.ground(new Vector3f(foot).add(0,rise,0),rise+drop);
        if (ground.isEmpty()) return;
        Vector3f target = ground.get();
        if (!target.isFinite() || target.y-foot.y > rise+1e-5f || foot.y-target.y > drop+1e-5f) return;
        target = new Matrix4f(modelToWorld).invert().transformPosition(new Vector3f(target).add(0,sole,0));
        lastTarget=new Vector3f(target);
        solver.setTarget(target);
        solver.apply(scene,locals,deltaSeconds);
    }
    @Override public void reset() { lastTarget=null; }
}
