package cn.minerealms.assimploader.animation;

import cn.minerealms.assimploader.config.RuntimePerformancePolicy;
import cn.minerealms.assimploader.core.AssimpModelLoader;
import cn.minerealms.assimploader.core.ModelImportMode;
import cn.minerealms.assimploader.rendering.CpuSkinning;
import com.google.gson.JsonParser;
import org.joml.Matrix4f;
import org.joml.Vector3f;
import org.junit.jupiter.api.Test;

import java.util.Map;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;

class RenderPoseInterpolationTest {
    private static final RuntimePerformancePolicy QUALITY = RuntimePerformancePolicy.forMode(RuntimePerformancePolicy.Mode.QUALITY);
    private static final InstancePerformanceSettings INTERPOLATED = new InstancePerformanceSettings(1, true, true, true, true, 32, 4096, true);

    private AnimationState state(boolean interpolate) {
        AnimationState state = new AnimationState("interpolation", AnimationTestScenes.create());
        state.initialize();
        state.playAnimation("A");
        if (interpolate) state.setPerformanceSettings(INTERPOLATED);
        return state;
    }

    @Test void optInCachesRenderPoseWithoutChangingAuthoritativePoseOrSimulation() {
        try (AnimationState state = state(true); AnimationState other = state(true); AnimationState defaults = state(false)) {
            int[] modifiers = {0};
            state.addModifier("probe", (scene, locals, seconds) -> { if (seconds > 0) modifiers[0]++; });
            PoseSnapshot previous = state.getPoseSnapshot();
            state.update(.05f);
            other.update(.1f);
            defaults.update(.05f);
            PoseSnapshot authoritative = state.getPoseSnapshot();
            PoseSnapshot visual = state.getRenderPose(.5f, QUALITY);
            assertEquals(.025, x(visual), 1e-6);
            assertEquals(.05, x(authoritative), 1e-6);
            assertSame(visual, state.getRenderPose(.5f, QUALITY));
            assertSame(previous, state.getRenderPose(0, QUALITY));
            assertSame(visual, state.getRenderPose(.5f, QUALITY));
            PoseSnapshot later = state.getRenderPose(.75f, QUALITY);
            assertNotSame(visual, later);
            assertNotEquals(visual.getFrameId(), later.getFrameId());
            assertSame(authoritative, state.getPoseSnapshot());
            assertEquals(.05, state.getSimulationTime(), 1e-6);
            assertEquals(1, modifiers[0]);
            assertEquals(.025, x(other.getRenderPose(.5f, QUALITY)), 1e-6);
            assertNotSame(visual, other.getRenderPose(.5f, QUALITY));
            assertSame(defaults.getPoseSnapshot(), defaults.getRenderPose(.5f, QUALITY));
            assertTrue(visual.getFrameId() < 0);
            assertEquals(3, visual.copyMeshInstances().size());
            assertEquals(-50, visual.copyMeshInstances().get(1).transform().m30(), 1e-6);
            assertEquals(50, visual.copyMeshInstances().get(2).transform().m30(), 1e-6);
        }
    }

    @Test void throttledCadenceProgressesAcrossEverySkippedTick() {
        var manager = new ClientAnimationManager(resource -> Optional.empty());
        Object owner = new Object();
        try {
            var state = manager.getOrCreateAnimationState(owner, "cadence", AnimationTestScenes.create());
            state.playAnimation("A");
            state.setPerformanceSettings(new InstancePerformanceSettings(4, true, true, true, true, 32, 4096, true));
            for (int i = 0; i < 4; i++) manager.updateManagedTick(QUALITY);
            assertEquals(.2, state.getSimulationTime(), 1e-6);
            PoseSnapshot authoritative = state.getPoseSnapshot();
            assertEquals(.025, x(state.getRenderPose(.5f, QUALITY)), 1e-6);
            for (int tick = 1; tick <= 3; tick++) {
                manager.updateManagedTick(QUALITY);
                assertSame(authoritative, state.getPoseSnapshot());
                assertEquals(.05 * (tick + .5), x(state.getRenderPose(.5f, QUALITY)), 1e-6);
            }
            manager.updateManagedTick(QUALITY);
            assertEquals(.225, x(state.getRenderPose(.5f, QUALITY)), 1e-6);
            assertEquals(.4, state.getSimulationTime(), 1e-6);
        } finally { manager.clear(); }
    }

    @Test void stateEditsPauseTeleportAndPolicySwitchDiscardHistory() {
        try (AnimationState state = state(true)) {
            state.update(.05f);
            assertNotSame(state.getPoseSnapshot(), state.getRenderPose(.5f, QUALITY));
            state.pause();
            assertSame(state.getPoseSnapshot(), state.getRenderPose(.5f, QUALITY));
            state.resume();
            assertSame(state.getPoseSnapshot(), state.getRenderPose(.5f, QUALITY));
            state.update(.05f);
            state.resetDynamics();
            assertSame(state.getPoseSnapshot(), state.getRenderPose(.5f, QUALITY));
            state.update(.05f);
            state.invalidateCache();
            assertSame(state.getPoseSnapshot(), state.getRenderPose(.5f, QUALITY));
            state.update(.05f);
            state.getRenderPose(.5f, RuntimePerformancePolicy.forMode(RuntimePerformancePolicy.Mode.PERFORMANCE));
            assertSame(state.getPoseSnapshot(), state.getRenderPose(.5f, QUALITY));
            state.update(.05f);
            state.playAnimation("B");
            assertSame(state.getPoseSnapshot(), state.getRenderPose(.5f, QUALITY));
            assertThrows(IllegalArgumentException.class, () -> state.getRenderPose(Float.NaN, QUALITY));
        }
        AnimationState closed = state(true);
        closed.update(.05f);
        closed.getRenderPose(.5f, QUALITY);
        closed.close();
        assertNull(closed.getRenderPose(.5f, QUALITY));
    }

    @Test void rotationsFollowShortestArcAndBoundsIncludeIntermediateGeometry() {
        var scene = MorphTargetTest.fixture();
        var mesh = scene.getMeshes().get(0);
        PoseSnapshot a = PoseSnapshot.capture(1, scene, Map.of("MorphNode", new Matrix4f().rotationY((float) -Math.PI / 4)), Map.of(mesh, new float[]{0}));
        PoseSnapshot b = PoseSnapshot.capture(2, scene, Map.of("MorphNode", new Matrix4f().rotationY((float) Math.PI / 4)), Map.of(mesh, new float[]{0}));
        PoseSnapshot middle = PoseSnapshot.interpolate(-1, a, b, .5f);
        assertEquals(1, middle.getBounds().maxX, 1e-5);
        assertTrue(middle.getBounds().maxX > Math.max(a.getBounds().maxX, b.getBounds().maxX) + .2);
        assertVerticesInBounds(middle, mesh);
        PoseSnapshot nearPi = PoseSnapshot.capture(3, scene, Map.of("MorphNode", new Matrix4f().rotationZ((float) Math.toRadians(170))));
        PoseSnapshot nearMinusPi = PoseSnapshot.capture(4, scene, Map.of("MorphNode", new Matrix4f().rotationZ((float) Math.toRadians(-170))));
        Matrix4f rotation = PoseSnapshot.interpolate(-2, nearPi, nearMinusPi, .5f).getNodeTransform("MorphNode");
        Vector3f direction = rotation.transformDirection(new Vector3f(1, 0, 0));
        assertEquals(-1, direction.x, 1e-5);
        assertEquals(0, direction.y, 1e-5);
        assertSame(a, PoseSnapshot.interpolate(-3, a, b, 0));
        assertSame(b, PoseSnapshot.interpolate(-3, a, b, 1));
    }

    @Test void morphsBlendBeforeGeometryAndBoundsWithoutMutatingInputs() {
        var scene = MorphTargetTest.fixture();
        var mesh = scene.getMeshes().get(0);
        PoseSnapshot a = PoseSnapshot.capture(1, scene, Map.of(), Map.of(mesh, new float[]{0}));
        PoseSnapshot b = PoseSnapshot.capture(2, scene, Map.of(), Map.of(mesh, new float[]{1}));
        PoseSnapshot mixed = PoseSnapshot.interpolate(-1, a, b, .25f);
        assertArrayEquals(new float[]{.25f}, mixed.getMorphWeights(mesh), 1e-6f);
        assertEquals(.5, CpuSkinning.geometry(mixed, mesh).positions()[0], 1e-6);
        assertEquals(4.5, mixed.getBounds().maxX, 1e-6);
        assertVerticesInBounds(mixed, mesh);
        assertArrayEquals(new float[]{0}, a.getMorphWeights(mesh));
        assertArrayEquals(new float[]{1}, b.getMorphWeights(mesh));
        try (AnimationState state = new AnimationState("morph-render", scene)) {
            state.initialize();
            state.setPerformanceSettings(INTERPOLATED);
            state.update(.05f);
            assertNotSame(state.getPoseSnapshot(), state.getRenderPose(.5f, QUALITY));
            state.setMorphWeights(mesh, new float[]{.75f});
            assertSame(state.getPoseSnapshot(), state.getRenderPose(.5f, QUALITY));
            assertArrayEquals(new float[]{.75f}, state.getRenderPose(.5f, QUALITY).getMorphWeights(mesh));
        }
    }

    @Test void changingShearAndDegenerateScaleSnapWithoutInvalidRotations() {
        var scene = MorphTargetTest.fixture();
        Matrix4f shear = new Matrix4f().m10(.5f);
        Matrix4f singular = new Matrix4f().scale(0, 1, 1);
        PoseSnapshot a = PoseSnapshot.capture(1, scene, Map.of("MorphNode", shear));
        PoseSnapshot b = PoseSnapshot.capture(2, scene, Map.of("MorphNode", singular));
        Matrix4f result = PoseSnapshot.interpolate(-1, a, b, .5f).getNodeTransform("MorphNode");
        assertTrue(result.isFinite());
        assertTrue(result.equals(singular, 1e-6f));
        assertThrows(IllegalArgumentException.class, () -> PoseSnapshot.interpolate(-1, a, b, Float.NaN));
        assertThrows(IllegalArgumentException.class, () -> PoseSnapshot.interpolate(-1, a,
                PoseSnapshot.capture(1, AnimationTestScenes.create(), Map.of()), .5f));
    }

    @Test void reflectedNonUniformScaleStillInterpolatesAndKeepsSnapshotInputsIndependent() {
        var scene = MorphTargetTest.fixture();
        Matrix4f from = new Matrix4f().translation(2, 3, 4).rotateY(.3f).scale(-2, 3, .5f);
        Matrix4f to = new Matrix4f().translation(6, 5, 2).rotateY(.8f).scale(-4, 1, 2);
        PoseSnapshot a = PoseSnapshot.capture(1, scene, Map.of("MorphNode", from));
        PoseSnapshot b = PoseSnapshot.capture(2, scene, Map.of("MorphNode", to));
        Matrix4f expected = AnimationBlender.blendTransforms(from, to, .25f);
        from.zero();
        to.zero();
        Matrix4f actual = PoseSnapshot.interpolate(-1, a, b, .25f).getNodeTransform("MorphNode");
        assertTrue(expected.equals(actual, 1e-5f));
        assertEquals(3, actual.m30(), 1e-5f);
        assertTrue(actual.isFinite());
    }

    @Test void blenderSkinAndMorphIntermediatePalettesKeepEveryVertexInsideBounds() throws Exception {
        String resource = "/assets/assimploader/models/validation/animation_probe_morph.glb";
        try (var input = getClass().getResourceAsStream(resource)) {
            assertNotNull(input);
            var scene = new AssimpModelLoader().loadModelFromMemory(resource, input.readAllBytes(), ModelImportMode.DYNAMIC);
            assertNotNull(scene);
            var mesh = scene.getMeshes().stream().filter(candidate -> candidate.hasMorphTargets()).findFirst().orElseThrow();
            assertTrue(mesh.hasBones());
            PoseSnapshot a = PoseSnapshot.capture(1, scene, Map.of(), Map.of(mesh, new float[]{0}));
            Matrix4f shoulder = new Matrix4f(a.copyLocalTransforms().get("Shoulder")).rotateZ(.8f);
            PoseSnapshot b = PoseSnapshot.capture(2, scene, Map.of("Shoulder", shoulder), Map.of(mesh, new float[]{1}));
            PoseSnapshot mixed = PoseSnapshot.interpolate(-1, a, b, .5f);
            assertArrayEquals(new float[]{.5f}, mixed.getMorphWeights(mesh));
            assertEquals(mesh.getBoneOffsets().size(), mixed.getBoneTransforms(mesh).length);
            assertVerticesInBounds(mixed, mesh);
            assertNotEquals(a.getBounds(), mixed.getBounds());
            assertArrayEquals(new float[]{0}, a.getMorphWeights(mesh));
        }
    }

    @Test void configurationRequiresExplicitBooleanOptIn() {
        assertFalse(InstancePerformanceSettings.DEFAULT.renderInterpolation());
        assertFalse(InstancePerformanceSettings.read(JsonParser.parseString("{}").getAsJsonObject()).renderInterpolation());
        assertTrue(InstancePerformanceSettings.read(JsonParser.parseString("{\"renderInterpolation\":true}").getAsJsonObject()).renderInterpolation());
        assertThrows(IllegalArgumentException.class, () -> InstancePerformanceSettings.read(
                JsonParser.parseString("{\"renderInterpolation\":1}").getAsJsonObject()));
    }

    private static float x(PoseSnapshot pose) { return pose.getNodeTransform("Joint").m30(); }

    private static void assertVerticesInBounds(PoseSnapshot pose, cn.minerealms.assimploader.model.AssimpMesh mesh) {
        float[] positions = CpuSkinning.geometry(pose, mesh).positions();
        var transform = mesh.hasBones() ? new Matrix4f() : pose.copyMeshInstances().stream()
                .filter(instance -> instance.mesh() == mesh).findFirst().orElseThrow().transform();
        var bounds = pose.getBounds().inflate(1e-5);
        for (int i = 0; i < positions.length; i += 3) {
            Vector3f point = transform.transformPosition(new Vector3f(positions[i], positions[i + 1], positions[i + 2]));
            assertTrue(bounds.contains(point.x, point.y, point.z), "Vertex outside interpolated bounds: " + point);
        }
    }
}
