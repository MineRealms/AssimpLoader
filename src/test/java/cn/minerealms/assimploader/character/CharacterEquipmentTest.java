package cn.minerealms.assimploader.character;

import cn.minerealms.assimploader.animation.AnimationState;
import cn.minerealms.assimploader.animation.InstancePerformanceSettings;
import cn.minerealms.assimploader.character.core.CharacterEquipmentDefinition;
import cn.minerealms.assimploader.character.core.CharacterProfile;
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
    private static final Path ASSET = Path.of("src/main/resources/assets/assimploader/models/character/block_player");

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
            assertEquals("assimploader:models/character/block_player/equipment.json", CharacterProfile.read(reader).equipmentDefinition());
        }
        var disabled = CharacterEquipmentDefinition.read(new StringReader("{\"version\":1,\"enabled\":false,\"right_hand\":null}"));
        assertFalse(disabled.enabled()); assertNull(disabled.leftHand()); assertNull(disabled.rightHand());
        assertThrows(IllegalArgumentException.class, () -> CharacterEquipmentDefinition.read(new StringReader("{\"version\":1,\"authority\":\"server\"}")));
        assertThrows(IllegalArgumentException.class, () -> CharacterEquipmentDefinition.read(new StringReader("{\"version\":1,\"right_hand\":{\"node\":\"Hand.R\",\"scale\":0}}")));
        assertThrows(IllegalArgumentException.class, () -> CharacterEquipmentDefinition.read(new StringReader("{\"version\":1,\"left_hand\":{\"node\":\"Hand.L\",\"rotation\":[0,0,0,0]}}")));
    }
}
