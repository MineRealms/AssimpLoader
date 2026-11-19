package cn.minerealms.assimploader.rendering;

import cn.minerealms.assimploader.AssimpLoader;
import cn.minerealms.assimploader.animation.AnimationState;
import cn.minerealms.assimploader.animation.PoseSnapshot;
import cn.minerealms.assimploader.compat.OculusCompat;
import cn.minerealms.assimploader.compat.OculusEntityDrawBridge;
import cn.minerealms.assimploader.config.AssimpLoaderConfig;
import cn.minerealms.assimploader.model.AssimpMaterial;
import cn.minerealms.assimploader.model.AssimpMesh;
import cn.minerealms.assimploader.model.AssimpScene;
import cn.minerealms.assimploader.render.AssimpRenderTypes;
import cn.minerealms.assimploader.render.ModelTextureCache;
import cn.minerealms.assimploader.render.SceneTransforms;
import cn.minerealms.assimploader.render.base.BaseAssimpRenderer;
import com.mojang.blaze3d.platform.GlStateManager;
import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.vertex.BufferBuilder;
import com.mojang.blaze3d.vertex.BufferUploader;
import com.mojang.blaze3d.vertex.DefaultVertexFormat;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexBuffer;
import com.mojang.blaze3d.vertex.VertexFormat;
import com.mojang.blaze3d.vertex.VertexFormatElement;
//? if neoforge {
/*
import com.mojang.blaze3d.vertex.ByteBufferBuilder;
import com.mojang.blaze3d.vertex.MeshData;
*///?}
import net.minecraft.client.renderer.ShaderInstance;
import org.joml.Matrix4f;
import org.lwjgl.opengl.GL11;
import org.lwjgl.opengl.GL20;
import org.lwjgl.opengl.GL30;

import java.util.ArrayList;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Map;

/** Persistent Oculus ENTITY buffers; Minecraft and the shader pack retain control of every draw. */
public final class OculusEntityBufferCache {
    public record Statistics(int buffers, long uploads, long vertices, long bytes, long draws, long shadowDraws) {}
    private record Entry(VertexBuffer buffer, AttributeLayout attributes, OculusPoseCompute compute, AssimpMaterial material) implements AutoCloseable {
        @Override public void close() {
            if (compute != null) compute.close();
            buffer.close();
        }
    }
    private static final Map<AssimpScene, Map<AssimpMesh, Entry>> SCENES = new IdentityHashMap<>();
    private static final Map<AnimationState,PoseEntry> POSES = new java.util.LinkedHashMap<>(16,.75f,true);
    private static final class PoseEntry {
        PoseSnapshot snapshot;
        cn.minerealms.assimploader.workbench.model.WorkbenchDocument document;
        final Map<AssimpMesh,Entry> meshes=new IdentityHashMap<>();
        long reservation;
        boolean compute;
    }
    public record PoseStatistics(int owners,int buffers,long residentBytes,long uploads,long uploadedBytes,
                                 long draws,long shadowDraws,long hits,long budgetFallbacks) {}
    private static long poseResident,poseUploads,poseUploadedBytes,poseDraws,poseShadowDraws,poseHits,poseFallbacks;
    private static final LocalMeshEncoder ENCODER = new LocalMeshEncoder();
    private static BufferBuilder scratch;
    //? if neoforge {
    /*
    private static ByteBufferBuilder scratchBuffer;
    *///?}
    private static boolean failed;
    private static boolean computeFailed;
    private static long uploads, vertices, bytes, draws, shadowDraws;

    private OculusEntityBufferCache() {}

    /** Called only by world Showcase renderers, never GUI items or a preview framebuffer. */
    public static boolean render(AssimpScene scene, PoseStack poses, int light, int overlay) {
        try(var transform=cn.minerealms.assimploader.workbench.client.WorkbenchRuntime.transform(scene,poses)) {
            return render(scene, cn.minerealms.assimploader.workbench.client.WorkbenchRuntime.staticInstances(scene), poses, light, overlay, true);
        }
    }

    /** Direct entity draws may animate node pivots while reusing immutable local-space vertex buffers. */
    public static boolean renderEntity(AssimpScene scene, List<SceneTransforms.Instance> instances,
                                       PoseStack poses, int light, int overlay) {
        try(var transform=cn.minerealms.assimploader.workbench.client.WorkbenchRuntime.transform(scene,poses)) {
            return render(scene, instances, poses, light, overlay, false);
        }
    }

    public static boolean renderPosed(AnimationState owner,PoseStack poses,int light,int overlay,boolean blockEntity) {
        return renderPosed(owner,owner==null?null:owner.getRenderPose(net.minecraft.client.Minecraft.getInstance().getFrameTime()),
                poses,light,overlay,blockEntity);
    }

    public static boolean renderPosed(AnimationState owner,PoseSnapshot snapshot,PoseStack poses,int light,int overlay,boolean blockEntity) {
        if(owner==null)return false;
        var scene=owner.getScene();
        try(var transform=cn.minerealms.assimploader.workbench.client.WorkbenchRuntime.transform(scene,poses)) {
            return renderConfiguredPosed(owner,cn.minerealms.assimploader.workbench.client.WorkbenchRuntime.pose(scene,snapshot),poses,light,overlay,blockEntity);
        }
    }
    private static boolean renderConfiguredPosed(AnimationState owner,PoseSnapshot snapshot,PoseStack poses,int light,int overlay,boolean blockEntity) {
        if(cn.minerealms.assimploader.debug.MaterialDebugViews.active() || owner==null || owner.isClosed() || failed || !OculusEntityDrawBridge.available()
                || !AssimpLoaderConfig.getOrDefault(AssimpLoaderConfig.CLIENT.oculusPoseCacheEnabled::get,true)
                || AssimpLoaderConfig.getOrDefault(AssimpLoaderConfig.CLIENT.optimizationLevel::get,
                    AssimpLoaderConfig.OptimizationLevel.AUTO)==AssimpLoaderConfig.OptimizationLevel.FALLBACK) return false;
        var scene=owner.getScene();var configured=owner.getModelInstance();
        if(snapshot==null || snapshot.getScene()!=scene || configured!=null&&configured.hasMaterialAnimation()) return false;
        RenderSystem.assertOnRenderThread();
        prunePosed();
        long budget=(long)AssimpLoaderConfig.getIntOrDefault(AssimpLoaderConfig.CLIENT.oculusPoseCacheMiB,128)*1024*1024;
        int capacity=AssimpLoaderConfig.getIntOrDefault(AssimpLoaderConfig.CLIENT.oculusPoseCacheInstances,128);
        int stride=OculusEntityDrawBridge.entityVertexStride();
        if(stride<=0) return false;
        long required=0;
        for(var mesh:scene.getMeshes()) required+=(long)mesh.getIndices().length*stride;
        if(required>budget) {poseFallbacks++;return false;}
        boolean compute=!computeFailed && ComputeSkinning.available()
                && AssimpLoaderConfig.getOrDefault(AssimpLoaderConfig.CLIENT.gpuSkinningEnabled::get,true)
                && AssimpLoaderConfig.getOrDefault(AssimpLoaderConfig.CLIENT.computeSkinningEnabled::get,false);
        if(compute) {
            long extra=0;
            for(var mesh:scene.getMeshes()) if(OculusPoseCompute.supports(mesh)) extra+=ComputeSkinning.requiredBytes(mesh);
            long computeBudget=(long)AssimpLoaderConfig.getIntOrDefault(AssimpLoaderConfig.CLIENT.computeSkinningMiB,128)*1024*1024;
            if(extra==0 || required+extra>Math.min(budget,computeBudget)) compute=false;
            else {
                required+=extra;budget=Math.min(budget,computeBudget);
                capacity=Math.min(capacity,AssimpLoaderConfig.getIntOrDefault(AssimpLoaderConfig.CLIENT.computeSkinningOwners,128));
            }
        }
        PoseEntry entry=POSES.get(owner);
        if(entry!=null && entry.compute!=compute) {
            closePose(entry);POSES.remove(owner);entry=null;
        }
        long previous=entry==null?0:entry.reservation;
        var eviction=POSES.entrySet().iterator();
        while(eviction.hasNext() && (poseResident-previous+required>budget || POSES.size()+(entry==null?1:0)>capacity)) {
            var candidate=eviction.next();
            if(candidate.getKey()==owner) continue;
            closePose(candidate.getValue());eviction.remove();
        }
        if(entry==null) {entry=new PoseEntry();entry.compute=compute;POSES.put(owner,entry);}
        poseResident+=required-entry.reservation;entry.reservation=required;
        DrawProgress progress=new DrawProgress();
        try(DrawState state=new DrawState();AutoCloseable phase=blockEntity
                ?OculusEntityDrawBridge.beginBlockEntity():OculusEntityDrawBridge.beginEntity()) {
            var document=cn.minerealms.assimploader.workbench.client.WorkbenchRuntime.document(scene);
            boolean materialChanged=entry.document!=document;
            boolean rebuild=entry.snapshot!=snapshot||materialChanged;
            boolean formatChanged=entry.meshes.values().stream().anyMatch(value->!OculusEntityDrawBridge.isEntityFormat(value.buffer().getFormat()));
            if(formatChanged||materialChanged) {entry.meshes.values().forEach(Entry::close);entry.meshes.clear();rebuild=true;}
            if(rebuild) {
                for(var mesh:scene.getMeshes()) {
                    Entry previousEntry=entry.meshes.get(mesh);
                    if(compute && OculusPoseCompute.supports(mesh)) {
                        try {
                            if(previousEntry!=null && previousEntry.compute()!=null) previousEntry.compute().update(snapshot);
                            else entry.meshes.put(mesh,upload(scene,mesh,snapshot,previousEntry,true));
                        } catch(RuntimeException error) {throw new ComputeFailure(error);}
                    } else entry.meshes.put(mesh,upload(scene,mesh,snapshot,previousEntry,false));
                }
                entry.snapshot=snapshot;
                entry.document=document;
            } else poseHits++;
            var instances=new ArrayList<SceneTransforms.Instance>();
            for(var instance:snapshot.copyMeshInstances()) instances.add(new SceneTransforms.Instance(instance.mesh(),
                    instance.mesh().hasBones()?new Matrix4f():instance.transform()));
            draw(scene,instances,poses,light,overlay,state,entry.meshes,true,progress);
            cn.minerealms.assimploader.debug.DebugGeometryRenderer.render(scene,snapshot,configured,poses,
                    net.minecraft.client.Minecraft.getInstance().renderBuffers().bufferSource());
            if(configured!=null) configured.noteRenderPass(OculusCompat.isRenderingShadowPass());
            return true;
        } catch(ComputeFailure error) {
            computeFailed=true;
            closePose(entry);POSES.remove(owner);
            AssimpLoader.LOGGER.warn("Oculus GPU deformation unavailable; retaining CPU-deformed VBOs until reload",error.getCause());
            return false;
        } catch(Exception | LinkageError error) {
            release();failed=true;
            AssimpLoader.LOGGER.warn("Oculus posed buffers unavailable; using normal submission until reload",error);
            return progress.started;
        }
    }

    /** Client tick also removes closed owners when no further model is drawn. */
    public static void prunePosed() {
        if(POSES.isEmpty()) return;
        RenderSystem.assertOnRenderThread();
        boolean enabled=AssimpLoaderConfig.getOrDefault(AssimpLoaderConfig.CLIENT.oculusPoseCacheEnabled::get,true)
                && OculusCompat.isShadersActive()
                && AssimpLoaderConfig.getOrDefault(AssimpLoaderConfig.CLIENT.optimizationLevel::get,
                    AssimpLoaderConfig.OptimizationLevel.AUTO)!=AssimpLoaderConfig.OptimizationLevel.FALLBACK;
        boolean computeEnabled=!computeFailed
                && AssimpLoaderConfig.getOrDefault(AssimpLoaderConfig.CLIENT.gpuSkinningEnabled::get,true)
                && AssimpLoaderConfig.getOrDefault(AssimpLoaderConfig.CLIENT.computeSkinningEnabled::get,false);
        var iterator=POSES.entrySet().iterator();
        while(iterator.hasNext()) {
            var entry=iterator.next();
            if(!enabled || entry.getKey().isClosed() || entry.getValue().compute&&!computeEnabled) {
                closePose(entry.getValue());iterator.remove();
            }
        }
        long budget=(long)AssimpLoaderConfig.getIntOrDefault(AssimpLoaderConfig.CLIENT.oculusPoseCacheMiB,128)*1024*1024;
        int capacity=AssimpLoaderConfig.getIntOrDefault(AssimpLoaderConfig.CLIENT.oculusPoseCacheInstances,128);
        long computeBudget=(long)AssimpLoaderConfig.getIntOrDefault(AssimpLoaderConfig.CLIENT.computeSkinningMiB,128)*1024*1024;
        int computeCapacity=AssimpLoaderConfig.getIntOrDefault(AssimpLoaderConfig.CLIENT.computeSkinningOwners,128);
        long computeBytes=0;int computeOwners=0;
        for(var entry:POSES.values()) if(entry.compute) {computeBytes+=entry.reservation;computeOwners++;}
        iterator=POSES.entrySet().iterator();
        while(iterator.hasNext()) {
            var entry=iterator.next().getValue();
            if(poseResident>budget || POSES.size()>capacity
                    || entry.compute&&(computeBytes>computeBudget || computeOwners>computeCapacity)) {
                if(entry.compute) {computeBytes-=entry.reservation;computeOwners--;}
                closePose(entry);iterator.remove();
            }
        }
    }
    private static void closePose(PoseEntry entry) {
        entry.meshes.values().forEach(Entry::close);poseResident-=entry.reservation;
        entry.meshes.clear();entry.reservation=0;entry.snapshot=null;entry.document=null;
    }
    public static PoseStatistics poseStatistics() {
        return new PoseStatistics(POSES.size(),POSES.values().stream().mapToInt(value->value.meshes.size()).sum(),
                poseResident,poseUploads,poseUploadedBytes,poseDraws,poseShadowDraws,poseHits,poseFallbacks);
    }
    public static boolean isPoseCached(AnimationState owner) { return POSES.containsKey(owner); }
    public static boolean isPoseCacheFailed() { return failed; }
    public record GpuPoseStatistics(int buffers,long dispatches,boolean failed) {}
    public static GpuPoseStatistics gpuPoseStatistics() {
        int count=0;long dispatches=0;
        for(var entry:POSES.values()) for(var mesh:entry.meshes.values()) if(mesh.compute()!=null) {
            count++;dispatches+=mesh.compute().dispatches();
        }
        return new GpuPoseStatistics(count,dispatches,computeFailed);
    }
    private static final class ComputeFailure extends RuntimeException {
        ComputeFailure(RuntimeException cause) {super(cause);}
    }
    private static final class DrawProgress {boolean started;}

    private static boolean render(AssimpScene scene, List<SceneTransforms.Instance> instances,
                                  PoseStack poses, int light, int overlay, boolean blockEntity) {
        if (cn.minerealms.assimploader.debug.MaterialDebugViews.active() || failed || scene.hasSkeleton()
                || !OculusEntityDrawBridge.available()
                || AssimpLoaderConfig.getOrDefault(AssimpLoaderConfig.CLIENT.optimizationLevel::get,
                    AssimpLoaderConfig.OptimizationLevel.AUTO) == AssimpLoaderConfig.OptimizationLevel.FALLBACK) return false;
        RenderSystem.assertOnRenderThread();
        DrawProgress progress=new DrawProgress();
        try (DrawState state = new DrawState(); AutoCloseable phase = blockEntity
                ? OculusEntityDrawBridge.beginBlockEntity() : OculusEntityDrawBridge.beginEntity()) {
            var entries = SCENES.computeIfAbsent(scene, ignored -> new IdentityHashMap<>());
            for (var instance : instances) {
                Entry entry = entries.get(instance.mesh());
                if (entry != null && (!OculusEntityDrawBridge.isEntityFormat(entry.buffer().getFormat())
                        || entry.material()!=ModelTextureCache.material(scene,instance.mesh()))) {
                    entry.close();
                    entries.remove(instance.mesh());
                }
                entries.computeIfAbsent(instance.mesh(), mesh -> upload(scene, mesh,null,null,false));
            }
            draw(scene,instances,poses,light,overlay,state,entries,false,progress);
            cn.minerealms.assimploader.debug.DebugGeometryRenderer.render(scene,null,null,poses,
                    net.minecraft.client.Minecraft.getInstance().renderBuffers().bufferSource());
            return true;
        } catch (Exception | LinkageError error) {
            release();
            failed = true;
            AssimpLoader.LOGGER.warn("Oculus entity buffer cache unavailable; using normal entity submission until reload", error);
            return progress.started;
        }
    }

    private static void draw(AssimpScene scene,List<SceneTransforms.Instance> instances,PoseStack poses,
                             int light,int overlay,DrawState state,Map<AssimpMesh,Entry> entries,boolean posed,DrawProgress progress) {
            int[] ids = OculusEntityDrawBridge.currentEntityIds();
            Matrix4f view = new Matrix4f(RenderSystem.getModelViewMatrix()).mul(poses.last().pose());
            Matrix4f modelView = new Matrix4f();
            boolean shadow = OculusCompat.isRenderingShadowPass();
            for (int pass = 0; pass < 2; pass++) for (var instance : instances) {
                if(!cn.minerealms.assimploader.workbench.client.WorkbenchRuntime.visible(scene,instance.mesh()))continue;
                var selection = MeshDrawSelection.current();
                if (selection != null && !selection.contains(instance.mesh())) continue;
                var material = ModelTextureCache.material(scene, instance.mesh());
                boolean transparent = material != null && material.isTransparent();
                if (transparent != (pass == 1)) continue;
                Entry entry = entries.get(instance.mesh());
                OculusEntityDrawBridge.clearShaderBinding();
                var type = AssimpRenderTypes.entity(ModelTextureCache.getTexture(scene, instance.mesh()), transparent);
                type.setupRenderState();
                try {
                    ShaderInstance shader = RenderSystem.getShader();
                    if (shader == null) throw new IllegalStateException("No entity shader selected by Oculus");
                    entry.buffer().bind();
                    state.setAttributes(entry.attributes(), light, overlay, ids);
                    modelView.set(view).mul(instance.transform());
                    OculusEntityDrawBridge.bindShader(shader);
                    // Once drawing starts, a whole-scene fallback would blend glass twice.
                    progress.started=true;
                    if (selection == null) entry.buffer().drawWithShader(modelView, RenderSystem.getProjectionMatrix(), shader);
                    else selection.draw(entry.buffer(), instance.mesh(), modelView, RenderSystem.getProjectionMatrix(), shader);
                    if(posed) {poseDraws++;if(shadow) poseShadowDraws++;}
                    else {draws++;if(shadow) shadowDraws++;}
                } finally {
                    OculusEntityDrawBridge.clearShaderBinding();
                    type.clearRenderState();
                }
            }
    }

    private static Entry upload(AssimpScene scene, AssimpMesh mesh,PoseSnapshot snapshot,Entry previous,boolean gpu) {
        AssimpMaterial material=ModelTextureCache.material(scene,mesh);
        //? if forge {
        if (scratch == null) scratch = new BufferBuilder(1 << 20);
        scratch.begin(VertexFormat.Mode.TRIANGLES, DefaultVertexFormat.NEW_ENTITY);
        //?}
        //? if neoforge {
        /*
        if (scratchBuffer == null) scratchBuffer = new ByteBufferBuilder(1 << 20);
        scratchBuffer.clear();
        scratch = new BufferBuilder(scratchBuffer, VertexFormat.Mode.TRIANGLES, DefaultVertexFormat.NEW_ENTITY);
        *///?}
        try {
            if(snapshot==null || gpu) ENCODER.encode(mesh, material, scratch);
            else {
                var geometry=CpuSkinning.geometry(snapshot,mesh);
                ENCODER.encode(mesh,geometry.positions(),geometry.normals(),material,scratch);
            }
        } catch (RuntimeException | LinkageError error) {
            //? if forge {
            scratch.discard();
            //?}
            //? if neoforge {
            /*
            scratchBuffer.discard();
            *///?}
            throw error;
        }
        //? if forge {
        BufferBuilder.RenderedBuffer data = scratch.end();
        //?}
        //? if neoforge {
        /*
        MeshData data = scratch.build();
        *///?}
        VertexBuffer buffer = null;
        OculusPoseCompute compute = null;
        boolean transferred = false, complete = false;
        try {
            var draw = data.drawState();
            if (!OculusEntityDrawBridge.isEntityFormat(draw.format()))
                throw new IllegalStateException("Oculus did not extend the entity vertex format");
            AttributeLayout attributes = AttributeLayout.from(draw.format());
            buffer = previous==null?new VertexBuffer(snapshot==null?VertexBuffer.Usage.STATIC:VertexBuffer.Usage.DYNAMIC):previous.buffer();
            buffer.bind();
            transferred = true;
            buffer.upload(data);
            // VertexBuffer.upload configures the VAO from the expanded ENTITY format.
            // Leave these per-instance attributes as constants instead of stale VBO data.
            buffer.bind();
            GL20.glDisableVertexAttribArray(attributes.overlay());
            GL20.glDisableVertexAttribArray(attributes.light());
            GL20.glDisableVertexAttribArray(attributes.entityIds());
            if(gpu) {
                try {compute=new OculusPoseCompute(mesh,draw.format());}
                catch(java.io.IOException error) {throw new java.io.UncheckedIOException(error);}
                compute.update(snapshot);
            }
            if(snapshot==null) {uploads++;vertices+=draw.vertexCount();bytes+=draw.vertexBufferSize();}
            else {poseUploads++;poseUploadedBytes+=draw.vertexBufferSize();}
            complete = true;
            if(snapshot==null) AssimpLoader.LOGGER.info("[OculusEntityCache] Uploaded {} vertices={} stride={} bytes={} attributes={}",
                    mesh.getName(), draw.vertexCount(), draw.format().getVertexSize(), draw.vertexBufferSize(), attributes);
            return new Entry(buffer, attributes, compute, material);
        } finally {
            if (!transferred) data.release();
            if (!complete) {
                if(compute!=null) compute.close();
                if(buffer!=null) buffer.close();
            }
        }
    }

    public static Statistics statistics() {
        return new Statistics(SCENES.values().stream().mapToInt(Map::size).sum(), uploads, vertices, bytes, draws, shadowDraws);
    }

    /** One accepted material edit must not invalidate unrelated resident models. */
    public static void invalidateOwner(AnimationState owner) {
        RenderSystem.assertOnRenderThread();
        var entry=POSES.remove(owner);if(entry!=null)closePose(entry);
    }

    public static void invalidateScene(AssimpScene scene) {
        RenderSystem.assertOnRenderThread();
        var entries=SCENES.remove(scene);if(entries!=null)entries.values().forEach(Entry::close);
        var iterator=POSES.entrySet().iterator();
        while(iterator.hasNext()){var entry=iterator.next();if(entry.getKey().getScene()==scene){closePose(entry.getValue());iterator.remove();}}
        BufferUploader.invalidate();
    }

    public static void clear() {
        release();
        failed = false;
        computeFailed = false;
        uploads = vertices = bytes = draws = shadowDraws = 0;
        poseUploads=poseUploadedBytes=poseDraws=poseShadowDraws=poseHits=poseFallbacks=0;
    }

    private static void release() {
        for (var entries : SCENES.values()) for (Entry entry : entries.values()) entry.close();
        SCENES.clear();
        POSES.values().forEach(OculusEntityBufferCache::closePose);POSES.clear();poseResident=0;
        BufferUploader.invalidate();
    }

    record AttributeLayout(int overlay, int light, int entityIds) {
        static AttributeLayout from(VertexFormat format) {
            var names = new ArrayList<>(format.getElementMapping().keySet());
            int overlay = names.indexOf("UV1"), light = names.indexOf("UV2"), ids = names.indexOf("iris_Entity");
            require(format, overlay, VertexFormatElement.Type.SHORT, 2);
            require(format, light, VertexFormatElement.Type.SHORT, 2);
            require(format, ids, VertexFormatElement.Type.USHORT, 3);
            return new AttributeLayout(overlay, light, ids);
        }

        private static void require(VertexFormat format, int index, VertexFormatElement.Type type, int count) {
            if (index < 0 || format.getElements().get(index).getType() != type
                    || format.getElements().get(index).getCount() != count)
                throw new IllegalArgumentException("Unsupported Oculus entity attributes: " + format);
        }
    }

    private static final class LocalMeshEncoder extends BaseAssimpRenderer {
        @Override protected String getModelName() { return "oculus-cache"; }

        void encode(AssimpMesh mesh, AssimpMaterial material, BufferBuilder destination) {
            renderMesh(mesh, material, new PoseStack(), destination, 0, 0);
        }
        void encode(AssimpMesh mesh,float[] positions,float[] normals,AssimpMaterial material,BufferBuilder destination) {
            renderMesh(mesh,positions,normals,material,new PoseStack(),destination,0,0);
        }
    }

    private static final class DrawState implements AutoCloseable {
        private final GpuDrawState gpu = new GpuDrawState();
        private final ShaderInstance shader = RenderSystem.getShader();
        private final int albedo = RenderSystem.getShaderTexture(0);
        private final int drawFramebuffer = GL11.glGetInteger(GL30.GL_DRAW_FRAMEBUFFER_BINDING);
        private final int readFramebuffer = GL11.glGetInteger(GL30.GL_READ_FRAMEBUFFER_BINDING);
        private final Map<Integer, int[]> constants = new java.util.HashMap<>();

        DrawState() {
            // Iris listeners upload to the currently bound program, not to the program that owns their locations.
            OculusEntityDrawBridge.clearShaderBinding();
        }

        void setAttributes(AttributeLayout attributes, int light, int overlay, int[] ids) {
            save(attributes.overlay());
            save(attributes.light());
            save(attributes.entityIds());
            GL30.glVertexAttribI4i(attributes.overlay(), (short) overlay, (short) (overlay >>> 16), 0, 1);
            GL30.glVertexAttribI4i(attributes.light(), (short) light, (short) (light >>> 16), 0, 1);
            GL30.glVertexAttribI4ui(attributes.entityIds(), ids[0] & 0xffff, ids[1] & 0xffff, ids[2] & 0xffff, 1);
        }

        private void save(int attribute) {
            constants.computeIfAbsent(attribute, index -> {
                int[] values = new int[4];
                GL30.glGetVertexAttribIiv(index, GL20.GL_CURRENT_VERTEX_ATTRIB, values);
                return values;
            });
        }

        @Override public void close() {
            try {
                OculusEntityDrawBridge.clearShaderBinding();
                constants.forEach((index, values) -> GL30.glVertexAttribI4iv(index, values));
                RenderSystem.setShader(() -> shader);
                RenderSystem.setShaderTexture(0, albedo);
                GlStateManager._glBindFramebuffer(GL30.GL_DRAW_FRAMEBUFFER, drawFramebuffer);
                GlStateManager._glBindFramebuffer(GL30.GL_READ_FRAMEBUFFER, readFramebuffer);
            } finally {
                gpu.close();
                BufferUploader.invalidate();
            }
        }
    }
}
