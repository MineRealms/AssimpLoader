package cn.minerealms.assimploader.animation;

import cn.minerealms.assimploader.config.RuntimePerformancePolicy;
import cn.minerealms.assimploader.model.AssimpScene;
import org.joml.Matrix4f;
import org.junit.jupiter.api.Test;

import java.io.StringReader;
import java.lang.reflect.Field;
import java.util.Map;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;

class InstancePerformanceTest {
    @Test void sidecarSettingsRemainIndependentAndControlManagedCadence() {
        ModelInstanceDefinition definition = definition("""
                {"updateIntervalTicks":4,"ik":false,"springBones":false,
                 "particles":false,"trails":false,"maxParticleEmissions":3,"maxTrailSamples":3}
                """);
        var scene = AnimationTestScenes.create();
        try (ModelInstance first = definition.instantiate(scene); ModelInstance second = definition.instantiate(scene)) {
            assertEquals(4, first.performance().updateIntervalTicks());
            assertFalse(first.performance().ik());
            assertFalse(first.performance().springBones());
            assertFalse(first.performance().particles());
            assertFalse(first.performance().trails());
            first.animation().setPerformanceSettings(InstancePerformanceSettings.DEFAULT);
            assertEquals(1, first.performance().updateIntervalTicks());
            assertEquals(4, second.performance().updateIntervalTicks());
        }
        var manager = new ClientAnimationManager(resource -> Optional.of(definition));
        Object owner = new Object();
        try {
            var state = manager.getOrCreateAnimationState(owner, "model", scene);
            for (int i = 0; i < 4; i++) manager.updateManagedTick(RuntimePerformancePolicy.forMode(RuntimePerformancePolicy.Mode.QUALITY));
            assertEquals(.2, state.getSimulationTime(), 1e-6);
            assertEquals(1, manager.getPerformanceStatistics().totalEvaluated());
            state.setPerformanceSettings(InstancePerformanceSettings.DEFAULT);
            manager.updateManagedTick(RuntimePerformancePolicy.forMode(RuntimePerformancePolicy.Mode.QUALITY));
            assertEquals(.25, state.getSimulationTime(), 1e-6);
        } finally { manager.clear(); }
    }

    @Test void sidecarRejectsUnknownNonIntegralAndOutOfRangeBudgets() {
        for (String settings : new String[]{
                "{\"updateIntervalTicks\":0}", "{\"updateIntervalTicks\":21}",
                "{\"updateIntervalTicks\":1.5}", "{\"maxParticleEmissions\":33}",
                "{\"maxParticleEmissions\":-1}", "{\"maxTrailSamples\":1}",
                "{\"maxTrailSamples\":4097}", "{\"maxTrailSamples\":2.5}", "{\"typo\":1}"}) {
            assertThrows(IllegalArgumentException.class, () -> definition(settings), settings);
        }
        try (ModelInstance instance = definition("{}").instantiate(AnimationTestScenes.create())) {
            assertEquals(InstancePerformanceSettings.DEFAULT, instance.performance());
        }
    }

    @Test void assetAndClientFeatureGatesBothApplyWhileGeneralModifiersContinue() throws Exception {
        try (PolicyScope ignored = new PolicyScope(policy(false, false, false, false, 2, 2));
             ModelInstance instance = definition("{}").instantiate(AnimationTestScenes.create())) {
            ProbeModifier ik = new ProbeModifier(PoseModifier.Category.IK);
            ProbeModifier spring = new ProbeModifier(PoseModifier.Category.SPRING);
            ProbeModifier general = new ProbeModifier(PoseModifier.Category.GENERAL);
            instance.animation().addModifier("ik", ik);
            instance.animation().addModifier("spring", spring);
            instance.animation().addModifier("general", general);
            instance.animation().update(.05f);
            assertEquals(0, ik.updates);
            assertEquals(0, spring.updates);
            assertEquals(1, general.updates);
        }
        try (PolicyScope ignored = new PolicyScope(RuntimePerformancePolicy.forMode(RuntimePerformancePolicy.Mode.QUALITY));
             ModelInstance instance = definition("{\"ik\":false,\"springBones\":false}")
                     .instantiate(AnimationTestScenes.create())) {
            ProbeModifier ik = new ProbeModifier(PoseModifier.Category.IK);
            ProbeModifier spring = new ProbeModifier(PoseModifier.Category.SPRING);
            instance.animation().addModifier("ik", ik);
            instance.animation().addModifier("spring", spring);
            instance.animation().update(.05f);
            assertEquals(0, ik.updates);
            assertEquals(0, spring.updates);
            instance.animation().setPerformanceSettings(InstancePerformanceSettings.DEFAULT);
            instance.animation().update(.05f);
            assertEquals(1, ik.updates);
            assertEquals(1, spring.updates);
        }
    }

    @Test void clientAndAssetBudgetsBoundAggregateParticlesAndTrailHistory() throws Exception {
        try (PolicyScope ignored = new PolicyScope(policy(true, true, true, true, 2, 2));
             ModelInstance instance = effects("{\"maxParticleEmissions\":3,\"maxTrailSamples\":3}")
                     .instantiate(AnimationTestScenes.create())) {
            int[] particles = {0};
            instance.setParticleSink((name, point, velocity) -> particles[0]++);
            for (int i = 0; i < 4; i++) instance.animation().update(.1f);
            assertEquals(8, particles[0], "budget is shared across all emitters in an instance update");
            assertEquals(2, instance.trails().get("blade").samples().size());
            instance.animation().setPerformanceSettings(new InstancePerformanceSettings(1, true, true, false, false, 3, 3));
            instance.animation().update(.1f);
            assertEquals(8, particles[0]);
            assertTrue(instance.trails().get("blade").samples().isEmpty());
        }
        try (PolicyScope ignored = new PolicyScope(RuntimePerformancePolicy.forMode(RuntimePerformancePolicy.Mode.QUALITY));
             ModelInstance instance = effects("{\"maxParticleEmissions\":3,\"maxTrailSamples\":3}")
                     .instantiate(AnimationTestScenes.create())) {
            int[] particles = {0};
            instance.setParticleSink((name, point, velocity) -> particles[0]++);
            for (int i = 0; i < 4; i++) instance.animation().update(.1f);
            assertEquals(12, particles[0]);
            assertEquals(3, instance.trails().get("blade").samples().size());
        }
        try (PolicyScope ignored = new PolicyScope(policy(true, true, false, false, 2, 2));
             ModelInstance instance = effects("{\"particles\":true,\"trails\":true}")
                     .instantiate(AnimationTestScenes.create())) {
            int[] particles = {0};
            instance.setParticleSink((name, point, velocity) -> particles[0]++);
            instance.animation().update(.1f);
            assertEquals(0, particles[0]);
            assertTrue(instance.trails().get("blade").samples().isEmpty());
        }
    }

    private static ModelInstanceDefinition definition(String performance) {
        return ModelInstanceDefinition.read(new StringReader("""
                {"version":1,"model":"test:model","performance":%s}
                """.formatted(performance)));
    }

    private static ModelInstanceDefinition effects(String performance) {
        return ModelInstanceDefinition.read(new StringReader("""
                {"version":1,"model":"test:model","performance":%s,
                 "attachments":{"a":{"node":"Joint"},"b":{"node":"Joint","translation":[0,1,0]}},
                 "trails":{"blade":{"start":"a","end":"b","capacity":32,"lifetime":2}},
                 "particles":{"first":{"attachment":"a","rate":1000},"second":{"attachment":"b","rate":1000}}}
                """.formatted(performance)));
    }

    private static RuntimePerformancePolicy policy(boolean ik, boolean spring, boolean particles, boolean trails,
                                                    int maxParticles, int maxSamples) {
        return new RuntimePerformancePolicy(RuntimePerformancePolicy.Mode.CUSTOM, 1, 1, 64, 1, 40,
                ik, spring, particles, trails, maxParticles, maxSamples, true, 32, 128);
    }

    private static final class ProbeModifier implements PoseModifier {
        private final Category category;
        private int updates;
        private ProbeModifier(Category category) { this.category = category; }
        @Override public Category category() { return category; }
        @Override public void apply(AssimpScene scene, Map<String, Matrix4f> locals, double deltaSeconds) {
            if (deltaSeconds > 0) updates++;
        }
    }

    private static final class PolicyScope implements AutoCloseable {
        private final Field field;
        private final RuntimePerformancePolicy previous;
        private PolicyScope(RuntimePerformancePolicy value) throws ReflectiveOperationException {
            field = RuntimePerformancePolicy.class.getDeclaredField("current");
            field.setAccessible(true);
            previous = (RuntimePerformancePolicy) field.get(null);
            field.set(null, value);
        }
        @Override public void close() throws IllegalAccessException { field.set(null, previous); }
    }
}
