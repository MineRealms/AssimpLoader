package cn.minerealms.assimploader.render;

import cn.minerealms.assimploader.material.LabPbrPacker;
import cn.minerealms.assimploader.material.PbrMaterialInputs;
import org.junit.jupiter.api.Test;

import java.util.HashMap;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

class PbrTexturePreparationTest {
    @Test
    void prepackedSiblingsRetainExactLabPbrChannelsInsteadOfBeingRepacked() {
        var albedo = image(0xff443322);
        var normal = image(0x7f3f1f0f);
        var specular = image(0xfe40e8b2);
        Map<String, Integer> reads = new HashMap<>();
        Map<String, LabPbrPacker.Image> files = Map.of("body.png", albedo,
                "body_n.png", normal, "body_s.png", specular);
        var prepared = PbrTexturePreparation.prepare("body.png", "body.png", scalar(0.9f), path -> {
            reads.merge(path, 1, Integer::sum);
            return files.get(path);
        });
        assertArrayEquals(albedo.pixels(), prepared.albedo().pixels());
        assertArrayEquals(normal.pixels(), prepared.maps().normal().pixels());
        assertArrayEquals(specular.pixels(), prepared.maps().specular().pixels());
        assertEquals("prepacked:body_n.png", prepared.normalSource());
        assertEquals("prepacked:body_s.png", prepared.specularSource());
        assertTrue(reads.values().stream().allMatch(count -> count == 1));
    }

    @Test
    void embeddedIndicesUseTheirOriginalFilenameToFindPrepackedSiblings() {
        var albedo = image(0xff102030);
        var specular = image(0xff112233);
        Map<String, LabPbrPacker.Image> files = Map.of("*0", albedo, "textures/body_s.png", specular);
        var prepared = PbrTexturePreparation.prepare("*0", "textures/body.png", null, files::get);
        assertArrayEquals(albedo.pixels(), prepared.albedo().pixels());
        assertNull(prepared.maps().normal());
        assertArrayEquals(specular.pixels(), prepared.maps().specular().pixels());
        assertEquals("prepacked:textures/body_s.png", prepared.specularSource());
    }

    @Test
    void missingSingleChannelDoesNotDiscardAvailableNormalOrScalarSpecular() {
        var normal = image(0xffffc080);
        var inputs = new PbrMaterialInputs(PbrMaterialInputs.Workflow.METALLIC_ROUGHNESS,
                new PbrMaterialInputs.TextureRef("normal.png", 0, 0),
                new PbrMaterialInputs.TextureRef("missing_roughness.png", 0, 0), null, null, null,
                0.25f, 0, 1, 1, 0, 0, 0, 0.04f);
        var prepared = PbrTexturePreparation.prepare("missing_albedo.png", "missing_albedo.png", inputs,
                path -> path.equals("normal.png") ? normal : null);
        assertEquals(0xffffffff, prepared.albedo().pixel(0, 0));
        assertNotNull(prepared.maps().normal());
        assertNotNull(prepared.maps().specular());
        assertEquals(191, prepared.maps().specular().pixel(0, 0) & 255);
        assertEquals("converted:METALLIC_ROUGHNESS", prepared.normalSource());
    }

    @Test
    void sharedAlbedoMayProduceDifferentMaterialSpecularPlanes() {
        var albedo = image(0xffd0c0b0);
        var smooth = PbrTexturePreparation.prepare("shared.png", "shared.png", scalar(0.1f), path ->
                path.equals("shared.png") ? albedo : null);
        var rough = PbrTexturePreparation.prepare("shared.png", "shared.png", scalar(0.9f), path ->
                path.equals("shared.png") ? albedo : null);
        assertArrayEquals(smooth.albedo().pixels(), rough.albedo().pixels());
        assertNotEquals(smooth.maps().specular().pixel(0, 0), rough.maps().specular().pixel(0, 0));
    }

    @Test
    void absentMaterialMapsAreNotReportedAsPbrImages() {
        var prepared = PbrTexturePreparation.prepare("body.png", "body.png", null, ignored -> null);
        assertNull(prepared.maps().normal());
        assertNull(prepared.maps().specular());
        assertEquals("none", prepared.normalSource());
        assertEquals("none", prepared.specularSource());
    }

    private static LabPbrPacker.Image image(int pixel) { return new LabPbrPacker.Image(1, 1, new int[]{pixel}); }

    private static PbrMaterialInputs scalar(float roughness) {
        return new PbrMaterialInputs(PbrMaterialInputs.Workflow.METALLIC_ROUGHNESS,
                null, null, null, null, null, roughness, 0, 1, 1, 0, 0, 0, 0.04f);
    }
}
