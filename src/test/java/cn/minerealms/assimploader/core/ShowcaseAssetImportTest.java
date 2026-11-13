package cn.minerealms.assimploader.core;

import cn.minerealms.assimploader.model.AssimpNode;
import org.junit.jupiter.api.Test;

import java.nio.file.Path;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class ShowcaseAssetImportTest {
    private static final List<String> ASSETS = List.of("ciws_material", "ciws_textured",
            "knife_material", "knife_textured", "probe_no_uv", "probe_textured");

    @Test
    void exportedFbxVariantsImportWithExpectedTextureAndUvContracts() throws Exception {
        AssimpModelLoader loader = new AssimpModelLoader();
        for (String name : ASSETS) {
            var scene = loader.loadModel(Path.of("model-tests", "fbx", name + ".fbx"), ModelImportMode.DYNAMIC);
            assertNotNull(scene, name);
            assertTrue(scene.getMeshCount() > 0, name);
            assertEquals(name.endsWith("_textured"), scene.hasEmbeddedTextures(), name);
            if (name.endsWith("_textured")) {
                assertEquals(1, scene.getTextureCount(), name);
                assertTrue(scene.getMeshes().stream().allMatch(mesh -> mesh.getTexCoords() != null), name);
            }
            if (name.equals("probe_no_uv")) {
                assertTrue(scene.getMeshes().stream().allMatch(mesh -> mesh.getTexCoords() == null));
            }
            int triangles = 0;
            for (var mesh : scene.getMeshes()) {
                triangles += mesh.getIndices().length / 3;
                for (float position : mesh.getVertices()) {
                    assertTrue(Float.isFinite(position), name);
                }
            }
            assertTrue(triangles <= (name.startsWith("ciws") ? 3000 : 200), name);
            System.out.printf("FBX %s: %d meshes, %d triangles, %d textures%n",
                    name, scene.getMeshCount(), triangles, scene.getTextureCount());
        }
    }

    @Test
    void ciwsRetainsNestedYawPitchAndRotorPivots() throws Exception {
        for (String name : List.of("ciws_material", "ciws_textured")) {
            var scene = new AssimpModelLoader().loadModel(
                    Path.of("model-tests", "fbx", name + ".fbx"), ModelImportMode.DYNAMIC);
            AssimpNode yaw = findEnding(scene.getRootNode(), "_Yaw");
            AssimpNode pitch = findEnding(scene.getRootNode(), "_Pitch");
            AssimpNode rotor = findEnding(scene.getRootNode(), "_Rotor");
            assertNotNull(yaw, name + " yaw");
            assertNotNull(pitch, name + " pitch");
            assertNotNull(rotor, name + " rotor");
            assertSame(pitch, findEnding(yaw, "_Pitch"));
            assertSame(rotor, findEnding(pitch, "_Rotor"));
            assertTrue(yaw.getTransformation().isFinite());
            assertTrue(pitch.getTransformation().isFinite());
            System.out.printf("%s yaw=%s pitch=%s rotor=%s%n", name,
                    yaw.getTransformation(), pitch.getTransformation(), rotor.getTransformation());
        }
    }

    private AssimpNode findEnding(AssimpNode node, String suffix) {
        if (node.getName().endsWith(suffix)) {
            return node;
        }
        for (var child : node.getChildren()) {
            AssimpNode found = findEnding(child, suffix);
            if (found != null) {
                return found;
            }
        }
        return null;
    }
}
