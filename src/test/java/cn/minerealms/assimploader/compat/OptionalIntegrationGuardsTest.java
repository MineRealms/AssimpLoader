package cn.minerealms.assimploader.compat;

import org.junit.jupiter.api.Test;

import java.util.Set;

import static org.junit.jupiter.api.Assertions.*;

class OptionalIntegrationGuardsTest {
    private static final String PREFIX = "cn.minerealms.assimploader.mixin.photon.";
    private static final String MESH = "com.lowdragmc.photon.gui.editor.MeshesResource";
    private static final String MATERIAL = "com.lowdragmc.photon.gui.editor.MaterialsResource";
    private static boolean initialized;

    @Test
    void bothOptionalModsAreRequired() {
        assertFalse(OptionalIntegrationGuards.requiredPhotonMods(Set.<String>of()::contains));
        assertFalse(OptionalIntegrationGuards.requiredPhotonMods(Set.of("photon")::contains));
        assertFalse(OptionalIntegrationGuards.requiredPhotonMods(Set.of("ldlib")::contains));
        assertTrue(OptionalIntegrationGuards.requiredPhotonMods(Set.of("photon", "ldlib")::contains));
    }

    @Test
    void missingOrPartialEditorClassesDisableOptionalMixins() {
        assertFalse(OptionalIntegrationGuards.shouldApplyPhotonMixin(MESH, PREFIX + "MeshesResourceMixin", name -> false));
        assertFalse(OptionalIntegrationGuards.photonEditorAvailable(name -> name.startsWith("com.lowdragmc.photon.")));
        assertFalse(OptionalIntegrationGuards.shouldApplyPhotonMixin(MESH, PREFIX + "MeshesResourceMixin",
                name -> !name.equals("com.lowdragmc.photon.client.gameobject.emitter.data.shape.MeshData")));
        assertFalse(OptionalIntegrationGuards.shouldApplyPhotonMixin(MATERIAL, PREFIX + "MaterialsResourceMixin",
                name -> !name.endsWith(".CustomShaderMaterial")));
    }

    @Test
    void completeCompatibleEditorAllowsBothKnownMixins() {
        assertTrue(OptionalIntegrationGuards.shouldApplyPhotonMixin(MESH, PREFIX + "MeshesResourceMixin", name -> true));
        assertTrue(OptionalIntegrationGuards.shouldApplyPhotonMixin(MATERIAL, PREFIX + "MaterialsResourceMixin", name -> true));
        assertTrue(OptionalIntegrationGuards.photonEditorAvailable(name -> true));
    }

    @Test
    void unexpectedTargetsAndMixinsAreRejected() {
        assertFalse(OptionalIntegrationGuards.shouldApplyPhotonMixin(MATERIAL, PREFIX + "MeshesResourceMixin", name -> true));
        assertFalse(OptionalIntegrationGuards.shouldApplyPhotonMixin(MESH, PREFIX + "UnknownMixin", name -> true));
    }

    @Test
    void classResourceLookupDoesNotInitializeTheTarget() {
        ClassLoader loader = getClass().getClassLoader();
        String target = getClass().getName() + "$InitializationProbe";
        assertTrue(OptionalIntegrationGuards.classResourcePresent(loader, target));
        assertFalse(initialized);
        assertFalse(OptionalIntegrationGuards.classResourcePresent(loader, "example.absent.OptionalClass"));
    }

    private static final class InitializationProbe {
        static { initialized = true; }
    }
}
