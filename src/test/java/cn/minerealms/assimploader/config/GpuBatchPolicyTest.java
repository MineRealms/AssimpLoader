package cn.minerealms.assimploader.config;

import org.junit.jupiter.api.Test;

import static cn.minerealms.assimploader.config.GpuBatchPolicy.Mode.*;
import static org.junit.jupiter.api.Assertions.*;

class GpuBatchPolicyTest {
    @Test void defaultsKeepBatchingAndOcclusionOffAndPublishOneSnapshot() {
        var defaults = GpuBatchPolicy.defaults();
        assertEquals(OFF, defaults.mode());
        assertFalse(defaults.enabled());
        assertFalse(defaults.usesHiZ());
        assertEquals(4096, defaults.maxInstances());
        assertEquals(128L * 1024 * 1024, defaults.geometryBytes());
        assertEquals(32L * 1024 * 1024, defaults.frameBytes());
        assertSame(GpuBatchPolicy.current(), GpuBatchPolicy.current());
    }

    @Test void hiZRequiresMdiAndExplicitOptIn() {
        assertFalse(policy(OFF, true).usesHiZ());
        assertFalse(policy(INSTANCED, true).usesHiZ());
        assertFalse(policy(MDI, false).usesHiZ());
        assertTrue(policy(MDI, true).usesHiZ());
        assertTrue(policy(INSTANCED, false).enabled());
    }

    @Test void rejectsNonFiniteToleranceAndStorageOutsideHardBounds() {
        assertThrows(NullPointerException.class, () -> policy(null, false));
        assertThrows(IllegalArgumentException.class, () -> new GpuBatchPolicy(MDI, 0, 128, 32, true, 1024, .0005));
        assertThrows(IllegalArgumentException.class, () -> new GpuBatchPolicy(MDI, 16385, 128, 32, true, 1024, .0005));
        assertThrows(IllegalArgumentException.class, () -> new GpuBatchPolicy(MDI, 1, 257, 32, true, 1024, .0005));
        assertThrows(IllegalArgumentException.class, () -> new GpuBatchPolicy(MDI, 1, 128, 0, true, 1024, .0005));
        assertThrows(IllegalArgumentException.class, () -> new GpuBatchPolicy(MDI, 1, 128, 129, true, 1024, .0005));
        assertThrows(IllegalArgumentException.class, () -> new GpuBatchPolicy(MDI, 1, 128, 32, true, 63, .0005));
        assertThrows(IllegalArgumentException.class, () -> new GpuBatchPolicy(MDI, 1, 128, 32, true, 2049, .0005));
        for (double tolerance : new double[]{Double.NaN, Double.NEGATIVE_INFINITY, Double.POSITIVE_INFINITY, -.0001, .05001})
            assertThrows(IllegalArgumentException.class, () -> new GpuBatchPolicy(MDI, 1, 128, 32, true, 1024, tolerance));
        assertEquals(256L * 1024 * 1024, new GpuBatchPolicy(MDI, 16384, 256, 128, true, 2048, .05).geometryBytes());
        assertEquals(0, new GpuBatchPolicy(MDI, 1, 1, 1, true, 64, 0).hiZBias());
    }

    private static GpuBatchPolicy policy(GpuBatchPolicy.Mode mode, boolean hiZ) {
        return new GpuBatchPolicy(mode, 4096, 128, 32, hiZ, 1024, .0005);
    }
}
