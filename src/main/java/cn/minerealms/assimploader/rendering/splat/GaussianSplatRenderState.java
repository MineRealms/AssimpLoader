package cn.minerealms.assimploader.rendering.splat;

import com.mojang.blaze3d.systems.RenderSystem;
import org.lwjgl.opengl.GL11;

/**
 * Manages OpenGL state for Gaussian splat rendering.
 *
 * Configures blending, culling, and depth testing for transparent splats.
 */
public class GaussianSplatRenderState {

    /**
     * Setup OpenGL state for splat rendering
     */
    public static void setup() {
        // Disable face culling (splats are billboards)
        RenderSystem.disableCull();

        // Enable alpha blending
        RenderSystem.enableBlend();
        RenderSystem.blendFunc(GL11.GL_SRC_ALPHA, GL11.GL_ONE_MINUS_SRC_ALPHA);

        // Enable depth test but disable depth writes (transparent objects)
        RenderSystem.enableDepthTest();
        RenderSystem.depthMask(false);
    }

    /**
     * Restore default OpenGL state
     */
    public static void restore() {
        // Restore face culling
        RenderSystem.enableCull();

        // Restore default blend function
        RenderSystem.defaultBlendFunc();

        // Restore depth writes
        RenderSystem.depthMask(true);
    }
}
