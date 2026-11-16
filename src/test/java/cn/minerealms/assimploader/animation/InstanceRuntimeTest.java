package cn.minerealms.assimploader.animation;

import cn.minerealms.assimploader.animation.ik.ChainIkModifier;
import cn.minerealms.assimploader.animation.ik.LookAtModifier;
import com.google.gson.JsonParser;
import org.joml.Matrix4f;
import org.joml.Vector3f;
import org.junit.jupiter.api.Test;

import java.io.StringReader;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

class InstanceRuntimeTest {
    private AnimationState state() {
        AnimationState state = new AnimationState("test", AnimationTestScenes.create());
        state.initialize();
        state.playAnimation("A");
        return state;
    }

    @Test void maskedOverrideAndBindRelativeAdditiveRemainInstanceLocal() {
        AnimationState state = state();
        AnimationState other = new AnimationState("other", state.getScene());
        other.initialize();
        state.setLayer("upper", "B", .5f, false, Map.of("Joint", 1f));
        assertEquals(10, x(state), 1e-4);
        state.update(1);
        assertEquals(11, x(state), 1e-4);
        assertEquals(0, x(other), 1e-4);
        state.setLayerWeight("upper", 0);
        assertEquals(1, x(state), 1e-4);
        state.setLayer("add", "B", .5f, true, Map.of("Joint", 1f));
        assertEquals(10, x(state), 1e-4); // base 1 + (sample 20 - bind 2) / 2
        state.setLayer("missing", "C", 1, false, Map.of("Other", 1f));
        assertEquals(10, x(state), 1e-4);
        assertThrows(IllegalArgumentException.class, () -> state.setLayerWeight("add", Float.NaN));
    }

    @Test void transitionInterruptionDoesNotApplyLayersTwice() {
        AnimationState state = state();
        state.setLayer("upper", "B", .5f, false, Map.of());
        state.transitionTo("B", 2);
        state.update(1);
        float displayed = x(state);
        state.transitionTo("C", 2);
        assertEquals(displayed, x(state), 1e-4);
        state.stop();
        assertEquals(2, x(state), 1e-4);
    }

    @Test void boundStateMachineUsesInstancePoseAndRestartsAfterStop() {
        AnimationState state = state();
        AnimationStateMachine machine = state.getStateMachine();
        machine.addState("idle", "A", true, 1, .2);
        machine.addState("action", "B", false, 2, .5);
        machine.addTransition("idle", "action", 1);
        assertTrue(machine.request("idle"));
        assertTrue(machine.request("action"));
        state.update(.5f);
        assertEquals(.5, machine.getBlendFactor(), 1e-5);
        assertEquals(10.75, x(state), 1e-4);
        state.stop();
        assertTrue(machine.request("action"));
        assertEquals(20, x(state), 1e-4);
        assertFalse(machine.request("missing"));
    }

    @Test void jsonInstantiatesIndependentPlaybackAndAttachmentCopies() {
        ModelInstanceDefinition definition = ModelInstanceDefinition.read(new StringReader("""
                {"version":1,"model":"test:model","initial":"idle",
                 "states":{"idle":{"clip":"A"},"action":{"clip":"B","transition":1}},
                 "attachments":{"tool":{"node":"Joint","translation":[1,0,0]}},
                 "layers":{"upper":{"clip":"C","weight":0,"mask":{"Joint":1}}}}
                """));
        var scene = AnimationTestScenes.create();
        try (ModelInstance a = definition.instantiate(scene); ModelInstance b = definition.instantiate(scene)) {
            a.animation().update(1);
            assertEquals(2, a.attachmentTransform("tool", new Matrix4f()).m30(), 1e-4);
            assertEquals(1, b.attachmentTransform("tool", new Matrix4f()).m30(), 1e-4);
            a.attachments().get("tool").offset().translation(500, 0, 0);
            assertEquals(12, a.attachmentTransform("tool", new Matrix4f().translation(10,0,0)).m30(), 1e-4);
            assertTrue(a.requestState("action"));
            assertTrue(a.animation().isTransitioning());
        }
    }

    @Test void inheritanceMergesMapsReplacesArraysAndRejectsCycles() {
        Map<String, com.google.gson.JsonObject> source = Map.of(
                "base", JsonParser.parseString("{\"states\":{\"idle\":{\"clip\":\"A\",\"speed\":1}},\"transitions\":[1]}").getAsJsonObject(),
                "child", JsonParser.parseString("{\"parent\":\"base\",\"states\":{\"idle\":{\"speed\":2}},\"transitions\":[2]}").getAsJsonObject());
        var merged = InstanceDefinitionResources.resolve("child", source::get);
        assertEquals("A", merged.getAsJsonObject("states").getAsJsonObject("idle").get("clip").getAsString());
        assertEquals(2, merged.getAsJsonArray("transitions").get(0).getAsInt());
        assertEquals(1, source.get("base").getAsJsonObject("states").getAsJsonObject("idle").get("speed").getAsInt());
        assertThrows(IllegalArgumentException.class, () -> InstanceDefinitionResources.resolve("loop",
                key -> JsonParser.parseString("{\"parent\":\"loop\"}").getAsJsonObject()));
        assertThrows(IllegalArgumentException.class, () -> ModelInstanceDefinition.read(new StringReader("{\"version\":2,\"model\":\"x\"}")));
        assertThrows(IllegalArgumentException.class, () -> ModelInstanceDefinition.read(new StringReader("{\"version\":1,\"model\":\"x\",\"typo\":1}")));
    }

    @Test void lookAtAccountsForRotatedParentAndPreservesAsset() {
        var scene = AnimationTestScenes.create();
        Map<String, Matrix4f> locals = new LinkedHashMap<>();
        locals.put("Root", new Matrix4f().rotateZ(.8f));
        PoseGraph graph = new PoseGraph(scene.getRootNode(), locals);
        Vector3f origin = graph.position("Joint");
        Matrix4f bind = new Matrix4f(scene.getRootNode().find("Joint").getTransformation());
        new LookAtModifier("Joint", new Vector3f(1,0,0), new Vector3f(origin).add(0,0,10), 1, (float)Math.PI)
                .apply(scene, locals, .05);
        Vector3f direction = new PoseGraph(scene.getRootNode(), locals).global("Joint").transformDirection(new Vector3f(1,0,0)).normalize();
        assertEquals(1, direction.z, 1e-5);
        assertTrue(origin.distance(new PoseGraph(scene.getRootNode(), locals).position("Joint")) < 1e-5);
        assertEquals(bind, scene.getRootNode().find("Joint").getTransformation());
    }

    @Test void ccdUsesParentSpaceAndRejectsDisconnectedChains() {
        var scene = AnimationTestScenes.create();
        Map<String, Matrix4f> locals = new LinkedHashMap<>();
        Vector3f target = new Vector3f(0,0,(float)Math.sqrt(29));
        new ChainIkModifier(List.of("Root", "Joint"), target, 1, 16, .001f).apply(scene, locals, .05);
        assertTrue(new PoseGraph(scene.getRootNode(), locals).position("Joint").distance(target) < .001);
        assertThrows(IllegalArgumentException.class, () -> new ChainIkModifier(List.of("Joint", "Other"), target, 1, 4, .01f)
                .apply(scene, new LinkedHashMap<>(), .05));
    }

    private float x(AnimationState state) { return state.getPoseSnapshot().getNodeTransform("Joint").m30(); }
}
