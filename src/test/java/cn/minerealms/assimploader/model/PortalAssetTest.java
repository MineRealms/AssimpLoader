package cn.minerealms.assimploader.model;

import cn.minerealms.assimploader.core.AssimpModelLoader;
import cn.minerealms.assimploader.core.ModelImportMode;
import cn.minerealms.assimploader.render.SceneTransforms;
import org.junit.jupiter.api.Test;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.HashSet;

import static org.junit.jupiter.api.Assertions.*;

class PortalAssetTest {
    @Test
    void shippedFbxAndCompanionGlbRetainPbrGlassAndWorldScale() throws Exception {
        for (String format : new String[]{"fbx", "glb"}) {
            byte[] bytes;
            if (format.equals("fbx")) {
                try (var input = getClass().getResourceAsStream("/assets/assimploader/models/showcase/portal_pbr.fbx")) {
                    assertNotNull(input);
                    bytes = input.readAllBytes();
                }
            } else {
                bytes = Files.readAllBytes(Path.of("model-tests", "portal", "portal_pbr.glb"));
            }
            var scene = new AssimpModelLoader().loadModelFromMemory("portal_pbr." + format, bytes, ModelImportMode.DYNAMIC);
            assertNotNull(scene, format);
            assertEquals(77, scene.getMeshes().size(), format);
            assertEquals(3266, scene.getMeshes().stream().mapToInt(mesh -> mesh.getIndices().length / 3).sum(), format);
            var used = new HashSet<Integer>();
            for (var mesh : scene.getMeshes()) {
                used.add(mesh.getMaterialIndex());
                assertNotNull(mesh.getTexCoords(), format);
            }
            assertEquals(8, used.size(), format);
            for (int index : used) {
                var material = scene.getMaterials().get(index);
                var inputs = material.getPbrInputs();
                assertNotNull(inputs.normal(), material.getName());
                assertNotNull(inputs.roughness(), material.getName());
                assertNotNull(inputs.metallic(), material.getName());
                assertFalse(inputs.hasUnsupportedUv(), material.getName());
                for (String path : new String[]{material.getDiffuseTexture(), inputs.normal().path(),
                        inputs.roughness().path(), inputs.metallic().path()}) {
                    assertTrue(scene.findEmbeddedTextureIndex(path) >= 0, format + ": " + path);
                }
            }
            var glass = scene.getMaterials().stream().filter(material -> material.getName().contains("Transparent_Glass"))
                    .findFirst().orElseThrow();
            assertTrue(glass.isTransparent(), format);
            assertEquals(0.24f, glass.getOpacity(), 0.01f, format);
            var bounds = SceneTransforms.bounds(scene);
            assertEquals(3.48, bounds.getXsize(), 0.02, format);
            assertEquals(3.8975, bounds.getYsize(), 0.02, format);
            assertEquals(1.2625, bounds.getZsize(), 0.02, format);
            assertEquals(0, bounds.minY, 0.01, format);
            assertEquals(0, bounds.getCenter().x, 0.01, format);
        }
    }
}
