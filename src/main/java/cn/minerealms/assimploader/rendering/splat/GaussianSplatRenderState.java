package cn.minerealms.assimploader.rendering.splat;

import com.mojang.blaze3d.systems.RenderSystem;
import org.lwjgl.opengl.*;

/** Restores exact incoming state, including indexed storage bindings and buffer texture units. */
public final class GaussianSplatRenderState implements AutoCloseable {
    private final boolean ssbo;
    private final int program = GL11.glGetInteger(GL20.GL_CURRENT_PROGRAM);
    private final int vao = GL11.glGetInteger(GL30.GL_VERTEX_ARRAY_BINDING);
    private final int arrayBuffer = GL11.glGetInteger(GL15.GL_ARRAY_BUFFER_BINDING);
    private final int textureBuffer = GL11.glGetInteger(GL31.GL_TEXTURE_BUFFER);
    private final int activeTexture = GL11.glGetInteger(GL13.GL_ACTIVE_TEXTURE);
    private final int texture0;
    private final int texture1;
    private final int storage;
    private final int storage0;
    private final int storage1;
    private final long storageStart0;
    private final long storageStart1;
    private final long storageSize0;
    private final long storageSize1;
    private final boolean depth = GL11.glIsEnabled(GL11.GL_DEPTH_TEST);
    private final boolean depthMask = GL11.glGetBoolean(GL11.GL_DEPTH_WRITEMASK);
    private final int depthFunction = GL11.glGetInteger(GL11.GL_DEPTH_FUNC);
    private final boolean cull = GL11.glIsEnabled(GL11.GL_CULL_FACE);
    private final boolean blend = GL11.glIsEnabled(GL11.GL_BLEND);
    private final int srcRgb = GL11.glGetInteger(GL14.GL_BLEND_SRC_RGB);
    private final int dstRgb = GL11.glGetInteger(GL14.GL_BLEND_DST_RGB);
    private final int srcAlpha = GL11.glGetInteger(GL14.GL_BLEND_SRC_ALPHA);
    private final int dstAlpha = GL11.glGetInteger(GL14.GL_BLEND_DST_ALPHA);
    private final int equationRgb = GL11.glGetInteger(GL20.GL_BLEND_EQUATION_RGB);
    private final int equationAlpha = GL11.glGetInteger(GL20.GL_BLEND_EQUATION_ALPHA);

    public GaussianSplatRenderState(boolean useSSBO) {
        ssbo = useSSBO;
        RenderSystem.activeTexture(GL13.GL_TEXTURE0);
        texture0 = GL11.glGetInteger(GL31.GL_TEXTURE_BINDING_BUFFER);
        RenderSystem.activeTexture(GL13.GL_TEXTURE1);
        texture1 = GL11.glGetInteger(GL31.GL_TEXTURE_BINDING_BUFFER);
        if (ssbo) {
            storage = GL11.glGetInteger(GL43.GL_SHADER_STORAGE_BUFFER_BINDING);
            storage0 = GL30.glGetIntegeri(GL43.GL_SHADER_STORAGE_BUFFER_BINDING, 0);
            storage1 = GL30.glGetIntegeri(GL43.GL_SHADER_STORAGE_BUFFER_BINDING, 1);
            storageStart0 = GL32.glGetInteger64i(GL43.GL_SHADER_STORAGE_BUFFER_START, 0);
            storageStart1 = GL32.glGetInteger64i(GL43.GL_SHADER_STORAGE_BUFFER_START, 1);
            storageSize0 = GL32.glGetInteger64i(GL43.GL_SHADER_STORAGE_BUFFER_SIZE, 0);
            storageSize1 = GL32.glGetInteger64i(GL43.GL_SHADER_STORAGE_BUFFER_SIZE, 1);
        } else {
            storage = storage0 = storage1 = 0;
            storageStart0 = storageStart1 = storageSize0 = storageSize1 = 0;
        }
    }

    public static void setup() {
        RenderSystem.disableCull();
        RenderSystem.enableBlend();
        RenderSystem.blendFuncSeparate(GL11.GL_SRC_ALPHA, GL11.GL_ONE_MINUS_SRC_ALPHA,
                GL11.GL_ONE, GL11.GL_ONE_MINUS_SRC_ALPHA);
        GL20.glBlendEquationSeparate(GL14.GL_FUNC_ADD, GL14.GL_FUNC_ADD);
        RenderSystem.enableDepthTest();
        RenderSystem.depthFunc(GL11.GL_LEQUAL);
        RenderSystem.depthMask(false);
    }

    @Override public void close() {
        GL20.glUseProgram(program);
        GL30.glBindVertexArray(vao);
        GL15.glBindBuffer(GL15.GL_ARRAY_BUFFER, arrayBuffer);
        GL15.glBindBuffer(GL31.GL_TEXTURE_BUFFER, textureBuffer);
        RenderSystem.activeTexture(GL13.GL_TEXTURE0);
        GL11.glBindTexture(GL31.GL_TEXTURE_BUFFER, texture0);
        RenderSystem.activeTexture(GL13.GL_TEXTURE1);
        GL11.glBindTexture(GL31.GL_TEXTURE_BUFFER, texture1);
        RenderSystem.activeTexture(activeTexture);
        if (ssbo) {
            restoreStorage(0, storage0, storageStart0, storageSize0);
            restoreStorage(1, storage1, storageStart1, storageSize1);
            GL15.glBindBuffer(GL43.GL_SHADER_STORAGE_BUFFER, storage);
        }
        if (depth) RenderSystem.enableDepthTest(); else RenderSystem.disableDepthTest();
        RenderSystem.depthMask(depthMask);
        RenderSystem.depthFunc(depthFunction);
        if (cull) RenderSystem.enableCull(); else RenderSystem.disableCull();
        if (blend) RenderSystem.enableBlend(); else RenderSystem.disableBlend();
        RenderSystem.blendFuncSeparate(srcRgb, dstRgb, srcAlpha, dstAlpha);
        GL20.glBlendEquationSeparate(equationRgb, equationAlpha);
    }

    private static void restoreStorage(int index, int buffer, long start, long size) {
        if (buffer != 0 && size > 0) GL30.glBindBufferRange(GL43.GL_SHADER_STORAGE_BUFFER, index, buffer, start, size);
        else GL30.glBindBufferBase(GL43.GL_SHADER_STORAGE_BUFFER, index, buffer);
    }
}
