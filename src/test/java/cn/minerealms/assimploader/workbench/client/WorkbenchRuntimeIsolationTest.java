package cn.minerealms.assimploader.workbench.client;

import cn.minerealms.assimploader.animation.AnimationState;
import cn.minerealms.assimploader.animation.PoseSnapshot;
import cn.minerealms.assimploader.core.AssimpModelLoader;
import cn.minerealms.assimploader.core.ModelImportMode;
import cn.minerealms.assimploader.model.AssimpMaterial;
import cn.minerealms.assimploader.model.AssimpScene;
import cn.minerealms.assimploader.render.ModelTextureCache;
import cn.minerealms.assimploader.render.SceneTransforms;
import cn.minerealms.assimploader.workbench.model.ModelTransform;
import cn.minerealms.assimploader.workbench.model.SceneMetadata;
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

    @Test void visibilityDistinguishesSharedMeshNodesWithIdenticalNamesAndTransforms() {
        var scene = scene("""
                [{"name":"Root","children":[1,2,3]},
                 {"name":"Shared","mesh":0}, {"name":"Shared","mesh":0},
                 {"name":"Group","children":[4]}, {"name":"Nested","mesh":0}]
                """);
        var root = scene.getRootNode();
        var shared = root.getChildren().stream().filter(node -> node.getName().equals("Shared")).toList();
        assertEquals(2, shared.size(), "The fixture must retain both same-named mesh nodes");
        var hidden = shared.get(0); var expected = shared.get(1);
        assertNotSame(hidden, expected);
        String path = SceneMetadata.nodePath(SceneMetadata.nodePath("", root.getName(), 0), hidden.getName(), root.getChildren().indexOf(hidden));
        var base = WorkbenchDocument.defaults(scene, "test:shared", "shared.gltf", "");
        var first = base.withNode(path, new WorkbenchDocument.NodeOverride(ModelTransform.IDENTITY, false))
                .withNode("Group", new WorkbenchDocument.NodeOverride(ModelTransform.IDENTITY, false));
        var second = base.withNode("Shared", new WorkbenchDocument.NodeOverride(ModelTransform.IDENTITY, false));
        Object a = new Object(), b = new Object();
        var source = PoseSnapshot.capture(1, scene, Map.of());
        var animated = source.copyMeshInstances().stream()
                .map(i -> new SceneTransforms.Instance(i.mesh(), i.transform(), i.node())).toList();
        assertEquals(3, animated.size());
        assertSame(hidden, animated.get(0).node());
        assertSame(expected, animated.get(1).node());
        assertEquals(animated.get(0).node().getName(), animated.get(1).node().getName());
        assertSame(animated.get(0).mesh(), animated.get(1).mesh());
        assertEquals(animated.get(0).transform(), animated.get(1).transform());
        try (var ignored = WorkbenchRuntime.instance(a, scene, first)) {
            assertTrue(WorkbenchRuntime.visible(scene, scene.getMeshes().get(0)), "A visible occurrence keeps geometry-only queries visible");
            assertEquals(java.util.List.of(expected), WorkbenchRuntime.instances(scene, SceneTransforms.instances(scene)).stream().map(SceneTransforms.Instance::node).toList());
            assertSame(expected, WorkbenchRuntime.instances(scene, SceneTransforms.instances(scene)).get(0).node());
            assertSame(expected, WorkbenchRuntime.instances(scene, animated).get(0).node());
            assertEquals(1, WorkbenchRuntime.staticInstances(scene).size());
            assertSame(expected, WorkbenchRuntime.staticInstances(scene).get(0).node());
            assertSame(source.copyMeshInstances().get(1).node(), expected);
            try (var nested = WorkbenchRuntime.instance(b, scene, second)) {
                var surviving = WorkbenchRuntime.instances(scene, animated);
                assertEquals(1, surviving.size());
                assertEquals("Nested", surviving.get(0).node().getName());
            }
            assertSame(expected, WorkbenchRuntime.instances(scene, animated).get(0).node(), "Nested owners must restore node visibility");
            assertEquals(1, WorkbenchRuntime.instances(scene, java.util.List.of(animated.get(1))).size(), "Filtering a subset does not renumber node occurrences");
            assertTrue(WorkbenchRuntime.instances(scene, java.util.List.of(animated.get(0))).isEmpty());
            assertEquals(1, WorkbenchRuntime.instances(scene, new java.util.ArrayList<>(java.util.List.of(animated.get(2), animated.get(1), animated.get(0)))).size(), "Filtering does not depend on submission order");
        } finally { WorkbenchRuntime.release(a, scene); WorkbenchRuntime.release(b, scene); }
        assertEquals(3, WorkbenchRuntime.instances(scene, animated).size());
    }

    @Test void hidingAnOccurrenceDoesNotRebuildOrMoveItsSameNamedSiblingPose() {
        var scene=scene("[{\"name\":\"Root\",\"children\":[1,2]},{\"name\":\"Shared\",\"mesh\":0,\"translation\":[2,0,0]},{\"name\":\"Shared\",\"mesh\":0,\"translation\":[7,0,0]}]");
        var root=scene.getRootNode();var hidden=root.getChildren().get(0);
        var path=SceneMetadata.nodePath(SceneMetadata.nodePath("",root.getName(),0),hidden.getName(),0);
        var document=WorkbenchDocument.defaults(scene,"test:visibility","model.gltf","").withNode(path,new WorkbenchDocument.NodeOverride(ModelTransform.IDENTITY,false));
        var original=PoseSnapshot.capture(1,scene,Map.of());
        var expected=original.copyMeshInstances().get(1).transform();
        try(var ignored=WorkbenchRuntime.instance(this,scene,document)) {
            assertSame(original,WorkbenchRuntime.pose(scene,original));
            var draws=WorkbenchRuntime.staticInstances(scene);
            assertEquals(1,draws.size());
            assertEquals(expected,draws.get(0).transform());
        } finally {WorkbenchRuntime.release(this,scene);}
    }

    @Test void doubleSidedOverridesAreDetachedAndIndependentOfBlendPassSelection() {
        var scene=scene();Object owner=new Object();var original=scene.getMaterials().get(0);
        var defaults=WorkbenchDocument.defaults(scene,"test:surface","model.gltf","");
        var material=defaults.materials().get(0);
        for(var alphaMode:WorkbenchDocument.AlphaMode.values())for(boolean doubleSided:new boolean[]{false,true}) {
            var value=new WorkbenchDocument.MaterialOverride(material.r(),material.g(),material.b(),.5f,
                    material.metallic(),material.roughness(),material.emission(),
                    alphaMode,
                    material.cutoff(),doubleSided,material.textures());
            try(var ignored=WorkbenchRuntime.instance(owner,scene,defaults.withMaterial(0,value))) {
                var detached=ModelTextureCache.material(scene,scene.getMeshes().get(0));
                assertNotSame(original,detached);
                assertEquals(doubleSided,detached.isDoubleSided());
                assertEquals(alphaMode==WorkbenchDocument.AlphaMode.BLEND||alphaMode==WorkbenchDocument.AlphaMode.SOURCE,detached.isTransparent());
                assertEquals(.5f,detached.getDiffuseColor()[3],"Authoring color remains unchanged");
                assertEquals(alphaMode==WorkbenchDocument.AlphaMode.BLEND||alphaMode==WorkbenchDocument.AlphaMode.SOURCE?.5f:1,detached.getRenderColor()[3],
                        "Baked OPAQUE/MASK coverage must not receive the material factor twice");
                assertEquals(.5f,detached.getAlphaPolicy().factor());
                assertEquals(material.cutoff(),detached.getAlphaPolicy().cutoff());
                assertSame(detached,ModelTextureCache.material(scene,scene.getMeshes().get(0)));
            }
        }
        WorkbenchRuntime.release(owner,scene);
        assertFalse(original.isDoubleSided(),"Editor policy must not alter the imported material");
        var legacy=new AssimpMaterial(original,new float[]{1,1,1,.5f},null,null,null,original.getPbrInputs(),false);
        assertTrue(legacy.isTransparent());
        assertTrue(legacy.isDoubleSided(),"The existing constructor preserves translucent no-cull behavior");
    }

    @Test void openingAnImportedSurfaceDoesNotForceItsTextureAlphaOpaque() {
        var scene=scene();var imported=scene.getMaterials().get(0);
        var document=WorkbenchDocument.defaults(scene,"test:source","model.gltf","");
        assertEquals(WorkbenchDocument.AlphaMode.SOURCE,document.materials().get(0).alphaMode());
        try(var ignored=WorkbenchRuntime.instance(this,scene,document)) {
            var variant=ModelTextureCache.material(scene,scene.getMeshes().get(0));
            assertFalse(variant.getAlphaPolicy().baked());
            assertEquals(imported.isTransparent(),variant.isTransparent());
            assertEquals(imported.isDoubleSided(),variant.isDoubleSided());
        } finally {WorkbenchRuntime.release(this,scene);}
    }

    private static WorkbenchDocument document(AssimpScene scene, float dx, float red, float green, boolean visible) {
        var defaults = WorkbenchDocument.defaults(scene, "test:owner", "model.gltf", "");
        var material = defaults.materials().get(0).withColor(red, green, 0, 1);
        return defaults.withMaterial(0, material).withNode("Joint", new WorkbenchDocument.NodeOverride(
                new ModelTransform(dx, 0, 0, 0, 0, 0, 1, 1, 1), visible));
    }

    @Test void socketUsesEditedAnimationAndOwnerTextureOriginsStayIndependent() {
        var scene=scene();var first=new Object();var second=new Object();
        var source=PoseSnapshot.capture(3,scene,Map.of());
        var doc=document(scene,3,1,0,true).withSocket("back",new WorkbenchDocument.Socket("Joint",ModelTransform.IDENTITY.withPosition(1,0,0)));
        var pathA=java.nio.file.Path.of("project-a/model.fbx").toAbsolutePath().normalize();
        var pathB=java.nio.file.Path.of("project-b/model.fbx").toAbsolutePath().normalize();
        AssimpMaterial bMaterial;
        try {
            try(var ignored=WorkbenchRuntime.instance(first,scene,doc,pathA)) {
                assertEquals(pathA,WorkbenchRuntime.textureSource(scene));
                assertEquals(6,WorkbenchRuntime.socket(scene,source,"back").m30(),1e-5);
                assertNull(WorkbenchRuntime.socket(scene,source,"unknown"));
            }
            try(var ignored=WorkbenchRuntime.instance(second,scene,doc,pathB)) {
                bMaterial=WorkbenchRuntime.material(scene,0);
                assertEquals(pathB,WorkbenchRuntime.textureSource(scene));
            }
            try(var ignored=WorkbenchRuntime.instance(first,scene,doc,pathB)) {
                assertEquals(pathB,WorkbenchRuntime.textureSource(scene));
                assertNotSame(bMaterial,WorkbenchRuntime.material(scene,0));
            }
            try(var ignored=WorkbenchRuntime.instance(second,scene,doc,pathB)) {assertSame(bMaterial,WorkbenchRuntime.material(scene,0));}
        } finally {WorkbenchRuntime.release(first,scene);WorkbenchRuntime.release(second,scene);}
    }

    private static AssimpScene scene() {
        return scene("[{\"name\":\"Root\",\"children\":[1]},{\"name\":\"Joint\",\"mesh\":0,\"translation\":[2,3,4]}]");
    }

    private static AssimpScene scene(String nodes) {
        var binary = ByteBuffer.allocate(36).order(ByteOrder.LITTLE_ENDIAN);
        for (float value : new float[]{0, 0, 0, 1, 0, 0, 0, 1, 0}) binary.putFloat(value);
        String json = """
                {"asset":{"version":"2.0"},"buffers":[{"byteLength":36,"uri":"data:application/octet-stream;base64,%s"}],
                 "bufferViews":[{"buffer":0,"byteLength":36}],
                 "accessors":[{"bufferView":0,"componentType":5126,"count":3,"type":"VEC3","min":[0,0,0],"max":[1,1,0]}],
                 "materials":[{"name":"Body","pbrMetallicRoughness":{"baseColorFactor":[1,1,1,1]}}],
                 "meshes":[{"primitives":[{"attributes":{"POSITION":0},"material":0}]}],
                 "nodes":%s,
                 "scenes":[{"nodes":[0]}],"scene":0}
                """.formatted(Base64.getEncoder().encodeToString(binary.array()), nodes);
        return new AssimpModelLoader().loadModelFromMemory("owner-" + UUID.randomUUID() + ".gltf", json.getBytes(StandardCharsets.UTF_8), ModelImportMode.DYNAMIC);
    }
}
