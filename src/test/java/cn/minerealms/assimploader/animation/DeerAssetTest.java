package cn.minerealms.assimploader.animation;

import cn.minerealms.assimploader.animation.ik.ChainIkModifier;
import cn.minerealms.assimploader.collision.ModelCollision;
import cn.minerealms.assimploader.collision.Ray;
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
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.*;

class DeerAssetTest {
    private static final String RESOURCE = "/assets/assimploader/models/validation/deer_showcase.glb";

    private static AssimpScene scene() throws Exception {
        try (var input = DeerAssetTest.class.getResourceAsStream(RESOURCE)) {
            assertNotNull(input);
            return new AssimpModelLoader().loadModelFromMemory(RESOURCE, input.readAllBytes(), ModelImportMode.DYNAMIC);
        }
    }

    private static AssimpScene fbx() throws Exception {
        return new AssimpModelLoader().loadModelFromMemory("deer_showcase.fbx",
                Files.readAllBytes(Path.of("model-tests/deer/deer_showcase.fbx")), ModelImportMode.DYNAMIC);
    }

    @Test void exportedAssetHasOneSkinnedPrimitiveAndElevenClips() throws Exception {
        var scene = scene();
        assertTrue(scene.hasSkeleton());
        assertEquals(1, scene.getMeshes().size());
        assertTrue(scene.getMeshes().get(0).hasBones());
        assertEquals(Set.of("idle", "walk", "gallop", "graze", "bugle", "antler_sweep", "rear_up", "jump", "hit", "death", "ik_probe"),
                scene.getAnimations().stream().map(a -> a.getName()).collect(java.util.stream.Collectors.toSet()));
        for (String leg : List.of("FL", "FR", "BL", "BR")) assertNotNull(scene.findNodeByName("hoof" + leg));
        var fbx = fbx();
        assertTrue(fbx.hasSkeleton());
        assertEquals(1, fbx.getMeshes().size());
        assertEquals(11, fbx.getAnimations().size());
    }

    @Test void importedSamplesMatchBlenderAcrossEveryBakedClip() throws Exception {
        verifyReferences(scene());
    }

    @Test void fbxSamplesMatchBlenderAcrossEveryBakedClip() throws Exception {
        verifyReferences(fbx());
    }

    private void verifyReferences(AssimpScene scene) throws Exception {
        try (var input = getClass().getResourceAsStream("/fixtures/deer_reference.json")) {
            assertNotNull(input);
            var samples = JsonParser.parseReader(new InputStreamReader(input, StandardCharsets.UTF_8))
                    .getAsJsonObject().getAsJsonArray("samples");
            for (var element : samples) {
                var sample = element.getAsJsonObject();
                try (var state = new AnimationState("deer", scene)) {
                    state.initialize();
                    String requested = sample.get("clip").getAsString();
                    String clip = java.util.Arrays.stream(state.getAvailableAnimations())
                            .filter(name -> name.equals(requested) || name.endsWith("|" + requested)).findFirst().orElseThrow();
                    assertTrue(state.transitionTo(clip, 0));
                    state.update(sample.get("seconds").getAsFloat());
                    var pose = state.getPoseSnapshot();
                    sample.getAsJsonObject("nodes").entrySet().forEach(entry -> assertVector(vector(entry.getValue().getAsJsonArray()),
                            pose.getNodeTransform(entry.getKey()).getTranslation(new Vector3f()), sample.get("clip") + "/" + entry.getKey()));
                    var low = new Vector3f(Float.POSITIVE_INFINITY);
                    var high = new Vector3f(Float.NEGATIVE_INFINITY);
                    for (var instance : pose.copyMeshInstances()) {
                        float[] positions = CpuSkinning.geometry(pose, instance.mesh()).positions();
                        for (int i = 0; i < positions.length; i += 3) {
                            var point = new Vector3f(positions[i], positions[i + 1], positions[i + 2]);
                            assertTrue(point.isFinite());
                            low.min(point); high.max(point);
                        }
                    }
                    assertVector(vector(sample.getAsJsonArray("min")), low, "minimum " + sample);
                    assertVector(vector(sample.getAsJsonArray("max")), high, "maximum " + sample);
                }
            }
        }
    }

    @Test void sidecarSpringsCollisionAndIndependentStatesUseActualAsset() throws Exception {
        var scene = scene();
        try (var input = getClass().getResourceAsStream(RESOURCE.replace(".glb", ".instance.json"))) {
            assertNotNull(input);
            var definition = ModelInstanceDefinition.read(new InputStreamReader(input, StandardCharsets.UTF_8));
            try (var first = definition.instantiate(scene); var second = definition.instantiate(scene)) {
                var retained = second.animation().getPoseSnapshot();
                for (String clip : List.of("walk", "gallop", "ik_probe", "graze", "bugle", "death")) {
                    assertTrue(first.requestState(clip));
                    for (int i = 0; i < 20; i++) first.animation().update(.05f);
                    var pose = first.animation().getPoseSnapshot();
                    for (String node : List.of("earL", "tail1", "stirrupR", "head")) assertTrue(pose.getNodeTransform(node).isFinite());
                    assertSame(retained, second.animation().getPoseSnapshot());
                }
                assertTrue(second.collision().raycast(retained, new Ray(new Vector3f(4, 1.05f, 0), new Vector3f(-1, 0, 0)), 8, ModelCollision.Mode.BONE).isPresent());
                assertTrue(second.collision().raycast(retained, new Ray(new Vector3f(4, 1.05f, 0), new Vector3f(-1, 0, 0)), 8, ModelCollision.Mode.MESH).isPresent());
            }
        }
    }

    @Test void runtimeIkCanLiftAnActualHoof() throws Exception {
        try (var state = new AnimationState("deer", scene())) {
            state.initialize();
            assertTrue(state.transitionTo("idle", 0));
            var original = state.getPoseSnapshot().getNodeTransform("hoofFL").getTranslation(new Vector3f());
            var target = new Vector3f(original).add(0, .15f, .04f);
            state.addModifier("probe", new ChainIkModifier(List.of("legFL0", "legFL1", "legFL2", "hoofFL"), target, 1, 32, .0001f));
            state.update(.05f);
            var actual = state.getPoseSnapshot().getNodeTransform("hoofFL").getTranslation(new Vector3f());
            assertTrue(actual.distance(target) < .003, () -> actual + " != " + target);
            assertTrue(actual.distance(original) > .1);
        }
    }

    @Test void terrainBodyAndFourFeetRespectGroundAndAllDeformedVerticesRemainBounded() throws Exception {
        var scene=scene();
        try(var input=getClass().getResourceAsStream(RESOURCE.replace(".glb",".instance.json"))) {
            var definition=ModelInstanceDefinition.read(new InputStreamReader(input,StandardCharsets.UTF_8));
            try(var instance=definition.instantiate(scene)) {
                instance.setWorldContext(new org.joml.Matrix4f().translation(100,20.12f,-30),(origin,distance)->
                        java.util.Optional.of(new Vector3f(origin.x,origin.x<100?20:20.25f,origin.z)));
                float maximum=0;
                for(int frame=0;frame<60;frame++) {
                    instance.animation().update(.05f);
                    var pose=instance.animation().getPoseSnapshot();
                    for(var entry:instance.feet().entrySet()) {
                        var target=entry.getValue().getLastTarget();assertNotNull(target);
                        float error=pose.getNodeTransform("hoof"+entry.getKey()).getTranslation(new Vector3f()).distance(target);
                        maximum=Math.max(maximum,error);
                        assertTrue(error<.025f,()->entry.getKey()+" foot error="+error+" body="+instance.groundBodyOffset());
                    }
                    var box=pose.getBounds();
                    for(var mesh:scene.getMeshes()) {
                        var positions=CpuSkinning.geometry(pose,mesh).positions();
                        for(int i=0;i<positions.length;i+=3) {
                            assertTrue(positions[i]>=box.minX-1e-5&&positions[i]<=box.maxX+1e-5);
                            assertTrue(positions[i+1]>=box.minY-1e-5&&positions[i+1]<=box.maxY+1e-5);
                            assertTrue(positions[i+2]>=box.minZ-1e-5&&positions[i+2]<=box.maxZ+1e-5);
                        }
                    }
                }
                System.out.println("Deer 60-frame terrain maximum foot error meters="+maximum);
            }
        }
    }

    private static Vector3f vector(JsonArray values) {
        return new Vector3f(values.get(0).getAsFloat(), values.get(1).getAsFloat(), values.get(2).getAsFloat());
    }
    private static void assertVector(Vector3f expected, Vector3f actual, String context) {
        assertTrue(expected.distance(actual) < .0003, () -> context + ": " + expected + " != " + actual);
    }
}
