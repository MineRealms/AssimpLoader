package cn.minerealms.assimploader.lookdev.render;

import cn.minerealms.assimploader.debug.DebugManager;
import cn.minerealms.assimploader.debug.DebugManager.DebugCategory;
import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.vertex.*;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.renderer.GameRenderer;
import net.minecraftforge.client.event.ScreenEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import org.joml.Matrix4f;
import org.lwjgl.opengl.GL11;

import javax.annotation.Nullable;

/**
 * Renders the LookDev offscreen texture as an overlay on the GUI.
 * This runs in the Minecraft render thread, not the ModernUI UI thread.
 */
public class LookDevOverlayRenderer {

    private static LookDevOverlayRenderer INSTANCE = null;

    @Nullable
    private OffscreenRenderer activeRenderer = null;
    private int renderX = 0;
    private int renderY = 0;
    private int renderWidth = 0;
    private int renderHeight = 0;
    private boolean enabled = false;

    private LookDevOverlayRenderer() {
    }

    public static LookDevOverlayRenderer getInstance() {
        if (INSTANCE == null) {
            INSTANCE = new LookDevOverlayRenderer();
        }
        return INSTANCE;
    }

    /**
     * Set the active renderer and render area.
     * Called from ModernUI thread when LookDev screen is opened.
     */
    public void setActiveRenderer(@Nullable OffscreenRenderer renderer, int x, int y, int width, int height) {
        this.activeRenderer = renderer;
        this.renderX = x;
        this.renderY = y;
        this.renderWidth = width;
        this.renderHeight = height;
        this.enabled = renderer != null;

        DebugManager.info(DebugCategory.LOOKDEV, "[LookDevOverlayRenderer] Active renderer set: enabled={}, area=({},{},{}x{})",
            enabled, x, y, width, height);
    }

    /**
     * Disable rendering (called when LookDev screen is closed)
     */
    public void disable() {
        this.enabled = false;
        this.activeRenderer = null;
        DebugManager.info(DebugCategory.LOOKDEV, "[LookDevOverlayRenderer] Disabled");
    }

    /**
     * Render the offscreen texture as an overlay.
     * This is called from Minecraft render thread after screen rendering.
     */
    @SubscribeEvent
    public void onScreenRender(ScreenEvent.Render.Post event) {
        DebugManager.info(DebugCategory.LOOKDEV, "[LookDevOverlayRenderer] onScreenRender called, enabled={}, activeRenderer={}", enabled, activeRenderer != null);

        if (!enabled || activeRenderer == null) {
            return;
        }

        try {
            // Directly render to FBO (we're already on render thread)
            activeRenderer.renderNow();

            int textureId = activeRenderer.getTextureId();
            DebugManager.info(DebugCategory.LOOKDEV, "[LookDevOverlayRenderer] onScreenRender: textureId={}", textureId);

            if (textureId <= 0) {
                DebugManager.warn(DebugCategory.LOOKDEV, "[LookDevOverlayRenderer] Invalid texture ID: {}", textureId);
                return;
            }

            // Render the FBO texture to screen
            renderTexture(event.getGuiGraphics(), textureId, renderX, renderY, renderWidth, renderHeight);
            DebugManager.info(DebugCategory.LOOKDEV, "[LookDevOverlayRenderer] Texture rendered at ({},{}) {}x{}", renderX, renderY, renderWidth, renderHeight);

        } catch (Exception e) {
            DebugManager.error(DebugCategory.LOOKDEV, "[LookDevOverlayRenderer] Render error", e);
        }
    }

    /**
     * Render an OpenGL texture to the screen using GuiGraphics.
     * This is safe to call from render thread.
     */
    private void renderTexture(GuiGraphics graphics, int textureId, int x, int y, int width, int height) {
        // Save current state
        RenderSystem.enableBlend();
        RenderSystem.defaultBlendFunc();
        RenderSystem.setShader(GameRenderer::getPositionTexShader);
        RenderSystem.setShaderColor(1.0f, 1.0f, 1.0f, 1.0f);

        // Bind the FBO texture
        RenderSystem.setShaderTexture(0, textureId);

        // Get pose stack
        PoseStack poseStack = graphics.pose();
        poseStack.pushPose();

        // Draw textured quad
        Matrix4f matrix = poseStack.last().pose();
        BufferBuilder bufferBuilder = Tesselator.getInstance().getBuilder();
        bufferBuilder.begin(VertexFormat.Mode.QUADS, DefaultVertexFormat.POSITION_TEX);

        // Flip V coordinate (OpenGL bottom-left origin -> screen top-left origin)
        bufferBuilder.vertex(matrix, x, y + height, 0).uv(0, 0).endVertex();
        bufferBuilder.vertex(matrix, x + width, y + height, 0).uv(1, 0).endVertex();
        bufferBuilder.vertex(matrix, x + width, y, 0).uv(1, 1).endVertex();
        bufferBuilder.vertex(matrix, x, y, 0).uv(0, 1).endVertex();

        BufferUploader.drawWithShader(bufferBuilder.end());

        poseStack.popPose();

        // Restore state
        RenderSystem.disableBlend();
    }
}
