package cn.minerealms.assimploader.workbench.client;

import cn.minerealms.assimploader.animation.AnimationState;
import cn.minerealms.assimploader.animation.PoseSnapshot;
import cn.minerealms.assimploader.core.AssimpModelLoader;
import cn.minerealms.assimploader.core.ModelImportMode;
import cn.minerealms.assimploader.model.AssimpMaterial;
import cn.minerealms.assimploader.model.AssimpScene;
import cn.minerealms.assimploader.render.ModelTextureCache;
import cn.minerealms.assimploader.workbench.model.ModelTransform;
import cn.minerealms.assimploader.workbench.model.WorkbenchDocument;
import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.vertex.PoseStack;
import org.joml.Matrix4f;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.charset.StandardCharsets;
import java.util.Base64;
import java.util.Map;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;

class WorkbenchRuntimeIsolationTest {
    @BeforeAll static void renderThread() {
        if (!RenderSystem.isOnRenderThread()) RenderSystem.initRenderThread();
    }

    @Test void twoOwnersOfOneImportedSceneKeepIndependentNodesMaterialsAndCachedPoses() {
        AssimpScene scene = scene();
        var a = new AnimationState("a", scene); a.initialize();
        var b = new AnimationState("b", scene); b.initialize();
        var source = PoseSnapshot.capture(10, scene, Map.of());
        var first = document(scene, 3, 1, 0, true);
        var second = document(scene, -4, 0, 1, false);
        PoseSnapshot bPose;
        AssimpMaterial bMaterial;
        var sourceNode = new Matrix4f(scene.getRootNode().find("Joint").getTransformation());
        var sourceColor = scene.getMaterials().get(0).getDiffuseColor();
        try {
            try (var ignored = WorkbenchRuntime.instance(a, scene, first)) {
                var aPose = WorkbenchRuntime.pose(scene, source);
                assertEquals(5, aPose.getNodeTransform("Joint").m30(), 1e-5);
                assertSame(aPose, WorkbenchRuntime.pose(scene, aPose), "Passing an already edited pose must not apply node offsets twice");
                assertEquals(5, WorkbenchRuntime.pose(scene, aPose).getNodeTransform("Joint").m30(), 1e-5);
                assertTrue(WorkbenchRuntime.visible(scene, scene.getMeshes().get(0)));
                assertArrayEquals(new float[]{1, 0, 0, 1}, ModelTextureCache.material(scene, scene.getMeshes().get(0)).getDiffuseColor());
                try (var nested = WorkbenchRuntime.instance(b, scene, second)) {
                    bPose = WorkbenchRuntime.pose(scene, source);
                    bMaterial = ModelTextureCache.material(scene, scene.getMeshes().get(0));
                    assertEquals(-2, bPose.getNodeTransform("Joint").m30(), 1e-5);
                    assertSame(bPose, WorkbenchRuntime.pose(scene, bPose));
                    assertFalse(WorkbenchRuntime.visible(scene, scene.getMeshes().get(0)));
                    assertArrayEquals(new float[]{0, 1, 0, 1}, bMaterial.getDiffuseColor());
                }
                assertSame(aPose, WorkbenchRuntime.pose(scene, source));
                assertSame(first, WorkbenchRuntime.document(scene));
            }
            var changed = document(scene, 8, .2f, .8f, true);
            try (var ignored = WorkbenchRuntime.instance(a, scene, changed)) {
                assertEquals(10, WorkbenchRuntime.pose(scene, source).getNodeTransform("Joint").m30(), 1e-5);
                assertArrayEquals(new float[]{.2f, .8f, 0, 1}, ModelTextureCache.material(scene, scene.getMeshes().get(0)).getDiffuseColor());
            }
            try (var ignored = WorkbenchRuntime.instance(b, scene, second)) {
                assertSame(bPose, WorkbenchRuntime.pose(scene, source), "A edits must not rebuild B's pose");
                assertSame(bMaterial, ModelTextureCache.material(scene, scene.getMeshes().get(0)), "A edits must not replace B's material variant");
                assertEquals(-2, bPose.getNodeTransform("Joint").m30(), 1e-5);
            }
            assertNull(WorkbenchRuntime.document(scene));
            assertSame(source, WorkbenchRuntime.pose(scene, source));
            assertNull(WorkbenchRuntime.material(scene, 0));
            assertEquals(sourceNode, scene.getRootNode().find("Joint").getTransformation());
            assertArrayEquals(sourceColor, scene.getMaterials().get(0).getDiffuseColor());
        } finally {
            WorkbenchRuntime.release(a, scene); WorkbenchRuntime.release(b, scene);
            a.close(); b.close();
        }
    }

    @Test void emptyInstanceMasksSourceWideEditsAndExceptionRestoresNestedScope() {
        var scene = scene();
        var global = document(scene, 100, 1, 0, false);
        WorkbenchRuntime.apply(scene, global);
        Object owner = new Object();
        var source = PoseSnapshot.capture(1, scene, Map.of());
        try (var ignored = WorkbenchRuntime.instance(owner, scene, null)) {
            assertNull(WorkbenchRuntime.document(scene));
            assertSame(source, WorkbenchRuntime.pose(scene, source));
            assertNull(WorkbenchRuntime.material(scene, 0));
            assertTrue(WorkbenchRuntime.visible(scene, scene.getMeshes().get(0)));
            var local = document(scene, 2, 0, 1, true);
            assertThrows(IllegalStateException.class, () -> {
                try (var nested = WorkbenchRuntime.instance(new Object(), scene, local)) {
                    assertSame(local, WorkbenchRuntime.document(scene));
                    throw new IllegalStateException("render consumer failed");
                }
            });
            assertNull(WorkbenchRuntime.document(scene));
        }
        assertSame(global, WorkbenchRuntime.document(scene));
        assertFalse(WorkbenchRuntime.visible(scene, scene.getMeshes().get(0)));
        assertEquals(102, WorkbenchRuntime.pose(scene, source).getNodeTransform("Joint").m30(), 1e-5);
    }

    @Test void rootTransformStackAndNestedScopesRestoreIndependently() {
        var scene = scene(); Object owner = new Object();
        var document = document(scene, 0, 1, 1, true).withTransform(new ModelTransform(4, 5, 6, 0, 0, 0, 2, 2, 2));
        var stack = new PoseStack(); Matrix4f original = new Matrix4f(stack.last().pose());
        try (var ignored = WorkbenchRuntime.instance(owner, scene, document)) {
            try (var transform = WorkbenchRuntime.transform(scene, stack)) {
                assertEquals(4, stack.last().pose().m30(), 1e-5);
                assertEquals(2, stack.last().pose().m00(), 1e-5);
            }
            assertEquals(original, stack.last().pose());
        } finally { WorkbenchRuntime.release(owner, scene); }
        assertNull(WorkbenchRuntime.document(scene));
        assertEquals(original, stack.last().pose());
    }

    @Test void ownerChangingSceneRebuildsDetachedMaterialAndPoseEvenForEqualDocument() {
        var firstScene = scene(); var secondScene = scene(); Object owner = new Object();
        var document = document(firstScene, 7, 1, 0, true);
        PoseSnapshot firstPose; AssimpMaterial firstMaterial;
        try (var ignored = WorkbenchRuntime.instance(owner, firstScene, document)) {
            firstPose = WorkbenchRuntime.pose(firstScene, PoseSnapshot.capture(1, firstScene, Map.of()));
            firstMaterial = WorkbenchRuntime.material(firstScene, 0);
        }
        try (var ignored = WorkbenchRuntime.instance(owner, secondScene, document)) {
            var pose = WorkbenchRuntime.pose(secondScene, PoseSnapshot.capture(1, secondScene, Map.of()));
            assertNotSame(firstPose, pose);
            assertSame(secondScene, pose.getScene());
            assertNotSame(firstMaterial, WorkbenchRuntime.material(secondScene, 0));
        } finally { WorkbenchRuntime.release(owner, secondScene); }
    }

    private static WorkbenchDocument document(AssimpScene scene, float dx, float red, float green, boolean visible) {
        var defaults = WorkbenchDocument.defaults(scene, "test:owner", "model.gltf", "");
        var material = defaults.materials().get(0).withColor(red, green, 0, 1);
        return defaults.withMaterial(0, material).withNode("Joint", new WorkbenchDocument.NodeOverride(
                new ModelTransform(dx, 0, 0, 0, 0, 0, 1, 1, 1), visible));
    }

    private static AssimpScene scene() {
        var binary = ByteBuffer.allocate(36).order(ByteOrder.LITTLE_ENDIAN);
        for (float value : new float[]{0, 0, 0, 1, 0, 0, 0, 1, 0}) binary.putFloat(value);
        String json = """
                {"asset":{"version":"2.0"},"buffers":[{"byteLength":36,"uri":"data:application/octet-stream;base64,%s"}],
                 "bufferViews":[{"buffer":0,"byteLength":36}],
                 "accessors":[{"bufferView":0,"componentType":5126,"count":3,"type":"VEC3","min":[0,0,0],"max":[1,1,0]}],
                 "materials":[{"name":"Body","pbrMetallicRoughness":{"baseColorFactor":[1,1,1,1]}}],
                 "meshes":[{"primitives":[{"attributes":{"POSITION":0},"material":0}]}],
                 "nodes":[{"name":"Root","children":[1]},{"name":"Joint","mesh":0,"translation":[2,3,4]}],
                 "scenes":[{"nodes":[0]}],"scene":0}
                """.formatted(Base64.getEncoder().encodeToString(binary.array()));
        return new AssimpModelLoader().loadModelFromMemory("owner-" + UUID.randomUUID() + ".gltf", json.getBytes(StandardCharsets.UTF_8), ModelImportMode.DYNAMIC);
    }
}
