package cn.minerealms.assimploader.workbench.client;

import cn.minerealms.assimploader.material.PbrMaterialInputs;
import cn.minerealms.assimploader.model.AssimpMaterial;
import cn.minerealms.assimploader.model.AssimpScene;
import cn.minerealms.assimploader.render.ModelTextureCache;
import cn.minerealms.assimploader.workbench.model.WorkbenchDocument;
import java.util.HashMap;
import java.util.Map;

/** Texture changes invalidate one material. Numeric edits are uniforms, so dragging cannot rebuild textures. */
public final class WorkbenchMaterials implements AutoCloseable {
    private record Variant(Map<String,String> paths, AssimpMaterial material) {}
    private final AssimpScene scene;
    private final Map<Integer,Variant> variants = new HashMap<>();
    private final Map<Integer,Map<String,String>> sourcePaths = new HashMap<>();
    public WorkbenchMaterials(AssimpScene scene) { this.scene = scene; }
    public AssimpMaterial material(int index, WorkbenchDocument document) {
        if(index<0||index>=scene.getMaterialCount())return null;
        AssimpMaterial source=scene.getMaterials().get(index);
        var edit=document==null?null:document.materials().get(index);
        if(edit==null||edit.textures().equals(sourcePaths.computeIfAbsent(index,i->WorkbenchDocument.MaterialOverride.from(source).textures())))return source;
        Variant cached=variants.get(index);
        if(cached!=null&&cached.paths.equals(edit.textures()))return cached.material;
        if(cached!=null)ModelTextureCache.releaseMaterial(scene,cached.material);
        var p=source.getPbrInputs();var paths=edit.textures();
        var inputs=new PbrMaterialInputs(p.workflow(),ref(paths,"normal",p.normal()),ref(paths,"roughness",p.roughness()),
                ref(paths,"metallic",p.metallic()),ref(paths,"occlusion",p.occlusion()),ref(paths,"emissive",p.emissive()),
                p.roughnessFactor(),p.metallicFactor(),p.normalScale(),p.occlusionStrength(),p.emissiveR(),p.emissiveG(),p.emissiveB(),p.dielectricF0());
        var material=new AssimpMaterial(source,source.getDiffuseColor(),paths.get("albedo"),paths.get("normal"),paths.get("specular"),inputs,source.isTransparent());
        variants.put(index,new Variant(paths,material));return material;
    }
    private static PbrMaterialInputs.TextureRef ref(Map<String,String> paths,String slot,PbrMaterialInputs.TextureRef original) {
        String path=paths.get(slot);return path==null?null:new PbrMaterialInputs.TextureRef(path,original==null?0:original.channel(),0);
    }
    @Override public void close() {variants.values().forEach(value->ModelTextureCache.releaseMaterial(scene,value.material));variants.clear();}
}
