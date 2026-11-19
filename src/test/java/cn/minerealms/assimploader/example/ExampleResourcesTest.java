package cn.minerealms.assimploader.example;

import cn.minerealms.assimpshowcase.showcase.ShowcaseModel;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;

import static org.junit.jupiter.api.Assertions.*;

class ExampleResourcesTest {
    @Test
    void registeredProbeBlockAndItemResolveToAnExistingAssimpAsset() throws IOException {
        ShowcaseModel probe = ShowcaseModel.PROBE_TEXTURED;
        JsonObject blockState = readJson("blockstates/" + probe.registryName() + ".json");
        JsonObject itemModel = readJson("models/item/" + probe.registryName() + ".json");
        String blockModel = blockState.getAsJsonObject("variants").getAsJsonObject("")
                .get("model").getAsString();
        assertEquals(blockModel, itemModel.get("parent").getAsString());

        JsonObject model = readJson("models/" + blockModel.split(":", 2)[1] + ".json");
        assertEquals("assimploader:assimp", model.get("loader").getAsString());
        String asset = model.get("model").getAsString();
        assertEquals("assimploader:models/showcase/" + probe.modelName() + ".fbx", asset);
        try (var stream = getClass().getResourceAsStream("/assets/assimploader/" + asset.split(":", 2)[1])) {
            assertNotNull(stream, "The registered example must ship its model asset");
            assertTrue(stream.read() >= 0);
        }
    }

    private JsonObject readJson(String path) throws IOException {
        try (var stream = getClass().getResourceAsStream("/assets/assimploader/" + path)) {
            assertNotNull(stream, path);
            return JsonParser.parseReader(new InputStreamReader(stream, StandardCharsets.UTF_8)).getAsJsonObject();
        }
    }
}
