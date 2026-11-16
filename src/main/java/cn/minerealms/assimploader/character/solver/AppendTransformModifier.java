package cn.minerealms.assimploader.character.solver;

import cn.minerealms.assimploader.animation.PoseGraph;
import cn.minerealms.assimploader.animation.PoseModifier;
import cn.minerealms.assimploader.model.AssimpScene;
import org.joml.Matrix4f;
import org.joml.Quaternionf;
import org.joml.Vector3f;

import java.util.*;

/** Local append deltas measured against rest pose, in validated dependency order. */
public final class AppendTransformModifier implements PoseModifier {
    public record Grant(String source,String target,float rotationWeight,float translationWeight,boolean afterPhysics) {
        public Grant {
            if (source==null || target==null || source.isBlank() || target.isBlank() || source.equals(target)
                    || !Float.isFinite(rotationWeight) || !Float.isFinite(translationWeight)
                    || Math.abs(rotationWeight)>4 || Math.abs(translationWeight)>4)
                throw new IllegalArgumentException("Invalid append transform");
        }
    }
    private final List<Grant> grants;
    private final Category phase;
    public AppendTransformModifier(AssimpScene scene,List<Grant> definitions,boolean afterPhysics) {
        phase=afterPhysics?Category.POST_PHYSICS:Category.GENERAL;
        var graph=new PoseGraph(scene.getRootNode(),new HashMap<>());
        var destinations=new HashMap<String,Grant>();
        for (var grant:definitions) {
            if(!graph.contains(grant.source) || !graph.contains(grant.target)) throw new IllegalArgumentException("Unknown append bone");
            if(graph.isAncestor(grant.target,grant.source)) throw new IllegalArgumentException("Append transform depends on its own descendant");
            if(destinations.putIfAbsent(grant.target,grant)!=null) throw new IllegalArgumentException("Duplicate append target: "+grant.target);
        }
        var ordered=new ArrayList<Grant>(); var active=new HashSet<String>(); var done=new HashSet<String>();
        for(var grant:definitions) visit(grant,destinations,active,done,ordered);
        for(var grant:ordered) {
            var dependency=destinations.get(grant.source);
            if(dependency!=null && dependency.afterPhysics && !grant.afterPhysics)
                throw new IllegalArgumentException("Pre-physics grant depends on post-physics grant");
        }
        grants=ordered.stream().filter(g->g.afterPhysics==afterPhysics).toList();
    }
    private static void visit(Grant grant,Map<String,Grant> all,Set<String> active,Set<String> done,List<Grant> result) {
        if(done.contains(grant.target))return;
        if(!active.add(grant.target))throw new IllegalArgumentException("Cyclic append transforms");
        var dependency=all.get(grant.source);if(dependency!=null)visit(dependency,all,active,done,result);
        active.remove(grant.target);done.add(grant.target);result.add(grant);
    }
    @Override public Category category(){return phase;}
    @Override public void apply(AssimpScene scene,Map<String,Matrix4f> locals,double dt) {
        var graph=new PoseGraph(scene.getRootNode(),locals);
        for(var grant:grants) {
            var rest=scene.findNodeByName(grant.source).getTransformation();
            var source=graph.local(grant.source);
            var delta=rest.getUnnormalizedRotation(new Quaternionf()).invert()
                    .mul(source.getUnnormalizedRotation(new Quaternionf())).normalize();
            if(delta.w<0)delta.set(-delta.x,-delta.y,-delta.z,-delta.w);
            float angle=2*(float)Math.acos(Math.max(-1,Math.min(1,delta.w)));
            var axis=new Vector3f(delta.x,delta.y,delta.z);
            var append=axis.lengthSquared()<1e-12f?new Quaternionf():new Quaternionf().fromAxisAngleRad(axis.normalize(),angle*grant.rotationWeight);
            var target=graph.local(grant.target);
            var rotation=target.getUnnormalizedRotation(new Quaternionf()).mul(append).normalize();
            var translation=target.getTranslation(new Vector3f()).add(source.getTranslation(new Vector3f())
                    .sub(rest.getTranslation(new Vector3f())).mul(grant.translationWeight));
            graph.setLocal(grant.target,new Matrix4f().translationRotateScale(translation,rotation,target.getScale(new Vector3f())));
        }
    }
}
