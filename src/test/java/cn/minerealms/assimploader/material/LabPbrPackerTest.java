package cn.minerealms.assimploader.material;

import org.junit.jupiter.api.Test;

import java.util.Map;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.*;

class LabPbrPackerTest {
    @Test
    void scalarRoughnessAndDielectricReflectanceUseDataValuesWithoutGamma() {
        var packed = LabPbrPacker.pack(inputs(PbrMaterialInputs.Workflow.METALLIC_ROUGHNESS,
                null, null, null, null, null, 0.25f, 0f, 1f, 1f, 0f), ignored -> null);
        assertNull(packed.normal());
        int specular = packed.specular().pixel(0, 0);
        assertEquals(191, channel(specular, 0));
        assertEquals(10, channel(specular, 1));
        assertEquals(0, channel(specular, 2));
        assertEquals(0, channel(specular, 3));
    }

    @Test
    void gltfPackedGAndBChannelsAreDecodedOnceAndMultipliedByFactors() {
        AtomicInteger loads = new AtomicInteger();
        var material = inputs(PbrMaterialInputs.Workflow.METALLIC_ROUGHNESS,
                null, ref("orm", 1), ref("orm", 2), null, null, 0.5f, 1f, 1f, 1f, 0f);
        var packed = LabPbrPacker.pack(material, path -> {
            loads.incrementAndGet();
            return image(231, 128, 255, 255);
        });
        assertEquals(1, loads.get());
        int specular = packed.specular().pixel(0, 0);
        assertEquals(191, channel(specular, 0));
        assertEquals(255, channel(specular, 1));
    }

    @Test
    void openGlNormalConvertsGreenAndUsesAoInsteadOfSourceZ() {
        var material = inputs(PbrMaterialInputs.Workflow.NONE, ref("normal", 0), null, null,
                ref("ao", 0), null, 0.5f, 0f, 1f, 1f, 0f);
        Map<String, LabPbrPacker.Image> images = Map.of("normal", image(128, 64, 240, 35), "ao", image(64, 0, 0, 0));
        var packed = LabPbrPacker.pack(material, images::get);
        int normal = packed.normal().pixel(0, 0);
        assertEquals(128, channel(normal, 0));
        assertEquals(191, channel(normal, 1));
        assertEquals(64, channel(normal, 2));
        assertEquals(255, channel(normal, 3));
        assertNull(packed.specular());
    }

    @Test
    void normalScaleZeroAndOcclusionStrengthZeroProduceNeutralMap() {
        var material = inputs(PbrMaterialInputs.Workflow.NONE, ref("normal", 0), null, null,
                ref("ao", 0), null, 0.5f, 0f, 0f, 0f, 0f);
        int normal = LabPbrPacker.pack(material, ignored -> image(230, 12, 0, 0)).normal().pixel(0, 0);
        assertEquals(128, channel(normal, 0));
        assertEquals(128, channel(normal, 1));
        assertEquals(255, channel(normal, 2));
        assertEquals(255, channel(normal, 3));
    }

    @Test
    void fractionalMetallicDoesNotAccidentallySelectReservedMetalCodes() {
        for (float metal : new float[]{0f, 0.25f, 0.49f, 0.5f, 1f}) {
            var material = inputs(PbrMaterialInputs.Workflow.METALLIC_ROUGHNESS, null, null, null,
                    null, null, 0.5f, metal, 1f, 1f, 0f);
            int encoded = LabPbrPacker.pack(material, ignored -> null).specular().pixel(0, 0);
            assertEquals(metal >= 0.5f ? 255 : 10, channel(encoded, 1));
        }
    }

    @Test
    void emissiveColorIsLinearizedAndNeverEmitsLabPbrSentinel255() {
        var material = inputs(PbrMaterialInputs.Workflow.METALLIC_ROUGHNESS, null, null, null,
                null, ref("emission", 0), 0.5f, 0f, 1f, 1f, 1f);
        int dim = LabPbrPacker.pack(material, ignored -> image(128, 128, 128, 255)).specular().pixel(0, 0);
        assertEquals(55, channel(dim, 3));
        int bright = LabPbrPacker.pack(material, ignored -> image(255, 255, 255, 255)).specular().pixel(0, 0);
        assertEquals(254, channel(bright, 3));
    }

    @Test
    void unsupportedUvMapDoesNotReadAnIncorrectUvChannel() {
        var material = inputs(PbrMaterialInputs.Workflow.NONE, new PbrMaterialInputs.TextureRef("normal", 0, 1),
                null, null, null, null, 0.5f, 0f, 1f, 1f, 0f);
        assertTrue(material.hasUnsupportedUv());
        var packed = LabPbrPacker.pack(material, path -> { fail("Unsupported UV must not resolve " + path); return null; });
        assertNull(packed.normal());
        assertNull(packed.specular());
    }

    @Test
    void immutableImageAndDimensionValidationPreventAliasingOrOversizedAllocation() {
        int[] pixels = {0xff112233};
        var image = new LabPbrPacker.Image(1, 1, pixels);
        pixels[0] = 0;
        image.pixels()[0] = 0;
        assertEquals(0xff112233, image.pixel(0, 0));
        assertThrows(IllegalArgumentException.class, () -> new LabPbrPacker.Image(0, 1, new int[0]));
        assertThrows(IllegalArgumentException.class, () -> new LabPbrPacker.Image(8192, 8192, new int[1]));
        assertThrows(IllegalArgumentException.class, () -> new LabPbrPacker.Image(2, 1, new int[1]));
    }

    private static PbrMaterialInputs.TextureRef ref(String path, int channel) { return new PbrMaterialInputs.TextureRef(path, channel, 0); }
    private static LabPbrPacker.Image image(int r, int g, int b, int a) {
        return new LabPbrPacker.Image(1, 1, new int[]{a << 24 | b << 16 | g << 8 | r});
    }
    private static int channel(int pixel, int index) { return pixel >>> (index * 8) & 255; }
    private static PbrMaterialInputs inputs(PbrMaterialInputs.Workflow workflow,
            PbrMaterialInputs.TextureRef normal, PbrMaterialInputs.TextureRef roughness,
            PbrMaterialInputs.TextureRef metallic, PbrMaterialInputs.TextureRef ao, PbrMaterialInputs.TextureRef emission,
            float roughnessFactor, float metallicFactor, float normalScale, float aoStrength, float emissionFactor) {
        return new PbrMaterialInputs(workflow, normal, roughness, metallic, ao, emission,
                roughnessFactor, metallicFactor, normalScale, aoStrength, emissionFactor, emissionFactor, emissionFactor, 0.04f);
    }
}
