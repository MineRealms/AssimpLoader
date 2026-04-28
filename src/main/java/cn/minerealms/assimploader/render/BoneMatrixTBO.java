package cn.minerealms.assimploader.render;

import cn.minerealms.assimploader.AssimpLoader;
import com.mojang.blaze3d.platform.GlStateManager;
import org.joml.Matrix4f;
import org.lwjgl.BufferUtils;
import org.lwjgl.opengl.*;

import java.nio.FloatBuffer;

/**
 * Texture Buffer Object (TBO) for efficient bone matrix transfer to GPU.
 * Supports up to 16384 bones (limited by max TBO size).
 *
 * Performance: ~10x faster than uniform arrays for 100+ bones.
 */
public class BoneMatrixTBO {
    private int tboId = -1;
    private int textureId = -1;
    private FloatBuffer buffer;
    private int capacity = 0;
    private boolean initialized = false;

    /**
     * Upload bone matrices to GPU via TBO
     *
     * @param matrices Array of bone transformation matrices
     */
    public void upload(Matrix4f[] matrices) {
        if (matrices == null || matrices.length == 0) {
            return;
        }

        // Check if TBO is supported
        if (!GLCapabilityDetector.isTBOSupported()) {
            AssimpLoader.LOGGER.warn("TBO not supported, cannot upload bone matrices");
            return;
        }

        // Initialize or resize if needed
        if (!initialized || capacity < matrices.length) {
            if (initialized) {
                cleanup();
            }
            initialize(matrices.length);
        }

        // Upload matrix data to buffer
        buffer.clear();
        for (Matrix4f matrix : matrices) {
            matrix.get(buffer);
        }
        buffer.flip();

        // Upload to GPU
        GL15.glBindBuffer(GL31.GL_TEXTURE_BUFFER, tboId);
        GL15.glBufferData(GL31.GL_TEXTURE_BUFFER, buffer, GL15.GL_DYNAMIC_DRAW);
        GL15.glBindBuffer(GL31.GL_TEXTURE_BUFFER, 0);
    }

    /**
     * Initialize TBO with given capacity
     */
    private void initialize(int boneCount) {
        capacity = boneCount;
        buffer = BufferUtils.createFloatBuffer(capacity * 16); // 16 floats per matrix

        // Create buffer object
        tboId = GL15.glGenBuffers();
        GL15.glBindBuffer(GL31.GL_TEXTURE_BUFFER, tboId);
        GL15.glBufferData(GL31.GL_TEXTURE_BUFFER, buffer.capacity() * 4L, GL15.GL_DYNAMIC_DRAW);

        // Create texture object
        textureId = GL11.glGenTextures();
        GL11.glBindTexture(GL31.GL_TEXTURE_BUFFER, textureId);
        GL31.glTexBuffer(GL31.GL_TEXTURE_BUFFER, GL30.GL_RGBA32F, tboId);

        // Unbind
        GL11.glBindTexture(GL31.GL_TEXTURE_BUFFER, 0);
        GL15.glBindBuffer(GL31.GL_TEXTURE_BUFFER, 0);

        initialized = true;

        AssimpLoader.LOGGER.info("BoneMatrixTBO initialized: capacity={} bones, size={} KB",
                capacity, (capacity * 16 * 4) / 1024);
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
        buffer = null;
        initialized = false;
    }

    /**
     * Check if TBO is initialized
     */
    public boolean isInitialized() {
        return initialized;
    }

    /**
     * Get current capacity (number of matrices)
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
}
