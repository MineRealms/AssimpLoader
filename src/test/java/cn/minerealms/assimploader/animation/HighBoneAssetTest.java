package cn.minerealms.assimploader.animation;

import cn.minerealms.assimploader.core.AssimpModelLoader;
import cn.minerealms.assimploader.core.ModelImportMode;
import cn.minerealms.assimploader.model.AssimpScene;
import cn.minerealms.assimploader.rendering.CpuSkinning;
import com.google.gson.JsonArray;
import com.google.gson.JsonParser;
import org.joml.Vector3f;
import org.junit.jupiter.api.Test;

import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.Arrays;
import java.util.HashSet;

import static org.junit.jupiter.api.Assertions.*;

class HighBoneAssetTest {
    private static AssimpScene scene() throws Exception {
        String resource = "/assets/assimploader/models/validation/highbone_probe.glb";
        try (var input = HighBoneAssetTest.class.getResourceAsStream(resource)) {
            assertNotNull(input, "Rebuild with model-tests/create_highbone_probe.py");
            return new AssimpModelLoader().loadModelFromMemory(resource, input.readAllBytes(), ModelImportMode.DYNAMIC);
        }
    }

    @Test void realExportWeightsEveryOneOfThe256BonesAndRetainsMorphAnimation() throws Exception {
        var scene = scene();
        assertTrue(scene.hasSkeleton());
        assertEquals(1, scene.getMeshes().size());
        var mesh = scene.getMeshes().get(0);
        assertEquals(256, mesh.getBoneNames().size());
        assertEquals(1, mesh.getMorphTargets().size());
        assertFalse(scene.getTextures().isEmpty());
        var data = mesh.getSkinnedData();
        var used = new HashSet<Integer>();
        for (int i = 0; i < data.getBoneIndices().length; i++) {
            if (data.getBoneWeights()[i] > 0) used.add(data.getBoneIndices()[i]);
        }
        assertEquals(256, used.size());
        assertTrue(used.containsAll(Arrays.asList(0, 63, 64, 127, 128, 191, 192, 255)));
        assertEquals(256, PoseSnapshot.capture(0, scene, java.util.Map.of()).getBoneCount(mesh));
        var pulse = scene.getAnimations().stream().filter(a -> a.getName().endsWith("MorphPulse")).findFirst().orElseThrow();
        assertEquals(1, pulse.getMorphChannels().size());
        assertArrayEquals(new float[]{1}, pulse.getMorphChannels().get(0).sample(pulse.getTicksPerSecond(), 1), 1e-5f);
    }

    @Test void combinedMorphAndEveryBoneCentroidMatchEvaluatedBlender() throws Exception {
        var scene = scene();
        var mesh = scene.getMeshes().get(0);
        var boneNames = mesh.getBoneNames().stream().toList();
        var data = mesh.getSkinnedData();
        int stride = data.getBoneIndices().length / data.getVertexCount();
        try (var input = getClass().getResourceAsStream("/fixtures/highbone_reference.json")) {
            assertNotNull(input);
            var reference = JsonParser.parseReader(new InputStreamReader(input, StandardCharsets.UTF_8)).getAsJsonObject();
            for (var element : reference.getAsJsonArray("samples")) {
                var sample = element.getAsJsonObject();
                try (var state = new AnimationState("highbone", scene)) {
                    state.initialize();
                    String wave = Arrays.stream(state.getAvailableAnimations()).filter(n -> n.endsWith("Wave")).findFirst().orElseThrow();
                    assertTrue(state.transitionTo(wave, 0));
                    state.setMorphWeights(mesh, new float[]{sample.get("weight").getAsFloat()});
                    state.update(sample.get("seconds").getAsFloat());
                    var pose = state.getPoseSnapshot();
                    sample.getAsJsonObject("nodes").entrySet().forEach(entry -> assertVector(vector(entry.getValue().getAsJsonArray()),
                            pose.getNodeTransform(entry.getKey()).getTranslation(new Vector3f()), entry.getKey()));
                    float[] positions = CpuSkinning.geometry(pose, mesh).positions();
                    Vector3f[] centroids = new Vector3f[256];
                    int[] counts = new int[256];
                    for (int i = 0; i < centroids.length; i++) centroids[i] = new Vector3f();
                    Vector3f low = new Vector3f(Float.POSITIVE_INFINITY), high = new Vector3f(Float.NEGATIVE_INFINITY);
                    for (int vertex = 0; vertex < data.getVertexCount(); vertex++) {
                        int owner = data.getBoneIndices()[vertex * stride];
                        assertEquals(1, data.getBoneWeights()[vertex * stride], 1e-6);
                        Vector3f point = new Vector3f(positions[vertex * 3], positions[vertex * 3 + 1], positions[vertex * 3 + 2]);
                        assertTrue(point.isFinite());
                        centroids[owner].add(point); counts[owner]++;
                        low.min(point); high.max(point);
                        var bounds = pose.getBounds();
                        assertTrue(point.x >= bounds.minX - 1e-5 && point.x <= bounds.maxX + 1e-5
                                && point.y >= bounds.minY - 1e-5 && point.y <= bounds.maxY + 1e-5
                                && point.z >= bounds.minZ - 1e-5 && point.z <= bounds.maxZ + 1e-5);
                    }
                    for (int bone = 0; bone < 256; bone++) {
                        assertTrue(counts[bone] > 0);
                        String name = boneNames.get(bone);
                        assertVector(vector(sample.getAsJsonObject("centroids").getAsJsonArray(name)), centroids[bone].div(counts[bone]), name);
                    }
                    assertVector(vector(sample.getAsJsonArray("min")), low, "minimum");
                    assertVector(vector(sample.getAsJsonArray("max")), high, "maximum");
                }
            }
        }
    }

    private static Vector3f vector(JsonArray values) {
        return new Vector3f(values.get(0).getAsFloat(), values.get(1).getAsFloat(), values.get(2).getAsFloat());
    }

    private static void assertVector(Vector3f expected, Vector3f actual, String context) {
        assertTrue(expected.distance(actual) < .0002, () -> context + ": " + expected + " != " + actual);
    }
}
