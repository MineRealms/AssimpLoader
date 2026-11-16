package cn.minerealms.assimploader.config;

import org.junit.jupiter.api.Test;

import static cn.minerealms.assimploader.config.RuntimePerformancePolicy.Mode.*;
import static org.junit.jupiter.api.Assertions.*;

class RuntimePerformancePolicyTest {
    @Test
    void qualityKeepsTheOriginalTickCadenceAndPresetsBoundBackgroundWork() {
        var quality = RuntimePerformancePolicy.forMode(QUALITY);
        var balanced = RuntimePerformancePolicy.forMode(BALANCED);
        var performance = RuntimePerformancePolicy.forMode(PERFORMANCE);
        assertEquals(1, quality.nearUpdateTicks());
        assertEquals(1, quality.farUpdateTicks());
        assertEquals(1, quality.hiddenUpdateTicks());
        assertEquals(1, balanced.nearUpdateTicks());
        assertEquals(1, performance.nearUpdateTicks());
        assertTrue(performance.farUpdateTicks() > balanced.farUpdateTicks());
        assertTrue(performance.hiddenUpdateTicks() > balanced.hiddenUpdateTicks());
        assertTrue(performance.farDistance() < balanced.farDistance());
        assertTrue(performance.maxParticleEmissions() < balanced.maxParticleEmissions());
        assertTrue(performance.maxTrailSamples() < balanced.maxTrailSamples());
        for (var mode : RuntimePerformancePolicy.Mode.values()) {
            var policy = RuntimePerformancePolicy.forMode(mode);
            assertEquals(mode, policy.mode());
            assertTrue(policy.ikEnabled() && policy.springBonesEnabled());
            assertTrue(policy.particlesEnabled() && policy.trailsEnabled());
            assertTrue(policy.permitsGpuMorph(32, 128L * 1024 * 1024));
        }
    }

    @Test
    void gpuMorphBudgetIsInclusiveAndRejectsDisabledOversizedAndInvalidRequests() {
        var bounded = custom(1, 2, 64, 10, 16, 128, true, 4, 2);
        assertTrue(bounded.permitsGpuMorph(4, 2L * 1024 * 1024));
        assertFalse(bounded.permitsGpuMorph(5, 1));
        assertFalse(bounded.permitsGpuMorph(1, 2L * 1024 * 1024 + 1));
        assertFalse(bounded.permitsGpuMorph(1, Long.MAX_VALUE));
        assertFalse(bounded.permitsGpuMorph(0, 1));
        assertFalse(bounded.permitsGpuMorph(1, 0));
        assertFalse(bounded.permitsGpuMorph(1, -1));
        assertFalse(custom(1, 2, 64, 10, 16, 128, false, 4, 2).permitsGpuMorph(1, 1));
    }

    @Test
    void directPoliciesRejectInvalidCadenceAndUnboundedStorage() {
        assertThrows(IllegalArgumentException.class, () -> custom(0, 2, 64, 10, 16, 128, true, 32, 128));
        assertThrows(IllegalArgumentException.class, () -> custom(4, 2, 64, 10, 16, 128, true, 32, 128));
        assertThrows(IllegalArgumentException.class, () -> custom(4, 4, 64, 2, 16, 128, true, 32, 128));
        assertThrows(IllegalArgumentException.class, () -> custom(1, 2, Double.NaN, 10, 16, 128, true, 32, 128));
        assertThrows(IllegalArgumentException.class, () -> custom(1, 2, Double.POSITIVE_INFINITY, 10, 16, 128, true, 32, 128));
        assertThrows(IllegalArgumentException.class, () -> custom(1, 2, 64, 10, 33, 128, true, 32, 128));
        assertThrows(IllegalArgumentException.class, () -> custom(1, 2, 64, 10, 16, 4097, true, 32, 128));
        assertThrows(IllegalArgumentException.class, () -> custom(1, 2, 64, 10, 16, 128, true, 33, 128));
        assertThrows(IllegalArgumentException.class, () -> custom(1, 2, 64, 10, 16, 128, true, 32, 129));
        var zeroEmissions = custom(20, 20, 4096, 20, 0, 2, true, 1, 1);
        assertEquals(0, zeroEmissions.maxParticleEmissions());
    }

    @Test
    void currentReturnsThePublishedSnapshotWithoutRecreatingIt() {
        assertSame(RuntimePerformancePolicy.current(), RuntimePerformancePolicy.current());
        assertThrows(NullPointerException.class, () -> RuntimePerformancePolicy.forMode(null));
    }

    private static RuntimePerformancePolicy custom(int near, int far, double distance, int hidden,
            int emissions, int samples, boolean gpuMorph, int targets, int memory) {
        return new RuntimePerformancePolicy(CUSTOM, near, far, distance, hidden, 40,
                true, true, true, true, emissions, samples, gpuMorph, targets, memory);
    }
}
