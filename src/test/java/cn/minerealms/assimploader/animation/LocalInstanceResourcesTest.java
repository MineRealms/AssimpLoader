package cn.minerealms.assimploader.animation;

import cn.minerealms.assimploader.core.AssimpModelLoader;
import cn.minerealms.assimploader.resource.LocalModelResources;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.*;

class LocalInstanceResourcesTest {
    @TempDir Path temp;
    @AfterEach void clear(){InstanceDefinitionResources.clear();}

    @Test void looseSidecarBindsItsRuntimeModelAndKeepsFileBytes()throws Exception{
        Path model=Files.writeString(temp.resolve("model.fbx"),"source");
        String json="{\"version\":1,\"model\":\"test:models/avatar/model.fbx\",\"initial\":\"idle\",\"states\":{\"idle\":{\"clip\":\"A\"}}}";
        Path sidecar=Files.writeString(temp.resolve("model.instance.json"),json);
        var definition=InstanceDefinitionResources.forFile(model).orElseThrow();
        assertEquals(AssimpModelLoader.fileIdentity(model),definition.getModel());
        try(var instance=definition.instantiate(AnimationTestScenes.create())){
            assertEquals("idle",instance.animation().getStateMachine().getCurrentStateName());
        }
        assertEquals(json,Files.readString(sidecar));
    }

    @Test void portableIndexResolvesNamespacedParentOutsideModelFolder()throws Exception{
        Files.writeString(temp.resolve("project.workbench.json"),"{}");
        Path assets=Files.createDirectories(temp.resolve("assets"));
        Path folder=Files.createDirectories(assets.resolve("test/models/avatar"));
        Path shared=Files.createDirectories(assets.resolve("common/instances"));
        Path model=Files.writeString(folder.resolve("model.fbx"),"source");
        Files.writeString(shared.resolve("base.json"),"{\"version\":1,\"model\":\"test:models/avatar/model.fbx\",\"initial\":\"idle\",\"states\":{\"idle\":{\"clip\":\"A\"}}}");
        Files.writeString(folder.resolve("model.instance.json"),"{\"parent\":\"common:instances/base.json\"}");
        Files.writeString(LocalModelResources.indexPath(model),"{\"version\":1,\"model\":\"test:models/avatar/model.fbx\",\"resources\":{\"common:instances/base.json\":\"common/instances/base.json\"}}");
        var definition=InstanceDefinitionResources.forFile(model).orElseThrow();
        try(var instance=definition.instantiate(AnimationTestScenes.create())){
            assertEquals("idle",instance.animation().getStateMachine().getCurrentStateName());
        }
        assertEquals(shared.resolve("base.json"),LocalModelResources.open(model).resolve("common:instances/base.json",model));
    }

    @Test void escapeCyclesAndMismatchedModelAreRejected()throws Exception{
        Path folder=Files.createDirectory(temp.resolve("asset"));
        Path model=Files.writeString(folder.resolve("model.fbx"),"source");
        Path sidecar=folder.resolve("model.instance.json");
        Files.writeString(sidecar,"{\"parent\":\"../outside.json\"}");
        assertThrows(RuntimeException.class,()->InstanceDefinitionResources.forFile(model));
        Files.writeString(sidecar,"{\"parent\":\"model.instance.json\"}");
        assertThrows(IllegalArgumentException.class,()->InstanceDefinitionResources.forFile(model));
        Files.writeString(sidecar,"{\"version\":1,\"model\":\"test:different.fbx\"}");
        assertThrows(RuntimeException.class,()->InstanceDefinitionResources.forFile(model));
        Files.writeString(LocalModelResources.indexPath(model),"{\"version\":1,\"resources\":{\"test:escape.json\":\"../outside.json\"}}");
        assertThrows(java.io.IOException.class,()->LocalModelResources.open(model));
    }
}
