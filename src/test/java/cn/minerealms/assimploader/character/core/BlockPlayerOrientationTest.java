package cn.minerealms.assimploader.character.core;

import cn.minerealms.assimploader.animation.AnimationState;
import cn.minerealms.assimploader.animation.PoseGraph;
import cn.minerealms.assimploader.core.AssimpModelLoader;
import cn.minerealms.assimploader.core.ModelImportMode;
import cn.minerealms.assimploader.model.AssimpScene;
import org.joml.Vector3f;
import org.junit.jupiter.api.Test;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

class BlockPlayerOrientationTest {
    private static final Path ROOT = Path.of("src/main/resources/assets/assimploader/models/character/block_player");
    private static AssimpScene scene() throws Exception {
        return new AssimpModelLoader().loadModel(ROOT.resolve("block_player.fbx"), ModelImportMode.DYNAMIC);
    }
    private static CharacterProfile profile() throws Exception {
        try (var reader = Files.newBufferedReader(ROOT.resolve("profile.json"))) { return CharacterProfile.read(reader); }
    }
    private static Vector3f direction(float relativeYaw, float pitch) {
        float y = (float) Math.toRadians(relativeYaw), p = (float) Math.toRadians(pitch);
        return new Vector3f(-(float)Math.sin(y)*(float)Math.cos(p), -(float)Math.sin(p), (float)Math.cos(y)*(float)Math.cos(p));
    }

    @Test void actualFbxFaceLooksDownForPositiveMinecraftPitchAndUpForNegativePitch() throws Exception {
        var scene = scene(); var profile = profile();
        // The authored eyes/mouth are on the imported model's +Z face, independently of profile metadata.
        Vector3f faceAxis = new PoseGraph(scene.getRootNode(), Map.of()).global("Head").invert()
                .transformDirection(new Vector3f(0, 0, 1)).normalize();
        for (float yaw : new float[]{-45, 0, 45}) for (float pitch : new float[]{-60, 0, 60}) {
            try (var state = new AnimationState("face", scene); var controller = new PlayerAnimationController(profile, state)) {
                controller.update(PlayerVisualInput.builder(0, 0).orientation(0, yaw, pitch).build());
                state.update(.2f);
                Vector3f face = state.getPoseSnapshot().getNodeTransform("Head").transformDirection(new Vector3f(faceAxis)).normalize();
                Vector3f expected = direction(yaw, pitch);
                assertTrue(face.distance(expected) < .001, () -> "yaw=" + yaw + " pitch=" + pitch + " face=" + face + " expected=" + expected);
            }
        }
    }

    @Test void actualFbxElytraAndSwimmingBodyAxesFollowDiveAndClimbPitch() throws Exception {
        var scene = scene(); var profile = profile();
        for (boolean swimming : new boolean[]{false, true}) for (float yaw : new float[]{-45, 0, 45})
            for (float pitch : new float[]{-90, -60, 0, 60, 90}) {
                try (var state = new AnimationState("travel", scene); var controller = new PlayerAnimationController(profile, state)) {
                    var input = PlayerVisualInput.builder(0, 0).onGround(false).orientation(10, 10 + yaw, pitch)
                            .elytra(!swimming).swimming(swimming).build();
                    controller.update(input); state.update(.2f);
                    var pose = state.getPoseSnapshot();
                    Vector3f hips = pose.getNodeTransform("Hips").getTranslation(new Vector3f());
                    Vector3f axis = pose.getNodeTransform("Head").getTranslation(new Vector3f()).sub(hips).normalize();
                    Vector3f expected = direction(yaw, pitch);
                    assertTrue(axis.distance(expected) < .001, () -> "swim=" + swimming + " yaw=" + yaw + " pitch=" + pitch + " body=" + axis + " expected=" + expected);
                    assertTrue(hips.distance(new Vector3f(0, .85f, 0)) < .001, "Visual travel rotation moved the hip pivot");
                    Vector3f face = pose.getNodeTransform("Head").transformDirection(new Vector3f(0, 0, 1)).normalize();
                    Vector3f gaze = direction(yaw, Math.max(-profile.headLook().maxPitch(), Math.min(profile.headLook().maxPitch(), pitch)));
                    assertTrue(face.distance(gaze) < .001, "Travel gaze must use the same model-space pitch convention");
                }
            }
    }

    @Test void leavingTravelAndResetReleaseTheOrientationModifier() throws Exception {
        var scene = scene(); var profile = profile();
        try (var state = new AnimationState("restore", scene)) {
            try (var controller = new PlayerAnimationController(profile, state)) {
                controller.update(PlayerVisualInput.builder(0, 0).elytra(true).orientation(0, 0, 60).build());state.update(.2f);
                controller.update(PlayerVisualInput.builder(1, .2).build());state.update(.2f);
                var pose = state.getPoseSnapshot();
                // The head origin moves with gaze and breathing; the trunk is the travel-orientation invariant.
                Vector3f axis = pose.getNodeTransform("Chest").getTranslation(new Vector3f())
                        .sub(pose.getNodeTransform("Hips").getTranslation(new Vector3f())).normalize();
                assertTrue(axis.dot(new Vector3f(0, 1, 0)) > .995f, "Grounded trunk retained dive alignment: " + axis);
                controller.reset();
            }
            assertNull(state.getModifier("character:travel-aim"));
            assertNull(state.getModifier("character:head-look"));
        }
    }
}
