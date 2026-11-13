package cn.minerealms.assimploader.compat;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class OculusCompatibilityTest {
    @Test
    void adjacentPbrNamesPreserveNamespaceAndImageExtension() {
        assertEquals("example:textures/metal_n.png", OculusPBRSupport.getNormalMapPath("example:textures/metal.png"));
        assertEquals("example:textures/metal_s.png", OculusPBRSupport.getSpecularMapPath("example:textures/metal.png"));
        assertEquals("textures/metal.v2_n.tga", OculusPBRSupport.getNormalMapPath("textures/metal.v2.tga"));
        assertEquals("textures/metal_s", OculusPBRSupport.getSpecularMapPath("textures/metal"));
    }

    @Test
    void directoryDotsAreNotMistakenForImageExtensions() {
        assertEquals("textures.v2/metal_n", OculusPBRSupport.getNormalMapPath("textures.v2/metal"));
        assertEquals("textures.v2\\metal_s", OculusPBRSupport.getSpecularMapPath("textures.v2\\metal"));
        assertNull(OculusPBRSupport.getNormalMapPath(null));
        assertNull(OculusPBRSupport.getSpecularMapPath(" "));
        assertNull(OculusPBRSupport.getNormalMapPath("*0"));
    }

    @Test
    void pbrRequiresActualNormalAndSpecularTextureBindings() {
        assertFalse(OculusCompat.hasPbrBindings(0, 0));
        assertFalse(OculusCompat.hasPbrBindings(2, 0));
        assertFalse(OculusCompat.hasPbrBindings(0, 3));
        assertFalse(OculusCompat.hasPbrBindings(-1, 3));
        assertTrue(OculusCompat.hasPbrBindings(2, 3));
    }
}
