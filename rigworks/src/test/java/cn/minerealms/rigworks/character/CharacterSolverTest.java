package cn.minerealms.rigworks.character;

import cn.minerealms.assimploader.animation.*;
import cn.minerealms.assimploader.character.solver.*;
import cn.minerealms.assimploader.core.*;
import cn.minerealms.assimploader.model.AssimpScene;
import org.joml.*;
import org.junit.jupiter.api.Test;

import java.io.InputStreamReader;
import java.nio.file.Path;
import java.util.*;

import static org.junit.jupiter.api.Assertions.*;

class CharacterSolverTest {
    private AssimpScene scene() throws Exception {
        return new AssimpModelLoader().loadModel(Path.of("src/main/resources/assets/rigworks/models/character/block_player/block_player.fbx"),ModelImportMode.DYNAMIC);
    }
    @Test void bezierInvertsTimeAxisWithStableEndpoints() {
        for(int i=0;i<=100;i++)assertEquals(i/100d,BezierTiming.LINEAR.sample(i/100d),1e-10);
        var ease=new BezierTiming(.5,0,1,.5);
        assertEquals(0,ease.sample(0));assertEquals(1,ease.sample(1));
        assertTrue(ease.sample(.5)<.35);
        assertThrows(IllegalArgumentException.class,()->new BezierTiming(Double.NaN,0,1,1));
    }
    @Test void appendUsesRestDeltaAndRejectsCycles() throws Exception {
        var scene=scene();var locals=new HashMap<String,Matrix4f>();var graph=new PoseGraph(scene.getRootNode(),locals);
        var original=graph.local("ChestFollower");
        graph.setLocal("Chest",graph.local("Chest").rotateY(.8f));
        var modifier=new AppendTransformModifier(scene,List.of(new AppendTransformModifier.Grant("Chest","ChestFollower",.5f,0,false)),false);
        modifier.apply(scene,locals,.05);
        var difference=original.getUnnormalizedRotation(new Quaternionf()).invert().mul(graph.local("ChestFollower").getUnnormalizedRotation(new Quaternionf()));
        assertEquals(.4,difference.angle(),1e-5);
        assertThrows(IllegalArgumentException.class,()->new AppendTransformModifier(scene,List.of(
                new AppendTransformModifier.Grant("ChestFollower","Head",1,0,false),
                new AppendTransformModifier.Grant("Head","ChestFollower",1,0,false)),false));
    }
    @Test void actualRigIkConvergesAndKneeHingeStaysWithinLimits() throws Exception {
        var scene=scene();var locals=new HashMap<String,Matrix4f>();var graph=new PoseGraph(scene.getRootNode(),locals);
        var original=graph.position("Foot.L");var target=new Vector3f(original).add(0,.12f,.12f);
        var limit=new LimitedIkModifier.Limit(new Vector3f(-2.7f,0,0),new Vector3f(.05f,0,0),new Vector3f(1,0,0));
        var ik=new LimitedIkModifier(List.of("Thigh.L","Shin.L","Foot.L"),target,Map.of("Shin.L",limit),128,.001f,.25f,1,false);
        ik.apply(scene,locals,.05);
        var relative=scene.findNodeByName("Shin.L").getTransformation().getUnnormalizedRotation(new Quaternionf()).invert()
                .mul(graph.local("Shin.L").getUnnormalizedRotation(new Quaternionf()));
        var angles=relative.getEulerAnglesXYZ(new Vector3f());
        assertEquals(0,angles.y,1e-5);assertEquals(0,angles.z,1e-5);
        assertTrue(angles.x>=-2.701 && angles.x<=.051);
        System.out.println("Block player limited IK error="+ik.lastError()+" knee="+angles);
        assertTrue(ik.lastError()<.01,()->"IK error "+ik.lastError());
        ik.setEnabled(false);var retained=graph.local("Thigh.L");ik.apply(scene,locals,.05);assertEquals(retained,graph.local("Thigh.L"));
    }
    @Test void groupBoneAndMeshMorphsShareCompletedPoseAndReset() throws Exception {
        var scene=scene();
        try(var state=new AnimationState("character",scene);
            var reader=new InputStreamReader(Objects.requireNonNull(getClass().getResourceAsStream("/assets/rigworks/models/character/block_player/rig.json")))) {
            state.initialize();state.transitionTo("idle",0);
            var installed=CharacterRigDefinition.read(reader).install(state);
            var before=state.getPoseSnapshot();
            System.out.println("Block player imported morphs="+installed.morphs().names());
            installed.morphs().setWeight("Expression",1);state.update(.05f);
            assertEquals(.8f,installed.morphs().expandedWeights().get("Smile"),1e-6);
            assertEquals(.2f,installed.morphs().expandedWeights().get("Blink"),1e-6);
            var after=state.getPoseSnapshot();
            assertNotEquals(before.getNodeTransform("Head"),after.getNodeTransform("Head"));
            for(var mesh:scene.getMeshes())for(int i=0;i<mesh.getMorphTargets().size();i++) {
                String name=mesh.getMorphTargets().get(i).getName();
                if(name.equals("Smile"))assertEquals(.8f,after.getMorphWeights(mesh)[i],1e-6);
            }
            installed.morphs().reset();state.update(.05f);
            assertTrue(installed.morphs().expandedWeights().isEmpty());
            assertThrows(IllegalArgumentException.class,()->new CharacterMorphModifier(scene,Map.of(),Map.of("A",Map.of("B",1f),"B",Map.of("A",1f))));
        }
    }
}
