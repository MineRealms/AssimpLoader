package cn.minerealms.assimploader.model;

import cn.minerealms.assimploader.core.AssimpModelLoader;
import cn.minerealms.assimploader.core.ModelImportMode;
import net.minecraft.resources.ResourceLocation;
import org.junit.jupiter.api.Test;

import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.charset.StandardCharsets;
import java.util.Base64;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;
import cn.minerealms.assimploader.util.ResourceLocations;
class AssimpMaterialTest {
    @Test
    void rgbPropertiesDefaultToOpaque() throws Exception {
        AssimpMaterial material = objMaterial("Kd 0.25 0.5 0.75\nKs 0.1 0.2 0.3\nKa 0.2 0.3 0.4\n");
        assertArrayEquals(new float[]{0.25f, 0.5f, 0.75f, 1f}, material.getDiffuseColor(), 1e-5f);
        assertEquals(1f, material.getSpecularColor()[3]);
        assertEquals(1f, material.getAmbientColor()[3]);
        assertFalse(material.isTransparent());
    }

    @Test
    void explicitOpacityIncludesBothEndpointsAndValuesNearThem() throws Exception {
        for (float opacity : new float[]{0f, 0.005f, 0.4f, 0.999f, 1f}) {
            AssimpMaterial material = objMaterial("Kd 0.25 0.5 0.75\nd " + opacity + "\n");
            assertEquals(opacity, material.getOpacity(), 1e-5f, "d " + opacity);
        }
    }

    @Test
    void explicitOpaqueValueWinsOverLegacyTransparentColor() throws Exception {
        AssimpMaterial material = objMaterial("Kd 0.25 0.5 0.75\nd 1\nTf 1 1 1\n");
        assertEquals(1f, material.getOpacity());
        assertFalse(material.isTransparent());
    }

    @Test
    void objTransparencyIsConvertedToOpacity() throws Exception {
        for (float transparency : new float[]{0f, 0.25f, 1f}) {
            AssimpMaterial material = objMaterial("Kd 0.25 0.5 0.75\nTr " + transparency + "\n");
            assertEquals(1f - transparency, material.getOpacity(), 1e-5f, "Tr " + transparency);
        }
    }

    @Test
    void authoredRgbaAlphaIsPreservedWithoutApplyingItTwice() {
        for (float alpha : new float[]{0f, 0.35f, 1f}) {
            byte[] json = gltf(alpha).getBytes(StandardCharsets.UTF_8);
            AssimpScene scene = new AssimpModelLoader().loadModelFromMemory("rgba.gltf", json, ModelImportMode.STATIC);
            assertNotNull(scene);
            AssimpMaterial material = scene.getMaterials().get(scene.getMeshes().get(0).getMaterialIndex());
            assertArrayEquals(new float[]{0.25f, 0.5f, 0.75f, alpha}, material.getDiffuseColor(), 1e-5f);
        }
    }

    @Test
    void gltfBlendModeUsesTransparencyWithOpaqueConstantAlpha() {
        for (String mode : new String[]{"BLEND", "MASK", "OPAQUE"}) {
            byte[] json = gltf(1f, mode).getBytes(StandardCharsets.UTF_8);
            AssimpScene scene = new AssimpModelLoader().loadModelFromMemory("alpha-mode.gltf", json, ModelImportMode.STATIC);
            assertNotNull(scene);
            AssimpMaterial material = scene.getMaterials().get(scene.getMeshes().get(0).getMaterialIndex());
            assertEquals(1f, material.getOpacity());
            assertEquals("BLEND".equals(mode), material.isTransparent(), mode);
        }
    }

    private static AssimpMaterial objMaterial(String properties) throws Exception {
        ResourceLocation model = ResourceLocations.of("example", "models/material/triangle.obj");
        Map<ResourceLocation, byte[]> files = Map.of(model,
                "mtllib paint.mtl\no Triangle\nv 0 0 0\nv 1 0 0\nv 0 1 0\nusemtl Paint\nf 1 2 3\n".getBytes(StandardCharsets.UTF_8),
                ResourceLocations.of("example", "models/material/paint.mtl"),
                ("newmtl Paint\n" + properties).getBytes(StandardCharsets.UTF_8));
        AssimpScene scene = new AssimpModelLoader().loadModelFromResources(model, files::get, ModelImportMode.STATIC);
        return scene.getMaterials().get(scene.getMeshes().get(0).getMaterialIndex());
    }

    private static String gltf(float alpha) {
        return gltf(alpha, "BLEND");
    }

    private static String gltf(float alpha, String alphaMode) {
        ByteBuffer vertices = ByteBuffer.allocate(36).order(ByteOrder.LITTLE_ENDIAN);
        for (float value : new float[]{0, 0, 0, 1, 0, 0, 0, 1, 0}) vertices.putFloat(value);
        String data = Base64.getEncoder().encodeToString(vertices.array());
        return """
                {"asset":{"version":"2.0"},
                 "buffers":[{"byteLength":36,"uri":"data:application/octet-stream;base64,%s"}],
                 "bufferViews":[{"buffer":0,"byteLength":36}],
                 "accessors":[{"bufferView":0,"componentType":5126,"count":3,"type":"VEC3","min":[0,0,0],"max":[1,1,0]}],
                 "materials":[{"alphaMode":"%s","pbrMetallicRoughness":{"baseColorFactor":[0.25,0.5,0.75,%s]}}],
                 "meshes":[{"primitives":[{"attributes":{"POSITION":0},"material":0}]}],
                 "nodes":[{"mesh":0}],"scenes":[{"nodes":[0]}],"scene":0}
                """.formatted(data, alphaMode, alpha);
    }
}
