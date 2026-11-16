package cn.minerealms.assimploader.character.core;

import cn.minerealms.assimploader.animation.AnimationState;
import cn.minerealms.assimploader.animation.ModelInstance;
import cn.minerealms.assimploader.animation.ModelInstanceDefinition;
import cn.minerealms.assimploader.core.AssimpModelLoader;
import cn.minerealms.assimploader.core.ModelImportMode;
import cn.minerealms.assimploader.model.AssimpScene;
import org.joml.Vector3f;
import org.junit.jupiter.api.Test;

import java.io.StringReader;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.charset.StandardCharsets;
import java.util.Base64;

import static cn.minerealms.assimploader.character.core.PlayerAnimationController.Intent.*;
import static org.junit.jupiter.api.Assertions.*;

class PlayerAnimationControllerTest {
    @Test void nativeSwingUsesObservedPhaseAndConsecutiveGenerationsWithoutQueueOrBlend() {
        try (var instance = instance(); var controller = new PlayerAnimationController(profile(false), instance)) {
            var state = instance.animation();
            controller.update(input(0).build());
            assertTrue(controller.update(input(1).swingAction(1,true,.2f).build()).changed());
            var player = state.getCurrentAnimation();
            double duration = player.getAnimation().getDuration();
            assertTrue(player.hasSynchronizedPhase());
            assertFalse(state.isTransitioning());
            assertEquals(duration*.2,player.getCurrentTime(),1e-6);
            assertEquals(0,state.getSimulationTime());
            state.update(.2f);
            assertEquals(duration*.2,player.getCurrentTime(),1e-6);
            assertFalse(controller.update(input(2).swingAction(1,true,.8f).build()).changed());
            assertEquals(duration*.8,player.getCurrentTime(),1e-6);
            assertTrue(controller.update(input(3).swingAction(2,true,0).build()).changed());
            assertEquals(0,player.getCurrentTime());
            assertTrue(controller.update(input(4).swingAction(3,true,.1f).build()).changed());
            assertEquals(duration*.1,player.getCurrentTime(),1e-6);
            assertEquals(WALK,controller.update(input(5).motion(2,0,0).swingAction(3,false,0).build()).selected());
            assertFalse(player.hasSynchronizedPhase());
            assertFalse(state.isTransitioning());
            assertEquals(IDLE,controller.update(input(6).swingAction(3,false,0).build()).selected());
        }
    }

    @Test void nativeSwingRejectsStaleTicksTimesAndGenerationsAndReleasesClockOnPriorityInterrupt() {
        try (var instance = instance(); var controller = new PlayerAnimationController(profile(false), instance)) {
            controller.update(input(2).swingAction(7,true,.6f).build());
            var player = instance.animation().getCurrentAnimation();
            double time = player.getCurrentTime();
            assertFalse(controller.update(input(1).swingAction(8,true,0).build()).accepted());
            assertFalse(controller.update(PlayerVisualInput.builder(3,0).swingAction(8,true,0).build()).accepted());
            assertFalse(controller.update(input(3).swingAction(6,true,0).build()).accepted());
            assertEquals(time,player.getCurrentTime());
            assertEquals(DEAD,controller.update(input(4).dead(true).swingAction(8,true,.2f).build()).requested());
            assertFalse(player.hasSynchronizedPhase());
            controller.update(input(5).swingAction(9,true,.3f).build());
            controller.reset();
            assertFalse(player.hasSynchronizedPhase());
        }
    }

    @Test void movementCadenceTracksSpeedClampsAndRestoresBindingPlaybackRate() {
        try (var instance = instance(); var controller = new PlayerAnimationController(profile(false), instance)) {
            var state = instance.animation();
            state.getStateMachine().addState("walk","Walk",true,1.7f,0);
            controller.update(input(0).motion(2.4,0,0).build());
            var walking = state.getCurrentAnimation();
            assertEquals(1,walking.getSpeed(),1e-6);
            controller.update(input(1).motion(4.8,0,0).build());
            assertEquals(2,walking.getSpeed(),1e-6);
            controller.update(input(2).motion(200,0,0).build());
            assertEquals(12,walking.getSpeed(),1e-6);
            controller.update(input(3).motion(.03,0,0).build());
            assertEquals(.25,walking.getSpeed(),1e-6);
            controller.update(input(4).build());
            assertEquals(1.7,walking.getSpeed(),1e-6);
            assertEquals(1,state.getCurrentAnimation().getSpeed(),1e-6);
            controller.update(input(5).motion(-2.4,0,0).build());
            assertEquals(1,state.getCurrentAnimation().getSpeed(),1e-6);
            controller.update(input(6).motion(0,2.4,0).build());
            assertEquals(1,state.getCurrentAnimation().getSpeed(),1e-6);
        }
    }

    @Test void timingOptOutPreservesLegacyClipCompletionAndBindingSpeed() {
        var profile = CharacterProfile.read(new StringReader("""
                {"version":1,"model":"example:avatar.glb","timing":{"swingSync":false,"movementSync":false}}
                """));
        try (var instance = instance(); var controller = new PlayerAnimationController(profile,instance)) {
            var state = instance.animation();
            state.getStateMachine().addState("walk","Walk",true,1.7f,0);
            controller.update(input(0).motion(20,0,0).build());
            assertEquals(1.7,state.getCurrentAnimation().getSpeed(),1e-6);
            controller.update(input(1).swingAction(1,true,.7f).build());
            assertFalse(state.getCurrentAnimation().hasSynchronizedPhase());
            assertEquals(0,state.getCurrentAnimation().getCurrentTime());
            state.update(.1f);
            assertEquals(SWING,controller.update(input(2).swingAction(1,false,0).build()).selected());
            assertTrue(controller.update(input(3).swingAction(2,true,0).build()).changed());
            assertEquals(0,state.getCurrentAnimation().getCurrentTime());
        }
    }

    @Test void synchronizedPhaseMapsDifferentClipLengthsAndReleasesToExistingSpeed() {
        try (var stack = org.lwjgl.system.MemoryStack.stackPush()) {
            for (double duration : new double[]{0,6,30,120}) {
                var clip = new cn.minerealms.assimploader.model.AssimpAnimation(
                        org.lwjgl.assimp.AIAnimation.calloc(stack).mDuration(duration).mTicksPerSecond(30),0,
                        cn.minerealms.assimploader.util.CoordinateSystemConverter.CoordinateSystem.MINECRAFT_LEFT_HANDED);
                var player = new cn.minerealms.assimploader.animation.AnimationPlayer(clip);
                player.setLooping(false);player.setSpeed(2);player.play();
                player.synchronizePhase(.4);player.update(5);
                assertEquals(duration*.4,player.getCurrentTime(),1e-6);
                assertTrue(player.isPlaying());assertEquals(2,player.getSpeed());
                player.synchronizePhase(1);player.update(5);assertEquals(duration,player.getCurrentTime(),1e-6);
                player.synchronizePhase(.1);player.releaseSynchronizedPhase();player.update(.01);
                assertEquals(Math.min(duration,duration*.1+.6),player.getCurrentTime(),1e-6);
                player.synchronizePhase(.5);player.stop();assertFalse(player.hasSynchronizedPhase());
                assertThrows(IllegalArgumentException.class,()->player.synchronizePhase(Double.NaN));
                assertThrows(IllegalArgumentException.class,()->player.synchronizePhase(1.1));
            }
        }
    }

    @Test void priorityAndDirectionalFallbackDriveExistingStateBindingsWithoutAdvancingTime() {
        try (var instance = instance(); var controller = new PlayerAnimationController(profile(false), instance)) {
            var state = instance.animation();
            assertEquals(IDLE, controller.update(input(0).build()).selected());
            assertEquals("idle", state.getStateMachine().getCurrentStateName());
            var running = controller.update(input(1).motion(3, 0, 0).sprinting(true).build());
            assertEquals(RUN, running.requested());
            assertEquals(WALK, running.selected());
            assertEquals("walk", state.getStateMachine().getCurrentStateName());
            assertEquals(STRAFE_LEFT, controller.update(input(2).motion(0, -2, 0).build()).requested());
            assertEquals(WALK_BACKWARD, controller.update(input(3).motion(-2, 0, 0).build()).requested());
            assertEquals(CROUCH_WALK, controller.update(input(4).motion(2, 0, 0).crouching(true).build()).requested());
            assertEquals(ELYTRA, controller.update(input(5).elytra(true).swimming(true).flying(true).usingItem(true).build()).requested());
            assertEquals(RIDE, controller.update(input(6).riding(true).elytra(true).build()).requested());
            assertEquals(SLEEP, controller.update(input(7).sleeping(true).riding(true).build()).requested());
            assertEquals(DEAD, controller.update(input(8).dead(true).sleeping(true).swinging(true).build()).requested());
            assertEquals(USE_ITEM, controller.update(input(9).usingItem(true).swinging(true).build()).requested());
            assertEquals(FALL, controller.update(input(10).onGround(false).jumping(true).motion(0, 0, -1).build()).requested());
            assertEquals(0, state.getSimulationTime());
        }
    }

    @Test void swingConsumesEdgesOnceAndRestartsOnlyForANewEvent() {
        try (var instance = instance(); var controller = new PlayerAnimationController(profile(false), instance)) {
            var state = instance.animation();
            controller.update(input(0).build());
            assertEquals(SWING, controller.update(input(1).swinging(true).build()).selected());
            state.update(.2f);
            double progressed = state.getCurrentAnimation().getCurrentTime();
            assertTrue(progressed > 0);
            assertFalse(controller.update(input(2).swinging(true).build()).changed());
            assertFalse(controller.update(input(2).swinging(false).build()).accepted());
            assertFalse(controller.update(input(3).swinging(true).build()).changed());
            assertEquals(progressed, state.getCurrentAnimation().getCurrentTime());
            state.update(2);
            assertEquals(IDLE, controller.update(input(4).swinging(true).build()).selected());
            controller.update(input(5).swinging(false).build());
            assertEquals(SWING, controller.update(input(6).swinging(true).build()).selected());
            state.update(.2f);
            controller.update(input(7).swinging(false).build());
            assertTrue(controller.update(input(8).swinging(true).build()).changed());
            assertEquals(0, state.getCurrentAnimation().getCurrentTime());
        }
    }

    @Test void jumpDoesNotRestartWhileHeldAndStaleInputsCannotChangeSelection() {
        try (var instance = instance(); var controller = new PlayerAnimationController(profile(false), instance)) {
            var state = instance.animation();
            controller.update(input(0).build());
            assertEquals(JUMP, controller.update(input(1).jumping(true).build()).selected());
            state.update(.25f);
            double progressed = state.getCurrentAnimation().getCurrentTime();
            assertFalse(controller.update(input(2).onGround(false).motion(0, 0, 1).jumping(true).build()).changed());
            assertEquals(progressed, state.getCurrentAnimation().getCurrentTime());
            assertFalse(controller.update(input(1).dead(true).build()).accepted());
            assertFalse(controller.update(PlayerVisualInput.builder(3, 0).dead(true).build()).accepted());
            assertEquals(JUMP, controller.lastSelection().selected());
            controller.reset();
            assertTrue(controller.update(input(0).build()).accepted());
            assertEquals(IDLE, controller.lastSelection().selected());
        }
    }

    @Test void directClipBindingsAndCompletelyMissingStatesKeepAUsablePose() {
        var scene = scene();
        try (var state = new AnimationState("test", scene)) {
            var direct = CharacterProfile.read(new StringReader("""
                    {"version":1,"model":"example:avatar.glb","animations":{"idle":"Idle","walk":"Walk"}}
                    """));
            try (var controller = new PlayerAnimationController(direct, state)) {
                var selection = controller.update(input(0).motion(2, 0, 0).build());
                assertEquals(WALK, selection.selected());
                assertFalse(selection.stateBinding());
                assertEquals("Walk", state.getCurrentAnimation().getAnimation().getName());
            }
            assertFalse(state.isClosed());
            var missing = CharacterProfile.read(new StringReader("{\"version\":1,\"model\":\"example:avatar.glb\"}"));
            try (var controller = new PlayerAnimationController(missing, state)) {
                assertNull(controller.update(input(0).build()).selected());
                assertNotNull(state.getPoseSnapshot());
            }
        }
    }

    @Test void headLookUsesBodyRelativeAnglesClampsAndReleasesItsOwnModifier() {
        try (var instance = instance()) {
            var state = instance.animation();
            try (var controller = new PlayerAnimationController(profile(true), instance)) {
                assertTrue(controller.hasHeadLook());
                assertTrue(controller.update(input(0).orientation(350, 10, 0).build()).headLook());
                state.update(.05f);
                Vector3f actual = state.getPoseSnapshot().getNodeTransform("Head").transformDirection(new Vector3f(0, 0, -1)).normalize();
                Vector3f expected = new Vector3f(0, 0, -1).rotateY((float)Math.toRadians(-20));
                assertTrue(actual.distance(expected) < 1e-4, actual + " expected " + expected);
                controller.update(input(1).orientation(0, 100, 90).build());
                state.update(.05f);
                actual = state.getPoseSnapshot().getNodeTransform("Head").transformDirection(new Vector3f(0, 0, -1)).normalize();
                expected.set(0, 0, -1).rotateY((float)Math.toRadians(-45));
                Vector3f right = new Vector3f(0, 1, 0).cross(expected).normalize();
                expected.rotateAxis((float)Math.toRadians(30), right.x, right.y, right.z);
                assertTrue(actual.distance(expected) < 1e-4);
                assertFalse(controller.update(input(2).sleeping(true).build()).headLook());
                state.update(.05f);
                actual = state.getPoseSnapshot().getNodeTransform("Head").transformDirection(new Vector3f(0, 0, -1)).normalize();
                assertTrue(actual.distance(new Vector3f(0, 0, -1)) < 1e-4);
            }
            assertNull(state.getModifier("character:head-look"));
            assertFalse(state.isClosed());
        }
    }

    private static PlayerVisualInput.Builder input(long sequence) { return PlayerVisualInput.builder(sequence, sequence * .05); }
    private static CharacterProfile profile(boolean headLook) {
        return CharacterProfile.read(new StringReader("""
                {"version":1,"model":"example:avatar.glb","humanoid":{"head":"Head"},
                 "headLook":{"enabled":%s,"maxYaw":45,"maxPitch":30}}
                """.formatted(headLook)));
    }
    private static ModelInstance instance() {
        return ModelInstanceDefinition.read(new StringReader("""
                {"version":1,"model":"example:avatar.glb","initial":"idle","states":{
                 "idle":{"clip":"Idle","transition":0},"walk":{"clip":"Walk","transition":0},
                 "swing":{"clip":"Swing","loop":false,"transition":0},"jump":{"clip":"Jump","loop":false,"transition":0}}}
                """)).instantiate(scene());
    }
    private static AssimpScene scene() {
        ByteBuffer data = ByteBuffer.allocate(68).order(ByteOrder.LITTLE_ENDIAN);
        for (float value : new float[]{0, 0, 0, 1, 0, 0, 0, 1, 0, 0, 1, 0, 1, 0, 0, 1, 0}) data.putFloat(value);
        String encoded = Base64.getEncoder().encodeToString(data.array());
        String json = """
                {"asset":{"version":"2.0"},"buffers":[{"byteLength":68,"uri":"data:application/octet-stream;base64,%s"}],
                 "bufferViews":[{"buffer":0,"byteOffset":0,"byteLength":36},{"buffer":0,"byteOffset":36,"byteLength":8},
                 {"buffer":0,"byteOffset":44,"byteLength":24}],"accessors":[
                 {"bufferView":0,"componentType":5126,"count":3,"type":"VEC3","min":[0,0,0],"max":[1,1,0]},
                 {"bufferView":1,"componentType":5126,"count":2,"type":"SCALAR","min":[0],"max":[1]},
                 {"bufferView":2,"componentType":5126,"count":2,"type":"VEC3"}],
                 "meshes":[{"primitives":[{"attributes":{"POSITION":0}}]}],
                 "nodes":[{"name":"Root","children":[1]},{"name":"Head","mesh":0,"translation":[0,1,0]}],
                 "animations":[
                 {"name":"Idle","samplers":[{"input":1,"output":2}],"channels":[{"sampler":0,"target":{"node":1,"path":"translation"}}]},
                 {"name":"Walk","samplers":[{"input":1,"output":2}],"channels":[{"sampler":0,"target":{"node":1,"path":"translation"}}]},
                 {"name":"Swing","samplers":[{"input":1,"output":2}],"channels":[{"sampler":0,"target":{"node":1,"path":"translation"}}]},
                 {"name":"Jump","samplers":[{"input":1,"output":2}],"channels":[{"sampler":0,"target":{"node":1,"path":"translation"}}]}],
                 "scenes":[{"nodes":[0]}],"scene":0}
                """.formatted(encoded);
        var scene = new AssimpModelLoader().loadModelFromMemory("character-core.gltf", json.getBytes(StandardCharsets.UTF_8), ModelImportMode.DYNAMIC);
        assertNotNull(scene);
        return scene;
    }
}
