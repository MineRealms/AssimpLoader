package cn.minerealms.assimploader.character;

import cn.minerealms.assimploader.animation.*;
import cn.minerealms.assimploader.character.solver.*;
import cn.minerealms.assimploader.core.*;
import com.google.gson.JsonParser;
import org.joml.*;
import org.junit.jupiter.api.Test;

import java.io.StringReader;
import java.nio.file.Path;
import java.util.*;

import static org.junit.jupiter.api.Assertions.*;

class CharacterMotionTest {
    private static final String MOTION="""
            {"duration":1,"bones":{"Head":[
              {"time":0,"translation":[0,0,0],"rotation":[0,0,0,1]},
              {"time":1,"translation":[1,1,1],"rotation":[0,0,1,0],
               "curves":{"x":[0.5,0,1,0.5],"rotation":[0.5,0,1,0.5]}}]},
             "morphs":{"Blink":[{"time":0,"weight":0},{"time":1,"weight":1}]},
             "ik":{"leg":[{"time":0.25,"enabled":false},{"time":0.75,"enabled":true}]}}
            """;
    private CharacterMotion motion(){return CharacterMotion.parse(JsonParser.parseString(MOTION).getAsJsonObject());}

    @Test void destinationCurvesControlAxesAndQuaternionIndependently() {
        var motion=motion();var sample=motion.sampleBone("Head",.5);
        assertTrue(sample.translation().x<.35);assertEquals(.5,sample.translation().y,1e-6);assertEquals(.5,sample.translation().z,1e-6);
        assertEquals(Math.PI*sample.translation().x,sample.rotation().angle(),1e-5);
        assertEquals(.5,motion.sampleMorph("Blink",.5),1e-6);
        assertEquals(new Vector3f(),motion.sampleBone("Head",-1).translation());
        assertEquals(new Vector3f(1),motion.sampleBone("Head",2).translation());
        assertThrows(IllegalArgumentException.class,()->motion.sampleBone("Head",Double.NaN));
    }
    @Test void ikSwitchesAreSteppedAndKeepDefaultBeforeFirstKey() {
        var motion=motion();assertTrue(motion.sampleIk("leg",0).isEmpty());
        assertFalse(motion.sampleIk("leg",.25).orElseThrow());assertFalse(motion.sampleIk("leg",.749).orElseThrow());
        assertTrue(motion.sampleIk("leg",.75).orElseThrow());assertTrue(motion.sampleIk("leg",3).orElseThrow());
    }
    @Test void malformedTracksFailBeforeInstallation() {
        assertThrows(IllegalArgumentException.class,()->CharacterMotion.parse(JsonParser.parseString(MOTION.replace("\"time\":1","\"time\":0")).getAsJsonObject()));
        assertThrows(IllegalArgumentException.class,()->CharacterMotion.parse(JsonParser.parseString(MOTION.replace("\"duration\":1","\"duration\":0.1")).getAsJsonObject()));
        assertThrows(IllegalArgumentException.class,()->CharacterMotion.parse(JsonParser.parseString(MOTION.replace("\"weight\":1","\"weight\":5")).getAsJsonObject()));
        assertThrows(IllegalArgumentException.class,()->CharacterMotion.parse(JsonParser.parseString(MOTION.replace("\"enabled\":true","\"enabled\":1")).getAsJsonObject()));
    }
    @Test void keysOwnTheirMutableJomlValues() {
        var p=new Vector3f(1);var q=new Quaternionf();var key=new CharacterMotion.BoneKey(0,p,q,CharacterMotion.Curves.LINEAR);
        p.zero();q.set(0,0,0,0);key.translation().zero();key.rotation().set(0,0,0,0);
        assertEquals(new Vector3f(1),key.translation());assertEquals(new Quaternionf(),key.rotation());
    }
    @Test void installedPlayerPublishesMotionBeforeMorphAndIkAndRestoresOnStop() throws Exception {
        var scene=new AssimpModelLoader().loadModel(Path.of("src/main/resources/assets/assimploader/models/character/block_player/block_player.fbx"),ModelImportMode.DYNAMIC);
        String definition="""
                {"version":1,"defaults":{"Blink":0},"ik":{"leg":{
                "chain":["Thigh.L","Shin.L","Foot.L"],"enabled":true,"weight":0}},"motions":{"probe":
                """+MOTION+"}}";
        try(var state=new AnimationState("motion",scene)) {
            state.initialize();var installed=CharacterRigDefinition.read(new StringReader(definition)).install(state);
            var player=installed.motions();player.play("probe",false);state.update(.5f);
            assertEquals(.5,player.time(),1e-6);assertFalse(installed.ik().get("leg").isEnabled());
            var rest=scene.findNodeByName("Head").getTransformation().getTranslation(new Vector3f());
            var head=new PoseGraph(scene.getRootNode(),Map.of()).local("Head");
            // Read the completed local matrix rather than comparing world translations through parents.
            var expected=new Matrix4f().translationRotateScale(rest.add(motion().sampleBone("Head",.5).translation()),
                    head.getUnnormalizedRotation(new Quaternionf()).mul(motion().sampleBone("Head",.5).rotation()),head.getScale(new Vector3f()));
            assertEquals(expected,state.getPoseSnapshot().copyLocalTransforms().get("Head"));
            assertEquals(.5,installed.morphs().expandedWeights().get("Blink"),1e-6);
            installed.morphs().setWeight("Blink",.8f);state.update(.1f);assertEquals(.8,installed.morphs().expandedWeights().get("Blink"),1e-6);
            player.pause();state.update(.1f);assertEquals(.6,player.time(),1e-6);
            player.seek(.8);state.update(.1f);assertTrue(installed.ik().get("leg").isEnabled());
            player.resume();state.update(.5f);assertEquals(1,player.time());
            player.reset();state.update(.05f);assertTrue(installed.ik().get("leg").isEnabled());assertEquals(.8,installed.morphs().expandedWeights().get("Blink"),1e-6);
            installed.morphs().reset();player.play("probe",true);state.update(1.25f);assertEquals(.25,player.time());
            state.stop();assertNull(player.currentName());assertTrue(installed.ik().get("leg").isEnabled());
        }
    }
}
