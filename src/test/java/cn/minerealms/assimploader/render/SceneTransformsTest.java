package cn.minerealms.assimploader.render;

import cn.minerealms.assimploader.core.AssimpModelLoader;
import cn.minerealms.assimploader.core.ModelImportMode;
import com.mojang.blaze3d.vertex.PoseStack;
import org.joml.Matrix4f;
import org.joml.Vector3f;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class SceneTransformsTest {
    @Test
    void dynamicFbxHierarchyHasTheSameWorldBoundsAsBakedStaticGeometry() throws Exception {
        byte[] data;
        try (var stream = getClass().getResourceAsStream("/assets/assimploader/models/showcase/ciws_material.fbx")) {
            assertNotNull(stream);
            data = stream.readAllBytes();
        }
        var loader = new AssimpModelLoader();
        var dynamic = loader.loadModelFromMemory("ciws_material.fbx", data, ModelImportMode.DYNAMIC);
        var baked = loader.loadModelFromMemory("ciws_material.fbx", data, ModelImportMode.STATIC);
        assertNotNull(dynamic);
        assertNotNull(baked);
        var dynamicBounds = SceneTransforms.bounds(dynamic);
        var bakedBounds = SceneTransforms.bounds(baked);
        assertEquals(bakedBounds.minX, dynamicBounds.minX, 0.001);
        assertEquals(bakedBounds.minY, dynamicBounds.minY, 0.001);
        assertEquals(bakedBounds.minZ, dynamicBounds.minZ, 0.001);
        assertEquals(bakedBounds.maxX, dynamicBounds.maxX, 0.001);
        assertEquals(bakedBounds.maxY, dynamicBounds.maxY, 0.001);
        assertEquals(bakedBounds.maxZ, dynamicBounds.maxZ, 0.001);
        assertTrue(dynamicBounds.getYsize() < 2, "FBX centimeter units must not leak into world/item bounds");
        var glb = loader.loadModel(java.nio.file.Path.of("model-tests/glb/ciws_material.glb"), ModelImportMode.DYNAMIC);
        var meterBounds = SceneTransforms.bounds(glb);
        assertEquals(meterBounds.getXsize(), dynamicBounds.getXsize(), 0.001);
        assertEquals(meterBounds.getYsize(), dynamicBounds.getYsize(), 0.001);
        assertEquals(meterBounds.getZsize(), dynamicBounds.getZsize(), 0.001);
        assertSame(SceneTransforms.instances(dynamic), SceneTransforms.instances(dynamic));

        Matrix4f local = new Matrix4f().translation(3, 4, 5).mul(dynamic.getRootNode().getTransformation());
        Matrix4f unchanged = new Matrix4f(local);
        var animated = SceneTransforms.instances(dynamic, java.util.Map.of(dynamic.getRootNode().getName(), local));
        var bind = SceneTransforms.instances(dynamic);
        assertEquals(bind.size(), animated.size());
        for (int index = 0; index < bind.size(); index++) {
            Matrix4f expected = new Matrix4f().translation(3, 4, 5).mul(bind.get(index).transform());
            assertTrue(expected.equals(animated.get(index).transform(), 1.0e-5f));
        }
        assertEquals(unchanged, local, "render traversal must not mutate animation matrices");
    }

    @Test
    void nodeNormalUsesInverseTransposeForNonUniformScale() {
        PoseStack stack = new PoseStack();
        Matrix4f transform = new Matrix4f().rotateY(0.6f).scale(2, 3, 4);
        SceneTransforms.apply(stack, transform);
        Vector3f tangent = transform.transformDirection(new Vector3f(1, 1, 0));
        Vector3f normal = stack.last().normal().transform(new Vector3f(1, -1, 0));
        assertEquals(0, tangent.dot(normal), 1.0e-5f);
    }
}
