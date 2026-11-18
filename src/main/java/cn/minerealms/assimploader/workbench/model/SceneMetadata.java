package cn.minerealms.assimploader.workbench.model;

import cn.minerealms.assimploader.lookdev.preview.PreviewSceneData;
import cn.minerealms.assimploader.model.AssimpNode;
import cn.minerealms.assimploader.model.AssimpScene;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

/** Import diagnostics calculated once, independent of rendering and editor drafts. */
public record SceneMetadata(List<NodeInfo> nodes, List<MeshInfo> meshes, List<MaterialInfo> materials,
                            List<ClipInfo> clips, Bounds bounds, List<String> issues,
                            boolean skinned, int embeddedTextures, String sourceFormat, String sourceGenerator) {
    public SceneMetadata {
        nodes=List.copyOf(nodes);meshes=List.copyOf(meshes);materials=List.copyOf(materials);
        clips=List.copyOf(clips);issues=List.copyOf(issues);
    }
    public record NodeInfo(String path,String name,int depth,int meshCount) {}
    public record MeshInfo(int index,String name,int vertices,int triangles,int material,int bones,int morphTargets,boolean hasUv) {}
    public record MaterialInfo(int index,String name) {}
    public record ClipInfo(String name,double seconds,int channels) {}
    public record Bounds(float minX,float minY,float minZ,float maxX,float maxY,float maxZ) {
        public float width(){return maxX-minX;} public float height(){return maxY-minY;} public float depth(){return maxZ-minZ;}
    }

    /** Node paths are structural (name + sibling index), so equal imported names remain individually editable. */
    public static String nodePath(String parent,String name,int sibling) {
        return parent+"/"+name.replace("~","~0").replace("/","~1")+"#"+sibling;
    }

    public static SceneMetadata inspect(AssimpScene scene) {
        return inspect(scene,null);
    }

    public static SceneMetadata inspect(AssimpScene scene,WorkbenchDocument document) {
        var nodes=new ArrayList<NodeInfo>();
        if(scene.getRootNode()!=null)collect(scene.getRootNode(),"",0,0,nodes);
        var meshes=new ArrayList<MeshInfo>();
        var issues=new ArrayList<String>();
        for(var mesh:scene.getMeshes()) {
            meshes.add(new MeshInfo(mesh.getIndex(),mesh.getName(),mesh.getVertexCount(),mesh.getIndices().length/3,
                    mesh.getMaterialIndex(),mesh.getBoneNames().size(),mesh.getMorphTargets().size(),mesh.hasTexCoords()));
            if(!mesh.hasTexCoords())issues.add("Mesh '"+mesh.getName()+"' has no UV coordinates");
        }
        var materials=new ArrayList<MaterialInfo>();
        for(var material:scene.getMaterials()) {
            materials.add(new MaterialInfo(material.getIndex(),material.getName()));
            var configured=document==null?null:document.materials().get(material.getIndex());
            for(var texture:(configured==null?WorkbenchDocument.MaterialOverride.from(scene,material):configured).textures().values()) {
                if(scene.findEmbeddedTextureIndex(texture)>=0)continue;
                Path file=resolveDependency(scene.getSourcePath(),texture);
                if(scene.getSourceResource()==null&&(file==null||!Files.isRegularFile(file)))
                    issues.add("Material '"+material.getName()+"' missing texture: "+texture);
            }
            if(material.getPbrInputs().hasUnsupportedUv())issues.add("Material '"+material.getName()+"' uses a nonzero UV channel");
        }
        var clips=new ArrayList<ClipInfo>();
        for(var clip:scene.getAnimations()) {
            double rate=clip.getTicksPerSecond()>0?clip.getTicksPerSecond():25;
            clips.add(new ClipInfo(clip.getName(),clip.getDuration()/rate,clip.getChannels().size()));
        }
        var preview=new PreviewSceneData(scene);
        var center=preview.center();var size=preview.size();
        var bounds=new Bounds(center.x-size.x/2,center.y-size.y/2,center.z-size.z/2,
                center.x+size.x/2,center.y+size.y/2,center.z+size.z/2);
        if(meshes.isEmpty())issues.add("Model has no drawable meshes");
        if(Math.max(size.x,Math.max(size.y,size.z))>128)issues.add("Large imported dimensions; inspect units and target scale before applying");
        return new SceneMetadata(nodes,meshes,materials,clips,bounds,issues.stream().distinct().toList(),
                scene.hasSkeleton(),scene.getTextureCount(),scene.getSourceFormat(),scene.getSourceGenerator());
    }

    public static Path resolveDependency(Path source,String reference) {
        if(source==null||reference==null||reference.isBlank()||reference.startsWith("*"))return null;
        String portable=reference.replace('\\','/');
        try {
            Path path=Path.of(portable);
            if(path.isAbsolute())return path.toAbsolutePath().normalize();
            if(portable.contains(":"))return null;
            return source.toAbsolutePath().normalize().getParent().resolve(path).normalize();
        } catch(RuntimeException invalid){return null;}
    }
    private static void collect(AssimpNode node,String parent,int sibling,int depth,List<NodeInfo> target) {
        String path=nodePath(parent,node.getName(),sibling);
        target.add(new NodeInfo(path,node.getName(),depth,node.getMeshIndices().length));
        for(int i=0;i<node.getChildren().size();i++)collect(node.getChildren().get(i),path,i,depth+1,target);
    }
}
