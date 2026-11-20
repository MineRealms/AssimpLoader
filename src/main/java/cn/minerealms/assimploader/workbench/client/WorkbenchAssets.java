package cn.minerealms.assimploader.workbench.client;

import cn.minerealms.assimploader.model.AssimpScene;
import cn.minerealms.assimploader.render.ModelTextureCache;
import cn.minerealms.assimploader.workbench.io.WorkbenchProjectStore;
import cn.minerealms.assimploader.workbench.model.WorkbenchDocument;
import cn.minerealms.assimploader.resource.LocalModelResources;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.packs.resources.ResourceManager;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.Map;
import java.util.Set;

/** Copies resource-pack sources into the same portable import path as dropped files. */
public final class WorkbenchAssets {
    @FunctionalInterface public interface DependencyContributor {
        Set<ResourceLocation> dependencies(AssimpScene scene,ResourceManager resources)throws IOException;
    }
    private static final Map<String,DependencyContributor> CONTRIBUTORS=new LinkedHashMap<>();
    private WorkbenchAssets() {}
    public static synchronized void registerDependencyContributor(String id,DependencyContributor contributor){
        if(CONTRIBUTORS.putIfAbsent(java.util.Objects.requireNonNull(id),java.util.Objects.requireNonNull(contributor))!=null)
            throw new IllegalArgumentException("Duplicate model dependency contributor: "+id);
    }
    public static WorkbenchProjectStore.Project importLoaded(WorkbenchProjectStore store,AssimpScene scene,ResourceManager resources) throws IOException {
        if(scene.getSourcePath()!=null&&Files.isRegularFile(scene.getSourcePath()))return store.importAsset(scene.getSourcePath());
        var source=scene.getSourceResource();
        if(source==null)throw new IOException("This cached model has no source file; import its original FBX");
        Files.createDirectories(store.root());Path temporary=Files.createTempDirectory(store.root(),".resource-");
        try {
            var collector=new Collector(resources,temporary);
            Path model=collector.copy(source);
            for(var material:scene.getMaterials())for(String reference:WorkbenchDocument.MaterialOverride.from(scene,material).textures().values()){
                if(scene.findEmbeddedTextureIndex(reference)>=0)continue;
                var location=ModelTextureCache.resolveResource(source,reference);
                if(location!=null&&resources.getResource(location).isPresent())collector.copy(location);
            }
            for(var material:scene.getMaterials())for(String reference:new String[]{material.getNormalMapPath(),material.getSpecularMapPath()}){
                var location=ModelTextureCache.resolveResource(source,reference);
                if(location!=null&&resources.getResource(location).isPresent())collector.copy(location);
            }
            String path=source.getPath();int dot=path.lastIndexOf('.');
            var sidecar=cn.minerealms.assimploader.util.ResourceLocations.of(source.getNamespace(),(dot<0?path:path.substring(0,dot))+".instance.json");
            if(resources.getResource(sidecar).isPresent())collector.collectJson(sidecar);
            if(path.endsWith(".gltf"))collector.collectJson(source);
            java.util.List<DependencyContributor> contributors;
            synchronized(WorkbenchAssets.class){contributors=java.util.List.copyOf(CONTRIBUTORS.values());}
            for(var contributor:contributors)for(var dependency:contributor.dependencies(scene,resources))collector.collectJson(dependency);
            var index=new JsonObject();index.addProperty("version",1);index.addProperty("model",source.toString());
            var aliases=new JsonObject();collector.copied.forEach((key,value)->aliases.addProperty(key.toString(),temporary.relativize(value).toString().replace('\\','/')));
            index.add("resources",aliases);Files.writeString(LocalModelResources.indexPath(model),index.toString());
            return store.importAsset(model,temporary);
        } finally {
            try(var paths=Files.walk(temporary)){for(Path path:paths.sorted(Comparator.reverseOrder()).toList())Files.deleteIfExists(path);}
        }
    }
    private static final class Collector {
        private final ResourceManager resources;private final Path root;
        private final Map<ResourceLocation,Path> copied=new LinkedHashMap<>();
        private final Set<ResourceLocation> jsons=new LinkedHashSet<>();private long bytes;
        Collector(ResourceManager resources,Path root){this.resources=resources;this.root=root;}
        Path copy(ResourceLocation location)throws IOException{
            if(copied.containsKey(location))return copied.get(location);
            if(copied.size()>=4096)throw new IOException("Portable resource graph exceeds 4096 files");
            Path path=root.resolve(location.getNamespace()).resolve(location.getPath()).normalize();
            if(!path.startsWith(root))throw new IOException("Resource path escapes temporary import");
            Files.createDirectories(path.getParent());
            try(var input=resources.open(location);var output=Files.newOutputStream(path)){
                byte[] buffer=new byte[16384];int count;
                while((count=input.read(buffer))!=-1){bytes+=count;if(bytes>2L*1024*1024*1024)throw new IOException("Portable resources exceed 2 GiB");output.write(buffer,0,count);}
            }
            copied.put(location,path);return path;
        }
        void collectJson(ResourceLocation location)throws IOException{
            Path file=copy(location);if(!jsons.add(location))return;
            if(jsons.size()>512)throw new IOException("Portable JSON graph exceeds 512 files");
            collectReferences(LocalModelResources.readJson(file),location,0);
        }
        private void collectReferences(JsonElement value,ResourceLocation source,int depth)throws IOException{
            if(depth>32)throw new IOException("Portable JSON nesting exceeds 32 levels");
            if(value.isJsonObject()){for(var entry:value.getAsJsonObject().entrySet())collectReferences(entry.getValue(),source,depth+1);}
            else if(value.isJsonArray()){for(var child:value.getAsJsonArray())collectReferences(child,source,depth+1);}
            else if(value.isJsonPrimitive()&&value.getAsJsonPrimitive().isString()){
                String reference=value.getAsString();
                if(!reference.matches("(?i).+\\.(json|fbx|gltf|glb|obj|dae|png|jpg|jpeg|tga|dds|bin|mtl|ktx2)"))return;
                var dependency=ModelTextureCache.resolveResource(source,reference);
                if(dependency==null||resources.getResource(dependency).isEmpty())throw new IOException("Missing portable resource: "+reference+" referenced by "+source);
                if(reference.endsWith(".json")||reference.endsWith(".gltf"))collectJson(dependency);else copy(dependency);
            }
        }
    }
}
