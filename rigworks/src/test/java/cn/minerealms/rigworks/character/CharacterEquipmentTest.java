package cn.minerealms.rigworks.character;

import cn.minerealms.assimploader.animation.AnimationState;
import cn.minerealms.assimploader.animation.InstancePerformanceSettings;
import cn.minerealms.rigworks.character.core.CharacterEquipmentDefinition;
import cn.minerealms.rigworks.character.core.CharacterProfile;
import cn.minerealms.assimploader.config.RuntimePerformancePolicy;
import cn.minerealms.assimploader.core.AssimpModelLoader;
import cn.minerealms.assimploader.core.ModelImportMode;
import org.joml.Matrix4f;
import org.joml.Vector3f;
import org.junit.jupiter.api.Test;

import java.io.StringReader;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.*;

class CharacterEquipmentTest {
    private static final Path ASSET = Path.of("src/main/resources/assets/rigworks/models/character/block_player");

    @Test void fixtureItemFrameAccountsForTheImportedHandBasis() throws Exception {
        var scene=new AssimpModelLoader().loadModel(ASSET.resolve("block_player.fbx"),ModelImportMode.DYNAMIC);
        var graph=new cn.minerealms.assimploader.animation.PoseGraph(scene.getRootNode(),cn.minerealms.assimploader.animation.SkinBindPose.locals(scene));
        try(var reader=Files.newBufferedReader(ASSET.resolve("equipment.json"))) {
            var equipment=CharacterEquipmentDefinition.read(reader);
            var expected=new org.joml.Quaternionf().rotationX((float)Math.PI).rotateX(-(float)Math.PI/2).rotateY((float)Math.PI);
            for(var mount:java.util.List.of(equipment.rightHand(),equipment.leftHand())) {
                var hand=graph.global(mount.node()).getUnnormalizedRotation(new org.joml.Quaternionf()).normalize();
                var correction=new org.joml.Quaternionf(hand).invert().mul(expected).normalize();
                var desired=new Matrix4f().rotate(expected).transformDirection(new Vector3f(mount==equipment.leftHand()?-1f/16:1f/16,.125f,-.625f));
                String shoulder=mount.node().endsWith(".L")?"UpperArm.L":"UpperArm.R";
                var offset=new org.joml.Quaternionf(hand).invert().transform(desired.sub(graph.position(mount.node()).sub(graph.position(shoulder))));
                assertEquals(0,offset.distance(new Vector3f(mount.position().x(),mount.position().y(),mount.position().z())),2e-6,
                        "Fixture grip retains native item metres and the anatomical side offset");
                assertTrue(Math.abs(new org.joml.Quaternionf(hand).mul(mount.rotation().quaternion()).dot(expected))>1-1e-6,
                        "The item frame must cancel imported hand roll: "+mount.node()+" correction="+correction);
            }
        }
    }

    @Test void fixtureAndDefaultGripUseVanillaHandRotationOrder() throws Exception {
        var expected=new org.joml.Quaternionf().rotateX(-(float)Math.PI/2).rotateY((float)Math.PI);
        try(var reader=Files.newBufferedReader(ASSET.resolve("equipment.json"))) {
            var equipment=CharacterEquipmentDefinition.read(reader);
            for(var mount:java.util.List.of(equipment.rightHand(),equipment.leftHand())) {
                var actual=mount.rotation().quaternion();
                assertTrue(Math.abs(actual.dot(expected))>1-1e-6);
                assertEquals(-.125,mount.position().z(),1e-6);
            }
        }
        var profile=CharacterProfile.read(new StringReader("""
                {"version":1,"model":"test:avatar.fbx","humanoid":{"right_hand":"Hand.R"}}
                """));
        assertTrue(Math.abs(CharacterEquipmentDefinition.defaults(profile).rightHand().rotation().quaternion().dot(expected))>1-1e-6);
    }

    @Test void importedBoneUnitsDoNotChangeItemSizeOrMetreOffset() {
        var mount = new CharacterEquipmentDefinition.Mount("Hand", new CharacterEquipmentDefinition.Position(0, .1f, 0),
                CharacterProfile.Rotation.IDENTITY, .75f);
        var bone = new Matrix4f().translation(1, 2, 3).rotateZ((float)Math.PI / 2).scale(100);
        var retained = new Matrix4f(bone);
        var transform = mount.transform(bone);
        assertEquals(.9, transform.m30(), 1e-6);
        assertEquals(2, transform.m31(), 1e-6);
        assertEquals(3, transform.m32(), 1e-6);
        assertEquals(.75, transform.getScale(new Vector3f()).x, 1e-6);
        assertEquals(retained, bone);
        assertThrows(IllegalArgumentException.class, () -> mount.transform(new Matrix4f().scale(0)));
        assertThrows(IllegalArgumentException.class, () -> mount.transform(new Matrix4f().scale(-1, 1, 1)));
    }

    @Test void equipmentConsumesExactInterpolatedPoseAndCannotMutateRetainedFrames() throws Exception {
        var scene = new AssimpModelLoader().loadModel(ASSET.resolve("block_player.fbx"), ModelImportMode.DYNAMIC);
        try (var state = new AnimationState("equipment", scene); var reader = Files.newBufferedReader(ASSET.resolve("equipment.json"))) {
            var equipment = CharacterEquipmentDefinition.read(reader);
            state.initialize(); state.transitionTo("idle", 0);
            state.setPerformanceSettings(new InstancePerformanceSettings(1, true, true, true, true, 32, 4096, true));
            state.addModifier("hand-motion-probe", (ignored, locals, seconds) -> {
                if (seconds > 0) locals.put("Hand.R", new Matrix4f(scene.findNodeByName("Hand.R").getTransformation()).translate(.002f, 0, 0));
            });
            var previous = state.getPoseSnapshot();
            state.update(.05f);
            var authoritative = state.getPoseSnapshot();
            var visual = state.getRenderPose(.5f, RuntimePerformancePolicy.forMode(RuntimePerformancePolicy.Mode.QUALITY));
            assertNotSame(authoritative, visual);
            var renderedSocket = equipment.rightHand().transform(visual);
            var simulationSocket = equipment.rightHand().transform(authoritative);
            assertTrue(renderedSocket.getTranslation(new Vector3f()).distance(simulationSocket.getTranslation(new Vector3f())) > .01f);
            var expected = new Matrix4f(renderedSocket);
            renderedSocket.identity();
            assertEquals(expected, equipment.rightHand().transform(visual));
            assertNotNull(equipment.leftHand().transform(visual));
            state.update(.05f);
            assertEquals(expected, equipment.rightHand().transform(visual));
            assertNotEquals(previous.getFrameId(), visual.getFrameId());
        }
    }

    @Test void equipmentProfileResourceAndStrictOptionalSocketsAreValidated() throws Exception {
        try (var reader = Files.newBufferedReader(ASSET.resolve("profile.json"))) {
            assertEquals("rigworks:models/character/block_player/equipment.json", CharacterProfile.read(reader).equipmentDefinition());
        }
        var disabled = CharacterEquipmentDefinition.read(new StringReader("{\"version\":1,\"enabled\":false,\"right_hand\":null}"));
        assertFalse(disabled.enabled()); assertNull(disabled.leftHand()); assertNull(disabled.rightHand());
        assertThrows(IllegalArgumentException.class, () -> CharacterEquipmentDefinition.read(new StringReader("{\"version\":1,\"authority\":\"server\"}")));
        assertThrows(IllegalArgumentException.class, () -> CharacterEquipmentDefinition.read(new StringReader("{\"version\":1,\"right_hand\":{\"node\":\"Hand.R\",\"scale\":0}}")));
        assertThrows(IllegalArgumentException.class, () -> CharacterEquipmentDefinition.read(new StringReader("{\"version\":1,\"left_hand\":{\"node\":\"Hand.L\",\"rotation\":[0,0,0,0]}}")));
    }
}
