package cn.minerealms.assimploader.rendering.splat;

import cn.minerealms.assimploader.AssimpLoader;
import cn.minerealms.assimploader.render.GLCapabilityDetector;
import cn.minerealms.assimploader.rendering.splat.data.GaussianSplatData;
import com.mojang.blaze3d.platform.GlStateManager;
import org.lwjgl.opengl.*;

import java.nio.FloatBuffer;

/**
 * Texture Buffer Object (TBO) for Gaussian splat data transfer to GPU.
 * Fallback for Oculus compatibility when SSBO is unavailable.
 *
 * Layout: Same as GaussianSplatBuffer
 * - position (3 floats)
 * - rotation (4 floats: w, x, y, z)
 * - scale (3 floats)
 * - opacity (1 float)
 * - sh coefficients (shDim * 3 floats)
 */
public class GaussianSplatTBO {
    private int tboId = -1;
    private int textureId = -1;
    private int capacity = 0;
    private int stride = 0;
    private boolean initialized = false;

    /**
     * Upload splat data to GPU via TBO
     *
     * @param data Gaussian splat data
     * @param buffer Packed float buffer from GaussianSplatBuffer
     */
    public void upload(GaussianSplatData data, FloatBuffer buffer) {
        if (data == null || buffer == null || data.getSplatCount() == 0) {
            return;
        }

        // Check if TBO is supported
        if (!GLCapabilityDetector.isTBOSupported()) {
            AssimpLoader.LOGGER.warn("TBO not supported, cannot upload splat data");
            return;
        }

        int splatCount = data.getSplatCount();
        int shDim = (data.getShDegree() + 1) * (data.getShDegree() + 1);
        int newStride = 11 + shDim * 3;

        // Initialize or resize if needed
        if (!initialized || capacity < splatCount || stride != newStride) {
            if (initialized) {
                cleanup();
            }
            initialize(splatCount, newStride);
        }

        // Upload to GPU
        GL15.glBindBuffer(GL31.GL_TEXTURE_BUFFER, tboId);
        GL15.glBufferData(GL31.GL_TEXTURE_BUFFER, buffer, GL15.GL_DYNAMIC_DRAW);
        GL15.glBindBuffer(GL31.GL_TEXTURE_BUFFER, 0);
    }

    /**
     * Initialize TBO with given capacity
     */
    private void initialize(int splatCount, int strideFloats) {
        capacity = splatCount;
        stride = strideFloats;

        // Create buffer object
        tboId = GL15.glGenBuffers();
        GL15.glBindBuffer(GL31.GL_TEXTURE_BUFFER, tboId);
        GL15.glBufferData(GL31.GL_TEXTURE_BUFFER, (long) capacity * stride * Float.BYTES, GL15.GL_DYNAMIC_DRAW);

        // Create texture object
        textureId = GL11.glGenTextures();
        GL11.glBindTexture(GL31.GL_TEXTURE_BUFFER, textureId);
        GL31.glTexBuffer(GL31.GL_TEXTURE_BUFFER, GL30.GL_RGBA32F, tboId);

        // Unbind
        GL11.glBindTexture(GL31.GL_TEXTURE_BUFFER, 0);
        GL15.glBindBuffer(GL31.GL_TEXTURE_BUFFER, 0);

        initialized = true;

        AssimpLoader.LOGGER.info("GaussianSplatTBO initialized: capacity={} splats, stride={} floats, size={} KB",
                capacity, stride, (capacity * stride * 4) / 1024);
    }

    /**
     * Bind TBO to texture unit for shader access
     *
     * @param textureUnit Texture unit (0-31)
     */
    public void bind(int textureUnit) {
        if (!initialized || textureId == -1) {
            return;
        }

        GlStateManager._activeTexture(GL13.GL_TEXTURE0 + textureUnit);
        GL11.glBindTexture(GL31.GL_TEXTURE_BUFFER, textureId);
    }

    /**
     * Unbind TBO from texture unit
     *
     * @param textureUnit Texture unit (0-31)
     */
    public void unbind(int textureUnit) {
        GlStateManager._activeTexture(GL13.GL_TEXTURE0 + textureUnit);
        GL11.glBindTexture(GL31.GL_TEXTURE_BUFFER, 0);
    }

    /**
     * Cleanup GPU resources
     */
    public void cleanup() {
        if (tboId != -1) {
            GL15.glDeleteBuffers(tboId);
            tboId = -1;
        }
        if (textureId != -1) {
            GL11.glDeleteTextures(textureId);
            textureId = -1;
        }
        initialized = false;
    }

    /**
     * Check if TBO is initialized
     */
    public boolean isInitialized() {
        return initialized;
    }

    /**
     * Get current capacity (number of splats)
     */
    public int getCapacity() {
        return capacity;
    }

    /**
     * Get texture ID for shader binding
     */
    public int getTextureId() {
        return textureId;
    }

    /**
     * Get stride in floats
     */
    public int getStride() {
        return stride;
    }
}
