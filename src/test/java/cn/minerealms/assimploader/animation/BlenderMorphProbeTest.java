package cn.minerealms.assimploader.animation;

import cn.minerealms.assimploader.core.AssimpModelLoader;
import cn.minerealms.assimploader.core.ModelImportMode;
import cn.minerealms.assimploader.model.AssimpScene;
import cn.minerealms.assimploader.rendering.CpuSkinning;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

class BlenderMorphProbeTest {
    private static AssimpScene scene() throws IOException {
        String path = "/assets/assimploader/models/validation/animation_probe_morph.glb";
        try (var stream = BlenderMorphProbeTest.class.getResourceAsStream(path)) {
            assertNotNull(stream, "Regenerate with create_animation_probe.py --morph-only");
            AssimpScene scene = new AssimpModelLoader().loadModelFromMemory(path, stream.readAllBytes(), ModelImportMode.DYNAMIC);
            assertNotNull(scene);
            return scene;
        }
    }

    @Test
    void originalGlbRetainsSkinMorphClipsAndEmbeddedChecker() throws IOException {
        var scene = scene();
        assertTrue(scene.hasSkeleton());
        assertNotNull(scene.findNodeByName("ToolSocket"));
        var bridge = scene.getMeshes().stream().filter(mesh -> mesh.hasMorphTargets()).findFirst().orElseThrow();
        assertTrue(bridge.hasBones());
        assertTrue(bridge.getBoneNames().containsAll(java.util.List.of("Shoulder", "Elbow")));
        assertEquals(1, bridge.getMorphTargets().size());
        assertTrue(scene.getAnimations().stream().anyMatch(clip -> clip.getName().contains("Idle")));
        assertTrue(scene.getAnimations().stream().anyMatch(clip -> clip.getName().contains("Reach")));
        assertTrue(scene.getAnimations().stream().anyMatch(clip -> clip.getName().contains("Fold")));
        var pulse = scene.getAnimations().stream().filter(clip -> clip.getName().contains("MorphPulse")).findFirst().orElseThrow();
        assertFalse(pulse.getMorphChannels().isEmpty());
        assertArrayEquals(new float[]{1}, pulse.getMorphChannels().get(0).sample(pulse.getTicksPerSecond(), 1), 1e-4f);
        assertFalse(scene.getTextures().isEmpty());
    }

    @Test
    void realSkinnedMorphChangesGeometryAndReturnsToBindWithoutAccumulation() throws IOException {
        var scene = scene();
        var bridge = scene.getMeshes().stream().filter(mesh -> mesh.hasMorphTargets()).findFirst().orElseThrow();
        var zero = PoseSnapshot.capture(0, scene, Map.of(), Map.of(bridge, new float[]{0}));
        var one = PoseSnapshot.capture(1, scene, Map.of(), Map.of(bridge, new float[]{1}));
        var restored = PoseSnapshot.capture(2, scene, Map.of(), Map.of(bridge, new float[]{0}));
        float[] bind = CpuSkinning.geometry(zero, bridge).positions();
        float[] morphed = CpuSkinning.geometry(one, bridge).positions();
        float maximum = 0;
        for (int i = 0; i < bind.length; i += 3) {
            float x = morphed[i] - bind[i], y = morphed[i + 1] - bind[i + 1], z = morphed[i + 2] - bind[i + 2];
            maximum = Math.max(maximum, (float) Math.sqrt(x * x + y * y + z * z));
            assertTrue(morphed[i] >= one.getBounds().minX - 1e-5 && morphed[i] <= one.getBounds().maxX + 1e-5);
            assertTrue(morphed[i + 1] >= one.getBounds().minY - 1e-5 && morphed[i + 1] <= one.getBounds().maxY + 1e-5);
            assertTrue(morphed[i + 2] >= one.getBounds().minZ - 1e-5 && morphed[i + 2] <= one.getBounds().maxZ + 1e-5);
        }
        assertTrue(maximum > .85f && maximum < .95f, "The authored maximum delta should be visible and preserve scale");
        assertArrayEquals(bind, CpuSkinning.geometry(restored, bridge).positions(), 1e-5f);
    }

    @Test
    void shapeKeyActionPlaysOnItsMeshBearingNode() throws IOException {
        var scene = scene();
        var bridge = scene.getMeshes().stream().filter(mesh -> mesh.hasMorphTargets()).findFirst().orElseThrow();
        var state = new AnimationState("real-morph", scene);
        state.initialize();
        String pulse = scene.getAnimations().stream().map(clip -> clip.getName()).filter(name -> name.contains("MorphPulse")).findFirst().orElseThrow();
        state.playAnimation(pulse);
        state.update(1);
        assertArrayEquals(new float[]{1}, state.getPoseSnapshot().getMorphWeights(bridge), 1e-4f);
        state.close();
    }
}
