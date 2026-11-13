package cn.minerealms.assimploader.core;

import net.minecraft.resources.ResourceLocation;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.charset.StandardCharsets;
import java.util.HashMap;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

class ResourceModelImportTest {
    @Test
    void gltfReadsItsExternalBinFromTheSameResourceNamespace() throws Exception {
        var model = new ResourceLocation("example", "models/triangle/mesh.gltf");
        Map<ResourceLocation, byte[]> files = new HashMap<>();
        files.put(model, gltf("mesh.bin"));
        files.put(new ResourceLocation("example", "models/triangle/mesh.bin"), triangle());
        var scene = new AssimpModelLoader().loadModelFromResources(model, files::get, ModelImportMode.DYNAMIC);
        assertEquals(1, scene.getMeshCount());
        assertEquals(3, scene.getMeshes().get(0).getVertexCount());
        assertEquals(model, scene.getSourceResource());
    }

    @Test
    void objReadsMaterialPropertiesFromItsSideFile() throws Exception {
        var model = new ResourceLocation("example", "models/obj/triangle.obj");
        Map<ResourceLocation, byte[]> files = Map.of(
                model, "mtllib paint.mtl\no Triangle\nv 0 0 0\nv 1 0 0\nv 0 1 0\nusemtl Paint\nf 1 2 3\n".getBytes(StandardCharsets.UTF_8),
                new ResourceLocation("example", "models/obj/paint.mtl"), "newmtl Paint\nKd 0.25 0.5 0.75\n".getBytes(StandardCharsets.UTF_8));
        var scene = new AssimpModelLoader().loadModelFromResources(model, files::get, ModelImportMode.STATIC);
        var material = scene.getMaterials().get(scene.getMeshes().get(0).getMaterialIndex());
        assertArrayEquals(new float[]{0.25f, 0.5f, 0.75f, 1f}, material.getDiffuseColor(), 1e-5f);
    }

    @Test
    void missingRequiredSideFileProducesAnImportError() {
        var model = new ResourceLocation("example", "models/triangle/mesh.gltf");
        assertThrows(IOException.class, () -> new AssimpModelLoader().loadModelFromResources(model,
                resource -> resource.equals(model) ? gltf("missing.bin") : null, ModelImportMode.STATIC));
    }

    @Test
    void resourcePathsCannotEscapeTheirNamespaceOrRoot() throws Exception {
        var model = new ResourceLocation("example", "models/triangle/mesh.gltf");
        assertEquals(new ResourceLocation("example", "models/shared.bin"), ResourceFileIO.resolve(model, "../shared.bin"));
        assertThrows(IOException.class, () -> ResourceFileIO.resolve(model, "../../../outside.bin"));
        assertThrows(IOException.class, () -> ResourceFileIO.resolve(model, "other:models/mesh.bin"));
        assertThrows(IOException.class, () -> ResourceFileIO.resolve(model, "/etc/file"));
        assertThrows(IOException.class, () -> ResourceFileIO.resolve(model, "C:\\private\\file"));
        assertThrows(IOException.class, () -> ResourceFileIO.resolve(model, "%2e%2e/file"));
        assertThrows(IOException.class, () -> new AssimpModelLoader().loadModelFromResources(model,
                resource -> resource.equals(model) ? gltf("../../../outside.bin") : null, ModelImportMode.STATIC));
    }

    @Test
    void nullMemoryDataDoesNotDereferenceBeforeValidation() {
        assertNull(new AssimpModelLoader().loadModelFromMemory("missing.fbx", null));
    }

    private static byte[] triangle() {
        ByteBuffer buffer = ByteBuffer.allocate(36).order(ByteOrder.LITTLE_ENDIAN);
        for (float value : new float[]{0, 0, 0, 1, 0, 0, 0, 1, 0}) buffer.putFloat(value);
        return buffer.array();
    }

    private static byte[] gltf(String uri) {
        return ("""
                {"asset":{"version":"2.0"},
                 "buffers":[{"byteLength":36,"uri":"%s"}],
                 "bufferViews":[{"buffer":0,"byteLength":36}],
                 "accessors":[{"bufferView":0,"componentType":5126,"count":3,"type":"VEC3","min":[0,0,0],"max":[1,1,0]}],
                 "meshes":[{"primitives":[{"attributes":{"POSITION":0}}]}],
                 "nodes":[{"mesh":0}],"scenes":[{"nodes":[0]}],"scene":0}
                """).formatted(uri).getBytes(StandardCharsets.UTF_8);
    }
}
