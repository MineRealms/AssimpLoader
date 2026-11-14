package cn.minerealms.assimploader.rendering;

import cn.minerealms.assimploader.AssimpLoader;
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
                entries.computeIfAbsent(instance.mesh(), mesh -> upload(scene, mesh));
            }
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
                    draws++;
                    if (shadow) shadowDraws++;
                } finally {
                    OculusEntityDrawBridge.clearShaderBinding();
                    type.clearRenderState();
                }
            }
            return true;
        } catch (Exception | LinkageError error) {
            release();
            failed = true;
            AssimpLoader.LOGGER.warn("Oculus entity buffer cache unavailable; using normal entity submission until reload", error);
            return false;
        }
    }

    private static Entry upload(AssimpScene scene, AssimpMesh mesh) {
        if (scratch == null) scratch = new BufferBuilder(1 << 20);
        scratch.begin(VertexFormat.Mode.TRIANGLES, DefaultVertexFormat.NEW_ENTITY);
        try {
            ENCODER.encode(mesh, ModelTextureCache.material(scene, mesh), scratch);
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
            buffer = new VertexBuffer(VertexBuffer.Usage.STATIC);
            buffer.bind();
            transferred = true;
            buffer.upload(data);
            // VertexBuffer.upload configures the VAO from the expanded ENTITY format.
            // Leave these per-instance attributes as constants instead of stale VBO data.
            buffer.bind();
            GL20.glDisableVertexAttribArray(attributes.overlay());
            GL20.glDisableVertexAttribArray(attributes.light());
            GL20.glDisableVertexAttribArray(attributes.entityIds());
            uploads++;
            vertices += draw.vertexCount();
            bytes += draw.vertexBufferSize();
            complete = true;
            AssimpLoader.LOGGER.info("[OculusEntityCache] Uploaded {} vertices={} stride={} bytes={} attributes={}",
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
    }

    private static void release() {
        for (var entries : SCENES.values()) for (Entry entry : entries.values()) entry.buffer().close();
        SCENES.clear();
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
