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
    private record Entry(VertexBuffer buffer, AttributeLayout attributes) {}
    private static final Map<AssimpScene, Map<AssimpMesh, Entry>> SCENES = new IdentityHashMap<>();
    private static final Map<AnimationState,PoseEntry> POSES = new java.util.LinkedHashMap<>(16,.75f,true);
    private static final class PoseEntry {
        PoseSnapshot snapshot;
        final Map<AssimpMesh,Entry> meshes=new IdentityHashMap<>();
        long reservation;
    }
    public record PoseStatistics(int owners,int buffers,long residentBytes,long uploads,long uploadedBytes,
                                 long draws,long shadowDraws,long hits,long budgetFallbacks) {}
    private static long poseResident,poseUploads,poseUploadedBytes,poseDraws,poseShadowDraws,poseHits,poseFallbacks;
    private static final LocalMeshEncoder ENCODER = new LocalMeshEncoder();
    private static BufferBuilder scratch;
    private static boolean failed;
    private static long uploads, vertices, bytes, draws, shadowDraws;

    private OculusEntityBufferCache() {}

    /** Called only by world Showcase renderers, never GUI items or a preview framebuffer. */
    public static boolean render(AssimpScene scene, PoseStack poses, int light, int overlay) {
        return render(scene, SceneTransforms.instances(scene), poses, light, overlay, true);
    }

    /** Direct entity draws may animate node pivots while reusing immutable local-space vertex buffers. */
    public static boolean renderEntity(AssimpScene scene, List<SceneTransforms.Instance> instances,
                                       PoseStack poses, int light, int overlay) {
        return render(scene, instances, poses, light, overlay, false);
    }

    public static boolean renderPosed(AnimationState owner,PoseStack poses,int light,int overlay,boolean blockEntity) {
        if(owner==null || owner.isClosed() || failed || !OculusEntityDrawBridge.available()
                || !AssimpLoaderConfig.getOrDefault(AssimpLoaderConfig.CLIENT.oculusPoseCacheEnabled::get,true)
                || AssimpLoaderConfig.getOrDefault(AssimpLoaderConfig.CLIENT.optimizationLevel::get,
                    AssimpLoaderConfig.OptimizationLevel.AUTO)==AssimpLoaderConfig.OptimizationLevel.FALLBACK) return false;
        var snapshot=owner.getPoseSnapshot();var scene=owner.getScene();var configured=owner.getModelInstance();
        if(snapshot==null || !SceneTransforms.isOpaque(scene) || configured!=null&&configured.hasMaterialAnimation()) return false;
        RenderSystem.assertOnRenderThread();
        prunePosed();
        long budget=(long)AssimpLoaderConfig.getIntOrDefault(AssimpLoaderConfig.CLIENT.oculusPoseCacheMiB,128)*1024*1024;
        int capacity=AssimpLoaderConfig.getIntOrDefault(AssimpLoaderConfig.CLIENT.oculusPoseCacheInstances,128);
        int stride=OculusEntityDrawBridge.entityVertexStride();
        if(stride<=0) return false;
        long required=0;
        for(var mesh:scene.getMeshes()) required+=(long)mesh.getIndices().length*stride;
        if(required>budget) {poseFallbacks++;return false;}
        PoseEntry entry=POSES.get(owner);
        long previous=entry==null?0:entry.reservation;
        var eviction=POSES.entrySet().iterator();
        while(eviction.hasNext() && (poseResident-previous+required>budget || POSES.size()+(entry==null?1:0)>capacity)) {
            var candidate=eviction.next();
            if(candidate.getKey()==owner) continue;
            closePose(candidate.getValue());eviction.remove();
        }
        if(entry==null) {entry=new PoseEntry();POSES.put(owner,entry);}
        poseResident+=required-entry.reservation;entry.reservation=required;
        try(DrawState state=new DrawState();AutoCloseable phase=blockEntity
                ?OculusEntityDrawBridge.beginBlockEntity():OculusEntityDrawBridge.beginEntity()) {
            boolean rebuild=entry.snapshot!=snapshot;
            for(var value:entry.meshes.values()) rebuild|=!OculusEntityDrawBridge.isEntityFormat(value.buffer().getFormat());
            if(rebuild) {
                for(var mesh:scene.getMeshes()) entry.meshes.put(mesh,upload(scene,mesh,snapshot,entry.meshes.get(mesh)));
                entry.snapshot=snapshot;
            } else poseHits++;
            var instances=new ArrayList<SceneTransforms.Instance>();
            for(var instance:snapshot.copyMeshInstances()) instances.add(new SceneTransforms.Instance(instance.mesh(),
                    instance.mesh().hasBones()?new Matrix4f():instance.transform()));
            draw(scene,instances,poses,light,overlay,state,entry.meshes,true);
            if(configured!=null) configured.noteRenderPass(OculusCompat.isRenderingShadowPass());
            return true;
        } catch(Exception | LinkageError error) {
            release();failed=true;
            AssimpLoader.LOGGER.warn("Oculus posed buffers unavailable; using normal submission until reload",error);
            return false;
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
        var iterator=POSES.entrySet().iterator();
        while(iterator.hasNext()) {
            var entry=iterator.next();
            if(!enabled || entry.getKey().isClosed()) {closePose(entry.getValue());iterator.remove();}
        }
        long budget=(long)AssimpLoaderConfig.getIntOrDefault(AssimpLoaderConfig.CLIENT.oculusPoseCacheMiB,128)*1024*1024;
        int capacity=AssimpLoaderConfig.getIntOrDefault(AssimpLoaderConfig.CLIENT.oculusPoseCacheInstances,128);
        iterator=POSES.entrySet().iterator();
        while(iterator.hasNext() && (poseResident>budget || POSES.size()>capacity)) {
            closePose(iterator.next().getValue());iterator.remove();
        }
    }
    private static void closePose(PoseEntry entry) {
        entry.meshes.values().forEach(value->value.buffer().close());poseResident-=entry.reservation;
    }
    public static PoseStatistics poseStatistics() {
        return new PoseStatistics(POSES.size(),POSES.values().stream().mapToInt(value->value.meshes.size()).sum(),
                poseResident,poseUploads,poseUploadedBytes,poseDraws,poseShadowDraws,poseHits,poseFallbacks);
    }
    public static boolean isPoseCached(AnimationState owner) { return POSES.containsKey(owner); }
    public static boolean isPoseCacheFailed() { return failed; }

    private static boolean render(AssimpScene scene, List<SceneTransforms.Instance> instances,
                                  PoseStack poses, int light, int overlay, boolean blockEntity) {
        if (failed || scene.hasSkeleton() || !SceneTransforms.isOpaque(scene)
                || !OculusEntityDrawBridge.available()
                || AssimpLoaderConfig.getOrDefault(AssimpLoaderConfig.CLIENT.optimizationLevel::get,
                    AssimpLoaderConfig.OptimizationLevel.AUTO) == AssimpLoaderConfig.OptimizationLevel.FALLBACK) return false;
        RenderSystem.assertOnRenderThread();
        try (DrawState state = new DrawState(); AutoCloseable phase = blockEntity
                ? OculusEntityDrawBridge.beginBlockEntity() : OculusEntityDrawBridge.beginEntity()) {
            var entries = SCENES.computeIfAbsent(scene, ignored -> new IdentityHashMap<>());
            for (var instance : instances) {
                Entry entry = entries.get(instance.mesh());
                if (entry != null && !OculusEntityDrawBridge.isEntityFormat(entry.buffer().getFormat())) {
                    entry.buffer().close();
                    entries.remove(instance.mesh());
                }
                entries.computeIfAbsent(instance.mesh(), mesh -> upload(scene, mesh,null,null));
            }
            draw(scene,instances,poses,light,overlay,state,entries,false);
            return true;
        } catch (Exception | LinkageError error) {
            release();
            failed = true;
            AssimpLoader.LOGGER.warn("Oculus entity buffer cache unavailable; using normal entity submission until reload", error);
            return false;
        }
    }

    private static void draw(AssimpScene scene,List<SceneTransforms.Instance> instances,PoseStack poses,
                             int light,int overlay,DrawState state,Map<AssimpMesh,Entry> entries,boolean posed) {
            int[] ids = OculusEntityDrawBridge.currentEntityIds();
            Matrix4f view = new Matrix4f(RenderSystem.getModelViewMatrix()).mul(poses.last().pose());
            Matrix4f modelView = new Matrix4f();
            boolean shadow = OculusCompat.isRenderingShadowPass();
            for (var instance : instances) {
                Entry entry = entries.get(instance.mesh());
                OculusEntityDrawBridge.clearShaderBinding();
                var type = AssimpRenderTypes.entity(ModelTextureCache.getTexture(scene, instance.mesh()), false);
                type.setupRenderState();
                try {
                    ShaderInstance shader = RenderSystem.getShader();
                    if (shader == null) throw new IllegalStateException("No entity shader selected by Oculus");
                    entry.buffer().bind();
                    state.setAttributes(entry.attributes(), light, overlay, ids);
                    modelView.set(view).mul(instance.transform());
                    OculusEntityDrawBridge.bindShader(shader);
                    entry.buffer().drawWithShader(modelView, RenderSystem.getProjectionMatrix(), shader);
                    if(posed) {poseDraws++;if(shadow) poseShadowDraws++;}
                    else {draws++;if(shadow) shadowDraws++;}
                } finally {
                    OculusEntityDrawBridge.clearShaderBinding();
                    type.clearRenderState();
                }
            }
    }

    private static Entry upload(AssimpScene scene, AssimpMesh mesh,PoseSnapshot snapshot,Entry previous) {
        if (scratch == null) scratch = new BufferBuilder(1 << 20);
        scratch.begin(VertexFormat.Mode.TRIANGLES, DefaultVertexFormat.NEW_ENTITY);
        try {
            if(snapshot==null) ENCODER.encode(mesh, ModelTextureCache.material(scene, mesh), scratch);
            else {
                var geometry=CpuSkinning.geometry(snapshot,mesh);
                ENCODER.encode(mesh,geometry.positions(),geometry.normals(),ModelTextureCache.material(scene,mesh),scratch);
            }
        } catch (RuntimeException | LinkageError error) {
            scratch.discard();
            throw error;
        }
        BufferBuilder.RenderedBuffer data = scratch.end();
        VertexBuffer buffer = null;
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
            if(snapshot==null) {uploads++;vertices+=draw.vertexCount();bytes+=draw.vertexBufferSize();}
            else {poseUploads++;poseUploadedBytes+=draw.vertexBufferSize();}
            complete = true;
            if(snapshot==null) AssimpLoader.LOGGER.info("[OculusEntityCache] Uploaded {} vertices={} stride={} bytes={} attributes={}",
                    mesh.getName(), draw.vertexCount(), draw.format().getVertexSize(), draw.vertexBufferSize(), attributes);
            return new Entry(buffer, attributes);
        } finally {
            if (!transferred) data.release();
            if (!complete && buffer != null) buffer.close();
        }
    }

    public static Statistics statistics() {
        return new Statistics(SCENES.values().stream().mapToInt(Map::size).sum(), uploads, vertices, bytes, draws, shadowDraws);
    }

    public static void clear() {
        release();
        failed = false;
        uploads = vertices = bytes = draws = shadowDraws = 0;
        poseUploads=poseUploadedBytes=poseDraws=poseShadowDraws=poseHits=poseFallbacks=0;
    }

    private static void release() {
        for (var entries : SCENES.values()) for (Entry entry : entries.values()) entry.buffer().close();
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
