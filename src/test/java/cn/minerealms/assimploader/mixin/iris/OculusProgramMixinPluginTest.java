package cn.minerealms.assimploader.mixin.iris;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class OculusProgramMixinPluginTest {
    @Test
    void acceptsPinnedOculusAndIrisIncludingLocalDevelopmentVersion() {
        assertTrue(OculusProgramMixinPlugin.supportsShaderMod("oculus", "1.8.0"));
        assertTrue(OculusProgramMixinPlugin.supportsShaderMod("iris", "1.8.12"));
        assertTrue(OculusProgramMixinPlugin.supportsShaderMod("iris", "1.8.12+mc1.21.1"));
        assertTrue(OculusProgramMixinPlugin.supportsShaderMod("iris", "1.8.12-snapshot+mc1.21.1-local"));
    }

    @Test
    void rejectsUnverifiedShaderVersionsAndOtherMods() {
        assertFalse(OculusProgramMixinPlugin.supportsShaderMod("oculus", "1.7.0"));
        assertFalse(OculusProgramMixinPlugin.supportsShaderMod("iris", "1.8.120"));
        assertFalse(OculusProgramMixinPlugin.supportsShaderMod("iris", "1.9.0"));
        assertFalse(OculusProgramMixinPlugin.supportsShaderMod("sodium", "1.8.12"));
    }
}
