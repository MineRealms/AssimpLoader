package cn.minerealms.assimploader.workbench.client;

import cn.minerealms.assimploader.model.AssimpScene;
import cn.minerealms.assimploader.render.ModelTextureCache;
import cn.minerealms.assimploader.workbench.io.WorkbenchProjectStore;
import cn.minerealms.assimploader.workbench.model.WorkbenchDocument;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.packs.resources.ResourceManager;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Comparator;

/** Copies resource-pack sources into the same portable import path as dropped files. */
public final class WorkbenchAssets {
    private WorkbenchAssets() {}
    public static WorkbenchProjectStore.Project importLoaded(WorkbenchProjectStore store,AssimpScene scene,ResourceManager resources) throws IOException {
        if(scene.getSourcePath()!=null&&Files.isRegularFile(scene.getSourcePath()))return store.importAsset(scene.getSourcePath());
        var source=scene.getSourceResource();
        if(source==null)throw new IOException("This cached model has no source file; import its original FBX");
        Files.createDirectories(store.root());Path temporary=Files.createTempDirectory(store.root(),".resource-");
        try {
            Path model=copy(resources,source,temporary);
            for(var material:scene.getMaterials())for(String reference:WorkbenchDocument.MaterialOverride.from(material).textures().values()){
                if(scene.findEmbeddedTextureIndex(reference)>=0)continue;
                var location=ModelTextureCache.resolveResource(source,reference);
                if(location!=null&&resources.getResource(location).isPresent())copy(resources,location,temporary);
            }
            return store.importAsset(model);
        } finally {
            try(var paths=Files.walk(temporary)){for(Path path:paths.sorted(Comparator.reverseOrder()).toList())Files.deleteIfExists(path);}
        }
    }
    private static Path copy(ResourceManager resources,ResourceLocation location,Path temporary)throws IOException {
        Path path=temporary.resolve(location.getNamespace()).resolve(location.getPath()).normalize();
        if(!path.startsWith(temporary))throw new IOException("Resource path escapes temporary import");
        Files.createDirectories(path.getParent());try(var stream=resources.open(location)){Files.copy(stream,path,java.nio.file.StandardCopyOption.REPLACE_EXISTING);}return path;
    }
}
