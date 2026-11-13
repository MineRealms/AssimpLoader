package cn.minerealms.assimploader.rendering.splat;

import cn.minerealms.assimploader.config.AssimpLoaderConfig;
import cn.minerealms.assimploader.rendering.splat.data.GaussianSplatData;
import com.mojang.blaze3d.systems.RenderSystem;
import org.joml.Matrix4f;
import org.joml.Vector3f;
import org.lwjgl.opengl.GL;

import java.io.IOException;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.Map;

/** Standalone splat renderer. It is not registered as a world renderer or shader-pack injector. */
public final class GaussianSplatRenderer {
    private final GaussianSplatShader shader = new GaussianSplatShader();
    private final Map<GaussianSplatData, GaussianSplatVBO> cache = new LinkedHashMap<>(16, 0.75f, true);
    private final GaussianSplatSorter sorter = new GaussianSplatSorter();
    private int[] selected = new int[0];
    private boolean initialized;
    private boolean ssbo;

    public void initialize() throws IOException {
        RenderSystem.assertOnRenderThread();
        if (initialized) return;
        var capabilities = GL.getCapabilities();
        if (!capabilities.OpenGL33) throw new IOException("Gaussian splat renderer requires OpenGL 3.3");
        ssbo = capabilities.OpenGL43;
        try {
            shader.compile(ssbo);
            initialized = true;
        } catch (IOException | RuntimeException | Error failure) {
            shader.delete();
            throw failure;
        }
    }

    public void render(GaussianSplatData data, Matrix4f view, Matrix4f projection, Vector3f camera,
                       float fovX, float fovY, float focal, float scaleModifier) {
        RenderSystem.assertOnRenderThread();
        if (!initialized) throw new IllegalStateException("Renderer not initialized");
        if (data == null || data.getSplatCount() == 0) return;
        if (!view.isFinite() || !projection.isFinite() || !camera.isFinite()
                || !Float.isFinite(fovX) || !Float.isFinite(fovY) || !Float.isFinite(focal)
                || !Float.isFinite(scaleModifier) || fovX <= 0 || fovY <= 0 || fovX >= Math.PI
                || fovY >= Math.PI || focal <= 0 || scaleModifier <= 0) {
            throw new IllegalArgumentException("Invalid splat render parameters");
        }
        if (selected.length < data.getSplatCount()) selected = new int[data.getSplatCount()];
        int count = GaussianSplatLOD.selectIndices(data, camera, selected, GaussianSplatLOD.Settings.configured());
        if (count == 0) return;
        sorter.sort(data.getSplats(), view, selected, count);
        try (GaussianSplatRenderState ignored = new GaussianSplatRenderState(ssbo)) {
            GaussianSplatVBO vbo = cache.get(data);
            if (vbo == null) {
                vbo = new GaussianSplatVBO();
                vbo.upload(data, ssbo);
                cache.put(data, vbo);
                trimCache(data);
            }
            vbo.uploadOrder(sorter.indicesBuffer(), sorter.getSortedCount());
            GaussianSplatRenderState.setup();
            shader.bind();
            shader.setViewMatrix(view);
            shader.setProjectionMatrix(projection);
            shader.setHFovxyFocal((float) Math.tan(fovX / 2), (float) Math.tan(fovY / 2), focal);
            shader.setCameraPos(camera);
            shader.setShDim(data.getShDim());
            shader.setScaleModifier(scaleModifier);
            shader.setRenderMod(data.getShDegree());
            vbo.render();
        }
    }

    private void trimCache(GaussianSplatData current) {
        int limit;
        try { limit = AssimpLoaderConfig.CLIENT.splatCacheSize.get(); }
        catch (IllegalStateException unloaded) { limit = AssimpLoaderConfig.CLIENT.splatCacheSize.getDefault(); }
        Iterator<Map.Entry<GaussianSplatData, GaussianSplatVBO>> entries = cache.entrySet().iterator();
        while (cache.size() > Math.max(1, limit) && entries.hasNext()) {
            var entry = entries.next();
            if (entry.getKey() == current) continue;
            entry.getValue().delete();
            entries.remove();
        }
    }

    public void clearCache() {
        for (GaussianSplatVBO vbo : cache.values()) vbo.delete();
        cache.clear();
        selected = new int[0];
        sorter.clear();
    }

    public void delete() {
        clearCache();
        shader.delete();
        initialized = false;
    }
    public boolean isInitialized() { return initialized; }
}
