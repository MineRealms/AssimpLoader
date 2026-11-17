package cn.minerealms.assimploader.animation.ik;

import cn.minerealms.assimploader.animation.AnimationBlender;
import cn.minerealms.assimploader.animation.PoseGraph;
import org.joml.Matrix4f;
import org.joml.Quaternionf;
import org.joml.Vector3f;

import java.util.List;

/** Analytic two-segment solve in the incoming pose's model frame; no assumed imported bone axis. */
public final class TwoBonePoseConstraint {
    public record Result(float error, float bendRadians, boolean clamped) {}
    private final String root, middle, tip;
    private final float minimumBend, maximumBend;

    public TwoBonePoseConstraint(String root,String middle,String tip,float minimumBend,float maximumBend) {
        if(root==null||middle==null||tip==null||List.of(root,middle,tip).stream().distinct().count()!=3
                || !Float.isFinite(minimumBend)||!Float.isFinite(maximumBend)
                ||minimumBend<0||maximumBend>=Math.PI||minimumBend>maximumBend)
            throw new IllegalArgumentException("Invalid two-bone constraint");
        this.root=root;this.middle=middle;this.tip=tip;this.minimumBend=minimumBend;this.maximumBend=maximumBend;
    }

    public Result solve(PoseGraph graph,Vector3f target,Vector3f pole,float weight) {
        if(!target.isFinite()||!pole.isFinite()||!Float.isFinite(weight)||weight<0||weight>1)
            throw new IllegalArgumentException("Invalid two-bone input");
        if(!graph.isAncestor(root,middle)||!graph.isAncestor(middle,tip))
            throw new IllegalArgumentException("Two-bone nodes must be an ordered descendant chain");
        Vector3f a=graph.position(root),b=graph.position(middle),c=graph.position(tip);
        float upper=a.distance(b),lower=b.distance(c);
        if(!Float.isFinite(upper)||!Float.isFinite(lower)||upper<1e-7f||lower<1e-7f)
            throw new IllegalArgumentException("Two-bone segments need finite nonzero lengths");
        if(weight==0) {
            float cosine=new Vector3f(b).sub(a).normalize().dot(new Vector3f(c).sub(b).normalize());
            return new Result(c.distance(target),(float)Math.acos(Math.max(-1,Math.min(1,cosine))),false);
        }
        Vector3f direction=new Vector3f(target).sub(a);
        float requested=direction.length();
        if(!Float.isFinite(requested))throw new IllegalArgumentException("Two-bone target is outside the numerical range");
        if(requested<1e-7f)direction.set(c).sub(a);
        if(direction.lengthSquared()<1e-12f)direction.set(b).sub(a);
        direction.normalize();
        double near=Math.sqrt(Math.max(0,upper*upper+lower*lower+2*upper*lower*Math.cos(maximumBend)));
        double far=Math.sqrt(Math.max(0,upper*upper+lower*lower+2*upper*lower*Math.cos(minimumBend)));
        float distance=(float)Math.max(Math.max(near,1e-7),Math.min(far,requested));
        Vector3f bend=new Vector3f(pole).sub(a);
        bend.fma(-bend.dot(direction),direction);
        if(bend.lengthSquared()<1e-12f) {
            bend.set(b).sub(a).fma(-new Vector3f(b).sub(a).dot(direction),direction);
            if(bend.lengthSquared()<1e-12f) {
                bend.set(Math.abs(direction.y)<.9f?new Vector3f(0,1,0):new Vector3f(1,0,0));
                bend.fma(-bend.dot(direction),direction);
            }
        }
        bend.normalize();
        float along=(upper*upper+distance*distance-lower*lower)/(2*distance);
        float height=(float)Math.sqrt(Math.max(0,upper*upper-along*along));
        Vector3f elbow=new Vector3f(a).fma(along,direction).fma(height,bend);
        Vector3f reached=new Vector3f(a).fma(distance,direction);
        Matrix4f rootBefore=graph.local(root),middleBefore=graph.local(middle);
        graph.rotateModel(root,new Quaternionf().rotationTo(new Vector3f(b).sub(a).normalize(),new Vector3f(elbow).sub(a).normalize()));
        Vector3f newMiddle=graph.position(middle);
        graph.rotateModel(middle,new Quaternionf().rotationTo(graph.position(tip).sub(newMiddle).normalize(),new Vector3f(reached).sub(newMiddle).normalize()));
        if(weight<1) {
            graph.setLocal(root,AnimationBlender.blendTransforms(rootBefore,graph.local(root),weight));
            graph.setLocal(middle,AnimationBlender.blendTransforms(middleBefore,graph.local(middle),weight));
        }
        float bendAngle=(float)Math.acos(Math.max(-1,Math.min(1,(distance*distance-upper*upper-lower*lower)/(2*upper*lower))));
        return new Result(graph.position(tip).distance(target),bendAngle,Math.abs(distance-requested)>1e-6f);
    }
}
