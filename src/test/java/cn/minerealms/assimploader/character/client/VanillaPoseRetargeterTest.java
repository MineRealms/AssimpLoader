package cn.minerealms.assimploader.character.client;

import cn.minerealms.assimploader.animation.PoseGraph;
import cn.minerealms.assimploader.animation.SkinBindPose;
import cn.minerealms.assimploader.character.core.CharacterProfile;
import cn.minerealms.assimploader.core.AssimpModelLoader;
import cn.minerealms.assimploader.core.ModelImportMode;
import cn.minerealms.assimploader.model.AssimpScene;
import org.joml.Matrix4f;
import org.joml.Quaternionf;
import org.joml.Vector3f;
import org.junit.jupiter.api.Test;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.LinkedHashMap;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

class VanillaPoseRetargeterTest {
    private static final Path ROOT = Path.of("src/main/resources/assets/assimploader/models/character/block_player");
    private static final VanillaPoseRetargeter.Part ZERO = part(0, 0, 0);
    private static VanillaPoseRetargeter.Part part(float x, float y, float z) {
        return new VanillaPoseRetargeter.Part(x, y, z, 0, 0, 0);
    }
    private static AssimpScene scene() throws Exception {
        return new AssimpModelLoader().loadModel(ROOT.resolve("block_player.fbx"), ModelImportMode.DYNAMIC);
    }
    private static CharacterProfile profile() throws Exception {
        try (var reader = Files.newBufferedReader(ROOT.resolve("profile.json"))) { return CharacterProfile.read(reader); }
    }
    private static Map<String, VanillaPoseRetargeter.Part> parts() {
        var parts = new LinkedHashMap<String, VanillaPoseRetargeter.Part>();
        for (String role : new String[]{"hips", "head", "left_upper_arm", "right_upper_arm", "left_thigh", "right_thigh"}) parts.put(role, ZERO);
        return parts;
    }
    private static Vector3f axis(PoseGraph graph, String from, String to) {
        return graph.position(to).sub(graph.position(from)).normalize();
    }

    @Test void realFbxUsesVanillaDownwardPitchWithoutImportedBoneRollInversion() throws Exception {
        var scene = scene(); var profile = profile();
        var bind = new PoseGraph(scene.getRootNode(), SkinBindPose.locals(scene));
        var localFace = bind.global("Head").invert().transformDirection(new Vector3f(0, 0, 1)).normalize();
        for (float pitch : new float[]{-1.2f, 0, 1.2f}) for (float yaw : new float[]{-.8f, 0, .8f}) {
            var retarget = new VanillaPoseRetargeter(profile, scene);
            var values = parts(); values.put("head", part(pitch, yaw, 0));
            var locals = new LinkedHashMap<String, Matrix4f>();
            retarget.update(new Matrix4f(), values); retarget.apply(scene, locals, 0);
            var face = new PoseGraph(scene.getRootNode(), locals).global("Head").transformDirection(new Vector3f(localFace)).normalize();
            var expected = new Quaternionf().rotationYXZ(-yaw, pitch, 0).transform(new Vector3f(0, 0, 1));
            assertEquals(0, face.distance(expected), 1e-5, "Visible face should follow Minecraft pitch/yaw");
        }
    }

    @Test void nativeIndependentLimbRotationsDoNotDoubleTheTorsoTwistOrShortenLimbs() throws Exception {
        var scene = scene(); var profile = profile();
        var bind = new PoseGraph(scene.getRootNode(), SkinBindPose.locals(scene));
        var values = parts(); values.put("hips", part(.5f, .7f, 0));
        values.put("right_upper_arm", part(-(float)Math.PI / 2, .35f, -.25f));
        values.put("left_thigh", part(.25f, 0, 0));
        var locals = new LinkedHashMap<String, Matrix4f>();
        var retarget = new VanillaPoseRetargeter(profile, scene);
        retarget.update(new Matrix4f(), values); retarget.apply(scene, locals, 0);
        var graph = new PoseGraph(scene.getRootNode(), locals);
        var arm = axis(graph, "UpperArm.R", "Forearm.R");
        var expected = new Quaternionf().rotationZYX(.25f, -.35f, -(float)Math.PI / 2)
                .transform(axis(bind, "UpperArm.R", "Forearm.R"));
        assertEquals(0, arm.distance(expected), 1e-5, "Arm angles are independent vanilla model parts, not relative to body");
        assertTrue(arm.z > .8f, "Native attack arm should reach in front of the visible +Z face: " + arm + " bind=" + axis(bind, "UpperArm.R", "Forearm.R"));
        for (String[] chain : new String[][]{{"UpperArm.R", "Forearm.R"}, {"Forearm.R", "Hand.R"},
                {"Thigh.L", "Shin.L"}, {"Shin.L", "Foot.L"}}) {
            assertEquals(bind.position(chain[0]).distance(bind.position(chain[1])),
                    graph.position(chain[0]).distance(graph.position(chain[1])), 1e-5, "Retargeting must preserve rig bone length");
        }
        var legExpected = new Quaternionf().rotationX(.25f).transform(axis(bind, "Thigh.L", "Shin.L"));
        assertEquals(0, axis(graph, "Thigh.L", "Shin.L").distance(legExpected), 1e-5);
    }

    @Test void rendererRootPitchAffectsBodyEquipmentAndAllLimbsInOnePose() throws Exception {
        var scene = scene(); var profile = profile();
        var bind = new PoseGraph(scene.getRootNode(), SkinBindPose.locals(scene));
        for (float pitch : new float[]{-90, -60, 0, 60, 90}) {
            // Vanilla's -90-pitch renderer turn becomes +90+pitch in imported model axes.
            var root = new Matrix4f().rotateX((float)Math.toRadians(90 + pitch));
            var locals = new LinkedHashMap<String, Matrix4f>();
            var retarget = new VanillaPoseRetargeter(profile, scene);
            retarget.update(root, parts()); retarget.apply(scene, locals, 0);
            var graph = new PoseGraph(scene.getRootNode(), locals);
            assertEquals(0, axis(graph, "Hips", "Head").distance(root.transformDirection(new Vector3f(0, 1, 0))), 1e-5);
            var hand = root.transformPosition(bind.position("Hand.R"));
            assertEquals(0, graph.position("Hand.R").distance(hand), 1e-5, "Held-item mount shares the same root pitch");
        }
    }

    @Test void disablingDoesNotOverwriteTheClipPose() throws Exception {
        var scene = scene(); var retarget = new VanillaPoseRetargeter(profile(), scene);
        var locals = new LinkedHashMap<String, Matrix4f>();
        locals.put("Hips", new Matrix4f().translate(1, 2, 3));
        retarget.update(new Matrix4f(), parts()); retarget.disable(); retarget.apply(scene, locals, 0);
        assertEquals(Map.of("Hips", new Matrix4f().translate(1, 2, 3)), locals);
    }

    @Test void originalCrouchAndAttackPivotOffsetsAreScaledFromTheRigsOwnProportions() throws Exception {
        var scene = scene(); var bind = new PoseGraph(scene.getRootNode(), SkinBindPose.locals(scene));
        var values = parts();
        values.put("hips", new VanillaPoseRetargeter.Part(.5f, .3f, 0, 0, 3.2f, 0));
        values.put("head", new VanillaPoseRetargeter.Part(0, 0, 0, 0, 4.2f, 0));
        values.put("right_upper_arm", new VanillaPoseRetargeter.Part(.4f, 0, 0, .4f, 3.2f, 2));
        values.put("left_thigh", new VanillaPoseRetargeter.Part(0, 0, 0, 0, .2f, 4));
        var retarget = new VanillaPoseRetargeter(profile(), scene);
        var locals = new LinkedHashMap<String, Matrix4f>();
        retarget.update(new Matrix4f(), values); retarget.apply(scene, locals, 0);
        var graph = new PoseGraph(scene.getRootNode(), locals);
        float vertical = bind.position("Head").distance(bind.position("Hips")) / 12;
        float horizontal = bind.position("UpperArm.L").distance(bind.position("UpperArm.R")) / 10;
        assertEquals(0, graph.position("Head").distance(bind.position("Head").add(0, -4.2f * vertical, 0)), 1e-5);
        assertEquals(0, graph.position("UpperArm.R").distance(bind.position("UpperArm.R").add(.4f * horizontal, -3.2f * vertical, -2 * vertical)), 1e-5);
        assertEquals(0, graph.position("Thigh.L").distance(bind.position("Thigh.L").add(0, -.2f * vertical, -4 * vertical)), 1e-5);
        assertEquals(bind.position("UpperArm.R").distance(bind.position("Forearm.R")),
                graph.position("UpperArm.R").distance(graph.position("Forearm.R")), 1e-5);
        assertTrue(graph.position("Hips").z < -.1, "Crouch torso pivots around the native body origin near the neck");
    }
}
