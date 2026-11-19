package cn.minerealms.assimploader.model;

import cn.minerealms.assimploader.core.AssimpModelLoader;
import cn.minerealms.assimploader.core.ModelImportMode;
import cn.minerealms.assimploader.material.PbrMaterialInputs;
import net.minecraft.resources.ResourceLocation;
import org.junit.jupiter.api.Test;
import org.lwjgl.assimp.AIMaterial;
import org.lwjgl.assimp.AIMaterialProperty;
import org.lwjgl.system.MemoryStack;
import org.lwjgl.system.MemoryUtil;

import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.util.Base64;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;
import static org.lwjgl.assimp.Assimp.*;
import cn.minerealms.assimploader.util.ResourceLocations;
class ImportedPbrMaterialTest {
    @Test
    void blenderFbxMetadataRestoresAuthoredKnifeScalarProperties() throws Exception {
        AssimpScene scene = new AssimpModelLoader().loadModel(Path.of("model-tests/fbx/knife_material.fbx"), ModelImportMode.DYNAMIC);
        assertTrue(scene.getSourceFormat().toLowerCase().contains("fbx"));
        assertTrue(scene.getSourceGenerator().toLowerCase().contains("blender"));
        AssimpMaterial steel = scene.getMaterials().stream().filter(m -> m.getName().endsWith("Knife_Steel")).findFirst().orElseThrow();
        var pbr = steel.getPbrInputs();
        assertEquals(PbrMaterialInputs.Workflow.BLENDER_FBX, pbr.workflow());
        assertEquals(0.32f, pbr.roughnessFactor(), 1e-5f);
        assertEquals(0.7f, pbr.metallicFactor(), 1e-5f);
        assertEquals(0.04f, pbr.dielectricF0(), 1e-5f);
        assertTrue(pbr.hasPbrInputs());
    }

    @Test
    void genericPhongShininessIsNotSilentlyClassifiedAsBlenderRoughness() throws Exception {
        ResourceLocation location = ResourceLocations.of("example", "models/plain.obj");
        Map<ResourceLocation, byte[]> files = Map.of(location,
                "mtllib plain.mtl\nv 0 0 0\nv 1 0 0\nv 0 1 0\nusemtl Paint\nf 1 2 3\n".getBytes(StandardCharsets.UTF_8),
                ResourceLocations.of("example", "models/plain.mtl"),
                "newmtl Paint\nKd .2 .4 .6\nNs 36\nmap_Ns gloss.png\n".getBytes(StandardCharsets.UTF_8));
        AssimpScene scene = new AssimpModelLoader().loadModelFromResources(location, files::get, ModelImportMode.STATIC);
        var pbr = scene.getMaterials().get(scene.getMeshes().get(0).getMaterialIndex()).getPbrInputs();
        assertEquals(PbrMaterialInputs.Workflow.NONE, pbr.workflow());
        assertNull(pbr.roughness());
        assertFalse(pbr.hasPbrInputs());
    }

    @Test
    void gltfRetainsPackedChannelsFactorsAndUnsupportedUvInformation() {
        AssimpScene scene = new AssimpModelLoader().loadModelFromMemory("pbr.gltf", gltf().getBytes(StandardCharsets.UTF_8), ModelImportMode.STATIC);
        assertNotNull(scene);
        var pbr = scene.getMaterials().get(scene.getMeshes().get(0).getMaterialIndex()).getPbrInputs();
        assertEquals(PbrMaterialInputs.Workflow.METALLIC_ROUGHNESS, pbr.workflow());
        assertEquals(0.25f, pbr.roughnessFactor(), 1e-5f);
        assertEquals(0.8f, pbr.metallicFactor(), 1e-5f);
        assertNotNull(pbr.roughness());
        assertNotNull(pbr.metallic());
        assertEquals(pbr.roughness().path(), pbr.metallic().path());
        assertEquals(1, pbr.roughness().channel());
        assertEquals(2, pbr.metallic().channel());
        assertEquals(1, pbr.normal().uvIndex());
        assertEquals(0.35f, pbr.normalScale(), 1e-5f);
        assertEquals(0.6f, pbr.occlusionStrength(), 1e-5f);
        assertNotNull(pbr.emissive());
        assertEquals(0.1f, pbr.emissiveR(), 1e-5f);
        assertEquals(0.2f, pbr.emissiveG(), 1e-5f);
        assertEquals(0.3f, pbr.emissiveB(), 1e-5f);
        assertTrue(pbr.hasUnsupportedUv());
    }

    @Test
    void recognizesBothNativeOcclusionStrengthKeysWithoutOverridingTheStandardKey() {
        for (int semantic : new int[]{aiTextureType_AMBIENT_OCCLUSION, aiTextureType_LIGHTMAP}) {
            assertEquals(0.6f, occlusionStrength(semantic, false, "glTF2 Importer"), 1e-5f);
            assertEquals(0.4f, occlusionStrength(semantic, true, "glTF2 Importer"), 1e-5f);
            assertEquals(1f, occlusionStrength(semantic, false, "Autodesk FBX Importer"), 1e-5f);
        }
    }

    private static float occlusionStrength(int semantic, boolean standard, String format) {
        try (var stack = MemoryStack.stackPush()) {
            var material = AIMaterial.calloc(stack);
            int count = standard ? 2 : 1;
            var pointers = stack.mallocPointer(count);
            for (int i = 0; i < count; i++) {
                var storage = stack.calloc(AIMaterialProperty.SIZEOF);
                var property = new AIMaterialProperty(storage);
                property.mKey().data(stack.UTF8(i == 0 ? "$tex.file.strength" : _AI_MATKEY_GLTF_STRENGTH_BASE));
                storage.putInt(AIMaterialProperty.MSEMANTIC, semantic);
                storage.putInt(AIMaterialProperty.MTYPE, aiPTI_Float);
                storage.putInt(AIMaterialProperty.MDATALENGTH, 4);
                var value = stack.malloc(4).putFloat(0, i == 0 ? 0.6f : 0.4f);
                MemoryUtil.memPutAddress(property.address() + AIMaterialProperty.MDATA, MemoryUtil.memAddress(value));
                pointers.put(i, property.address());
            }
            material.mProperties(pointers).mNumAllocated(count);
            return new AssimpMaterial(material, 0, format, "").getPbrInputs().occlusionStrength();
        }
    }

    private static String gltf() {
        ByteBuffer vertices = ByteBuffer.allocate(36).order(ByteOrder.LITTLE_ENDIAN);
        for (float value : new float[]{0, 0, 0, 1, 0, 0, 0, 1, 0}) vertices.putFloat(value);
        String data = Base64.getEncoder().encodeToString(vertices.array());
        return """
                {"asset":{"version":"2.0","generator":"PBR test fixture"},
                 "buffers":[{"byteLength":36,"uri":"data:application/octet-stream;base64,%s"}],
                 "bufferViews":[{"buffer":0,"byteLength":36}],
                 "accessors":[{"bufferView":0,"componentType":5126,"count":3,"type":"VEC3","min":[0,0,0],"max":[1,1,0]}],
                 "images":[{"uri":"data:image/png;base64,iVBORw0KGgoAAAANSUhEUgAAAAEAAAABCAQAAAC1HAwCAAAAC0lEQVR42mNk+A8AAQUBAScY42YAAAAASUVORK5CYII="}],
                 "textures":[{"source":0}],
                 "materials":[{"pbrMetallicRoughness":{"roughnessFactor":0.25,"metallicFactor":0.8,"metallicRoughnessTexture":{"index":0}},
                     "normalTexture":{"index":0,"scale":0.35,"texCoord":1},"occlusionTexture":{"index":0,"strength":0.6},
                     "emissiveTexture":{"index":0},"emissiveFactor":[0.1,0.2,0.3]}],
                 "meshes":[{"primitives":[{"attributes":{"POSITION":0},"material":0}]}],
                 "nodes":[{"mesh":0}],"scenes":[{"nodes":[0]}],"scene":0}
                """.formatted(data);
    }
}
