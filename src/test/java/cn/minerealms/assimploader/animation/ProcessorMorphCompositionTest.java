package cn.minerealms.assimploader.animation;

import cn.minerealms.assimploader.compat.kubejs.AssimpModelHandle;
import cn.minerealms.assimploader.core.AssimpModelLoader;
import cn.minerealms.assimploader.core.ModelImportMode;
import cn.minerealms.assimploader.model.AssimpMesh;
import cn.minerealms.assimploader.model.AssimpScene;
import com.google.gson.Gson;
import org.joml.Matrix4f;
import org.junit.jupiter.api.Test;

import java.util.LinkedHashMap;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

class ProcessorMorphCompositionTest {
    private static Map<String, Object> definition(String clip, boolean additive, Map<String, Float> mask, float weight) {
        return Map.of("version", 1, "clips", Map.of("pulse", Map.of("clip", clip, "weight", weight,
                "additive", additive, "mask", mask, "loop", false)));
    }
    private static ModelAnimationProcessor processor(AssimpScene scene, boolean additive, Map<String, Float> mask, float weight) {
        return ModelAnimationProcessor.compile(scene, new Gson().toJsonTree(definition("Pulse", additive, mask, weight)).getAsJsonObject());
    }
    private static Map<AssimpMesh, float[]> baseline(AssimpMesh mesh, float weight) {
        var result = new LinkedHashMap<AssimpMesh, float[]>(); result.put(mesh, new float[]{weight}); return result;
    }

    @Test void morphSamplingUsesTheSkeletalClockAndNeverAdvancesItAgain() {
        var scene = MorphTargetTest.fixture(); var mesh = scene.getMeshes().get(0);
        try (var processor = processor(scene, false, Map.of(), 1)) {
            processor.apply(scene, new LinkedHashMap<>(), .5);
            for (int index = 0; index < 5; index++) {
                var weights = baseline(mesh, .2f); processor.applyMorphs(scene, weights);
                assertArrayEquals(new float[]{.5f}, weights.get(mesh), 1e-5f);
            }
            processor.apply(scene, new LinkedHashMap<>(), .25);
            var weights = baseline(mesh, .2f); processor.applyMorphs(scene, weights);
            assertArrayEquals(new float[]{.75f}, weights.get(mesh), 1e-5f);
            assertArrayEquals(new float[]{.25f}, mesh.getDefaultMorphWeights(), "Shared asset defaults cannot be mutated");
        }
    }

    @Test void nodeAndMeshMasksScaleMorphWeightsWithoutLeakingIntoUnselectedNodes() {
        var scene = MorphTargetTest.fixture(); var mesh = scene.getMeshes().get(0);
        String owner = scene.getNodeNameForMesh(mesh);
        assertNotEquals(mesh.getName(), owner, "Fixture must distinguish a mesh ID from its owning node");
        for (String mask : new String[]{owner, mesh.getName()}) {
            try (var processor = processor(scene, false, Map.of(mask, .5f), .5f)) {
                processor.phase("pulse", .25);
                processor.apply(scene, new LinkedHashMap<>(), .4);
                float[] original = {.2f}; var weights = new LinkedHashMap<AssimpMesh, float[]>(); weights.put(mesh, original);
                processor.applyMorphs(scene, weights);
                assertArrayEquals(new float[]{.275f}, weights.get(mesh), 1e-5f, "Node/mesh mask and clip weight multiply");
                assertArrayEquals(new float[]{.2f}, original, "Composition must not modify its input array");
            }
        }
        try (var processor = processor(scene, false, Map.of(owner, .25f, mesh.getName(), .75f), 1)) {
            processor.apply(scene, new LinkedHashMap<>(), .5);
            var weights = baseline(mesh, .2f); processor.applyMorphs(scene, weights);
            assertArrayEquals(new float[]{.425f}, weights.get(mesh), 1e-5f,
                    "Selecting a mesh by both aliases uses the larger mask, not an accumulated weight");
        }
        try (var processor = processor(scene, false, Map.of(owner, 0f), 1)) {
            processor.apply(scene, new LinkedHashMap<>(), .5);
            var weights = baseline(mesh, .2f); processor.applyMorphs(scene, weights);
            assertArrayEquals(new float[]{.2f}, weights.get(mesh), 1e-5f);
        }
    }

    @Test void additiveMorphsUseImportedDefaultWeightsAsTheirReference() {
        var scene = MorphTargetTest.fixture(); var mesh = scene.getMeshes().get(0);
        try (var processor = processor(scene, true, Map.of(), .5f)) {
            processor.apply(scene, new LinkedHashMap<>(), .5);
            var weights = baseline(mesh, .6f); processor.applyMorphs(scene, weights);
            assertArrayEquals(new float[]{.725f}, weights.get(mesh), 1e-5f);
        }
    }

    @Test void configuredSpeedAndLoopingApplyToMorphSamplingThroughTheExistingClipPlayer() {
        var scene = MorphTargetTest.fixture(); var mesh = scene.getMeshes().get(0);
        var json = new Gson().toJsonTree(definition("Pulse", false, Map.of(), 1)).getAsJsonObject();
        var clip = json.getAsJsonObject("clips").getAsJsonObject("pulse");
        clip.addProperty("speed", 2); clip.addProperty("loop", true);
        try (var processor = ModelAnimationProcessor.compile(scene, json)) {
            processor.apply(scene, new LinkedHashMap<>(), .25);
            var half = baseline(mesh, .2f); processor.applyMorphs(scene, half);
            assertArrayEquals(new float[]{.5f}, half.get(mesh), 1e-5f);
            processor.apply(scene, new LinkedHashMap<>(), .75);
            var wrapped = baseline(mesh, .2f); processor.applyMorphs(scene, wrapped);
            assertArrayEquals(new float[]{0}, wrapped.get(mesh), 1e-5f);
        }
    }

    @Test void disabledControlsFreezeTheirClockWhileZeroWeightControlsKeepTime() {
        var scene = MorphTargetTest.fixture(); var mesh = scene.getMeshes().get(0);
        try (var processor = processor(scene, false, Map.of(), 0)) {
            processor.apply(scene, new LinkedHashMap<>(), .5);
            var hidden = baseline(mesh, .2f); processor.applyMorphs(scene, hidden);
            assertArrayEquals(new float[]{.2f}, hidden.get(mesh), 1e-5f);
            processor.weight("pulse", 1);
            processor.enabled("pulse", false); processor.apply(scene, new LinkedHashMap<>(), 1);
            var disabled = baseline(mesh, .2f); processor.applyMorphs(scene, disabled);
            assertArrayEquals(new float[]{.2f}, disabled.get(mesh), 1e-5f);
            processor.enabled("pulse", true); processor.apply(scene, new LinkedHashMap<>(), 0);
            var resumed = baseline(mesh, .2f); processor.applyMorphs(scene, resumed);
            assertArrayEquals(new float[]{.5f}, resumed.get(mesh), 1e-5f);
            processor.enabled(false); processor.apply(scene, new LinkedHashMap<>(), 1);
            var globallyDisabled = baseline(mesh, .2f); processor.applyMorphs(scene, globallyDisabled);
            assertArrayEquals(new float[]{.2f}, globallyDisabled.get(mesh), 1e-5f);
            processor.enabled(true); processor.apply(scene, new LinkedHashMap<>(), .25);
            var again = baseline(mesh, .2f); processor.applyMorphs(scene, again);
            assertArrayEquals(new float[]{.75f}, again.get(mesh), 1e-5f);
        }
    }

    @Test void phaseHoldReleaseAndResetShareTheSameMorphTimeline() {
        var scene = MorphTargetTest.fixture(); var mesh = scene.getMeshes().get(0);
        try (var processor = processor(scene, false, Map.of(), 1)) {
            processor.phase("pulse", .5); processor.apply(scene, new LinkedHashMap<>(), .4);
            var held = baseline(mesh, .2f); processor.applyMorphs(scene, held);
            assertArrayEquals(new float[]{1}, held.get(mesh), 1e-5f);
            processor.releasePhase("pulse"); processor.apply(scene, new LinkedHashMap<>(), .5);
            var released = baseline(mesh, .2f); processor.applyMorphs(scene, released);
            assertArrayEquals(new float[]{.5f}, released.get(mesh), 1e-5f);
            processor.reset(); processor.apply(scene, new LinkedHashMap<>(), 0);
            var reset = baseline(mesh, .2f); processor.applyMorphs(scene, reset);
            assertArrayEquals(new float[]{0}, reset.get(mesh), 1e-5f);
        }
    }

    @Test void statePublishesProcessorMorphsAndKeepsTwoOwnersIndependent() {
        var scene = MorphTargetTest.fixture(); var mesh = scene.getMeshes().get(0);
        try (var a = new AnimationState("a", scene); var b = new AnimationState("b", scene)) {
            a.initialize(); b.initialize();
            a.setMorphWeights(mesh, new float[]{.2f}); b.setMorphWeights(mesh, new float[]{.2f});
            var ah = new AssimpModelHandle(a); var bh = new AssimpModelHandle(b);
            ah.processorDefinition("morph", definition("Pulse", false, Map.of(), 1));
            bh.processorDefinition("morph", definition("Pulse", false, Map.of(), 1));
            ah.processorPhase("morph", "pulse", .25); bh.processorPhase("morph", "pulse", .5);
            a.update(.1f); b.update(.1f);
            assertArrayEquals(new float[]{.5f}, a.getPoseSnapshot().getMorphWeights(mesh), 1e-5f);
            assertArrayEquals(new float[]{1}, b.getPoseSnapshot().getMorphWeights(mesh), 1e-5f);
            ah.processorEnabled("morph", false); a.update(.1f);
            assertArrayEquals(new float[]{.2f}, a.getPoseSnapshot().getMorphWeights(mesh), 1e-5f);
        }
    }

    @Test void realBlenderShapeKeyClipUsesItsMeshBearingNodeChannel() throws Exception {
        String path = "/assets/assimploader/models/validation/animation_probe_morph.glb";
        try (var stream = getClass().getResourceAsStream(path)) {
            assertNotNull(stream);
            var scene = new AssimpModelLoader().loadModelFromMemory(path, stream.readAllBytes(), ModelImportMode.DYNAMIC);
            var clip = scene.getAnimations().stream().filter(value -> value.getName().contains("MorphPulse")).findFirst().orElseThrow();
            var mesh = scene.getMeshes().stream().filter(AssimpMesh::hasMorphTargets).findFirst().orElseThrow();
            try (var processor = ModelAnimationProcessor.compile(scene,
                    new Gson().toJsonTree(definition(clip.getName(), false, Map.of(), 1)).getAsJsonObject())) {
                processor.apply(scene, new LinkedHashMap<String, Matrix4f>(), 1);
                var weights = baseline(mesh, 0); processor.applyMorphs(scene, weights);
                assertArrayEquals(new float[]{1}, weights.get(mesh), 1e-4f);
            }
            assertNotNull(scene.findNodeByName("Shoulder"));
            try (var processor = ModelAnimationProcessor.compile(scene,
                    new Gson().toJsonTree(definition(clip.getName(), false, Map.of("Shoulder", 1f), 1)).getAsJsonObject())) {
                processor.apply(scene, new LinkedHashMap<>(), 1);
                var weights = baseline(mesh, 0); processor.applyMorphs(scene, weights);
                assertArrayEquals(new float[]{0}, weights.get(mesh), 1e-4f, "A joint mask does not select a separate mesh-bearing Morph node");
            }
        }
    }
}
