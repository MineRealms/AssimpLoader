package cn.minerealms.assimploader.character.solver;

import cn.minerealms.assimploader.animation.AnimationBlender;
import cn.minerealms.assimploader.animation.PoseGraph;
import cn.minerealms.assimploader.animation.PoseModifier;
import cn.minerealms.assimploader.model.AssimpScene;
import org.joml.Matrix4f;
import org.joml.Quaternionf;
import org.joml.Vector3f;

import java.util.*;

/** Bounded CCD with rest-relative XYZ limits or an exact local hinge axis. Angles are radians. */
public final class LimitedIkModifier implements PoseModifier {
    public record Limit(Vector3f lower,Vector3f upper,Vector3f hinge) {
        public Limit {
            lower=new Vector3f(lower);upper=new Vector3f(upper);hinge=hinge==null?null:new Vector3f(hinge);
            if(!lower.isFinite() || !upper.isFinite() || lower.x>upper.x || lower.y>upper.y || lower.z>upper.z
                    || Math.min(lower.x,Math.min(lower.y,lower.z)) < -Math.PI*2
                    || Math.max(upper.x,Math.max(upper.y,upper.z))>Math.PI*2)
                throw new IllegalArgumentException("Invalid joint limits");
            if(hinge!=null) {
                if(!hinge.isFinite() || hinge.lengthSquared()<1e-10f)throw new IllegalArgumentException("Invalid hinge");
                hinge.normalize();
            }
        }
        @Override public Vector3f lower(){return new Vector3f(lower);}
        @Override public Vector3f upper(){return new Vector3f(upper);}
        @Override public Vector3f hinge(){return hinge==null?null:new Vector3f(hinge);}
    }
    private final List<String> chain;
    private final Map<String,Limit> limits;
    private final int iterations;
    private final float tolerance,maxAngle,weight;
    private final Category phase;
    private final Vector3f target;
    private boolean enabled=true;
    private float lastError;
    public LimitedIkModifier(List<String> chain,Vector3f target,Map<String,Limit> limits,int iterations,
                             float tolerance,float maxAngle,float weight,boolean afterPhysics) {
        this.chain=List.copyOf(chain);this.limits=Map.copyOf(limits);this.target=new Vector3f(target);
        if(chain.size()<2 || chain.size()>64 || chain.stream().distinct().count()!=chain.size()
                || !target.isFinite() || iterations<1 || iterations>128 || !Float.isFinite(tolerance) || tolerance<=0
                || !Float.isFinite(maxAngle) || maxAngle<=0 || maxAngle>Math.PI
                || !Float.isFinite(weight) || weight<0 || weight>1 || !chain.containsAll(limits.keySet()))
            throw new IllegalArgumentException("Invalid constrained IK");
        this.iterations=iterations;this.tolerance=tolerance;this.maxAngle=maxAngle;this.weight=weight;
        phase=afterPhysics?Category.POST_PHYSICS:Category.IK;
    }
    public void setTarget(Vector3f value) {if(!value.isFinite())throw new IllegalArgumentException("Non-finite IK target");target.set(value);}
    public void setEnabled(boolean value){enabled=value;}
    public boolean isEnabled(){return enabled;}
    public Vector3f target(){return new Vector3f(target);}
    public float lastError(){return lastError;}
    @Override public Category category(){return phase;}
    @Override public void apply(AssimpScene scene,Map<String,Matrix4f> locals,double dt) {
        if(!enabled || weight==0)return;
        var graph=new PoseGraph(scene.getRootNode(),locals);
        var before=new LinkedHashMap<String,Matrix4f>();
        for(int i=0;i<chain.size();i++) {
            String node=chain.get(i);before.put(node,graph.local(node));
            if(i>0 && !graph.isAncestor(chain.get(i-1),node))throw new IllegalArgumentException("IK chain is not ordered");
        }
        String tip=chain.get(chain.size()-1);
        for(int iteration=0;iteration<iterations;iteration++) {
            if(graph.position(tip).distanceSquared(target)<=tolerance*tolerance)break;
            for(int i=chain.size()-2;i>=0;i--) {
                String node=chain.get(i);
                var origin=graph.position(node);
                var end=graph.position(tip).sub(origin);var desired=new Vector3f(target).sub(origin);
                if(end.lengthSquared()<1e-12 || desired.lengthSquared()<1e-12)continue;
                var delta=new Quaternionf().rotationTo(end.normalize(),desired.normalize());
                if(delta.w<0)delta.set(-delta.x,-delta.y,-delta.z,-delta.w);
                float angle=2*(float)Math.acos(Math.min(1,Math.max(-1,delta.w)));
                if(angle>maxAngle)delta.identity().slerp(new Quaternionf().rotationTo(end,desired),maxAngle/angle);
                graph.rotateModel(node,delta);
                var limit=limits.get(node);
                if(limit!=null) {
                    var matrix=graph.local(node);
                    var rest=scene.findNodeByName(node).getTransformation().getUnnormalizedRotation(new Quaternionf()).normalize();
                    var relative=new Quaternionf(rest).invert().mul(matrix.getUnnormalizedRotation(new Quaternionf())).normalize();
                    if(limit.hinge!=null) {
                        var axis=limit.hinge;
                        float projected=relative.x*axis.x+relative.y*axis.y+relative.z*axis.z;
                        float hingeAngle=2*(float)Math.atan2(projected,relative.w);
                        hingeAngle=(float)Math.atan2(Math.sin(hingeAngle),Math.cos(hingeAngle));
                        // Hinge interval uses the first pair; XYZ intervals apply to unrestricted joints.
                        relative.fromAxisAngleRad(axis,Math.max(limit.lower.x,Math.min(limit.upper.x,hingeAngle)));
                    } else {
                        var angles=relative.getEulerAnglesXYZ(new Vector3f()).max(limit.lower).min(limit.upper);
                        relative.rotationXYZ(angles.x,angles.y,angles.z);
                    }
                    graph.setLocal(node,new Matrix4f().translationRotateScale(matrix.getTranslation(new Vector3f()),
                            rest.mul(relative),matrix.getScale(new Vector3f())));
                }
            }
        }
        if(weight<1)before.forEach((node,matrix)->graph.setLocal(node,AnimationBlender.blendTransforms(matrix,graph.local(node),weight)));
        lastError=graph.position(tip).distance(target);
    }
}
