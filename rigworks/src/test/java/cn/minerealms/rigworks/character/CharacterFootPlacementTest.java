package cn.minerealms.rigworks.character;

import cn.minerealms.assimploader.animation.AnimationState;
import cn.minerealms.assimploader.animation.ModelInstanceDefinition;
import cn.minerealms.assimploader.animation.PoseGraph;
import cn.minerealms.assimploader.animation.PoseSnapshot;
import cn.minerealms.assimploader.animation.ik.FootPlacementModifier;
import cn.minerealms.assimploader.core.AssimpModelLoader;
import cn.minerealms.assimploader.core.ModelImportMode;
import org.joml.Matrix3f;
import org.joml.Matrix4f;
import org.joml.Vector3f;
import org.junit.jupiter.api.Test;

import java.io.StringReader;
import java.nio.file.Path;
import java.util.HashMap;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;

class CharacterFootPlacementTest {
    private static final Path FBX=Path.of("src/main/resources/assets/rigworks/models/character/block_player/block_player.fbx");

    @Test void importedCrouchUsesForwardKneesAndKeepsSolesOnFloorWithoutChangingLengths() throws Exception {
        var scene=new AssimpModelLoader().loadModel(FBX,ModelImportMode.DYNAMIC);
        var neutral=new PoseGraph(scene.getRootNode(),new HashMap<>());
        try(var state=new AnimationState("foot-crouch",scene)) {
            state.initialize();state.transitionTo("crouch",0);state.update(.05f);
            var locals=new HashMap<>(state.getPoseSnapshot().copyLocalTransforms());
            var graph=new PoseGraph(scene.getRootNode(),locals);
            for(String side:List.of("L","R")) {
                String hip="Thigh."+side,knee="Shin."+side,foot="Foot."+side;
                float upper=graph.position(hip).distance(graph.position(knee));
                float lower=graph.position(knee).distance(graph.position(foot));
                // The sole's authored horizontal reference is separate from the knee bend solve.
                var soleFrame=neutral.global(foot).setTranslation(graph.position(foot));
                graph.setGlobal(foot,soleFrame);
                var solver=new FootPlacementModifier(List.of(hip,knee,foot),
                        (origin,distance)->Optional.of(new Vector3f(origin.x,0,origin.z)),
                        .5f,.5f,.1f,1,new Vector3f(0,0,1),true);
                solver.apply(scene,locals,.05);
                graph=new PoseGraph(scene.getRootNode(),locals);
                var a=graph.position(hip);var b=graph.position(knee);var c=graph.position(foot);
                var axis=new Vector3f(c).sub(a).normalize();
                var bend=new Vector3f(b).sub(a);bend.fma(-bend.dot(axis),axis);
                assertTrue(bend.z>.03f,"Knee must bend toward model +Z: "+side+" "+bend);
                assertEquals(upper,a.distance(b),2e-5);assertEquals(lower,b.distance(c),2e-5);
                assertEquals(.1,c.y,5e-4,"Sole clearance must match the configured ground");
                assertTrue(soleFrame.get3x3(new Matrix3f()).equals(graph.global(foot).get3x3(new Matrix3f()),.002f));
            }
            var pose=PoseSnapshot.capture(100,scene,locals);
            assertEquals(0,pose.getBounds().minY,.003,"Skinned footwear must touch the floor");
            assertTrue(pose.getBounds().maxY>1.2);
        }
    }

    @Test void modelInstanceAcceptsPoleAndRejectsZeroOrNonThreeNodeDirections() throws Exception {
        var scene=new AssimpModelLoader().loadModel(FBX,ModelImportMode.DYNAMIC);
        String definition="""
                {"version":1,"model":"test:foot","footPlacement":{"left":{
                  "chain":["Thigh.L","Shin.L","Foot.L"],"pole":[0,0,1],"preserveOrientation":true}}}
                """;
        try(var instance=ModelInstanceDefinition.read(new StringReader(definition)).instantiate(scene)) {
            instance.setWorldContext(new Matrix4f(),(origin,distance)->Optional.of(new Vector3f(origin.x,0,origin.z)));
            instance.animation().update(.05f);
            assertNotNull(instance.feet().get("left").getLastTarget());
        }
        assertThrows(IllegalArgumentException.class,()->ModelInstanceDefinition.read(new StringReader(
                definition.replace("[0,0,1]","[0,0,0]"))).instantiate(scene));
        assertThrows(IllegalArgumentException.class,()->new FootPlacementModifier(List.of("Thigh.L","Foot.L"),
                null,.3f,.5f,.1f,1,new Vector3f(0,0,1),true));
    }

    @Test void parallelPoleAndUnreachableTargetRemainFiniteAndPreserveLengths() throws Exception {
        var scene=new AssimpModelLoader().loadModel(FBX,ModelImportMode.DYNAMIC);
        var locals=new HashMap<String,Matrix4f>();var graph=new PoseGraph(scene.getRootNode(),locals);
        float upper=graph.position("Thigh.L").distance(graph.position("Shin.L"));
        float lower=graph.position("Shin.L").distance(graph.position("Foot.L"));
        var solver=new FootPlacementModifier(List.of("Thigh.L","Shin.L","Foot.L"),
                (origin,distance)->Optional.of(new Vector3f(origin.x,-2,origin.z)),
                .3f,3,.1f,1,new Vector3f(0,-1,0),true);
        solver.apply(scene,locals,.05);graph=new PoseGraph(scene.getRootNode(),locals);
        assertNotNull(solver.getLastTarget());
        assertTrue(graph.global("Foot.L").isFinite());
        assertEquals(upper,graph.position("Thigh.L").distance(graph.position("Shin.L")),2e-5);
        assertEquals(lower,graph.position("Shin.L").distance(graph.position("Foot.L")),2e-5);
        assertTrue(graph.position("Foot.L").y>-.1,"Unreachable floor must not stretch the leg");
    }
}
