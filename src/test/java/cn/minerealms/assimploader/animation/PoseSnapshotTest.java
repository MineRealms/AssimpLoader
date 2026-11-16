package cn.minerealms.assimploader.animation;

import cn.minerealms.assimploader.core.AssimpModelLoader;
import cn.minerealms.assimploader.core.ModelImportMode;
import cn.minerealms.assimploader.model.AssimpMesh;
import cn.minerealms.assimploader.model.AssimpScene;
import net.minecraft.world.phys.AABB;
import org.joml.Matrix4f;
import org.junit.jupiter.api.Test;

import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.charset.StandardCharsets;
import java.util.Base64;
import java.util.LinkedHashMap;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

class PoseSnapshotTest {
    @Test
    void capturesHelperParentsBranchesAndMeshSpecificOffsets() {
        AssimpScene scene = fixture(ModelImportMode.DYNAMIC);
        PoseSnapshot pose = PoseSnapshot.capture(42, scene, Map.of("Helper", new Matrix4f().translation(7, 0, 0)));
        assertEquals(42, pose.getFrameId());
        assertSame(scene, pose.getScene());
        assertMatrix(new Matrix4f().translation(8, 2, 0), pose.getNodeTransform("BoneA"));
        assertMatrix(new Matrix4f().translation(13, 0, 0), pose.getNodeTransform("BoneB"));
        assertMatrix(new Matrix4f().translation(8, 2, 3),
                pose.getAttachmentTransform("BoneA", new Matrix4f().translation(0, 0, 3)));
        assertNull(pose.getNodeTransform("Missing"));
        assertNull(pose.getAttachmentTransform("Missing", new Matrix4f()));
        assertTrue(pose.copyLocalTransforms().containsKey("StaticMesh"));
        assertMatrix(new Matrix4f().translation(0, 2, 0), pose.copyLocalTransforms().get("BoneA"));

        AssimpMesh first = mesh(scene, "SkinA");
        AssimpMesh second = mesh(scene, "SkinB");
        assertEquals(2, pose.getBoneCount(first));
        assertEquals(2, pose.getBoneCount(second));
        for (AssimpMesh mesh : new AssimpMesh[]{first, second}) {
            Matrix4f[] palette = pose.getBoneTransforms(mesh);
            for (int i = 0; i < mesh.getBoneNames().size(); i++) {
                String name = mesh.getBoneNames().get(i);
                assertMatrix(new Matrix4f().translation(mesh == first ? 5 : 4, 0, 0), palette[i]);
                assertMatrix(pose.getNodeTransform(name).mul(mesh.getBoneOffsets().get(name)), palette[i]);
            }
        }
        assertNull(pose.getBoneTransforms(mesh(scene, "Static")));
        assertEquals(0, pose.getBoneCount(mesh(scene, "Static")));
    }

    @Test
    void matricesStayIndependentOfProducerConsumerAndLaterCaptures() {
        AssimpScene scene = fixture(ModelImportMode.DYNAMIC);
        AssimpMesh mesh = mesh(scene, "SkinA");
        Matrix4f override = new Matrix4f().translation(7, 0, 0);
        Map<String, Matrix4f> overrides = new LinkedHashMap<>();
        overrides.put("Helper", override);
        PoseSnapshot first = PoseSnapshot.capture(1, scene, overrides);
        override.translation(20, 0, 0);
        overrides.clear();
        first.getNodeTransform("BoneA").zero();
        first.getAttachmentTransform("BoneA", new Matrix4f()).zero();
        first.copyLocalTransforms().get("Helper").zero();
        Matrix4f[] returnedPalette = first.getBoneTransforms(mesh);
        returnedPalette[0].zero();
        returnedPalette[1] = new Matrix4f().zero();
        PoseSnapshot second = PoseSnapshot.capture(2, scene, Map.of("Helper", new Matrix4f().translation(12, 0, 0)));
        assertMatrix(new Matrix4f().translation(8, 2, 0), first.getNodeTransform("BoneA"));
        assertMatrix(new Matrix4f().translation(13, 2, 0), second.getNodeTransform("BoneA"));
        for (Matrix4f matrix : first.getBoneTransforms(mesh)) assertMatrix(new Matrix4f().translation(5, 0, 0), matrix);
        assertMatrix(new Matrix4f().translation(7, 0, 0), first.copyLocalTransforms().get("Helper"));
        scene.findNodeByName("Helper").getTransformation().zero();
        mesh.getBoneOffsets().values().forEach(Matrix4f::zero);
        assertMatrix(new Matrix4f().translation(8, 2, 0), first.getNodeTransform("BoneA"));
        for (Matrix4f matrix : first.getBoneTransforms(mesh)) assertMatrix(new Matrix4f().translation(5, 0, 0), matrix);
    }

    @Test
    void animatedBoundsIncludeWeightedUnweightedAndStaticVertices() {
        AssimpScene scene = fixture(ModelImportMode.DYNAMIC);
        PoseSnapshot bind = PoseSnapshot.capture(1, scene, Map.of());
        PoseSnapshot animated = PoseSnapshot.capture(2, scene, Map.of("Helper", new Matrix4f().translation(7, 0, 0)));
        AABB bounds = animated.getBounds();
        assertEquals(-3, bounds.minX, 1e-5);
        assertEquals(6, bounds.maxX, 1e-5);
        assertEquals(0, bounds.minY, 1e-5);
        assertEquals(1, bounds.maxY, 1e-5);
        assertTrue(bounds.minZ <= -2 && bounds.maxZ >= 0);
        assertEquals(1, bind.getBounds().maxX, 1e-5);

        PoseSnapshot movedStatic = PoseSnapshot.capture(3, scene, Map.of(
                "Helper", new Matrix4f().translation(7, 0, 0),
                "StaticMesh", new Matrix4f().translation(-20, 0, 0)));
        assertEquals(-19, movedStatic.getBounds().minX, 1e-5);
        assertEquals(-3, animated.getBounds().minX, 1e-5);
    }

    @Test
    void detachedWeightedBonesUseTheirOverrideAndOtherwiseIdentity() {
        AssimpScene scene = fixture(ModelImportMode.DYNAMIC);
        AssimpMesh mesh = mesh(scene, "SkinA");
        Matrix4f offset = mesh.getBoneOffsets().remove("BoneB");
        mesh.getBoneOffsets().put("Detached", offset);
        PoseSnapshot bind = PoseSnapshot.capture(1, scene, Map.of());
        PoseSnapshot posed = PoseSnapshot.capture(2, scene, Map.of("Detached", new Matrix4f().translation(20, 0, 0)));
        int index = mesh.getBoneNames().indexOf("Detached");
        assertMatrix(offset, bind.getBoneTransforms(mesh)[index]);
        assertMatrix(new Matrix4f().translation(20, 0, 0).mul(offset), posed.getBoneTransforms(mesh)[index]);
        assertNull(posed.getNodeTransform("Detached"));
    }

    @Test
    void staticImportsKeepTheirBakedMeshTransforms() {
        AssimpScene scene = fixture(ModelImportMode.STATIC);
        PoseSnapshot bind = PoseSnapshot.capture(1, scene, Map.of());
        PoseSnapshot overridden = PoseSnapshot.capture(2, scene, Map.of("Helper", new Matrix4f().translation(100, 0, 0)));
        assertEquals(bind.getBounds(), overridden.getBounds());
        double minimum = Double.POSITIVE_INFINITY, maximum = Double.NEGATIVE_INFINITY;
        for (AssimpMesh mesh : scene.getMeshes()) {
            for (int i = 0; i < mesh.getVertices().length; i += 3) {
                minimum = Math.min(minimum, mesh.getVertices()[i]);
                maximum = Math.max(maximum, mesh.getVertices()[i]);
            }
        }
        assertEquals(minimum, bind.getBounds().minX, 1e-5);
        assertEquals(maximum, bind.getBounds().maxX, 1e-5);
    }

    @Test
    void duplicateBindNodeNamesKeepIndependentInstancesAndFirstDepthFirstLookup() {
        ByteBuffer binary = ByteBuffer.allocate(36).order(ByteOrder.LITTLE_ENDIAN);
        for (float value : new float[]{0, 0, 0, 1, 0, 0, 0, 1, 0}) binary.putFloat(value);
        String json = """
                {"asset":{"version":"2.0"},
                 "buffers":[{"byteLength":36,"uri":"data:application/octet-stream;base64,%s"}],
                 "bufferViews":[{"buffer":0,"byteLength":36}],
                 "accessors":[{"bufferView":0,"componentType":5126,"count":3,"type":"VEC3","min":[0,0,0],"max":[1,1,0]}],
                 "meshes":[{"primitives":[{"attributes":{"POSITION":0}}]}],
                 "nodes":[{"name":"Root","translation":[1,0,0],"children":[1,3]},
                  {"name":"Duplicate","translation":[2,0,0],"children":[2]},
                  {"name":"FirstMesh","mesh":0},
                  {"name":"Duplicate","translation":[8,0,0],"children":[4]},
                  {"name":"SecondMesh","mesh":0}],"scenes":[{"nodes":[0]}],"scene":0}
                """.formatted(Base64.getEncoder().encodeToString(binary.array()));
        AssimpScene scene = new AssimpModelLoader().loadModelFromMemory("duplicates.gltf",
                json.getBytes(StandardCharsets.UTF_8), ModelImportMode.DYNAMIC);
        assertNotNull(scene);
        PoseSnapshot pose = PoseSnapshot.capture(1, scene, Map.of());
        var instances = pose.copyMeshInstances();
        assertEquals(2, instances.size());
        assertMatrix(new Matrix4f().translation(3, 0, 0), instances.get(0).transform());
        assertMatrix(new Matrix4f().translation(9, 0, 0), instances.get(1).transform());
        assertMatrix(new Matrix4f().translation(2, 0, 0), pose.copyLocalTransforms().get("Duplicate"));
        assertMatrix(new Matrix4f().translation(3, 0, 0), pose.getNodeTransform("Duplicate"));
        assertEquals(3, pose.getBounds().minX, 1e-5);
        assertEquals(10, pose.getBounds().maxX, 1e-5);
        assertTrue(pose.copyLocalOverrides().isEmpty());
        instances.get(0).transform().zero();
        assertMatrix(new Matrix4f().translation(3, 0, 0), pose.copyMeshInstances().get(0).transform());
        assertThrows(UnsupportedOperationException.class, instances::clear);

        Matrix4f override = new Matrix4f().translation(20, 0, 0);
        PoseSnapshot animated = PoseSnapshot.capture(2, scene, Map.of("Duplicate", override));
        override.zero();
        animated.copyLocalOverrides().get("Duplicate").zero();
        assertEquals(1, animated.copyLocalOverrides().size());
        assertMatrix(new Matrix4f().translation(20, 0, 0), animated.copyLocalOverrides().get("Duplicate"));
        for (var instance : animated.copyMeshInstances()) assertMatrix(new Matrix4f().translation(21, 0, 0), instance.transform());
    }

    private static AssimpMesh mesh(AssimpScene scene, String name) {
        return scene.getMeshes().stream().filter(mesh -> mesh.getName().equals(name)).findFirst().orElseThrow();
    }

    private static void assertMatrix(Matrix4f expected, Matrix4f actual) {
        assertNotNull(actual);
        assertTrue(expected.equals(actual, 1e-5f), () -> "Expected " + expected + " but was " + actual);
    }

    private static AssimpScene fixture(ModelImportMode mode) {
        ByteBuffer binary = ByteBuffer.allocate(412).order(ByteOrder.LITTLE_ENDIAN);
        for (float value : new float[]{0, 0, 0, 1, 0, 0, 0, 1, 0, 0, 0, 2}) binary.putFloat(value);
        for (short value : new short[]{0, 0, 0, 0, 1, 0, 0, 0, 0, 1, 0, 0, 0, 0, 0, 0}) binary.putShort(value);
        for (float value : new float[]{1, 0, 0, 0, 1, 0, 0, 0, .5f, .5f, 0, 0, 0, 0, 0, 0}) binary.putFloat(value);
        for (short value : new short[]{0, 1, 2, 0, 2, 3}) binary.putShort(value);
        for (Matrix4f matrix : new Matrix4f[]{new Matrix4f().translation(-8, 0, 0),
                new Matrix4f().translation(-3, -2, 0), new Matrix4f().translation(-9, 0, 0),
                new Matrix4f().translation(-4, -2, 0)}) {
            for (float value : matrix.get(new float[16])) binary.putFloat(value);
        }
        String data = Base64.getEncoder().encodeToString(binary.array());
        String json = """
                {"asset":{"version":"2.0"},
                 "buffers":[{"byteLength":412,"uri":"data:application/octet-stream;base64,%s"}],
                 "bufferViews":[{"buffer":0,"byteOffset":0,"byteLength":48},
                  {"buffer":0,"byteOffset":48,"byteLength":32},{"buffer":0,"byteOffset":80,"byteLength":64},
                  {"buffer":0,"byteOffset":144,"byteLength":12},{"buffer":0,"byteOffset":156,"byteLength":128},
                  {"buffer":0,"byteOffset":284,"byteLength":128}],
                 "accessors":[{"bufferView":0,"componentType":5126,"count":4,"type":"VEC3","min":[0,0,0],"max":[1,1,2]},
                  {"bufferView":1,"componentType":5123,"count":4,"type":"VEC4"},
                  {"bufferView":2,"componentType":5126,"count":4,"type":"VEC4"},
                  {"bufferView":3,"componentType":5123,"count":6,"type":"SCALAR"},
                  {"bufferView":4,"componentType":5126,"count":2,"type":"MAT4"},
                  {"bufferView":5,"componentType":5126,"count":2,"type":"MAT4"}],
                 "meshes":[{"name":"SkinA","primitives":[{"attributes":{"POSITION":0,"JOINTS_0":1,"WEIGHTS_0":2},"indices":3}]},
                  {"name":"SkinB","primitives":[{"attributes":{"POSITION":0,"JOINTS_0":1,"WEIGHTS_0":2},"indices":3}]},
                  {"name":"Static","primitives":[{"attributes":{"POSITION":0},"indices":3}]}],
                 "nodes":[{"name":"SceneRoot","translation":[1,0,0],"children":[1,4,5,6]},
                  {"name":"Helper","translation":[2,0,0],"children":[2,3]},
                  {"name":"BoneA","translation":[0,2,0]},{"name":"BoneB","translation":[5,0,0]},
                  {"name":"SkinMeshA","mesh":0,"skin":0},{"name":"SkinMeshB","mesh":1,"skin":1},
                  {"name":"StaticMesh","translation":[-4,0,0],"mesh":2}],
                 "skins":[{"joints":[3,2],"inverseBindMatrices":4},{"joints":[3,2],"inverseBindMatrices":5}],
                 "scenes":[{"nodes":[0]}],"scene":0}
                """.formatted(data);
        AssimpScene scene = new AssimpModelLoader().loadModelFromMemory("pose.gltf", json.getBytes(StandardCharsets.UTF_8), mode);
        assertNotNull(scene, "The generated glTF fixture must import");
        return scene;
    }
}
