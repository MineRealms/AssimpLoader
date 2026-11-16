package cn.minerealms.assimploader.animation;

import cn.minerealms.assimploader.core.AssimpModelLoader;
import cn.minerealms.assimploader.core.ModelImportMode;
import cn.minerealms.assimploader.model.AssimpMesh;
import cn.minerealms.assimploader.model.AssimpScene;
import cn.minerealms.assimploader.rendering.CpuSkinning;
import cn.minerealms.assimploader.util.CoordinateSystemConverter.CoordinateSystem;
import org.joml.Matrix4f;
import org.junit.jupiter.api.Test;
import org.lwjgl.assimp.AIAnimMesh;
import org.lwjgl.assimp.AIMesh;
import org.lwjgl.assimp.AIVector3D;
import org.lwjgl.assimp.Assimp;
import org.lwjgl.system.MemoryStack;

import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.charset.StandardCharsets;
import java.util.Base64;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

public class MorphTargetTest {
    @Test
    void importsRealGltfDefaultsAndAnimationAfterNativeSceneIsReleased() {
        AssimpScene scene = fixture();
        AssimpMesh mesh = scene.getMeshes().get(0);
        assertTrue(mesh.hasMorphTargets());
        assertEquals(1, mesh.getMorphTargets().size());
        assertArrayEquals(new float[]{0.25f}, mesh.getDefaultMorphWeights());
        PoseSnapshot defaults = PoseSnapshot.capture(1, scene, Map.of());
        assertTrue(defaults.hasActiveMorphs(mesh));
        assertEquals(0.5f, CpuSkinning.geometry(defaults, mesh).positions()[0], 1e-5);
        assertEquals(4.5, defaults.getBounds().maxX, 1e-5);
        var channels = scene.getAnimations().get(0).getMorphChannels();
        assertEquals(1, channels.size());
        assertEquals(3, channels.get(0).getKeyCount());
        double ticks = scene.getAnimations().get(0).getTicksPerSecond();
        assertArrayEquals(new float[]{0.5f}, channels.get(0).sample(0.5 * ticks, 1), 1e-5f);
        assertArrayEquals(new float[]{1}, channels.get(0).sample(ticks, 1), 1e-5f);
        assertArrayEquals(new float[]{0}, channels.get(0).sample(2 * ticks, 1), 1e-5f);
    }

    @Test
    void zeroOneZeroNeverAccumulatesAndSnapshotsOwnWeights() {
        AssimpScene scene = fixture();
        AssimpMesh mesh = scene.getMeshes().get(0);
        float[] producer = {1};
        PoseSnapshot zero = PoseSnapshot.capture(0, scene, Map.of(), Map.of(mesh, new float[]{0}));
        PoseSnapshot one = PoseSnapshot.capture(1, scene, Map.of(), Map.of(mesh, producer));
        producer[0] = 0;
        one.getMorphWeights(mesh)[0] = 0;
        PoseSnapshot restored = PoseSnapshot.capture(2, scene, Map.of(), Map.of(mesh, new float[]{0}));
        assertArrayEquals(mesh.getVertices(), CpuSkinning.geometry(zero, mesh).positions());
        assertEquals(2, CpuSkinning.geometry(one, mesh).positions()[0], 1e-5);
        assertArrayEquals(mesh.getVertices(), CpuSkinning.geometry(restored, mesh).positions());
        assertEquals(6, one.getBounds().maxX, 1e-5);
        assertEquals(4, zero.getBounds().maxX, 1e-5);
        assertEquals(zero.getBounds(), restored.getBounds());
        assertFalse(zero.hasActiveMorphs());
        assertArrayEquals(new float[]{1}, one.getMorphWeights(mesh));
        assertThrows(IllegalArgumentException.class,
                () -> PoseSnapshot.capture(3, scene, Map.of(), Map.of(mesh, new float[]{Float.NaN})));
        assertThrows(IllegalArgumentException.class,
                () -> PoseSnapshot.capture(3, scene, Map.of(), Map.of(mesh, new float[0])));
    }

    @Test
    void morphingPrecedesSkinningAndBoundsContainTheSameResult() {
        AssimpScene scene = fixture(true);
        AssimpMesh mesh = scene.getMeshes().get(0);
        assertTrue(mesh.hasBones());
        PoseSnapshot pose = PoseSnapshot.capture(1, scene,
                Map.of("Joint", new Matrix4f().translation(5, 0, 0)), Map.of(mesh, new float[]{1}));
        assertEquals(7, CpuSkinning.geometry(pose, mesh).positions()[0], 1e-5);
        assertEquals(8, pose.getBounds().maxX, 1e-5);
        assertEquals(7, pose.getBounds().minX, 1e-5);
        assertEquals(0, mesh.getVertices()[0], 1e-5);
    }

    @Test
    void perInstancePlaybackSamplesMorphChannelsAndExplicitWeightsOverrideThem() {
        AssimpScene scene = fixture();
        AssimpMesh mesh = scene.getMeshes().get(0);
        AnimationState first = new AnimationState("morph-a", scene);
        AnimationState second = new AnimationState("morph-b", scene);
        first.initialize();
        second.initialize();
        first.update(0.5f);
        assertArrayEquals(new float[]{0.5f}, first.getPoseSnapshot().getMorphWeights(mesh), 1e-5f);
        assertArrayEquals(new float[]{0}, second.getPoseSnapshot().getMorphWeights(mesh), 1e-5f);
        first.setMorphWeights(mesh, new float[]{0.75f});
        first.update(0.25f);
        assertArrayEquals(new float[]{0.75f}, first.getPoseSnapshot().getMorphWeights(mesh), 1e-5f);
        first.resetMorphWeights();
        assertArrayEquals(new float[]{0.75f}, first.getPoseSnapshot().getMorphWeights(mesh), 1e-5f);
        first.update(0.25f);
        assertArrayEquals(new float[]{1}, first.getPoseSnapshot().getMorphWeights(mesh), 1e-5f);
        first.stop();
        assertArrayEquals(new float[]{0.25f}, first.getPoseSnapshot().getMorphWeights(mesh), 1e-5f);
        first.close();
        second.close();
    }

    @Test
    void relativeExtrapolationAndNormalizedWeightsHaveDistinctSemantics() {
        AssimpMesh relative = nativeMesh(Assimp.aiMorphingMethod_MORPH_RELATIVE);
        AssimpMesh normalized = nativeMesh(Assimp.aiMorphingMethod_MORPH_NORMALIZED);
        assertEquals(5, MorphDeformation.deform(relative, new float[]{2}).positions()[0], 1e-5);
        assertEquals(-1, MorphDeformation.deform(relative, new float[]{-1}).positions()[0], 1e-5);
        assertEquals(3, MorphDeformation.deform(normalized, new float[]{2}).positions()[0], 1e-5);
        assertEquals(1, MorphDeformation.deform(normalized, new float[]{-1}).positions()[0], 1e-5);
        float[] deltas = relative.getMorphTargets().get(0).copyPositionDeltas();
        deltas[0] = 100;
        assertEquals(3, MorphDeformation.deform(relative, new float[]{1}).positions()[0], 1e-5);
    }

    @Test
    void invalidNativeTargetCountsAreRejectedBeforeReadingStreams() {
        try (MemoryStack stack = MemoryStack.stackPush()) {
            AIMesh mesh = AIMesh.calloc(stack).mVertices(AIVector3D.calloc(1, stack));
            AIMesh.nmNumVertices(mesh.address(), 1);
            AIAnimMesh target = AIAnimMesh.calloc(stack).mNumVertices(2);
            mesh.mAnimMeshes(stack.pointers(target.address()));
            assertThrows(IllegalArgumentException.class, () -> new AssimpMesh(mesh, 0, CoordinateSystem.MINECRAFT_LEFT_HANDED));
        }
    }

    @Test
    void targetNormalsAreCopiedBlendedAndNormalized() {
        AssimpMesh copied;
        try (MemoryStack stack = MemoryStack.stackPush()) {
            AIVector3D.Buffer vertices = AIVector3D.calloc(1, stack);
            AIVector3D.Buffer normals = AIVector3D.calloc(1, stack);
            normals.get(0).set(0, 1, 0);
            AIVector3D.Buffer targetNormals = AIVector3D.calloc(1, stack);
            targetNormals.get(0).set(1, 0, 0);
            AIMesh mesh = AIMesh.calloc(stack).mVertices(vertices).mNormals(normals).mMethod(Assimp.aiMorphingMethod_MORPH_RELATIVE);
            AIMesh.nmNumVertices(mesh.address(), 1);
            AIAnimMesh target = AIAnimMesh.calloc(stack).mNumVertices(1).mNormals(targetNormals);
            mesh.mAnimMeshes(stack.pointers(target.address()));
            copied = new AssimpMesh(mesh, 0, CoordinateSystem.MINECRAFT_LEFT_HANDED);
            targetNormals.get(0).set(0, 0, 1);
        }
        float diagonal = (float) Math.sqrt(0.5);
        assertArrayEquals(new float[]{diagonal, diagonal, 0}, MorphDeformation.deform(copied, new float[]{0.5f}).normals(), 1e-5f);
        assertArrayEquals(new float[]{1, 0, 0}, MorphDeformation.deform(copied, new float[]{1}).normals(), 1e-5f);
        assertArrayEquals(new float[]{0, 1, 0}, copied.getNormals());
    }

    private static AssimpMesh nativeMesh(int method) {
        try (MemoryStack stack = MemoryStack.stackPush()) {
            AIVector3D.Buffer base = AIVector3D.calloc(1, stack);
            base.get(0).set(1, 0, 0);
            AIVector3D.Buffer replacement = AIVector3D.calloc(1, stack);
            replacement.get(0).set(3, 0, 0);
            AIMesh mesh = AIMesh.calloc(stack).mVertices(base).mMethod(method);
            AIMesh.nmNumVertices(mesh.address(), 1);
            AIAnimMesh target = AIAnimMesh.calloc(stack).mNumVertices(1).mVertices(replacement);
            mesh.mAnimMeshes(stack.pointers(target.address()));
            return new AssimpMesh(mesh, 0, CoordinateSystem.MINECRAFT_LEFT_HANDED);
        }
    }

    public static AssimpScene fixture() {
        return fixture(false);
    }

    private static AssimpScene fixture(boolean skinned) {
        ByteBuffer binary = ByteBuffer.allocate(skinned ? 232 : 96).order(ByteOrder.LITTLE_ENDIAN);
        for (float value : new float[]{0,0,0, 1,0,0, 0,1,0, 2,0,0, 2,0,0, 2,0,0, 0,1,2, 0,1,0}) binary.putFloat(value);
        if (skinned) {
            for (int i = 0; i < 12; i++) binary.putShort((short) 0);
            for (int i = 0; i < 3; i++) for (float weight : new float[]{1,0,0,0}) binary.putFloat(weight);
            for (float value : new float[]{1,0,0,0, 0,1,0,0, 0,0,1,0, 0,0,0,1}) binary.putFloat(value);
        }
        String json = """
                {"asset":{"version":"2.0"},
                 "buffers":[{"byteLength":%d,"uri":"data:application/octet-stream;base64,%s"}],
                 "bufferViews":[{"buffer":0,"byteOffset":0,"byteLength":36},
                  {"buffer":0,"byteOffset":36,"byteLength":36},
                  {"buffer":0,"byteOffset":72,"byteLength":12},
                  {"buffer":0,"byteOffset":84,"byteLength":12}%s],
                 "accessors":[{"bufferView":0,"componentType":5126,"count":3,"type":"VEC3","min":[0,0,0],"max":[1,1,0]},
                  {"bufferView":1,"componentType":5126,"count":3,"type":"VEC3"},
                  {"bufferView":2,"componentType":5126,"count":3,"type":"SCALAR","min":[0],"max":[2]},
                  {"bufferView":3,"componentType":5126,"count":3,"type":"SCALAR"}%s],
                 "meshes":[{"name":"MorphMesh","weights":[0.25],"extras":{"targetNames":["Stretch"]},
                  "primitives":[{"attributes":{"POSITION":0%s},"targets":[{"POSITION":1}]}]}],
                 %s
                 "nodes":[{"name":"MorphNode","mesh":0,"translation":[3,0,0]%s}%s],
                 "animations":[{"name":"Pulse","samplers":[{"input":2,"output":3,"interpolation":"LINEAR"}],
                  "channels":[{"sampler":0,"target":{"node":0,"path":"weights"}}]}],
                 "scenes":[{"nodes":%s}],"scene":0}
                """.formatted(binary.capacity(), Base64.getEncoder().encodeToString(binary.array()),
                skinned ? ",{\"buffer\":0,\"byteOffset\":96,\"byteLength\":24},{\"buffer\":0,\"byteOffset\":120,\"byteLength\":48},{\"buffer\":0,\"byteOffset\":168,\"byteLength\":64}" : "",
                skinned ? ",{\"bufferView\":4,\"componentType\":5123,\"count\":3,\"type\":\"VEC4\"},{\"bufferView\":5,\"componentType\":5126,\"count\":3,\"type\":\"VEC4\"},{\"bufferView\":6,\"componentType\":5126,\"count\":1,\"type\":\"MAT4\"}" : "",
                skinned ? ",\"JOINTS_0\":4,\"WEIGHTS_0\":5" : "",
                skinned ? "\"skins\":[{\"inverseBindMatrices\":6,\"joints\":[1]}]," : "",
                skinned ? ",\"skin\":0" : "", skinned ? ",{\"name\":\"Joint\"}" : "", skinned ? "[0,1]" : "[0]");
        AssimpScene scene = new AssimpModelLoader().loadModelFromMemory("morph_probe.gltf",
                json.getBytes(StandardCharsets.UTF_8), ModelImportMode.DYNAMIC);
        assertNotNull(scene);
        return scene;
    }
}
