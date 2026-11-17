package cn.minerealms.rigworks.character.core;

import com.google.gson.JsonParser;
import org.junit.jupiter.api.Test;

import java.io.StringReader;
import java.util.HashMap;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

class CharacterProfileTest {
    @Test void readsImmutableNamedRigMappingsAndOptionalPhysicsResource() {
        var json = JsonParser.parseString("""
                {"version":1,"model":"example:models/player/avatar.glb","scale":0.75,
                 "humanoid":{"head":{"node":"Head","referenceRotation":[0,0,0,2]},"hips":"Hips"},
                 "animations":{"idle":"Idle","walk":"walking"},"physicsDefinition":"example:physics/avatar.json",
                 "headLook":{"enabled":true,"forward":[0,0,-2],"maxYaw":45,"maxPitch":30}}
                """).getAsJsonObject();
        var profile = CharacterProfile.parse(json);
        json.getAsJsonObject("animations").addProperty("idle", "mutated");
        json.getAsJsonObject("humanoid").addProperty("head", "mutated");
        assertEquals("Idle", profile.animation("idle"));
        assertEquals("run", profile.animation("run"));
        assertEquals("Head", profile.bone("head").node());
        assertEquals(1, profile.bone("head").referenceRotation().w());
        assertEquals(-1, profile.headLook().forward().z());
        assertEquals(.75, profile.scale());
        assertEquals("example:physics/avatar.json", profile.physicsDefinition());
        assertThrows(UnsupportedOperationException.class, () -> profile.humanoid().clear());
        var quaternion = profile.bone("head").referenceRotation().quaternion();
        quaternion.w = 0;
        assertEquals(1, profile.bone("head").referenceRotation().w());
    }

    @Test void directConstructionCopiesMapsAndReaderDefaultsAreUseful() {
        Map<String, String> animations = new HashMap<>(Map.of("idle", "Idle"));
        var profile = new CharacterProfile("example:models/models/avatar.fbx", 1, Map.of(), animations,
                null, .15, .02, CharacterProfile.HeadLook.defaults());
        animations.put("idle", "changed");
        assertEquals("Idle", profile.animation("idle"));
        var minimal = read("{\"version\":1,\"model\":\"example:avatar.glb\"}");
        assertEquals(1, minimal.scale());
        assertNull(minimal.physicsDefinition());
        assertTrue(minimal.humanoid().isEmpty());
        assertEquals(.02, minimal.movementThreshold());
    }

    @Test void rejectsUnknownSchemaInvalidTypesAndNonFiniteOrDegenerateTransforms() {
        for (String extra : new String[]{"\"version\":2", "\"scale\":0", "\"scale\":101", "\"scale\":\"1\"",
                "\"movementThreshold\":-1", "\"blendSeconds\":-1", "\"headLook\":{\"enabled\":\"yes\"}",
                "\"headLook\":{\"forward\":[0,0,0]}", "\"headLook\":{\"maxPitch\":91}", "\"headLook\":{\"maxYaw\":181}",
                "\"humanoid\":{\"head\":{\"node\":\"Head\",\"referenceRotation\":[0,0,0,0]}}",
                "\"humanoid\":{\"head\":{\"node\":\"Head\",\"unknown\":1}}", "\"animations\":{\"typo\":\"Idle\"}",
                "\"animations\":{\"idle\":1}", "\"unknown\":1"}) {
            assertThrows(RuntimeException.class, () -> read("{\"version\":1,\"model\":\"example:avatar.glb\"," + extra + "}"), extra);
        }
        for (String model : new String[]{"../avatar.glb", "example:../avatar.glb", "example:/avatar.glb", "C:/avatar.fbx", "example:Models/avatar.glb"})
            assertThrows(IllegalArgumentException.class, () -> read("{\"version\":1,\"model\":\"" + model + "\"}"));
        assertThrows(IllegalArgumentException.class, () -> new CharacterProfile.Direction(Float.NaN, 0, 1));
        assertThrows(IllegalArgumentException.class, () -> new CharacterProfile.Rotation(0, Float.POSITIVE_INFINITY, 0, 1));
        assertThrows(IllegalArgumentException.class, () -> read("[]"));
    }

    @Test void timingDefaultsAndStrictOverridesAndSwingObservationsAreValidated() {
        var minimal = read("{\"version\":1,\"model\":\"example:avatar.glb\"}");
        assertEquals(CharacterProfile.Timing.DEFAULT,minimal.timing());
        var custom = read("""
                {"version":1,"model":"example:avatar.glb","timing":{
                 "swingSync":false,"movementSync":false,"walkStride":2,"runStride":4,
                 "crouchStride":1,"minRate":0.5,"maxRate":8}}
                """);
        assertFalse(custom.timing().swingSync());assertFalse(custom.timing().movementSync());
        assertEquals(4,custom.timing().runStride());assertEquals(8,custom.timing().maxRate());
        for (String bad : new String[]{"\"swingSync\":1","\"movementSync\":\"true\"","\"walkStride\":0",
                "\"runStride\":33","\"crouchStride\":-1","\"minRate\":0","\"maxRate\":65",
                "\"minRate\":3,\"maxRate\":1","\"typo\":1"})
            assertThrows(RuntimeException.class,()->read("{\"version\":1,\"model\":\"example:avatar.glb\",\"timing\":{"+bad+"}}"),bad);
        assertThrows(IllegalArgumentException.class,()->new PlayerVisualInput.SwingAction(-1,true,0));
        assertThrows(IllegalArgumentException.class,()->new PlayerVisualInput.SwingAction(0,true,Float.NaN));
        assertThrows(IllegalArgumentException.class,()->new PlayerVisualInput.SwingAction(0,true,1.1f));
        assertNull(PlayerVisualInput.builder(0,0).build().swingAction());
    }

    private static CharacterProfile read(String value) { return CharacterProfile.read(new StringReader(value)); }
}
