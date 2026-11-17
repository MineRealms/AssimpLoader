package cn.minerealms.rigworks.character.client;

import cn.minerealms.assimploader.client.transform.EditableTransform;
import cn.minerealms.rigworks.character.core.CharacterEquipmentDefinition;
import net.minecraft.world.entity.HumanoidArm;
import net.minecraft.world.phys.Vec3;
import org.junit.jupiter.api.Assumptions;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.io.StringReader;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class CharacterEquipmentEditorSessionTest {
    @TempDir Path config;
    private static final String PROFILE = "test:characters/avatar/profile.json";
    private static final EditableTransform RIGHT = new EditableTransform(.2f, -.3f, .4f, 25, -60, 135, .8f);
    private static final EditableTransform LEFT = new EditableTransform(-.4f, .1f, -.2f, -35, 80, 15, .6f);

    @Test void previewResetSaveReloadAndCancelPreserveBothHandsAndGripMetadata() throws Exception {
        Target target = new Target(config);
        var session = new CharacterEquipmentEditorSession(target);
        assertEquals(PROFILE, session.profileId());
        assertTrue(session.available(HumanoidArm.RIGHT)); assertTrue(session.available(HumanoidArm.LEFT));
        assertEquals("Hand.R", session.node(HumanoidArm.RIGHT));
        assertEquals(config.resolve("rigworks/equipment/test/characters/avatar/profile.json"), session.outputPath());
        session.preview(HumanoidArm.RIGHT, RIGHT);
        assertSame(target.resource.leftHand(), target.value.leftHand());
        assertFalse(target.value.enabled());
        assertEquals(target.resource.grips(), target.value.grips());
        assertFalse(Files.exists(session.outputPath()), "Preview must not perform filesystem writes");
        session.preview(HumanoidArm.LEFT, LEFT);
        assertTransform(RIGHT, session.get(HumanoidArm.RIGHT));
        Path saved = session.save();
        String json = Files.readString(saved);
        var decoded = CharacterEquipmentDefinition.read(new StringReader(json));
        assertEquals(target.value, decoded);
        assertTrue(decoded.toJson().getAsJsonObject("right_hand").has("rotation"));
        assertTrue(decoded.toJson().getAsJsonObject("left_hand").has("position"));
        session.reset(HumanoidArm.RIGHT);
        assertEquals(target.resource.rightHand(), target.value.rightHand());
        assertTransform(LEFT, session.get(HumanoidArm.LEFT));
        session.preview(HumanoidArm.LEFT, RIGHT);
        session.cancel(); session.cancel();
        assertFalse(session.valid());
        assertEquals(decoded, target.value, "Cancel restores the most recently saved pair of hands");
        assertEquals(json, Files.readString(saved), "Cancel never deletes or rewrites saved overrides");
        Target rebound = new Target(config);
        var reopened = new CharacterEquipmentEditorSession(rebound);
        assertTransform(RIGHT, reopened.get(HumanoidArm.RIGHT));
        assertTransform(LEFT, reopened.get(HumanoidArm.LEFT));
        assertEquals(target.resource.grips(), rebound.value.grips());
    }

    @Test void invalidBindingsCannotEditSaveOrRestoreAReplacementCharacter() {
        Target target = new Target(config);
        var session = new CharacterEquipmentEditorSession(target);
        session.preview(HumanoidArm.RIGHT, RIGHT);
        var preview = target.value;
        target.active = false;
        assertFalse(session.valid()); assertFalse(session.available(HumanoidArm.LEFT));
        assertThrows(IllegalStateException.class, () -> session.preview(HumanoidArm.LEFT, LEFT));
        assertThrows(IllegalStateException.class, session::save);
        session.cancel();
        assertSame(preview, target.value, "An expired editor must not mutate an inactive/replaced binding");
    }

    @Test void missingSocketsAndDynamicWorldFocusRemainExplicit() {
        Target target = new Target(config);
        target.value = new CharacterEquipmentDefinition(true, null, target.value.leftHand(), List.of());
        var session = new CharacterEquipmentEditorSession(target);
        assertFalse(session.available(HumanoidArm.RIGHT));
        assertThrows(IllegalStateException.class, () -> session.get(HumanoidArm.RIGHT));
        session.reset(HumanoidArm.RIGHT);
        assertTrue(session.available(HumanoidArm.RIGHT));
        assertEquals(new Vec3(12, 64, -3), session.focus(HumanoidArm.RIGHT, 2));
        assertEquals(1, target.partialTick);
        assertEquals(new Vec3(11, 64, -3), session.focus(HumanoidArm.LEFT, -.5f));
        assertEquals(0, target.partialTick);
        assertThrows(IllegalArgumentException.class, () -> session.focus(HumanoidArm.LEFT, Float.NaN));
    }

    @Test void invalidJsonAndInvalidNodesKeepResourceEquipmentAndReportThePath() throws Exception {
        Target target = new Target(config);
        var store = target.overrides;
        store.save(target.resource);
        Files.writeString(store.path(), "{\"version\":1,\"unknown\":true}");
        List<Path> warnings = new ArrayList<>();
        assertSame(target.resource, store.loadOrDefault(target.resource, ignored -> fail("Invalid JSON reached scene validation"),
                (path, error) -> warnings.add(path)));
        assertEquals(List.of(store.path()), warnings);
        store.save(target.resource);
        assertSame(target.resource, store.loadOrDefault(target.resource, ignored -> {throw new IllegalArgumentException("Unknown node");},
                (path, error) -> warnings.add(path)));
        assertEquals(2, warnings.size());
    }

    @Test void rejectsUnsafeProfileComponentsAndValidatesBeforeReplacingSavedData() throws Exception {
        for (String id : List.of("test:../escape", "test:models/../../escape", "test:/absolute", "test:models//hand",
                "test:trailing.", "con:profile", "test:com1/item"))
            assertThrows(IllegalArgumentException.class, () -> new CharacterEquipmentOverrides(config, id), id);
        Target target = new Target(config);
        target.overrides.save(target.resource);
        String previous = Files.readString(target.overrides.path());
        var invalid = new CharacterEquipmentDefinition(true, target.resource.rightHand(), target.resource.leftHand(),
                List.of(target.resource.grips().get(0), target.resource.grips().get(0)));
        assertThrows(IllegalArgumentException.class, () -> target.overrides.save(invalid));
        assertEquals(previous, Files.readString(target.overrides.path()));
        try (var files = Files.list(target.overrides.path().getParent())) {
            assertEquals(List.of(target.overrides.path()), files.toList(), "Atomic saves must clean temporary files");
        }
    }

    @Test void refusesSymlinkRedirectsDuringReadAndAtomicSave(@TempDir Path outside) throws Exception {
        try { Files.createSymbolicLink(config.resolve("rigworks"), outside); }
        catch (IOException | UnsupportedOperationException | SecurityException unsupported) {
            Assumptions.assumeTrue(false, "Symbolic links unavailable on this test host");
        }
        Target target = new Target(config);
        assertThrows(IOException.class, () -> target.overrides.save(target.resource));
        assertFalse(Files.exists(outside.resolve("equipment")));
        Path redirected = outside.resolve("equipment/test/characters/avatar/profile.json");
        Files.createDirectories(redirected.getParent());
        Files.writeString(redirected, target.resource.toJson().toString());
        List<Exception> warnings = new ArrayList<>();
        assertSame(target.resource, target.overrides.loadOrDefault(target.resource, ignored -> {}, (path, error) -> warnings.add(error)));
        assertEquals(1, warnings.size());
    }

    private static void assertTransform(EditableTransform expected, EditableTransform actual) {
        assertEquals(expected.x(), actual.x()); assertEquals(expected.y(), actual.y()); assertEquals(expected.z(), actual.z());
        assertEquals(expected.scale(), actual.scale());
        assertTrue(Math.abs(expected.quaternion().dot(actual.quaternion())) > 1 - 1e-6);
    }

    private static final class Target implements CharacterEquipmentEditorSession.Target {
        final CharacterEquipmentDefinition resource = CharacterEquipmentDefinition.read(new StringReader("""
                {"version":1,"enabled":false,
                 "right_hand":{"node":"Hand.R","position":[0.01,0.02,0.03],"rotation":[0,0,0,1],"scale":0.9},
                 "left_hand":{"node":"Hand.L","position":[-0.01,0.04,-0.02],"rotation":[0,1,0,0],"scale":0.7},
                 "grips":[{"id":"support","items":["minecraft:bow"],"tags":["forge:tools"],
                    "states":["held","using"],"primary_arm":"left","socket":[0.1,0.2,0.3],
                    "pole":[0.3,-0.5,0.1],"hand_offset":[0.02,0,0],"rotation":[0,0,1,0],
                    "min_bend":3,"max_bend":170,"weight":0.6,"orientation_weight":1,"free_support":true}]}
                """));
        final CharacterEquipmentOverrides overrides;
        CharacterEquipmentDefinition value;
        boolean active = true;
        float partialTick;
        Target(Path config) {
            overrides = new CharacterEquipmentOverrides(config, PROFILE);
            value = overrides.loadOrDefault(resource, ignored -> {}, (path, error) -> {});
        }
        @Override public boolean valid() { return active; }
        @Override public String profileId() { return PROFILE; }
        @Override public CharacterEquipmentDefinition resource() { return resource; }
        @Override public CharacterEquipmentDefinition equipment() { return value; }
        @Override public void equipment(CharacterEquipmentDefinition definition) { value = definition; }
        @Override public CharacterEquipmentOverrides overrides() { return overrides; }
        @Override public Vec3 focus(HumanoidArm arm, float tick) {
            partialTick = tick;
            return new Vec3(arm == HumanoidArm.RIGHT ? 12 : 11, 64, -3);
        }
    }
}
