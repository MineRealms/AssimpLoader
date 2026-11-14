package cn.minerealms.assimploader.rendering;

import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.shaders.ProgramManager;
import net.minecraft.client.Minecraft;
import org.lwjgl.opengl.GL11;
import org.lwjgl.opengl.GL13;
import org.lwjgl.opengl.GL15;
import org.lwjgl.opengl.GL20;
import org.lwjgl.opengl.GL30;
import org.lwjgl.opengl.GL31;

/** Restores both OpenGL bindings and Minecraft's cached state after an immediate entity draw. */
final class GpuDrawState implements AutoCloseable {
    private final int program = GL11.glGetInteger(GL20.GL_CURRENT_PROGRAM);
    private final int vao = GL11.glGetInteger(GL30.GL_VERTEX_ARRAY_BINDING);
    private final int arrayBuffer = GL11.glGetInteger(GL15.GL_ARRAY_BUFFER_BINDING);
    private final int textureBuffer = GL11.glGetInteger(GL31.GL_TEXTURE_BUFFER);
    private final int activeTexture = GL11.glGetInteger(GL13.GL_ACTIVE_TEXTURE);
    private final boolean blend = GL11.glIsEnabled(GL11.GL_BLEND);
    private final boolean cull = GL11.glIsEnabled(GL11.GL_CULL_FACE);
    private final boolean depth = GL11.glIsEnabled(GL11.GL_DEPTH_TEST);
    private final boolean depthMask = GL11.glGetBoolean(GL11.GL_DEPTH_WRITEMASK);
    private final int depthFunction = GL11.glGetInteger(GL11.GL_DEPTH_FUNC);
    private final int overlay = RenderSystem.getShaderTexture(1);
    private final int lightmap = RenderSystem.getShaderTexture(2);
    private final int[] textures = new int[4];
    private final int boneTexture;

    GpuDrawState() {
        try {
            for (int unit = 0; unit < textures.length; unit++) {
                RenderSystem.activeTexture(GL13.GL_TEXTURE0 + unit);
                textures[unit] = GL11.glGetInteger(GL11.GL_TEXTURE_BINDING_2D);
            }
            boneTexture = GL11.glGetInteger(GL31.GL_TEXTURE_BINDING_BUFFER);
        } finally {
            RenderSystem.activeTexture(activeTexture);
        }
    }

    void prepare() {
        RenderSystem.disableBlend();
        RenderSystem.enableCull();
        RenderSystem.enableDepthTest();
        RenderSystem.depthFunc(GL11.GL_LEQUAL);
        RenderSystem.depthMask(true);
        var gameRenderer = Minecraft.getInstance().gameRenderer;
        gameRenderer.lightTexture().turnOnLightLayer();
        gameRenderer.overlayTexture().setupOverlayColor();
        RenderSystem.activeTexture(GL13.GL_TEXTURE1);
        RenderSystem.bindTexture(RenderSystem.getShaderTexture(1));
        RenderSystem.activeTexture(GL13.GL_TEXTURE2);
        RenderSystem.bindTexture(RenderSystem.getShaderTexture(2));
    }

    @Override
    public void close() {
        GL30.glBindVertexArray(vao);
        GL15.glBindBuffer(GL15.GL_ARRAY_BUFFER, arrayBuffer);
        GL15.glBindBuffer(GL31.GL_TEXTURE_BUFFER, textureBuffer);
        // Oculus caches _glUseProgram separately. Raw restoration alone makes its next bind a false cache hit.
        // Keep the raw bind for custom shaders that also bypass that cache, then synchronize Oculus' cache.
        GL20.glUseProgram(program);
        ProgramManager.glUseProgram(program);
        RenderSystem.setShaderTexture(1, overlay);
        RenderSystem.setShaderTexture(2, lightmap);
        for (int unit = 0; unit < textures.length; unit++) {
            RenderSystem.activeTexture(GL13.GL_TEXTURE0 + unit);
            RenderSystem.bindTexture(textures[unit]);
        }
        GL11.glBindTexture(GL31.GL_TEXTURE_BUFFER, boneTexture);
        RenderSystem.activeTexture(activeTexture);
        if (blend) RenderSystem.enableBlend(); else RenderSystem.disableBlend();
        if (cull) RenderSystem.enableCull(); else RenderSystem.disableCull();
        if (depth) RenderSystem.enableDepthTest(); else RenderSystem.disableDepthTest();
        RenderSystem.depthFunc(depthFunction);
        RenderSystem.depthMask(depthMask);
    }
}
