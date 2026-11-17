package cn.minerealms.assimploader.animation;

import cn.minerealms.assimploader.core.AssimpModelLoader;
import cn.minerealms.assimploader.core.ModelImportMode;
import cn.minerealms.assimploader.model.AssimpScene;
import com.google.gson.JsonParser;
import org.joml.Matrix4f;
import org.joml.Quaternionf;
import org.joml.Vector3f;
import org.junit.jupiter.api.Test;

import java.nio.file.Path;
import java.util.HashMap;

import static org.junit.jupiter.api.Assertions.*;

class ScriptPoseModifierTest {
    private AssimpScene scene() throws Exception {
        return new AssimpModelLoader().loadModel(Path.of("src/main/resources/assets/assimploader/models/character/block_player/block_player.fbx"), ModelImportMode.DYNAMIC);
    }
    private ScriptPoseModifier pose(AssimpScene scene,String json) {
        return new ScriptPoseModifier(scene,JsonParser.parseString(json).getAsJsonObject());
    }
    @Test void restRelativePoseRetainsImportedTranslationScaleAndOtherBones() throws Exception {
        var scene=scene();var locals=new HashMap<String,Matrix4f>();
        Matrix4f rest=new Matrix4f(scene.findNodeByName("Forearm.R").getTransformation());
        locals.put("Head",new Matrix4f().rotateY(.5f));
        var pose=pose(scene,"{\"bones\":{\"Forearm.R\":{\"rotation\":[90,0,0]}}}");
        pose.apply(scene,locals,.05);
        assertTrue(new Matrix4f(rest).rotateX((float)Math.PI/2).equals(locals.get("Forearm.R"),1e-4f));
        assertTrue(new Matrix4f().rotateY(.5f).equals(locals.get("Head"),1e-6f));
        assertEquals(PoseModifier.Category.GENERAL,pose.category());
        assertTrue(rest.equals(scene.findNodeByName("Forearm.R").getTransformation(),0));
    }
    @Test void additiveAndBoneWeightsComposeWithBaseClip() throws Exception {
        var scene=scene();var locals=new HashMap<String,Matrix4f>();
        Matrix4f rest=new Matrix4f(scene.findNodeByName("Forearm.R").getTransformation());
        Matrix4f base=new Matrix4f(rest).rotateX(.2f);locals.put("Forearm.R",base);
        var pose=pose(scene,"{\"additive\":true,\"weight\":0.5,\"bones\":{\"Forearm.R\":{\"rotation\":[90,0,0],\"weight\":0.5}}}");
        pose.apply(scene,locals,.05);
        var delta=new Matrix4f(base).invert().mul(locals.get("Forearm.R")).getUnnormalizedRotation(new Quaternionf());
        assertEquals(Math.PI/8,delta.angle(),1e-5);
    }
    @Test void modelSpaceArmAndKneeRotationsSurviveActualImportedBoneRolls() throws Exception {
        var scene=scene();
        var bind=new PoseGraph(scene.getRootNode(),java.util.Map.of());
        for(String side:new String[]{"L","R"}) {
            var locals=new HashMap<String,Matrix4f>();
            pose(scene,"{\"space\":\"model\",\"bones\":{\"UpperArm."+side+"\":{\"rotation\":[-90,0,0]}}}").apply(scene,locals,.05);
            var graph=new PoseGraph(scene.getRootNode(),locals);
            Vector3f expected=bind.position("Hand."+side).sub(bind.position("UpperArm."+side)).rotateX(-(float)Math.PI/2);
            Vector3f actual=graph.position("Hand."+side).sub(graph.position("UpperArm."+side));
            assertTrue(expected.distance(actual)<1e-4,side+" arm did not rotate about model X: "+actual);
            assertTrue(actual.z>.2,side+" hand should reach in front of the visible face: "+actual+" bind="+bind.position("Hand."+side).sub(bind.position("UpperArm."+side)));
            locals.clear();
            pose(scene,"{\"space\":\"model\",\"bones\":{\"Shin."+side+"\":{\"rotation\":[90,0,0]}}}").apply(scene,locals,.05);
            graph=new PoseGraph(scene.getRootNode(),locals);
            expected=bind.position("Foot."+side).sub(bind.position("Shin."+side)).rotateX((float)Math.PI/2);
            actual=graph.position("Foot."+side).sub(graph.position("Shin."+side));
            assertTrue(expected.distance(actual)<1e-4,side+" knee did not rotate about model X");
        }
    }
    @Test void rejectsUnknownBonesOptionsAndInvalidTransforms() throws Exception {
        var scene=scene();
        for(String json:new String[]{"{\"bones\":{\"typo\":{}}}","{\"bones\":{},\"stage\":\"physics\"}",
                "{\"bones\":{},\"weight\":2}","{\"bones\":{},\"space\":\"world\"}","{\"bones\":{\"Head\":{\"scale\":[0,1,1]}}}",
                "{\"bones\":{\"Head\":{\"rotation\":[1,2]}}}","{\"bones\":{},\"typo\":true}"})
            assertThrows(IllegalArgumentException.class,()->pose(scene,json));
    }
    @Test void postPhysicsIsExplicitAndZeroWeightLeavesPoseUntouched() throws Exception {
        var scene=scene();var locals=new HashMap<String,Matrix4f>();
        var pose=pose(scene,"{\"stage\":\"post_physics\",\"weight\":0,\"bones\":{\"Head\":{\"rotation\":[90,0,0]}}}");
        assertEquals(PoseModifier.Category.POST_PHYSICS,pose.category());pose.apply(scene,locals,.05);assertTrue(locals.isEmpty());
    }
}
