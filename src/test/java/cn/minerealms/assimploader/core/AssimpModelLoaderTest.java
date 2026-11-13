package cn.minerealms.assimploader.core;

import cn.minerealms.assimploader.model.AssimpScene;
import org.joml.Vector3f;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Base64;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class AssimpModelLoaderTest {
    @TempDir
    Path directory;

    @Test
    void bundledExampleCanBeImportedFromResourceMemory() throws Exception {
        try (var stream = getClass().getResourceAsStream("/assets/assimploader/models/showcase/probe_textured.fbx")) {
            assertNotNull(stream);
            AssimpScene scene = new AssimpModelLoader().loadModelFromMemory(
                    "probe_textured.fbx", stream.readAllBytes(), ModelImportMode.STATIC);
            assertNotNull(scene);
            assertTrue(scene.getMeshCount() > 0);
            assertTrue(scene.getMeshes().stream().anyMatch(mesh -> mesh.getVertices().length > 0));
            for (var mesh : scene.getMeshes()) {
                for (float position : mesh.getVertices()) {
                    assertTrue(Float.isFinite(position), "Imported example positions must remain finite");
                }
            }
        }
    }

    @Test
    void dynamicNodeTransformsMatchStaticPretransformedVertices() throws Exception {
        Path model = createTranslatedTriangle();
        AssimpModelLoader loader = new AssimpModelLoader();
        AssimpScene dynamicScene = loader.loadModel(model, ModelImportMode.DYNAMIC);
        AssimpScene staticScene = loader.loadModel(model, ModelImportMode.STATIC);

        var node = dynamicScene.findNodeByName("Pivot");
        assertNotNull(node);
        float[] dynamicVertices = dynamicScene.getMeshes().get(0).getVertices();
        float[] staticVertices = staticScene.getMeshes().get(0).getVertices();
        assertEquals(staticVertices.length, dynamicVertices.length);
        for (int i = 0; i < dynamicVertices.length; i += 3) {
            Vector3f transformed = node.getTransformation().transformPosition(new Vector3f(
                    dynamicVertices[i], dynamicVertices[i + 1], dynamicVertices[i + 2]));
            assertEquals(staticVertices[i], transformed.x, 1e-5f);
            assertEquals(staticVertices[i + 1], transformed.y, 1e-5f);
            assertEquals(staticVertices[i + 2], transformed.z, 1e-5f);
        }
        Vector3f origin = node.getTransformation().transformPosition(new Vector3f());
        assertEquals(2, origin.x, 1e-5f);
        assertEquals(3, origin.y, 1e-5f);
        assertEquals(-4, origin.z, 1e-5f);
    }

    private Path createTranslatedTriangle() throws Exception {
        ByteBuffer vertices = ByteBuffer.allocate(36).order(ByteOrder.LITTLE_ENDIAN);
        for (float v : new float[]{0, 0, 0, 1, 0, 0, 0, 1, 0}) {
            vertices.putFloat(v);
        }
        String encoded = Base64.getEncoder().encodeToString(vertices.array());
        String gltf = """
                {
                  "asset": {"version": "2.0"},
                  "buffers": [{"byteLength": 36, "uri": "data:application/octet-stream;base64,%s"}],
                  "bufferViews": [{"buffer": 0, "byteLength": 36}],
                  "accessors": [{"bufferView": 0, "componentType": 5126, "count": 3,
                    "type": "VEC3", "min": [0,0,0], "max": [1,1,0]}],
                  "meshes": [{"primitives": [{"attributes": {"POSITION": 0}}]}],
                  "nodes": [{"name": "Pivot", "mesh": 0, "translation": [2,3,4],
                    "rotation": [0,0.70710678,0,0.70710678]}],
                  "scenes": [{"nodes": [0]}], "scene": 0
                }
                """.formatted(encoded);
        Path file = directory.resolve("translated.gltf");
        Files.writeString(file, gltf);
        return file;
    }
}
