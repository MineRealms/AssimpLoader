package cn.minerealms.assimploader.showcase;

import cn.minerealms.assimploader.core.AssimpModelLoader;
import cn.minerealms.assimploader.core.ModelImportMode;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import org.junit.jupiter.api.Test;

import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;

import static org.junit.jupiter.api.Assertions.*;

class ShowcaseAssetsTest {
    @Test
    void everyRegisteredExhibitHasACompleteModelResourceChain() throws Exception {
        for (ShowcaseModel model : ShowcaseModel.values()) {
            JsonObject state = json("blockstates/" + model.registryName() + ".json");
            JsonObject item = json("models/item/" + model.registryName() + ".json");
            String reference = "assimploader:showcase/" + model.modelName();
            assertEquals(reference, state.getAsJsonObject("variants").getAsJsonObject("").get("model").getAsString());
            assertEquals(reference, item.get("parent").getAsString());
            JsonObject geometry = json("models/showcase/" + model.modelName() + ".json");
            assertEquals("assimploader:assimp", geometry.get("loader").getAsString());
            assertEquals("assimploader:models/showcase/" + model.modelName() + ".fbx", geometry.get("model").getAsString());
            assertEquals(model.modelName().startsWith("ciws") ? "dynamic" : "static", geometry.get("mode").getAsString());
        }
    }

    @Test
    void allFbxAssetsImportWithTheirIntendedTextureAndPivotContracts() throws Exception {
        AssimpModelLoader loader = new AssimpModelLoader();
        for (ShowcaseModel model : ShowcaseModel.values()) {
            try (var stream = getClass().getResourceAsStream("/assets/assimploader/models/showcase/" + model.modelName() + ".fbx")) {
                assertNotNull(stream, model.modelName());
                boolean ciws = model.modelName().startsWith("ciws");
                var scene = loader.loadModelFromMemory(model.modelName() + ".fbx", stream.readAllBytes(),
                        ciws ? ModelImportMode.DYNAMIC : ModelImportMode.STATIC);
                assertNotNull(scene, model.modelName());
                assertFalse(scene.getMeshes().isEmpty(), model.modelName());
                assertEquals(model.modelName().endsWith("textured") || model == ShowcaseModel.PBR_PROBE
                                || model == ShowcaseModel.PORTAL_PBR,
                        scene.hasEmbeddedTextures(), model.modelName());
                if (scene.hasEmbeddedTextures()) {
                    for (var mesh : scene.getMeshes()) {
                        var material = scene.getMaterials().get(mesh.getMaterialIndex());
                        assertTrue(scene.findEmbeddedTextureIndex(material.getDiffuseTexture()) >= 0,
                                model.modelName() + ": embedded material reference " + material.getDiffuseTexture());
                    }
                }
                if (model == ShowcaseModel.PROBE_NO_UV) {
                    assertTrue(scene.getMeshes().stream().allMatch(mesh -> mesh.getTexCoords() == null));
                }
                if (ciws) {
                    String prefix = model == ShowcaseModel.CIWS_MATERIAL ? "CIWS_Material" : "CIWS_Textured";
                    assertNotNull(scene.findNodeByName(prefix + "_Yaw"));
                    assertNotNull(scene.findNodeByName(prefix + "_Pitch"));
                    assertNotNull(scene.findNodeByName(prefix + "_Rotor"));
                }
                for (var mesh : scene.getMeshes()) {
                    for (float value : mesh.getVertices()) assertTrue(Float.isFinite(value), model.modelName());
                }
            }
        }
    }

    @Test
    void bundledRocketFallbackHasANozzleAtTheWorldAnchor() throws Exception {
        try (var stream = getClass().getResourceAsStream("/assets/assimploader/models/showcase/rocket_engine.fbx")) {
            assertNotNull(stream);
            var scene = new AssimpModelLoader().loadModelFromMemory("rocket_engine.fbx", stream.readAllBytes(),
                    ModelImportMode.STATIC);
            assertNotNull(scene);
            var bounds = ShowcaseVisibility.modelBounds(scene);
            assertEquals(0, bounds.minY, 0.01);
            assertEquals(4.6, bounds.maxY, 0.01);
            assertEquals(2.1485, bounds.getXsize(), 0.01);
            assertEquals(2.1485, bounds.getZsize(), 0.01);
            assertEquals(4976, scene.getMeshes().stream().mapToInt(mesh -> mesh.getIndices().length / 3).sum());
            assertFalse(ShowcaseModel.ROCKET_ENGINE.isGalleryExhibit());
        }
    }

    private JsonObject json(String path) throws Exception {
        try (var stream = getClass().getResourceAsStream("/assets/assimploader/" + path)) {
            assertNotNull(stream, path);
            return JsonParser.parseReader(new InputStreamReader(stream, StandardCharsets.UTF_8)).getAsJsonObject();
        }
    }
}
