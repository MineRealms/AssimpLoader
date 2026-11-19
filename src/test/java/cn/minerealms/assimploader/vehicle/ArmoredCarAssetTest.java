package cn.minerealms.assimploader.vehicle;

import cn.minerealms.assimpshowcase.vehicle.ArmoredCarEntity;

import cn.minerealms.assimploader.core.AssimpModelLoader;
import cn.minerealms.assimploader.core.ModelImportMode;
import cn.minerealms.assimploader.render.SceneTransforms;
import cn.minerealms.assimpshowcase.vehicle.client.ArmoredCarGeometry;
import org.joml.Vector3f;
import org.junit.jupiter.api.Test;

import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.*;
import static org.junit.jupiter.api.Assumptions.assumeTrue;

class ArmoredCarAssetTest {
    private static final Path ASSET = Path.of("run", "resourcepacks", "ArmoredCar_Local_PBR", "assets",
            "assimploader", "models", "entity", "armored_car", "armored_car.fbx");

    @Test
    void localFbxPreservesRuntimeUnitsPbrAndAllWheelPivots() throws Exception {
        assumeTrue(Files.isRegularFile(ASSET), "User-supplied asset pack is local and is not redistributed with source");
        var scene = new AssimpModelLoader().loadModel(ASSET, ModelImportMode.DYNAMIC);
        assertEquals(7, scene.getMeshCount());
        var bounds = SceneTransforms.bounds(scene);
        assertEquals(2.0, bounds.getXsize(), 0.015);
        assertEquals(2.5, bounds.getYsize(), 0.015);
        assertEquals(4.5, bounds.getZsize(), 0.015);
        assertEquals(ArmoredCarEntity.MODEL_WIDTH, bounds.getXsize() * ArmoredCarEntity.MODEL_SCALE, 0.03);
        assertEquals(ArmoredCarEntity.MODEL_HEIGHT, bounds.getYsize() * ArmoredCarEntity.MODEL_SCALE, 0.03);
        assertEquals(ArmoredCarEntity.MODEL_LENGTH, bounds.getZsize() * ArmoredCarEntity.MODEL_SCALE, 0.03);
        assertEquals(0, bounds.minY, 0.015);
        assertFalse(scene.hasSkeleton());
        for (var material : scene.getMaterials()) {
            var inputs = material.getPbrInputs();
            assertNotNull(material.getDiffuseTexture());
            assertNotNull(inputs.normal());
            assertNotNull(inputs.roughness());
            assertNotNull(inputs.metallic());
            for (String texture : java.util.List.of(material.getDiffuseTexture(), inputs.normal().path(),
                    inputs.roughness().path(), inputs.metallic().path()))
                assertTrue(Files.isRegularFile(ASSET.getParent().resolve(texture.replace('\\', '/'))), texture);
        }
        ArmoredCarGeometry geometry = new ArmoredCarGeometry(scene);
        var rest = geometry.pose(0, 0);
        var moving = geometry.pose(0.9f, 0.3f);
        assertEquals(scene.getMeshCount(), rest.size());
        int wheels = 0, steeringWheels = 0;
        for (int index = 0; index < rest.size(); index++) {
            var bind = rest.get(index);
            var animated = moving.get(index);
            String name = bind.mesh().getName();
            if (name.startsWith("Wheel_") && !name.contains("Spare")) {
                wheels++;
                assertTrue(bind.transform().getTranslation(new Vector3f()).equals(
                        animated.transform().getTranslation(new Vector3f()), 1.0e-5f), name + " wheel center moved");
                assertFalse(bind.transform().equals(animated.transform(), 1.0e-5f), name + " did not roll");
                var before = bind.transform().transformDirection(new Vector3f(1, 0, 0)).normalize();
                var after = animated.transform().transformDirection(new Vector3f(1, 0, 0)).normalize();
                if (name.contains("Front")) {
                    steeringWheels++;
                    assertEquals(Math.cos(0.3), before.dot(after), 1.0e-4);
                } else assertTrue(before.equals(after, 1.0e-5f));
            } else assertTrue(bind.transform().equals(animated.transform(), 1.0e-5f), name + " static part moved");
        }
        assertEquals(4, wheels);
        assertEquals(2, steeringWheels);
        assertTrue(scene.getMeshes().stream().allMatch(mesh -> mesh.getTexCoords() != null));
        assertTrue(scene.getMeshes().stream().mapToInt(mesh -> mesh.getIndices().length / 3).sum() < 120_000);
    }
}
