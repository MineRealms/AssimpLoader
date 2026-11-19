package cn.minerealms.assimploader.workbench.client;

import cn.minerealms.assimploader.animation.PoseSnapshot;
import cn.minerealms.assimploader.material.PbrMaterialInputs;
import cn.minerealms.assimploader.model.*;
import cn.minerealms.assimploader.render.ModelTextureCache;
import cn.minerealms.assimploader.render.SceneTransforms;
import cn.minerealms.assimploader.workbench.model.*;
import com.mojang.blaze3d.vertex.PoseStack;
import java.util.*;
import org.joml.Matrix4f;

/** Accepted local overrides. Drafts stay in the screen and never leak into shared imported data. */
public final class WorkbenchRuntime {
    private static final Map<String,WorkbenchDocument> APPLIED=new LinkedHashMap<>();
    private static final Map<AssimpScene,Binding> BINDINGS=new WeakHashMap<>();
    private static final Map<Object,OwnerBinding> OWNERS=new WeakHashMap<>();
    private static final ThreadLocal<InstanceBinding> CURRENT=new ThreadLocal<>();
    private record OwnerBinding(AssimpScene scene,Binding binding) {}
    private record InstanceBinding(AssimpScene scene,Binding binding) {}
    private static long serial;
    private static final class Binding {
        final WorkbenchDocument doc;
        final Map<Integer,AssimpMaterial> materials=new HashMap<>();
        final Set<Integer> hidden=new HashSet<>();
        PoseSnapshot source,pose;
        Binding(WorkbenchDocument doc,AssimpScene scene){this.doc=doc;if(scene.getRootNode()!=null)visibility(scene.getRootNode(),"",0,true,doc,hidden);}
    }
    private WorkbenchRuntime(){}
    private static String key(AssimpScene scene){return scene.getSourceResource()!=null?scene.getSourceResource().toString():scene.getSourcePath()!=null?scene.getSourcePath().toAbsolutePath().normalize().toString():"memory:"+System.identityHashCode(scene);}
    public static void apply(AssimpScene scene,WorkbenchDocument document){
        APPLIED.put(key(scene),document);while(APPLIED.size()>128)APPLIED.remove(APPLIED.keySet().iterator().next());
        BINDINGS.entrySet().removeIf(entry->{if(key(entry.getKey()).equals(key(scene))){entry.getValue().materials.values().forEach(m->ModelTextureCache.releaseMaterial(entry.getKey(),m));return true;}return false;});
        cn.minerealms.assimploader.rendering.OculusEntityBufferCache.invalidateScene(scene);
    }
    /** Owner scopes also suppress source-wide overrides when this instance has no edits. */
    public static InstanceScope instance(Object owner,AssimpScene scene,WorkbenchDocument document){
        Objects.requireNonNull(owner);Objects.requireNonNull(scene);
        var owned=OWNERS.get(owner);Binding binding=owned==null?null:owned.binding();
        if(document==null){if(binding!=null)release(owner,scene);binding=null;}
        else if(binding==null||owned.scene()!=scene||!binding.doc.equals(document)){
            if(binding!=null)release(owner,scene);
            binding=new Binding(document,scene);OWNERS.put(owner,new OwnerBinding(scene,binding));
            if(owner instanceof cn.minerealms.assimploader.animation.AnimationState animation)
                cn.minerealms.assimploader.rendering.OculusEntityBufferCache.invalidateOwner(animation);
        }
        var previous=CURRENT.get();CURRENT.set(new InstanceBinding(scene,binding));return new InstanceScope(previous);
    }
    public static void release(Object owner,AssimpScene scene){
        var binding=OWNERS.remove(owner);
        if(binding!=null)binding.binding().materials.values().forEach(m->ModelTextureCache.releaseMaterial(binding.scene(),m));
        if(owner instanceof cn.minerealms.assimploader.animation.AnimationState animation)
            cn.minerealms.assimploader.rendering.OculusEntityBufferCache.invalidateOwner(animation);
    }
    public static final class InstanceScope implements AutoCloseable {
        private final InstanceBinding previous;private boolean closed;
        private InstanceScope(InstanceBinding previous){this.previous=previous;}
        @Override public void close(){if(!closed){closed=true;if(previous==null)CURRENT.remove();else CURRENT.set(previous);}}
    }
    public static WorkbenchDocument document(AssimpScene scene){var current=CURRENT.get();return current!=null&&current.scene()==scene?current.binding()==null?null:current.binding().doc:APPLIED.get(key(scene));}
    private static Binding binding(AssimpScene scene){var current=CURRENT.get();if(current!=null&&current.scene()==scene)return current.binding();var doc=document(scene);if(doc==null)return null;var old=BINDINGS.get(scene);if(old==null||old.doc!=doc){old=new Binding(doc,scene);BINDINGS.put(scene,old);}return old;}
    public static AssimpMaterial material(AssimpScene scene,int index){
        Binding binding=binding(scene);if(binding==null||index<0||index>=scene.getMaterialCount())return null;
        var edit=binding.doc.materials().get(index);if(edit==null)return null;
        return binding.materials.computeIfAbsent(index,i->{var original=scene.getMaterials().get(i);var p=original.getPbrInputs();var paths=edit.textures();
            var inputs=new PbrMaterialInputs(p.workflow()==PbrMaterialInputs.Workflow.NONE?PbrMaterialInputs.Workflow.METALLIC_ROUGHNESS:p.workflow(),ref(paths,"normal",p.normal()),ref(paths,"roughness",p.roughness()),ref(paths,"metallic",p.metallic()),
                    ref(paths,"occlusion",p.occlusion()),ref(paths,"emissive",p.emissive()),edit.roughness(),edit.metallic(),p.normalScale(),p.occlusionStrength(),edit.emission(),edit.emission(),edit.emission(),p.dielectricF0());
            return new AssimpMaterial(original,new float[]{edit.r(),edit.g(),edit.b(),edit.a()},paths.get("albedo"),paths.get("normal"),paths.get("specular"),inputs,edit.alphaMode()==WorkbenchDocument.AlphaMode.BLEND);
        });
    }
    private static PbrMaterialInputs.TextureRef ref(Map<String,String> paths,String slot,PbrMaterialInputs.TextureRef source){String path=paths.get(slot);return path==null?null:new PbrMaterialInputs.TextureRef(path,source==null?0:source.channel(),0);}
    public static boolean visible(AssimpScene scene,AssimpMesh mesh){Binding b=binding(scene);return b==null||!b.hidden.contains(mesh.getIndex());}
    public static List<SceneTransforms.Instance> instances(AssimpScene scene,List<SceneTransforms.Instance> instances){return document(scene)==null?instances:instances.stream().filter(i->visible(scene,i.mesh())).toList();}
    public static List<SceneTransforms.Instance> staticInstances(AssimpScene scene){
        var doc=document(scene);
        if(doc==null||doc.nodes().isEmpty())return instances(scene,SceneTransforms.instances(scene));
        var snapshot=pose(scene,null);
        return instances(scene,snapshot.copyMeshInstances().stream().map(i->new SceneTransforms.Instance(i.mesh(),i.transform())).toList());
    }
    public static PoseSnapshot pose(AssimpScene scene,PoseSnapshot source){
        Binding b=binding(scene);if(b==null||b.doc.nodes().isEmpty())return source;
        if(source!=null&&source==b.pose)return source;
        if(b.source==source&&b.pose!=null)return b.pose;
        var locals=source==null?new LinkedHashMap<String,Matrix4f>():new LinkedHashMap<>(source.copyLocalTransforms());
        if(scene.getRootNode()!=null)applyNodes(scene.getRootNode(),"",0,b.doc,locals);
        var morphs=new IdentityHashMap<AssimpMesh,float[]>();if(source!=null)for(var mesh:scene.getMeshes())if(mesh.hasMorphTargets())morphs.put(mesh,source.getMorphWeights(mesh));
        b.source=source;b.pose=PoseSnapshot.capture(++serial,scene,locals,morphs);return b.pose;
    }
    private static void applyNodes(AssimpNode node,String parent,int sibling,WorkbenchDocument doc,Map<String,Matrix4f> locals){
        String path=SceneMetadata.nodePath(parent,node.getName(),sibling);var edit=doc.nodes().getOrDefault(path,doc.nodes().get(node.getName()));
        if(edit!=null)locals.put(node.getName(),new Matrix4f(locals.getOrDefault(node.getName(),node.getTransformation())).mul(edit.transform().matrix()));
        for(int i=0;i<node.getChildren().size();i++)applyNodes(node.getChildren().get(i),path,i,doc,locals);
    }
    private static void visibility(AssimpNode node,String parent,int sibling,boolean visible,WorkbenchDocument doc,Set<Integer> hidden){
        String path=SceneMetadata.nodePath(parent,node.getName(),sibling);var edit=doc.nodes().getOrDefault(path,doc.nodes().get(node.getName()));
        if(edit!=null)visible&=edit.visible();if(!visible)for(int index:node.getMeshIndices())hidden.add(index);
        for(int i=0;i<node.getChildren().size();i++)visibility(node.getChildren().get(i),path,i,visible,doc,hidden);
    }
    public static Scope transform(AssimpScene scene,PoseStack stack){var doc=document(scene);if(doc==null)return new Scope(null);stack.pushPose();SceneTransforms.apply(stack,doc.transform().matrix());return new Scope(stack);}
    public static final class Scope implements AutoCloseable {private final PoseStack stack;private Scope(PoseStack stack){this.stack=stack;}public void close(){if(stack!=null)stack.popPose();}}
}
