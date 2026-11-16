package cn.minerealms.assimploader.render.base;

import cn.minerealms.assimploader.animation.AnimationState;
import cn.minerealms.assimploader.animation.PoseSnapshot;
import cn.minerealms.assimploader.core.AssimpModelLoader;
import cn.minerealms.assimploader.core.ModelImportMode;
import cn.minerealms.assimploader.model.AssimpMaterial;
import cn.minerealms.assimploader.model.AssimpMesh;
import cn.minerealms.assimploader.model.AssimpScene;
import cn.minerealms.assimploader.render.SceneTransforms;
import cn.minerealms.assimploader.rendering.CpuSkinning;
import cn.minerealms.assimploader.util.CoordinateSystemConverter;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.resources.ResourceLocation;
import org.joml.Matrix3f;
import org.joml.Matrix4f;
import org.junit.jupiter.api.Test;
import org.lwjgl.assimp.AIFace;
import org.lwjgl.assimp.AIMesh;
import org.lwjgl.assimp.AIVector3D;
import org.lwjgl.system.MemoryStack;

import java.util.ArrayList;
import java.util.Base64;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicInteger;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.charset.StandardCharsets;

import static org.junit.jupiter.api.Assertions.*;

class BaseAssimpRendererTest {
    @Test
    void adjacentTrianglesRemainSeparateTrianglesInEntityBatches() {
        try (MemoryStack stack = MemoryStack.stackPush()) {
            AIVector3D.Buffer vertices = AIVector3D.calloc(4, stack);
            vertices.get(0).set(0, 0, 0);
            vertices.get(1).set(1, 0, 0);
            vertices.get(2).set(1, 1, 0);
            vertices.get(3).set(0, 1, 0);
            AIFace.Buffer faces = AIFace.calloc(2, stack);
            faces.get(0).mIndices(stack.ints(0, 1, 2));
            faces.get(1).mIndices(stack.ints(0, 2, 3));
            AIMesh nativeMesh = AIMesh.calloc(stack).mNumVertices(4).mVertices(vertices).mFaces(faces);
            AssimpMesh mesh = new AssimpMesh(nativeMesh, 0,
                    CoordinateSystemConverter.CoordinateSystem.MINECRAFT_LEFT_HANDED);
            RecordingRenderer renderer = new RecordingRenderer();

            renderer.renderMesh(mesh, null, new PoseStack(), null, 0, 0);

            assertEquals(List.of(2, 1, 0, 3, 2, 0), renderer.emittedIndices);
        }
    }

    @Test
    void staticInstancesSubmitOpaqueMeshesBeforeGlassWithoutReorderingEachPass() {
        AssimpScene scene = skinnedGlassAndMetal();
        MaterialRecordingRenderer renderer = new MaterialRecordingRenderer();
        renderer.renderInstances(scene, glassFirstInstances(scene), new PoseStack(), ignored -> null, 0, 0);

        assertEquals(List.of(0.75f, 0.75f, 0.25f, 0.25f), renderer.reds);
        assertEquals(List.of(2f, 4f, 1f, 3f), renderer.translations);
    }

    @Test
    void cpuSkinnedInstancesSubmitOpaqueMeshesBeforeGlass() {
        AssimpScene scene = skinnedGlassAndMetal();
        AnimationState state = new AnimationState("transparent-test", scene);
        state.initialize();
        assertTrue(state.hasSkinnedMeshes());
        assertTrue(scene.getMeshes().stream().allMatch(mesh -> mesh.hasBones() && state.getBoneHierarchy(mesh) != null));
        MaterialRecordingRenderer renderer = new MaterialRecordingRenderer();

        renderer.renderInstances(scene, state, glassFirstInstances(scene), new PoseStack(), ignored -> null, 0, 0);

        assertEquals(List.of(0.75f, 0.75f, 0.25f, 0.25f), renderer.reds);
    }

    @Test
    void allMaterialPassesUseOneCapturedSnapshotWithoutReadingLivePlayers() {
        AssimpScene scene = skinnedGlassAndMetal();
        PoseSnapshot first = PoseSnapshot.capture(1, scene,
                Map.of("RootJoint", new Matrix4f().translation(2, 0, 0)));
        PoseSnapshot next = PoseSnapshot.capture(2, scene,
                Map.of("RootJoint", new Matrix4f().translation(9, 0, 0)));
        AtomicInteger captures = new AtomicInteger();
        AnimationState state = new AnimationState("transparent-test", scene) {
            @Override public PoseSnapshot getPoseSnapshot() {
                return captures.getAndIncrement() == 0 ? first : next;
            }
            @Override public cn.minerealms.assimploader.animation.AnimationPlayer getCurrentAnimation() {
                throw new AssertionError("Rendering must not read the mutable animation player");
            }
        };
        MaterialRecordingRenderer renderer = new MaterialRecordingRenderer();

        renderer.renderInstances(scene, state, glassFirstInstances(scene), new PoseStack(), ignored -> null, 0, 0);

        assertEquals(1, captures.get());
        assertEquals(List.of(2f, 2f, 2f, 2f), renderer.positions);
        assertEquals(List.of(0f, 0f, 0f, 0f), renderer.translations);
    }

    @Test
    void snapshotCacheSeparatesInstancesAndPreservesPreviouslyCompletedFrames() {
        AssimpScene scene = skinnedGlassAndMetal();
        AssimpMesh mesh = scene.getMeshes().get(0);
        PoseSnapshot first = PoseSnapshot.capture(7, scene,
                Map.of("RootJoint", new Matrix4f().translation(2, 0, 0)));
        PoseSnapshot otherInstance = PoseSnapshot.capture(7, scene,
                Map.of("RootJoint", new Matrix4f().translation(9, 0, 0)));

        var firstGeometry = CpuSkinning.geometry(first, mesh);
        var secondGeometry = CpuSkinning.geometry(otherInstance, mesh);
        assertSame(firstGeometry, CpuSkinning.geometry(first, mesh));
        assertNotSame(firstGeometry, secondGeometry);
        assertEquals(2f, firstGeometry.positions()[0], 1e-6f);
        assertEquals(9f, secondGeometry.positions()[0], 1e-6f);
        assertEquals(2f, firstGeometry.positions()[0], 1e-6f);
        CpuSkinning.clear();
        assertNotSame(firstGeometry, CpuSkinning.geometry(first, mesh));
    }

    @Test
    void resourceReplacementRejectsSnapshotsFromThePreviousScene() {
        AssimpScene oldScene = skinnedGlassAndMetal();
        AssimpScene newScene = skinnedGlassAndMetal();
        PoseSnapshot oldPose = PoseSnapshot.capture(1, oldScene, Map.of());
        AnimationState oldState = new AnimationState("transparent-test", oldScene) {
            @Override public PoseSnapshot getPoseSnapshot() { return oldPose; }
        };

        assertSame(oldPose, BaseAssimpRenderer.snapshotForScene(oldScene, oldState));
        assertNull(BaseAssimpRenderer.snapshotForScene(newScene, oldState));
        assertNull(BaseAssimpRenderer.snapshotForScene(newScene, null));
    }

    private static List<SceneTransforms.Instance> glassFirstInstances(AssimpScene scene) {
        AssimpMesh glass = scene.getMeshes().stream().filter(mesh -> scene.getMaterials().get(mesh.getMaterialIndex()).isTransparent()).findFirst().orElseThrow();
        AssimpMesh metal = scene.getMeshes().stream().filter(mesh -> !scene.getMaterials().get(mesh.getMaterialIndex()).isTransparent()).findFirst().orElseThrow();
        return List.of(new SceneTransforms.Instance(glass, new Matrix4f().translation(1, 0, 0)),
                new SceneTransforms.Instance(metal, new Matrix4f().translation(2, 0, 0)),
                new SceneTransforms.Instance(glass, new Matrix4f().translation(3, 0, 0)),
                new SceneTransforms.Instance(metal, new Matrix4f().translation(4, 0, 0)));
    }

    private static AssimpScene skinnedGlassAndMetal() {
        ByteBuffer buffer = ByteBuffer.allocate(172).order(ByteOrder.LITTLE_ENDIAN);
        for (float value : new float[]{0, 0, 0, 1, 0, 0, 0, 1, 0}) buffer.putFloat(value);
        for (int i = 0; i < 12; i++) buffer.putShort((short) 0);
        for (int i = 0; i < 3; i++) for (float weight : new float[]{1, 0, 0, 0}) buffer.putFloat(weight);
        for (float value : new float[]{1, 0, 0, 0, 0, 1, 0, 0, 0, 0, 1, 0, 0, 0, 0, 1}) buffer.putFloat(value);
        String json = """
                {"asset":{"version":"2.0"},
                 "buffers":[{"byteLength":172,"uri":"data:application/octet-stream;base64,%s"}],
                 "bufferViews":[{"buffer":0,"byteOffset":0,"byteLength":36},
                                {"buffer":0,"byteOffset":36,"byteLength":24},
                                {"buffer":0,"byteOffset":60,"byteLength":48},
                                {"buffer":0,"byteOffset":108,"byteLength":64}],
                 "accessors":[{"bufferView":0,"componentType":5126,"count":3,"type":"VEC3","min":[0,0,0],"max":[1,1,0]},
                              {"bufferView":1,"componentType":5123,"count":3,"type":"VEC4"},
                              {"bufferView":2,"componentType":5126,"count":3,"type":"VEC4"},
                              {"bufferView":3,"componentType":5126,"count":1,"type":"MAT4"}],
                 "materials":[{"name":"Glass","alphaMode":"BLEND","pbrMetallicRoughness":{"baseColorFactor":[0.25,0.5,0.75,0.24]}},
                              {"name":"Metal","pbrMetallicRoughness":{"baseColorFactor":[0.75,0.5,0.25,1]}}],
                 "meshes":[{"primitives":[{"attributes":{"POSITION":0,"JOINTS_0":1,"WEIGHTS_0":2},"material":0},
                                             {"attributes":{"POSITION":0,"JOINTS_0":1,"WEIGHTS_0":2},"material":1}]}],
                 "skins":[{"inverseBindMatrices":3,"joints":[1]}],
                 "nodes":[{"name":"Portal","mesh":0,"skin":0},{"name":"RootJoint"}],
                 "scenes":[{"nodes":[0,1]}],"scene":0}
                """.formatted(Base64.getEncoder().encodeToString(buffer.array()));
        AssimpScene scene = new AssimpModelLoader().loadModelFromMemory("glass-and-metal.gltf",
                json.getBytes(StandardCharsets.UTF_8), ModelImportMode.DYNAMIC);
        assertNotNull(scene);
        assertEquals(2, scene.getMeshes().size());
        return scene;
    }

    private static class MaterialRecordingRenderer extends BaseAssimpRenderer {
        private final List<Float> reds = new ArrayList<>();
        private final List<Float> translations = new ArrayList<>();
        private final List<Float> positions = new ArrayList<>();

        @Override protected String getModelName() { return "transparent-test"; }
        @Override protected ResourceLocation getTextureLocation(AssimpScene scene, AssimpMesh mesh, String name) { return null; }
        @Override protected RenderType getRenderType(AssimpMaterial material, ResourceLocation texture) { return null; }

        @Override
        protected void addVertex(VertexConsumer consumer, Matrix4f pose, Matrix3f normalMatrix,
                                 float[] vertices, float[] normals, float[] texCoords, int index,
                                 float r, float g, float b, float a, int light, int overlay) {
            if (index == 2) {
                reds.add(r);
                translations.add(pose.m30());
                positions.add(vertices[index * 3]);
            }
        }
    }

    private static class RecordingRenderer extends BaseAssimpRenderer {
        private final List<Integer> emittedIndices = new ArrayList<>();

        @Override
        protected String getModelName() {
            return "test";
        }

        @Override
        protected void addVertex(VertexConsumer consumer, Matrix4f pose, Matrix3f normalMatrix,
                                 float[] vertices, float[] normals, float[] texCoords, int vertexIndex,
                                 float red, float green, float blue, float alpha,
                                 int packedLight, int packedOverlay) {
            emittedIndices.add(vertexIndex);
        }
    }
}
