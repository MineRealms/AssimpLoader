package cn.minerealms.assimploader.model;

import cn.minerealms.assimploader.core.AssimpModelLoader;
import cn.minerealms.assimploader.core.ModelImportMode;
import cn.minerealms.assimploader.material.PbrMaterialInputs;
import org.junit.jupiter.api.Test;

import java.util.HashSet;

import static org.junit.jupiter.api.Assertions.*;

class PbrProbeAssetTest {
    @Test
    void blenderProbeRetainsThreeMaterialBindingsSharingOneAlbedo() throws Exception {
        try (var input = getClass().getResourceAsStream("/assets/assimploader/models/showcase/pbr_probe.fbx")) {
            assertNotNull(input);
            var scene = new AssimpModelLoader().loadModelFromMemory("pbr_probe.fbx", input.readAllBytes(), ModelImportMode.DYNAMIC);
            assertNotNull(scene);
            assertEquals(4, scene.getMeshes().size(), "Only three spheres and the normal-map slope are exported");
            var albedos = new HashSet<String>();
            var roughnessMaps = new HashSet<String>();
            var metallicMaps = new HashSet<String>();
            for (var mesh : scene.getMeshes()) {
                var material = scene.getMaterials().get(mesh.getMaterialIndex());
                var pbr = material.getPbrInputs();
                assertEquals(PbrMaterialInputs.Workflow.BLENDER_FBX, pbr.workflow());
                assertNotNull(pbr.normal());
                assertNotNull(pbr.roughness());
                assertNotNull(pbr.metallic());
                assertFalse(pbr.hasUnsupportedUv());
                for (String path : new String[]{material.getDiffuseTexture(), pbr.normal().path(),
                        pbr.roughness().path(), pbr.metallic().path()}) {
                    assertTrue(scene.findEmbeddedTextureIndex(path) >= 0, path);
                }
                albedos.add(material.getDiffuseTexture());
                roughnessMaps.add(pbr.roughness().path());
                metallicMaps.add(pbr.metallic().path());
            }
            assertEquals(1, albedos.size());
            assertEquals(2, roughnessMaps.size());
            assertEquals(2, metallicMaps.size());
        }
    }
}
