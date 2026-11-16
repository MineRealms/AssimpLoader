package cn.minerealms.assimploader.rendering;

import cn.minerealms.assimploader.core.AssimpModelLoader;
import cn.minerealms.assimploader.core.ModelImportMode;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class BatchFixtureTest {
    @Test void staticCrowdFixtureContainsOpaqueTriangleMeshesWithinArenaBudget() throws Exception {
        String path="/assets/assimploader/models/showcase/probe_textured.fbx";
        try(var input=getClass().getResourceAsStream(path)) {
            assertNotNull(input);
            var scene=new AssimpModelLoader().loadModelFromMemory(path,input.readAllBytes(),ModelImportMode.STATIC);
            assertFalse(scene.hasSkeleton());
            assertTrue(scene.getMaterials().stream().noneMatch(material->material.isTransparent()));
            long bytes=0;
            for(var mesh:scene.getMeshes()) {
                assertTrue(mesh.getVertexCount()>0);assertTrue(mesh.getIndices().length>0);assertEquals(0,mesh.getIndices().length%3);
                bytes+=(long)mesh.getVertexCount()*64+(long)mesh.getIndices().length*4;
            }
            assertTrue(bytes<1024*1024,"Static probe unexpectedly exceeds 1 MiB: "+bytes);
        }
    }
}
