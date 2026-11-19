package cn.minerealms.assimploader.workbench.client;

import cn.minerealms.assimploader.core.AssimpModelLoader;
import cn.minerealms.assimploader.core.ModelImportMode;
import cn.minerealms.assimploader.lookdev.preview.PreviewSceneData;
import cn.minerealms.assimploader.model.AssimpNode;
import cn.minerealms.assimploader.model.AssimpScene;
import cn.minerealms.assimploader.workbench.model.ModelTransform;
import cn.minerealms.assimploader.workbench.model.SceneMetadata;
import cn.minerealms.assimploader.workbench.model.WorkbenchDocument;
import com.google.gson.JsonObject;
import org.joml.Matrix4f;
import org.junit.jupiter.api.Test;

import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.charset.StandardCharsets;
import java.util.Base64;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

class WorkbenchPoseVisibilityTest {
    @Test void drawPlanKeepsEachSharedMeshOccurrenceAndCompatibleLegacyConstructor() {
        var scene=fixture(ModelImportMode.DYNAMIC);var data=new PreviewSceneData(scene);
        assertEquals(2,data.draws().size());var first=data.draws().get(0);var second=data.draws().get(1);
        assertEquals(first.meshIndex(),second.meshIndex());assertNotNull(first.node());assertNotNull(second.node());
        assertNotSame(first.node(),second.node());assertEquals(first.node().getName(),second.node().getName());
        assertSame(scene.findNodeByName("Left").getChildren().get(0),first.node());
        assertSame(scene.findNodeByName("Right").getChildren().get(0),second.node());
        assertNull(new PreviewSceneData.Draw(first.meshIndex(),new Matrix4f()).node());
    }

    @Test void hidingOneDuplicateNamedNodeDoesNotHideOrMoveItsOtherSharedMeshOccurrence() {
        var scene=fixture(ModelImportMode.DYNAMIC);var data=new PreviewSceneData(scene);var pose=new WorkbenchPose(scene);
        var first=data.draws().get(0);var second=data.draws().get(1);var original=document();
        var hidden=original.withNode(path(scene.getRootNode(),first.node()),hidden());
        pose.sample(hidden,0);
        assertFalse(pose.visible(first));assertTrue(pose.visible(second));assertTrue(pose.visible(first.meshIndex()));
        assertEquals(1,pose.draws().stream().filter(pose::visible).count());
        var shown=pose.draws().stream().filter(pose::visible).findFirst().orElseThrow();
        assertSame(second.node(),shown.node());assertTrue(second.transform().equals(shown.transform(),1e-6f));
        var legacy=new PreviewSceneData.Draw(first.meshIndex(),new Matrix4f());assertTrue(pose.visible(legacy));

        pose.sample(hidden.withNode(path(scene.getRootNode(),second.node()),hidden()),0);
        assertFalse(pose.visible(first));assertFalse(pose.visible(second));assertFalse(pose.visible(legacy));
        assertFalse(pose.visible(first.meshIndex()));assertEquals(0,pose.draws().stream().filter(pose::visible).count());

        pose.sample(original,0);
        assertTrue(pose.visible(first));assertTrue(pose.visible(second));assertEquals(2,pose.draws().stream().filter(pose::visible).count());
        assertSame(first.node(),data.draws().get(0).node());assertSame(second.node(),data.draws().get(1).node());
    }

    @Test void hiddenParentKeepsItsChildHiddenWhileTheOtherOccurrenceRemainsVisible() {
        var scene=fixture(ModelImportMode.DYNAMIC);var data=new PreviewSceneData(scene);var pose=new WorkbenchPose(scene);
        var left=scene.findNodeByName("Left");var first=data.draws().get(0);var second=data.draws().get(1);
        var edited=document().withNode(path(scene.getRootNode(),left),hidden())
                .withNode(path(scene.getRootNode(),first.node()),WorkbenchDocument.NodeOverride.DEFAULT);
        pose.sample(edited,0);
        assertFalse(pose.visible(first));assertTrue(pose.visible(second));assertEquals(1,pose.draws().stream().filter(pose::visible).count());
        assertFalse(pose.visible(new PreviewSceneData.Draw(first.meshIndex(),new Matrix4f(),left)));
    }

    @Test void bakedStaticDrawsUseMeshFallbackAndStillRespectAHiddenRoot() {
        var scene=fixture(ModelImportMode.STATIC);var data=new PreviewSceneData(scene);var pose=new WorkbenchPose(scene);
        assertFalse(data.draws().isEmpty());assertTrue(data.draws().stream().allMatch(draw->draw.node()==null));
        pose.sample(document(),0);assertTrue(pose.draws().stream().allMatch(pose::visible));
        pose.sample(document().withNode(path(scene.getRootNode(),scene.getRootNode()),hidden()),0);
        assertTrue(pose.draws().stream().noneMatch(pose::visible));
    }

    private static WorkbenchDocument.NodeOverride hidden(){return new WorkbenchDocument.NodeOverride(ModelTransform.IDENTITY,false);}
    private static WorkbenchDocument document(){return new WorkbenchDocument(1,"test:shared","assets/model.gltf","",ModelTransform.IDENTITY,Map.of(),Map.of(),Map.of(),WorkbenchDocument.AnimationPreview.DEFAULT,new JsonObject());}
    private static String path(AssimpNode root,AssimpNode target){var value=path(root,target,"",0);assertNotNull(value);return value;}
    private static String path(AssimpNode node,AssimpNode target,String parent,int sibling){
        String value=SceneMetadata.nodePath(parent,node.getName(),sibling);if(node==target)return value;
        for(int i=0;i<node.getChildren().size();i++){var found=path(node.getChildren().get(i),target,value,i);if(found!=null)return found;}
        return null;
    }
    private static AssimpScene fixture(ModelImportMode mode){
        ByteBuffer vertices=ByteBuffer.allocate(36).order(ByteOrder.LITTLE_ENDIAN);
        for(float value:new float[]{0,0,0,1,0,0,0,1,0})vertices.putFloat(value);
        String source="""
                {"asset":{"version":"2.0"},
                 "buffers":[{"byteLength":36,"uri":"data:application/octet-stream;base64,%s"}],
                 "bufferViews":[{"buffer":0,"byteLength":36}],
                 "accessors":[{"bufferView":0,"componentType":5126,"count":3,"type":"VEC3","min":[0,0,0],"max":[1,1,0]}],
                 "meshes":[{"primitives":[{"attributes":{"POSITION":0}}]}],
                 "nodes":[{"name":"Root","children":[1,3]},
                  {"name":"Left","children":[2]},{"name":"Twin","mesh":0},
                  {"name":"Right","children":[4]},{"name":"Twin","mesh":0,"translation":[5,0,0]}],
                 "scenes":[{"nodes":[0]}],"scene":0}
                """.formatted(Base64.getEncoder().encodeToString(vertices.array()));
        return new AssimpModelLoader().loadModelFromMemory("shared-visibility.gltf",source.getBytes(StandardCharsets.UTF_8),mode);
    }
}
