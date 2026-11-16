package cn.minerealms.assimploader.animation.ik;

import cn.minerealms.assimploader.animation.PoseGraph;
import cn.minerealms.assimploader.animation.PoseModifier;
import cn.minerealms.assimploader.model.AssimpScene;
import org.joml.Matrix4f;
import org.joml.Vector3f;

import java.util.List;
import java.util.Map;

/** A bounded body offset gives the lowest grounded leg reach before individual foot IK. */
public final class GroundBodyModifier implements PoseModifier {
    private final String node;
    private final List<String> feet;
    private final float rise,drop,maximumOffset,sole;
    private final Matrix4f modelToWorld=new Matrix4f();
    private FootPlacementModifier.GroundSampler sampler;
    private float lastOffset;

    public GroundBodyModifier(String node,List<String> feet,float rise,float drop,float maximumOffset,float sole) {
        if(node==null || node.isBlank() || feet.isEmpty() || feet.size()>16 || !Float.isFinite(rise) || rise<0
                || !Float.isFinite(drop) || drop<=0 || rise+drop>FootPlacementModifier.MAX_GROUND_TRACE
                || !Float.isFinite(maximumOffset) || maximumOffset<=0 || maximumOffset>FootPlacementModifier.MAX_GROUND_TRACE
                || !Float.isFinite(sole) || Math.abs(sole)>FootPlacementModifier.MAX_GROUND_TRACE) throw new IllegalArgumentException("Invalid ground body settings");
        this.node=node;this.feet=List.copyOf(feet);this.rise=rise;this.drop=drop;this.maximumOffset=maximumOffset;this.sole=sole;
    }
    @Override public Category category() { return Category.GROUND; }
    public float lastOffset() { return lastOffset; }
    public void bind(Matrix4f transform,FootPlacementModifier.GroundSampler sampler) {
        if(!transform.isFinite() || Math.abs(transform.determinant())<1e-10f) throw new IllegalArgumentException("Invalid ground body anchor");
        modelToWorld.set(transform);this.sampler=sampler;
    }
    @Override public void apply(AssimpScene scene,Map<String,Matrix4f> locals,double seconds) {
        if(seconds==0) return;
        lastOffset=0;
        if(sampler==null) return;
        var graph=new PoseGraph(scene.getRootNode(),locals);
        float offset=Float.POSITIVE_INFINITY;
        for(String foot:feet) {
            var point=modelToWorld.transformPosition(graph.position(foot));
            var ground=sampler.ground(new Vector3f(point).add(0,rise,0),rise+drop);
            if(ground.isEmpty() || !ground.get().isFinite()) continue;
            float delta=ground.get().y+sole-point.y;
            if(delta>=-drop && delta<=rise) offset=Math.min(offset,delta);
        }
        if(!Float.isFinite(offset)) return;
        lastOffset=Math.max(-maximumOffset,Math.min(maximumOffset,offset));
        var modelDelta=new Matrix4f(modelToWorld).invert().transformDirection(new Vector3f(0,lastOffset,0));
        var transform=graph.global(node);
        transform.m30(transform.m30()+modelDelta.x).m31(transform.m31()+modelDelta.y).m32(transform.m32()+modelDelta.z);
        graph.setGlobal(node,transform);
    }
    @Override public void reset() { lastOffset=0; }
}
