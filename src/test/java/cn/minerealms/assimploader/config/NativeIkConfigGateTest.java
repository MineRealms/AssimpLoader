package cn.minerealms.assimploader.config;

import org.junit.jupiter.api.Test;

import static cn.minerealms.assimploader.config.RuntimePerformancePolicy.Mode.*;
import static org.junit.jupiter.api.Assertions.*;

class NativeIkConfigGateTest {
    @Test
    void nativeIkIsOptInForEveryPreset() {
        for (var mode : RuntimePerformancePolicy.Mode.values()) {
            assertFalse(RuntimePerformancePolicy.forMode(mode).nativeIkEnabled(),
                    "nativeIkEnabled must default to false for " + mode);
        }
        assertFalse(RuntimePerformancePolicy.current().nativeIkEnabled());
    }

    @Test
    void nativeIkCanBeEnabledAndSurvivesCopy() {
        var enabled = RuntimePerformancePolicy.forMode(BALANCED).withNativeIk(true);
        assertTrue(enabled.nativeIkEnabled());
        assertTrue(RuntimePerformancePolicy.forMode(PERFORMANCE).withNativeIk(true).ikEnabled());
        assertFalse(enabled.withNativeIk(false).nativeIkEnabled());
    }

    @Test
    void legacyConstructorDefaultsToDisabled() {
        var legacy = new RuntimePerformancePolicy(CUSTOM, 1, 2, 64, 10, 40,
                true, true, true, true, 16, 128, true, 32, 128);
        assertFalse(legacy.nativeIkEnabled());
        assertTrue(legacy.ikEnabled());
    }

    @Test
    void configKeyExistsBesideIkEnabled() throws Exception {
        var runtime = AssimpLoaderConfig.RuntimeConfig.class;
        assertNotNull(runtime.getField("ikEnabled"));
        assertNotNull(runtime.getField("nativeIkEnabled"));
    }
}
