package cn.minerealms.assimploader.animation;

import cn.minerealms.assimploader.core.AssimpModelLoader;
import cn.minerealms.assimploader.core.ModelImportMode;
import cn.minerealms.assimploader.model.AssimpScene;
import org.junit.jupiter.api.Test;

import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.charset.StandardCharsets;
import java.util.Base64;

import static org.junit.jupiter.api.Assertions.*;

class AnimationStateTest {
    private AnimationState state() {
        AnimationState state = new AnimationState("test", AnimationTestScenes.create());
        state.initialize();
        assertTrue(state.playAnimation("A"));
        return state;
    }

    @Test
    void crossfadeAdvancesBothClipsAndPreservesOldSnapshots() {
        AnimationState state = state();
        state.update(1);
        PoseSnapshot old = state.getPoseSnapshot();
        assertEquals(1, x(old), 1e-4);
        assertTrue(state.transitionTo("B", 2));
        assertEquals(1, x(state.getPoseSnapshot()), 1e-4);
        state.update(1);
        assertEquals(11.5, x(state.getPoseSnapshot()), 1e-4);
        assertTrue(state.isTransitioning());
        state.update(1);
        assertEquals(22, x(state.getPoseSnapshot()), 1e-4);
        assertFalse(state.isTransitioning());
        assertEquals(1, x(old), 1e-4);
    }

    @Test
    void interruptingTransitionFreezesDisplayedSourceWithoutJump() {
        AnimationState state = state();
        state.transitionTo("B", 2);
        state.update(1);
        float displayed = x(state.getPoseSnapshot());
        assertEquals(11, displayed, 1e-4);
        state.transitionTo("C", 2);
        assertEquals(displayed, x(state.getPoseSnapshot()), 1e-4);
        state.update(1);
        assertEquals(26, x(state.getPoseSnapshot()), 1e-4);
        state.update(1);
        assertEquals(42, x(state.getPoseSnapshot()), 1e-4);
    }

    @Test
    void pauseStopAndSparseTargetHaveDefinedPoses() {
        AnimationState state = state();
        state.update(1);
        state.pause();
        PoseSnapshot paused = state.getPoseSnapshot();
        state.update(1);
        assertSame(paused, state.getPoseSnapshot());
        state.resume();
        state.transitionTo("D", 2);
        state.update(2);
        assertEquals(2, x(state.getPoseSnapshot()), 1e-4);
        assertEquals(102, state.getPoseSnapshot().getNodeTransform("Other").m30(), 1e-4);
        state.stop();
        assertNull(state.getCurrentAnimation());
        assertEquals(2, x(state.getPoseSnapshot()), 1e-4);
        assertEquals(-3, state.getPoseSnapshot().getNodeTransform("Other").m30(), 1e-4);
        state.transitionTo("C", 0);
        assertEquals(40, x(state.getPoseSnapshot()), 1e-4);
    }

    @Test
    void instancesAreIndependentAndInvalidCommandsDoNotPublish() {
        AssimpScene scene = AnimationTestScenes.create();
        AnimationState first = new AnimationState("same", scene);
        AnimationState second = new AnimationState("same", scene);
        first.initialize();
        second.initialize();
        first.playAnimation("A");
        second.playAnimation("A");
        first.update(1);
        assertEquals(1, x(first.getPoseSnapshot()), 1e-4);
        assertEquals(0, x(second.getPoseSnapshot()), 1e-4);
        PoseSnapshot before = first.getPoseSnapshot();
        assertFalse(first.transitionTo("Missing", 1));
        assertThrows(IllegalArgumentException.class, () -> first.transitionTo("B", Double.NaN));
        assertThrows(IllegalArgumentException.class, () -> first.transitionTo("B", -1));
        assertThrows(IllegalArgumentException.class, () -> first.update(Float.POSITIVE_INFINITY));
        assertThrows(IllegalArgumentException.class, () -> first.update(-1));
        first.update(0);
        assertSame(before, first.getPoseSnapshot());
        first.close();
        first.close();
        assertTrue(first.isClosed());
        assertNull(first.getPoseSnapshot());
        assertThrows(IllegalStateException.class, () -> first.update(1));
        assertEquals(1, x(before), 1e-4);
    }

    @Test
    void sameTargetDoesNotRestartAndImmediateSwitchClearsTransition() {
        AnimationState state = state();
        state.transitionTo("B", 2);
        state.update(.5f);
        PoseSnapshot before = state.getPoseSnapshot();
        state.transitionTo("B", 2);
        assertSame(before, state.getPoseSnapshot());
        state.transitionTo("C", 0);
        assertFalse(state.isTransitioning());
        assertEquals(40, x(state.getPoseSnapshot()), 1e-4);
        state.update(1);
        assertEquals(41, x(state.getPoseSnapshot()), 1e-4);
    }

    private static float x(PoseSnapshot pose) { return pose.getNodeTransform("Joint").m30(); }

    @Test
    void unanimatedDuplicateNodesKeepTheirOwnBindTransformsDuringCrossfade() {
        AnimationState state = state();
        assertEquals(-50, state.getPoseSnapshot().copyMeshInstances().get(1).transform().m30(), 1e-4);
        assertEquals(50, state.getPoseSnapshot().copyMeshInstances().get(2).transform().m30(), 1e-4);
        state.transitionTo("B", 2);
        state.update(1);
        assertEquals(-50, state.getPoseSnapshot().copyMeshInstances().get(1).transform().m30(), 1e-4);
        assertEquals(50, state.getPoseSnapshot().copyMeshInstances().get(2).transform().m30(), 1e-4);
    }
}

final class AnimationTestScenes {
    private AnimationTestScenes() {}

    static AssimpScene create() {
        ByteBuffer binary = ByteBuffer.allocate(140).order(ByteOrder.LITTLE_ENDIAN);
        for (float value : new float[]{0, 0, 0, 1, 0, 0, 0, 1, 0, 0, 10}) binary.putFloat(value);
        for (int clip = 0; clip < 4; clip++) {
            float start = clip == 3 ? 100 : clip * 20;
            for (float value : new float[]{start, 3, 4, start + 10, 3, 4}) binary.putFloat(value);
        }
        String json = """
                {"asset":{"version":"2.0"},
                 "buffers":[{"byteLength":140,"uri":"data:application/octet-stream;base64,%s"}],
                 "bufferViews":[{"buffer":0,"byteOffset":0,"byteLength":36},
                  {"buffer":0,"byteOffset":36,"byteLength":8},
                  {"buffer":0,"byteOffset":44,"byteLength":24},
                  {"buffer":0,"byteOffset":68,"byteLength":24},
                  {"buffer":0,"byteOffset":92,"byteLength":24},
                  {"buffer":0,"byteOffset":116,"byteLength":24}],
                 "accessors":[{"bufferView":0,"componentType":5126,"count":3,"type":"VEC3","min":[0,0,0],"max":[1,1,0]},
                  {"bufferView":1,"componentType":5126,"count":2,"type":"SCALAR","min":[0],"max":[10]},
                  {"bufferView":2,"componentType":5126,"count":2,"type":"VEC3"},
                  {"bufferView":3,"componentType":5126,"count":2,"type":"VEC3"},
                  {"bufferView":4,"componentType":5126,"count":2,"type":"VEC3"},
                  {"bufferView":5,"componentType":5126,"count":2,"type":"VEC3"}],
                 "meshes":[{"primitives":[{"attributes":{"POSITION":0}}]}],
                 "nodes":[{"name":"Root","children":[1,2,3,4]},
                  {"name":"Joint","mesh":0,"translation":[2,3,4]},
                  {"name":"Other","translation":[-3,0,0]},
                  {"name":"Duplicate","mesh":0,"translation":[-50,0,0]},
                  {"name":"Duplicate","mesh":0,"translation":[50,0,0]}],
                 "animations":[
                  {"name":"A","samplers":[{"input":1,"output":2}],"channels":[{"sampler":0,"target":{"node":1,"path":"translation"}}]},
                  {"name":"B","samplers":[{"input":1,"output":3}],"channels":[{"sampler":0,"target":{"node":1,"path":"translation"}}]},
                  {"name":"C","samplers":[{"input":1,"output":4}],"channels":[{"sampler":0,"target":{"node":1,"path":"translation"}}]},
                  {"name":"D","samplers":[{"input":1,"output":5}],"channels":[{"sampler":0,"target":{"node":2,"path":"translation"}}]}],
                 "scenes":[{"nodes":[0]}],"scene":0}
                """.formatted(Base64.getEncoder().encodeToString(binary.array()));
        AssimpScene scene = new AssimpModelLoader().loadModelFromMemory("animation-state.gltf",
                json.getBytes(StandardCharsets.UTF_8), ModelImportMode.DYNAMIC);
        assertNotNull(scene);
        assertEquals(4, scene.getAnimations().size());
        return scene;
    }
}
