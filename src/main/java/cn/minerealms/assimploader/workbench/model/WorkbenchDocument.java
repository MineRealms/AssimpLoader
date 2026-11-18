package cn.minerealms.assimploader.workbench.model;

import cn.minerealms.assimploader.model.AssimpMaterial;
import cn.minerealms.assimploader.model.AssimpScene;
import com.google.gson.JsonObject;

import java.util.LinkedHashMap;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.stream.Collectors;

/** Immutable edits layered over an imported scene. Extension sections belong to consumer modules. */
public record WorkbenchDocument(int version, String assetId, String sourcePath, String contentHash,
                                ModelTransform transform, Map<String, NodeOverride> nodes,
                                Map<Integer, MaterialOverride> materials, Map<String, Socket> sockets,
                                AnimationPreview animation, JsonObject extensions) {
    public static final int VERSION = 1;

    public WorkbenchDocument {
        if (version != VERSION) throw new IllegalArgumentException("Unsupported project version: " + version);
        requireText(assetId, 256, "assetId"); requireText(sourcePath, 4096, "sourcePath");
        if (contentHash == null || !contentHash.matches("[a-f0-9]{64}|")) throw new IllegalArgumentException("Invalid SHA-256");
        Objects.requireNonNull(transform); Objects.requireNonNull(animation);
        nodes = Map.copyOf(nodes); materials = Map.copyOf(materials); sockets = Map.copyOf(sockets);
        if (nodes.size() > 65536 || materials.size() > 65536 || sockets.size() > 1024)
            throw new IllegalArgumentException("Project contains too many overrides");
        nodes.keySet().forEach(key -> requireText(key, 4096, "node"));
        materials.keySet().forEach(key -> { if (key < 0) throw new IllegalArgumentException("Negative material index"); });
        sockets.keySet().forEach(key -> {if (!key.matches("[a-zA-Z0-9_.:-]{1,128}")) throw new IllegalArgumentException("Invalid socket name");});
        extensions = Objects.requireNonNull(extensions).deepCopy();
        if (extensions.toString().length() > 1_048_576) throw new IllegalArgumentException("Extension data exceeds 1 MiB");
    }

    @Override public JsonObject extensions() { return extensions.deepCopy(); }

    public record NodeOverride(ModelTransform transform, boolean visible) {
        public static final NodeOverride DEFAULT = new NodeOverride(ModelTransform.IDENTITY, true);
        public NodeOverride { Objects.requireNonNull(transform); }
    }

    public enum AlphaMode { OPAQUE, MASK, BLEND }

    /** Texture keys: albedo, normal, specular, roughness, metallic, occlusion, emissive, orm. */
    public record MaterialOverride(float r, float g, float b, float a, float metallic, float roughness,
                                   float emission, AlphaMode alphaMode, float cutoff, boolean doubleSided,
                                   Map<String, String> textures) {
        public MaterialOverride {
            unit(r); unit(g); unit(b); unit(a); unit(metallic); unit(roughness); unit(cutoff);
            if (!Float.isFinite(emission) || emission < 0 || emission > 65504) throw new IllegalArgumentException("Invalid HDR emission");
            Objects.requireNonNull(alphaMode);
            textures = Map.copyOf(textures);
            if (textures.size() > 16) throw new IllegalArgumentException("Too many texture slots");
            textures.forEach((key,value) -> {requireText(key,64,"texture slot");requireText(value,4096,"texture path");});
        }
        public static MaterialOverride from(AssimpMaterial material) {
            float[] color = material.getDiffuseColor();
            var pbr = material.getPbrInputs();
            var textures = new LinkedHashMap<String, String>();
            texture(textures, "albedo", material.getDiffuseTexture());
            texture(textures, "normal", material.getNormalTexture());
            texture(textures, "specular", material.getSpecularTexture());
            if (pbr.roughness() != null) texture(textures,"roughness",pbr.roughness().path());
            if (pbr.metallic() != null) texture(textures,"metallic",pbr.metallic().path());
            if (pbr.occlusion() != null) texture(textures,"occlusion",pbr.occlusion().path());
            if (pbr.emissive() != null) texture(textures,"emissive",pbr.emissive().path());
            return new MaterialOverride(importColor(color[0]),importColor(color[1]),importColor(color[2]),importColor(color[3]),pbr.metallicFactor(),pbr.roughnessFactor(),
                    Math.max(pbr.emissiveR(),Math.max(pbr.emissiveG(),pbr.emissiveB())),
                    material.isTransparent() ? AlphaMode.BLEND : AlphaMode.OPAQUE,.1f,true,textures);
        }
        public static MaterialOverride from(AssimpScene scene,AssimpMaterial material) {
            var value=from(material);var textures=new LinkedHashMap<>(value.textures());
            for(String slot:List.of("normal","specular")) {
                if(textures.containsKey(slot))continue;
                String reference=slot.equals("normal")?material.getNormalMapPath():material.getSpecularMapPath();
                var file=SceneMetadata.resolveDependency(scene.getSourcePath(),reference);
                if(reference!=null&&(scene.findEmbeddedTextureIndex(reference)>=0||file!=null&&java.nio.file.Files.isRegularFile(file)))
                    textures.put(slot,reference);
            }
            return new MaterialOverride(value.r,value.g,value.b,value.a,value.metallic,value.roughness,value.emission,
                    value.alphaMode,value.cutoff,value.doubleSided,textures);
        }
        public MaterialOverride withSurface(float m, float rough, float glow) {
            return new MaterialOverride(r,g,b,a,m,rough,glow,alphaMode,cutoff,doubleSided,textures);
        }
        public MaterialOverride withColor(float red,float green,float blue,float alpha) {
            return new MaterialOverride(red,green,blue,alpha,metallic,roughness,emission,alphaMode,cutoff,doubleSided,textures);
        }
        private static void unit(float value) { if (!Float.isFinite(value)||value<0||value>1) throw new IllegalArgumentException("Material value must be in [0,1]"); }
        private static void texture(Map<String,String> target,String slot,String path) {if(path!=null&&!path.isBlank())target.put(slot,path);}
        private static float importColor(float value) {return Float.isFinite(value)?Math.max(0,Math.min(1,value)):1;}
    }

    public record Socket(String node, ModelTransform transform) {
        public Socket { requireText(node,4096,"socket node"); Objects.requireNonNull(transform); }
    }

    public record AnimationPreview(String clip, double time, float speed, boolean loop, boolean playing) {
        public static final AnimationPreview DEFAULT = new AnimationPreview("",0,1,true,false);
        public AnimationPreview {
            Objects.requireNonNull(clip);
            if(clip.length()>1024||!Double.isFinite(time)||time<0||time>1e10||!Float.isFinite(speed)||Math.abs(speed)>100)
                throw new IllegalArgumentException("Invalid animation preview state");
        }
    }

    public static WorkbenchDocument defaults(AssimpScene scene,String assetId,String sourcePath,String contentHash) {
        var materials = new LinkedHashMap<Integer,MaterialOverride>();
        for(int i=0;i<scene.getMaterialCount();i++)materials.put(i,MaterialOverride.from(scene,scene.getMaterials().get(i)));
        return new WorkbenchDocument(VERSION,assetId,sourcePath,contentHash,ModelTransform.IDENTITY,Map.of(),materials,
                Map.of(),AnimationPreview.DEFAULT,new JsonObject());
    }
    public WorkbenchDocument withTransform(ModelTransform value) { return copy(value,nodes,materials,sockets,animation,extensions); }
    public WorkbenchDocument withNode(String node, NodeOverride value) { var map=new LinkedHashMap<>(nodes);if(value==null)map.remove(node);else map.put(node,value);return copy(transform,map,materials,sockets,animation,extensions); }
    public WorkbenchDocument withMaterial(int material, MaterialOverride value) {var map=new LinkedHashMap<>(materials);if(value==null)map.remove(material);else map.put(material,value);return copy(transform,nodes,map,sockets,animation,extensions);}
    public WorkbenchDocument withSocket(String name, Socket value) {var map=new LinkedHashMap<>(sockets);if(value==null)map.remove(name);else map.put(name,value);return copy(transform,nodes,materials,map,animation,extensions);}
    public WorkbenchDocument withAnimation(AnimationPreview value) {return copy(transform,nodes,materials,sockets,value,extensions);}
    public WorkbenchDocument withExtension(String name, JsonObject value) {var extra=extensions();if(value==null)extra.remove(name);else extra.add(name,value.deepCopy());return copy(transform,nodes,materials,sockets,animation,extra);}
    public WorkbenchDocument withSource(String path,String hash) {return new WorkbenchDocument(version,assetId,path,hash,transform,nodes,materials,sockets,animation,extensions);}
    public List<String> validateReferences(SceneMetadata metadata) {
        var issues=new ArrayList<String>();
        var paths=metadata.nodes().stream().map(SceneMetadata.NodeInfo::path).collect(Collectors.toSet());
        var names=metadata.nodes().stream().map(SceneMetadata.NodeInfo::name).collect(Collectors.toSet());
        for(String node:nodes.keySet())if(!paths.contains(node)&&!names.contains(node))issues.add("Unknown edited node: "+node);
        for(var entry:sockets.entrySet())if(!paths.contains(entry.getValue().node())&&!names.contains(entry.getValue().node()))
            issues.add("Socket '"+entry.getKey()+"' references unknown node: "+entry.getValue().node());
        for(int material:materials.keySet())if(material>=metadata.materials().size())issues.add("Unknown edited material: "+material);
        if(!animation.clip().isEmpty()&&metadata.clips().stream().noneMatch(clip->clip.name().equals(animation.clip())))
            issues.add("Unknown animation clip: "+animation.clip());
        return List.copyOf(issues);
    }
    private WorkbenchDocument copy(ModelTransform t, Map<String,NodeOverride> n, Map<Integer,MaterialOverride> m,
                                   Map<String,Socket> s, AnimationPreview a,JsonObject e) {
        return new WorkbenchDocument(version,assetId,sourcePath,contentHash,t,n,m,s,a,e);
    }
    private static void requireText(String value,int max,String what) {
        if(value==null||value.isBlank()||value.length()>max||value.indexOf('\0')>=0)throw new IllegalArgumentException("Invalid "+what);
    }
}
