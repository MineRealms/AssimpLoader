package cn.minerealms.assimploader.animation;

import cn.minerealms.assimploader.config.RuntimePerformancePolicy;
import org.junit.jupiter.api.Test;

import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;

class AnimationCadenceTest {
    private static final RuntimePerformancePolicy PERFORMANCE =
            RuntimePerformancePolicy.forMode(RuntimePerformancePolicy.Mode.PERFORMANCE);

    @Test void unseenNearFarAndHiddenHaveBoundedIntervals() {
        AnimationCadence cadence = new AnimationCadence(0);
        assertEquals(1, cadence.interval(100, PERFORMANCE, 1));
        cadence.rendered(100, 32 * 32);
        assertEquals(4, cadence.interval(100, PERFORMANCE, 1));
        assertEquals(4, cadence.interval(120, PERFORMANCE, 1));
        assertEquals(20, cadence.interval(121, PERFORMANCE, 1));
        cadence.rendered(122, 1);
        assertEquals(1, cadence.interval(122, PERFORMANCE, 1));
        assertEquals(6, cadence.interval(122, PERFORMANCE, 6));
    }

    @Test void nearestPassWinsOnlyWithinTheSameTick() {
        AnimationCadence cadence = new AnimationCadence(0);
        cadence.rendered(1, 10_000);
        cadence.rendered(1, 4);
        cadence.rendered(1, 20_000);
        assertEquals(1, cadence.interval(1, PERFORMANCE, 1));
        cadence.rendered(2, 10_000);
        assertEquals(4, cadence.interval(2, PERFORMANCE, 1));
        assertThrows(IllegalArgumentException.class, () -> cadence.rendered(2, -1));
        assertThrows(IllegalArgumentException.class, () -> cadence.rendered(2, Double.NaN));
        assertThrows(IllegalArgumentException.class, () -> cadence.rendered(2, Double.POSITIVE_INFINITY));
    }

    @Test void staggeringDistributesWorkAndRetainsAllPlaybackSeconds() {
        AnimationCadence[] cadences = new AnimationCadence[4];
        double[] elapsed = new double[4];
        for (int i = 0; i < cadences.length; i++) cadences[i] = new AnimationCadence(i);
        for (int tick = 1; tick <= 20; tick++) {
            int evaluations = 0;
            for (int i = 0; i < cadences.length; i++) {
                cadences[i].rendered(tick, 10_000);
                float seconds = cadences[i].step(tick, .05f, PERFORMANCE, 1, false);
                elapsed[i] += seconds;
                if (seconds > 0) evaluations++;
            }
            assertEquals(1, evaluations, "far evaluations should be spread across ticks");
        }
        for (int i = 0; i < cadences.length; i++) {
            assertTrue(elapsed[i] <= 1.000001 && elapsed[i] >= .85);
            cadences[i].rendered(21, 0);
            elapsed[i] += cadences[i].step(21, 0, PERFORMANCE, 1, false);
            assertEquals(1, elapsed[i], 1e-6, "returning near must flush retained time");
        }
    }

    @Test void pauseDiscardsPendingTimeBeforeResume() {
        AnimationCadence cadence = new AnimationCadence(0);
        cadence.rendered(0, 10_000);
        assertEquals(0, cadence.step(1, .05f, PERFORMANCE, 1, false));
        assertEquals(0, cadence.step(2, .05f, PERFORMANCE, 1, false));
        assertEquals(0, cadence.step(3, .05f, PERFORMANCE, 1, true));
        assertEquals(.05, cadence.step(4, .05f, PERFORMANCE, 1, false), 1e-6);
    }

    @Test void managedFarOwnerEvaluatesLessWithoutSlowingItsClock() {
        var manager = new ClientAnimationManager(resource -> Optional.empty());
        Object owner = new Object();
        try {
            var state = manager.getOrCreateAnimationState(owner, "model", AnimationTestScenes.create());
            PoseSnapshot before = state.getPoseSnapshot();
            for (int i = 0; i < 4; i++) manager.markRendered(state, 10_000);
            assertSame(before, state.getPoseSnapshot());
            assertEquals(0, manager.getPerformanceStatistics().totalEvaluated());
            for (int i = 0; i < 20; i++) {
                manager.markRendered(state, 10_000);
                manager.updateManagedTick(PERFORMANCE);
            }
            assertEquals(1, state.getSimulationTime(), 1e-6);
            assertEquals(5, manager.getPerformanceStatistics().totalEvaluated());
            assertEquals(15, manager.getPerformanceStatistics().totalSkipped());
            assertEquals(PERFORMANCE.mode(), manager.getPerformanceStatistics().mode());
            manager.release(owner);
            assertTrue(state.isClosed());
            assertEquals(0, manager.getPerformanceStatistics().instances());
            manager.clear();
            assertEquals(0, manager.getPerformanceStatistics().totalEvaluated());
            assertEquals(0, manager.getPerformanceStatistics().totalSkipped());
        } finally { manager.clear(); }
    }

    @Test void explicitUpdateDiscardsSchedulerDebtAndPausedOwnersDoNotCatchUp() {
        var manager = new ClientAnimationManager(resource -> Optional.empty());
        Object owner = new Object();
        try {
            var state = manager.getOrCreateAnimationState(owner, "model", AnimationTestScenes.create());
            manager.markRendered(state, 10_000);
            manager.updateManagedTick(PERFORMANCE);
            assertEquals(0, state.getSimulationTime());
            manager.update(.5f);
            for (int i = 0; i < 3; i++) manager.updateManagedTick(PERFORMANCE);
            assertEquals(.65, state.getSimulationTime(), 1e-6);
            manager.updateManagedTick(PERFORMANCE);
            state.pause();
            manager.updateManagedTick(PERFORMANCE);
            assertEquals(1, manager.getPerformanceStatistics().paused());
            state.resume();
            manager.updateManagedTick(PERFORMANCE);
            manager.updateManagedTick(PERFORMANCE);
            assertEquals(.75, state.getSimulationTime(), 1e-6);
        } finally { manager.clear(); }
    }

    @Test void standaloneManagedStateAlsoHonorsItsAssetBudget() {
        var manager = new ClientAnimationManager(resource -> Optional.empty());
        Object owner = new Object();
        try {
            var state = manager.getOrCreateAnimationState(owner, "model", AnimationTestScenes.create());
            state.setPerformanceSettings(new InstancePerformanceSettings(4, true, true, true, true, 32, 4096));
            for (int i = 0; i < 4; i++) {
                manager.updateManagedTick(RuntimePerformancePolicy.forMode(RuntimePerformancePolicy.Mode.QUALITY));
            }
            assertEquals(.2, state.getSimulationTime(), 1e-6);
            assertEquals(1, manager.getPerformanceStatistics().totalEvaluated());
        } finally { manager.clear(); }
    }
}
