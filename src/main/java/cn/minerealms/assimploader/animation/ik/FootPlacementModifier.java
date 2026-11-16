package cn.minerealms.assimploader.animation.ik;

import cn.minerealms.assimploader.animation.PoseGraph;
import cn.minerealms.assimploader.animation.PoseModifier;
import cn.minerealms.assimploader.model.AssimpScene;
import org.joml.Matrix4f;
import org.joml.Quaternionf;
import org.joml.Vector3f;

import java.util.List;
import java.util.Map;
import java.util.Optional;

/** Tick-only world ground sampling with explicit model/world conversion and bounded reach. */
public final class FootPlacementModifier implements PoseModifier {
    public static final float MAX_GROUND_TRACE=64;
    @Override public Category category() { return Category.IK; }
    @FunctionalInterface public interface GroundSampler { Optional<Vector3f> ground(Vector3f origin, float downDistance); }
    private final List<String> chain;
    private GroundSampler sampler;
    private final Matrix4f modelToWorld = new Matrix4f();
    private final float rise, drop, sole, weight;
    private final ChainIkModifier solver;
    private final Vector3f pole;
    private final boolean preserveOrientation;
    private Vector3f lastTarget;

    public FootPlacementModifier(List<String> chain, GroundSampler sampler, float rise, float drop, float sole, float weight) {
        this(chain,sampler,rise,drop,sole,weight,null,false);
    }
    public FootPlacementModifier(List<String> chain, GroundSampler sampler, float rise, float drop, float sole, float weight,
                                 Vector3f pole, boolean preserveOrientation) {
        if (chain.size()<2 || chain.size()>16 || !Float.isFinite(rise) || rise<0 || !Float.isFinite(drop) || drop<=0
                || rise+drop>MAX_GROUND_TRACE || !Float.isFinite(sole) || Math.abs(sole)>MAX_GROUND_TRACE
                || !Float.isFinite(weight) || weight<0 || weight>1) throw new IllegalArgumentException("Invalid foot placement");
        this.chain=List.copyOf(chain); this.sampler=sampler;
        this.rise=rise; this.drop=drop; this.sole=sole; this.weight=weight;
        if (pole!=null && (chain.size()!=3 || !pole.isFinite() || !Float.isFinite(pole.lengthSquared()) || pole.lengthSquared()<1e-12f))
            throw new IllegalArgumentException("Foot pole requires a three-node chain and a finite nonzero direction");
        this.pole=pole==null?null:new Vector3f(pole).normalize();
        this.preserveOrientation=preserveOrientation;
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
        String end=chain.get(chain.size()-1);
        Matrix4f orientation=preserveOrientation?graph.global(end):null;
        if (pole==null) {
            solver.setTarget(target);
            solver.apply(scene,locals,deltaSeconds);
            // The CCD adapter has its own graph, so discard the cached transforms from ground sampling.
            graph=new PoseGraph(scene.getRootNode(),locals);
        } else solveTwoBone(graph,target);
        if (orientation!=null) {
            orientation.setTranslation(graph.position(end));
            graph.setGlobal(end,orientation);
        }
    }

    private void solveTwoBone(PoseGraph graph, Vector3f target) {
        String root=chain.get(0), joint=chain.get(1), end=chain.get(2);
        if (!graph.isAncestor(root,joint) || !graph.isAncestor(joint,end))
            throw new IllegalArgumentException("Ground IK nodes must form an ordered chain");
        Vector3f a=graph.position(root), b=graph.position(joint), c=graph.position(end);
        float upper=a.distance(b), lower=b.distance(c);
        if (upper<1e-6f || lower<1e-6f) return;
        Vector3f direction=new Vector3f(target).sub(a);
        float distance=direction.length();
        if (distance<1e-6f) {
            direction.set(c).sub(a);
            if (direction.lengthSquared()<1e-12f) direction.set(b).sub(a);
        }
        direction.normalize();
        // A pole is a model-space bend direction, not a world-space point or a bone-local axis.
        Vector3f bend=new Vector3f(pole).fma(-pole.dot(direction),direction);
        if (bend.lengthSquared()<1e-10f) {
            bend.set(b).sub(a);
            bend.fma(-bend.dot(direction),direction);
            if (bend.lengthSquared()<1e-10f) {
                bend.set(Math.abs(direction.x)<.8f?1:0,Math.abs(direction.x)<.8f?0:1,0);
                bend.fma(-bend.dot(direction),direction);
            }
        }
        bend.normalize();
        float epsilon=Math.min(upper,lower)*1e-5f;
        float reach=Math.max(Math.abs(upper-lower)+epsilon,Math.min(distance,upper+lower-epsilon));
        float along=(upper*upper+reach*reach-lower*lower)/(2*reach);
        float height=(float)Math.sqrt(Math.max(0,upper*upper-along*along));
        Vector3f desiredJoint=new Vector3f(a).fma(along,direction).fma(height,bend);
        Vector3f reachableEnd=new Vector3f(a).fma(reach,direction);
        Matrix4f originalRoot=graph.local(root), originalJoint=graph.local(joint);
        graph.rotateModel(root,new Quaternionf().rotationTo(new Vector3f(b).sub(a).normalize(),desiredJoint.sub(a).normalize()));
        Vector3f actualJoint=graph.position(joint);
        graph.rotateModel(joint,new Quaternionf().rotationTo(graph.position(end).sub(actualJoint).normalize(),
                reachableEnd.sub(actualJoint).normalize()));
        if (weight<1) {
            graph.setLocal(root,cn.minerealms.assimploader.animation.AnimationBlender.blendTransforms(originalRoot,graph.local(root),weight));
            graph.setLocal(joint,cn.minerealms.assimploader.animation.AnimationBlender.blendTransforms(originalJoint,graph.local(joint),weight));
        }
    }
    @Override public void reset() { lastTarget=null; }
}
