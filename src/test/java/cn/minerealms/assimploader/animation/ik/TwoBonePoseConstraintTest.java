package cn.minerealms.assimploader.animation.ik;

import cn.minerealms.assimploader.animation.PoseGraph;
import cn.minerealms.assimploader.model.AssimpNode;
import org.joml.Matrix4f;
import org.joml.Quaternionf;
import org.joml.Vector3f;
import org.junit.jupiter.api.Test;
import org.lwjgl.assimp.AINode;
import org.lwjgl.system.MemoryStack;

import java.util.LinkedHashMap;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

class TwoBonePoseConstraintTest {
    private static PoseGraph graph(Map<String,Matrix4f> locals) {
        try(var stack=MemoryStack.stackPush()) {
            var root=AINode.calloc(stack);root.mName().data(stack.UTF8("root"));
            var middle=AINode.calloc(stack);middle.mName().data(stack.UTF8("middle"));
            var tip=AINode.calloc(stack);tip.mName().data(stack.UTF8("tip"));
            root.mChildren(stack.pointers(middle.address()));
            middle.mChildren(stack.pointers(tip.address()));
            return new PoseGraph(new AssimpNode(root),locals);
        }
    }
    private static Map<String,Matrix4f> locals() {
        var locals=new LinkedHashMap<String,Matrix4f>();
        locals.put("root",new Matrix4f().translationRotateScale(new Vector3f(.3f,.6f,-.2f),new Quaternionf().rotationXYZ(.3f,-.5f,.7f),new Vector3f(1)));
        locals.put("middle",new Matrix4f().translationRotateScale(new Vector3f(.2f,-.8f,.4f),new Quaternionf().rotationZYX(.7f,.4f,-.3f),new Vector3f(1)));
        locals.put("tip",new Matrix4f().translation(-.4f,-.6f,.3f));
        return locals;
    }
    private static TwoBonePoseConstraint constraint(float min,float max) {
        return new TwoBonePoseConstraint("root","middle","tip",(float)Math.toRadians(min),(float)Math.toRadians(max));
    }
    private static float bend(PoseGraph graph) {
        var upper=graph.position("middle").sub(graph.position("root")).normalize();
        var lower=graph.position("tip").sub(graph.position("middle")).normalize();
        return (float)Math.acos(Math.max(-1,Math.min(1,upper.dot(lower))));
    }

    @Test void arbitraryImportedAxesReachTargetPreserveLengthsAndFollowPole() {
        for(var direction:new Vector3f[]{new Vector3f(1,0,0),new Vector3f(0,0,-1),new Vector3f(.3f,-.4f,.8f).normalize()}) {
            var graph=graph(locals());var origin=graph.position("root");
            float upper=origin.distance(graph.position("middle")),lower=graph.position("middle").distance(graph.position("tip"));
            var target=new Vector3f(origin).fma(1.1f,direction);
            var pole=new Vector3f(origin).add(.2f,1,.3f);
            var result=constraint(2,160).solve(graph,target,pole,1);
            assertEquals(0,result.error(),2e-6);assertFalse(result.clamped());
            assertEquals(upper,origin.distance(graph.position("middle")),2e-6);
            assertEquals(lower,graph.position("middle").distance(graph.position("tip")),2e-6);
            var expected=new Vector3f(pole).sub(origin);expected.fma(-expected.dot(direction),direction).normalize();
            var actual=graph.position("middle").sub(origin);actual.fma(-actual.dot(direction),direction).normalize();
            assertEquals(0,actual.distance(expected),2e-6);
            assertEquals(result.bendRadians(),bend(graph),2e-6);
        }
    }

    @Test void unreachableAndOverfoldedTargetsStopAtConfiguredBendLimits() {
        for(float distance:new float[]{0,100}) {
            var graph=graph(locals());var origin=graph.position("root");
            var target=new Vector3f(origin).add(distance,0,0);
            var result=constraint(25,120).solve(graph,target,new Vector3f(origin).add(0,1,0),1);
            assertTrue(result.clamped());assertTrue(graph.global("tip").isFinite());
            assertEquals(Math.toRadians(distance==0?120:25),bend(graph),3e-6);
        }
    }

    @Test void collinearPoleAndStraightOrFoldedIncomingPoseRemainFinite() {
        for(float tipY:new float[]{-1,1}) {
            var locals=locals();locals.put("root",new Matrix4f());
            locals.put("middle",new Matrix4f().translation(0,-1,0));
            locals.put("tip",new Matrix4f().translation(0,tipY,0));
            var graph=graph(locals);var target=new Vector3f(0,0,1);
            var result=constraint(2,160).solve(graph,target,new Vector3f(0,0,2),1);
            assertEquals(0,result.error(),2e-6);assertTrue(graph.global("middle").isFinite());
            assertEquals(1,graph.position("middle").length(),2e-6);
        }
    }

    @Test void zeroWeightDoesNotCreateOverridesOrPerturbMatrices() {
        var locals=locals();var before=new LinkedHashMap<String,Matrix4f>();
        locals.forEach((name,matrix)->before.put(name,new Matrix4f(matrix)));
        constraint(20,120).solve(graph(locals),new Vector3f(1,2,3),new Vector3f(0,1,0),0);
        assertEquals(before,locals);
    }

    @Test void invalidBoundsAndNonfiniteTargetsAreRejectedBeforeWrites() {
        assertThrows(IllegalArgumentException.class,()->constraint(50,20));
        assertThrows(IllegalArgumentException.class,()->constraint(0,180));
        var locals=locals();var before=new LinkedHashMap<>(locals);
        assertThrows(IllegalArgumentException.class,()->constraint(0,160).solve(graph(locals),new Vector3f(Float.NaN,0,0),new Vector3f(),1));
        assertEquals(before,locals);
    }
}
