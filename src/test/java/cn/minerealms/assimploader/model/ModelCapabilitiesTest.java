package cn.minerealms.assimploader.model;

import cn.minerealms.assimploader.animation.AnimationState;
import cn.minerealms.assimploader.animation.MorphTargetTest;
import cn.minerealms.assimploader.compat.kubejs.AssimpModelHandle;
import cn.minerealms.assimploader.core.AssimpModelLoader;
import cn.minerealms.assimploader.core.ModelImportMode;
import org.junit.jupiter.api.Test;

import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

class ModelCapabilitiesTest {
    @SuppressWarnings("unchecked") private static Map<String, Object> section(Map<String, Object> value, String key) {
        return (Map<String, Object>) value.get(key);
    }

    @Test void inventoryReportsCopiedMorphAndClipDataWithoutInferringAuthoringSemantics() {
        var scene = MorphTargetTest.fixture(); var inventory = ModelCapabilities.of(scene);
        assertEquals(1, section(inventory, "mesh").get("count"));
        assertEquals(1, section(inventory, "morph").get("targetCount"));
        assertEquals(1, section(inventory, "morph").get("channelCount"));
        assertEquals("present", section(inventory, "morph").get("imported"));
        assertEquals(1, section(inventory, "clips").get("count"));
        assertEquals("DYNAMIC", section(inventory, "source").get("importMode"));
        assertEquals("preserved", section(inventory, "source").get("hierarchy"));
        assertEquals("unknown", section(inventory, "skin").get("sourcePresence"));
        for (String feature : List.of("fbxConstraints", "fbxPhysics")) {
            var authoring = section(section(inventory, "authoring"), feature);
            assertEquals("unknown", authoring.get("sourcePresence"));
            assertEquals("not_exposed_by_adapter", authoring.get("imported"));
            assertEquals("source_graph_not_supported", authoring.get("runtime"));
            assertEquals("required_not_inferred", authoring.get("alternativeDefinition"));
        }
        assertEquals("not_evaluated", section(inventory, "pbr").get("textureResolution"));
        assertEquals("not_evaluated", section(inventory, "pbr").get("activeShader"));
    }

    @Test void staticImportReportsItsActualModeAndCannotPromiseRecoveringDiscardedSkinOrClips() {
        byte[] obj = "v 0 0 0\nv 1 0 0\nv 0 1 0\nf 1 2 3\n".getBytes(StandardCharsets.UTF_8);
        var scene = new AssimpModelLoader().loadModelFromMemory("capability_triangle.obj", obj, ModelImportMode.STATIC);
        assertNotNull(scene);
        var inventory = ModelCapabilities.of(scene);
        assertEquals("STATIC", section(inventory, "source").get("importMode"));
        assertEquals("pretransformed", section(inventory, "source").get("hierarchy"));
        for (String feature : List.of("skin", "clips", "morph")) {
            assertEquals("requires_dynamic_import", section(inventory, feature).get("runtime"));
            assertEquals("no_imported_data", section(inventory, feature).get("imported"));
            assertEquals("unknown", section(inventory, feature).get("sourcePresence"));
        }
    }

    @Test void realBlenderFixtureReportsSkinNamesMorphChannelsAndPbrInputsFromTheImportedAdapter() throws Exception {
        String path = "/assets/assimploader/models/validation/animation_probe_morph.glb";
        try (var stream = getClass().getResourceAsStream(path)) {
            assertNotNull(stream);
            var scene = new AssimpModelLoader().loadModelFromMemory(path, stream.readAllBytes(), ModelImportMode.DYNAMIC);
            var inventory = ModelCapabilities.of(scene);
            assertEquals("present", section(inventory, "skin").get("imported"));
            assertTrue(((List<?>) section(inventory, "skin").get("bones")).contains("Shoulder"));
            assertTrue(((Number) section(inventory, "morph").get("channelCount")).intValue() > 0);
            assertEquals(scene.getAnimations().size(), section(inventory, "clips").get("count"));
            assertEquals((int) scene.getMaterials().stream().filter(material -> material.getPbrInputs().hasPbrInputs()).count(),
                    section(inventory, "pbr").get("materialCount"));
            assertEquals("not_evaluated", section(inventory, "skin").get("gpuEligibility"));
        }
    }

    @Test void cachedInventoryIsSharedAcrossInstancesButCannotBeMutatedByScripts() {
        var scene = MorphTargetTest.fixture(); var inventory = ModelCapabilities.of(scene);
        assertSame(inventory, ModelCapabilities.of(scene));
        assertNotSame(inventory, ModelCapabilities.of(MorphTargetTest.fixture()));
        assertThrows(UnsupportedOperationException.class, () -> inventory.put("source", Map.of()));
        assertThrows(UnsupportedOperationException.class, () -> section(inventory, "source").put("importMode", "STATIC"));
        assertThrows(UnsupportedOperationException.class, () -> ((List<?>) section(inventory, "mesh").get("items")).clear());
        try (var a = new AnimationState("capability-a", scene); var b = new AnimationState("capability-b", scene)) {
            var ah = new AssimpModelHandle(a); var bh = new AssimpModelHandle(b);
            assertSame(ah.capabilities(), bh.capabilities());
            assertSame(inventory, ah.capabilities());
            a.close();
            assertThrows(IllegalStateException.class, ah::capabilities);
            assertSame(inventory, bh.capabilities());
        }
    }
}
