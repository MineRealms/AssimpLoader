package cn.minerealms.assimploader.animation;

import cn.minerealms.assimploader.core.AssimpModelLoader;
import cn.minerealms.assimploader.core.ModelImportMode;
import cn.minerealms.assimploader.model.AssimpScene;
import cn.minerealms.assimploader.rendering.CpuSkinning;
import com.google.gson.JsonArray;
import com.google.gson.JsonParser;
import org.joml.Matrix4f;
import org.joml.Vector3f;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.Arrays;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

class BlenderAnimationProbeTest {
    private static AssimpScene scene() throws IOException {
        String path = "/assets/assimploader/models/validation/animation_probe.fbx";
        try (var stream = BlenderAnimationProbeTest.class.getResourceAsStream(path)) {
            assertNotNull(stream, "Rebuild the original FBX with model-tests/create_animation_probe.py");
            AssimpScene scene = new AssimpModelLoader().loadModelFromMemory(path, stream.readAllBytes(), ModelImportMode.DYNAMIC);
            assertNotNull(scene);
            return scene;
        }
    }

    @Test
    void exportedFbxRetainsClipsBonesAndAnUnweightedSocketNode() throws IOException {
        var scene = scene();
        System.out.println("Blender probe clips: " + scene.getAnimations().stream().map(a -> a.getName()).toList());
        assertTrue(scene.hasSkeleton());
        assertEquals(3, scene.getAnimations().size());
        assertNotNull(scene.findNodeByName("ToolSocket"));
        assertTrue(scene.getMeshes().stream().anyMatch(m -> m.getBoneNames().containsAll(Arrays.asList("Shoulder", "Elbow"))),
                "The bridge must retain two influences for genuine blend-weight coverage");
        for (var animation : scene.getAnimations()) {
            assertTrue(animation.getDuration() > 0);
            assertTrue(animation.getTicksPerSecond() > 0);
            assertEquals(2, animation.getDuration() / animation.getTicksPerSecond(), 1e-4);
        }
    }

    @Test
    void bindSkinMatchesNodeGeometryAcrossAllExportedMeshes() throws IOException {
        var scene = scene();
        PoseSnapshot bind = PoseSnapshot.capture(1, scene, Map.of());
        int weightedMeshes = 0;
        for (var instance : bind.copyMeshInstances()) {
            var mesh = instance.mesh();
            if (!mesh.hasBones()) continue;
            weightedMeshes++;
            var positions = CpuSkinning.geometry(bind, mesh).positions();
            for (int index = 0; index < mesh.getVertexCount(); index++) {
                Vector3f expected = instance.transform().transformPosition(point(mesh.getVertices(), index * 3));
                Vector3f actual = point(positions, index * 3);
                assertTrue(expected.distance(actual) < 1e-4, () -> mesh.getName() + ": " + expected + " != " + actual);
            }
        }
        assertTrue(weightedMeshes >= 7);
    }

    @Test
    void actualFbxInstancesCrossfadeInterruptAndPublishBoundsContainingDeformedVertices() throws IOException {
        var scene = scene();
        AnimationState first = new AnimationState("probe", scene), second = new AnimationState("probe", scene);
        first.initialize();
        second.initialize();
        first.playAnimation(clip(first, "Idle"));
        second.playAnimation(clip(second, "Reach"));
        first.update(.5f);
        second.update(.75f);
        PoseSnapshot original = first.getPoseSnapshot();
        Matrix4f originalSocket = original.getNodeTransform("ToolSocket");
        assertTrue(originalSocket.getTranslation(new Vector3f()).distance(
                second.getPoseSnapshot().getNodeTransform("ToolSocket").getTranslation(new Vector3f())) > .2f);
        first.transitionTo(clip(first, "Reach"), 2);
        assertTrue(originalSocket.equals(first.getPoseSnapshot().getNodeTransform("ToolSocket"), 1e-4f));
        first.update(1);
        var midpoint = first.getPoseSnapshot();
        first.transitionTo(clip(first, "Fold"), 2);
        assertTrue(midpoint.getNodeTransform("ToolSocket").equals(first.getPoseSnapshot().getNodeTransform("ToolSocket"), 1e-4f));
        first.update(.5f);
        assertBounds(first.getPoseSnapshot());
        assertBounds(second.getPoseSnapshot());
        assertTrue(originalSocket.equals(original.getNodeTransform("ToolSocket"), 1e-6f));
        first.update(2);
        assertFalse(first.isTransitioning());
        first.stop();
        assertBounds(first.getPoseSnapshot());
    }

    private static void assertBounds(PoseSnapshot snapshot) {
        var bounds = snapshot.getBounds();
        for (var instance : snapshot.copyMeshInstances()) {
            var mesh = instance.mesh();
            float[] positions = mesh.hasBones() ? CpuSkinning.geometry(snapshot, mesh).positions() : mesh.getVertices();
            for (int index = 0; index < positions.length; index += 3) {
                Vector3f point = point(positions, index);
                if (!mesh.hasBones()) instance.transform().transformPosition(point);
                assertTrue(point.x >= bounds.minX - 1e-5 && point.x <= bounds.maxX + 1e-5
                        && point.y >= bounds.minY - 1e-5 && point.y <= bounds.maxY + 1e-5
                        && point.z >= bounds.minZ - 1e-5 && point.z <= bounds.maxZ + 1e-5,
                        () -> mesh.getName() + " outside pose bounds: " + point + " / " + bounds);
            }
        }
    }

    @Test
    void sampledFbxSocketAndGeometryMatchBlendersEvaluatedReference() throws IOException {
        var scene = scene();
        try (var stream = getClass().getResourceAsStream("/fixtures/animation_probe_reference.json")) {
            assertNotNull(stream);
            var reference = JsonParser.parseReader(new InputStreamReader(stream, StandardCharsets.UTF_8)).getAsJsonObject();
            for (var element : reference.getAsJsonArray("samples")) {
                var sample = element.getAsJsonObject();
                AnimationState state = new AnimationState("probe", scene);
                state.initialize();
                state.playAnimation(clip(state, sample.get("clip").getAsString()));
                state.update(sample.get("seconds").getAsFloat());
                PoseSnapshot pose = state.getPoseSnapshot();
                Vector3f socket = pose.getNodeTransform("ToolSocket").getTranslation(new Vector3f());
                assertVector(vector(sample.getAsJsonArray("socket")), socket);
                Vector3f low = new Vector3f(Float.POSITIVE_INFINITY), high = new Vector3f(Float.NEGATIVE_INFINITY);
                for (var instance : pose.copyMeshInstances()) {
                    var mesh = instance.mesh();
                    float[] positions = mesh.hasBones() ? CpuSkinning.geometry(pose, mesh).positions() : mesh.getVertices();
                    for (int index = 0; index < positions.length; index += 3) {
                        Vector3f point = point(positions, index);
                        if (!mesh.hasBones()) instance.transform().transformPosition(point);
                        low.min(point);
                        high.max(point);
                    }
                }
                assertVector(vector(sample.getAsJsonArray("geometry_min")), low);
                assertVector(vector(sample.getAsJsonArray("geometry_max")), high);
                state.close();
            }
        }
    }

    private static Vector3f vector(JsonArray values) {
        return new Vector3f(values.get(0).getAsFloat(), values.get(1).getAsFloat(), values.get(2).getAsFloat());
    }

    private static void assertVector(Vector3f expected, Vector3f actual) {
        assertTrue(expected.distance(actual) < 1e-4, () -> expected + " != " + actual);
    }

    private static String clip(AnimationState state, String suffix) {
        return Arrays.stream(state.getAvailableAnimations()).filter(name -> name.endsWith(suffix)).findFirst().orElseThrow();
    }

    private static Vector3f point(float[] positions, int index) {
        return new Vector3f(positions[index], positions[index + 1], positions[index + 2]);
    }
}
