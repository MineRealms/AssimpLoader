package cn.minerealms.assimploader.lookdev.preview;

import cn.minerealms.assimploader.core.AssimpModelLoader;
import cn.minerealms.assimploader.core.ModelImportMode;
import org.junit.jupiter.api.Test;

import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.charset.StandardCharsets;
import java.util.Base64;

import static org.junit.jupiter.api.Assertions.*;

class PreviewSceneDataTest {
    @Test
    void dynamicFbxBoundsMatchStaticImportIncludingRootUnitScaleAndNestedPivots() throws Exception {
        byte[] bytes;
        try (var stream = getClass().getResourceAsStream("/assets/assimploader/models/showcase/ciws_material.fbx")) {
            assertNotNull(stream);
            bytes = stream.readAllBytes();
        }
        AssimpModelLoader loader = new AssimpModelLoader();
        PreviewSceneData dynamic = new PreviewSceneData(loader.loadModelFromMemory("ciws.fbx", bytes, ModelImportMode.DYNAMIC));
        PreviewSceneData fixed = new PreviewSceneData(loader.loadModelFromMemory("ciws.fbx", bytes, ModelImportMode.STATIC));
        assertTrue(dynamic.center().equals(fixed.center(), 1e-4f));
        assertTrue(dynamic.size().equals(fixed.size(), 1e-4f));
        assertEquals(fixed.triangles(), dynamic.triangles());
        assertTrue(dynamic.radius() > 0 && Float.isFinite(dynamic.radius()));
    }

    @Test
    void repeatedMeshInstancesContributeTheirOwnTransformToBoundsAndDrawCount() throws Exception {
        ByteBuffer vertices = ByteBuffer.allocate(36).order(ByteOrder.LITTLE_ENDIAN);
        for (float value : new float[]{0, 0, 0, 1, 0, 0, 0, 1, 0}) vertices.putFloat(value);
        String gltf = """
                {"asset":{"version":"2.0"},
                 "buffers":[{"byteLength":36,"uri":"data:application/octet-stream;base64,%s"}],
                 "bufferViews":[{"buffer":0,"byteLength":36}],
                 "accessors":[{"bufferView":0,"componentType":5126,"count":3,"type":"VEC3",
                   "min":[0,0,0],"max":[1,1,0]}],
                 "meshes":[{"primitives":[{"attributes":{"POSITION":0}}]}],
                 "nodes":[{"name":"Parent","translation":[0,3,0],"children":[1,2]},
                   {"mesh":0},{"mesh":0,"translation":[5,0,0]}],
                 "scenes":[{"nodes":[0]}],"scene":0}
                """.formatted(Base64.getEncoder().encodeToString(vertices.array()));
        var scene = new AssimpModelLoader().loadModelFromMemory("instances.gltf",
                gltf.getBytes(StandardCharsets.UTF_8), ModelImportMode.DYNAMIC);
        PreviewSceneData data = new PreviewSceneData(scene);
        assertEquals(2, data.draws().size());
        assertEquals(2, data.triangles());
        assertEquals(6, data.size().x, 1e-5);
        assertEquals(3.5, data.center().y, 1e-5);
    }
}
