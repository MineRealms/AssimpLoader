package cn.minerealms.assimploader.rendering.splat;

import cn.minerealms.assimploader.rendering.splat.data.GaussianSplatData;
import com.mojang.blaze3d.systems.RenderSystem;
import org.lwjgl.opengl.*;

import java.nio.FloatBuffer;
import java.nio.IntBuffer;

/** Scalar texture buffers: R32F cloud data and R32I original point indices. */
public final class GaussianSplatTBO {
    private int buffer;
    private int texture;
    private int capacity;
    private int stride;

    public void upload(GaussianSplatData data, FloatBuffer values) {
        if (data == null || values == null) throw new IllegalArgumentException("Missing splat data");
        stride = new GaussianSplatBuffer(data.getShDegree()).getStride();
        if (values.remaining() != Math.multiplyExact(data.getSplatCount(), stride)) {
            throw new IllegalArgumentException("Packed splat stride does not match metadata");
        }
        allocate(values.remaining(), GL30.GL_R32F);
        GL15.glBindBuffer(GL31.GL_TEXTURE_BUFFER, buffer);
        if (values.hasRemaining()) GL15.glBufferSubData(GL31.GL_TEXTURE_BUFFER, 0, values);
    }

    public void allocateIndices(int count) {
        stride = 1;
        allocate(count, GL30.GL_R32I);
    }

    private void allocate(int scalars, int format) {
        if (scalars < 0 || scalars > GL11.glGetInteger(GL31.GL_MAX_TEXTURE_BUFFER_SIZE)) {
            throw new IllegalArgumentException("Splat texture buffer exceeds GPU scalar capacity");
        }
        cleanup();
        try {
            buffer = GL15.glGenBuffers();
            GL15.glBindBuffer(GL31.GL_TEXTURE_BUFFER, buffer);
            GL15.glBufferData(GL31.GL_TEXTURE_BUFFER, Math.max(1L, scalars) * Integer.BYTES, GL15.GL_DYNAMIC_DRAW);
            texture = GL11.glGenTextures();
            GL11.glBindTexture(GL31.GL_TEXTURE_BUFFER, texture);
            GL31.glTexBuffer(GL31.GL_TEXTURE_BUFFER, format, buffer);
            capacity = scalars;
        } catch (RuntimeException | Error failure) {
            cleanup();
            throw failure;
        }
    }

    public void updateIndices(IntBuffer indices) {
        if (indices.remaining() > capacity) throw new IllegalArgumentException("Too many splat indices");
        GL15.glBindBuffer(GL31.GL_TEXTURE_BUFFER, buffer);
        if (indices.hasRemaining()) GL15.glBufferSubData(GL31.GL_TEXTURE_BUFFER, 0, indices);
    }

    public void bind(int unit) {
        if (texture == 0) throw new IllegalStateException("Texture buffer not initialized");
        RenderSystem.activeTexture(GL13.GL_TEXTURE0 + unit);
        GL11.glBindTexture(GL31.GL_TEXTURE_BUFFER, texture);
    }

    public void unbind(int unit) {
        RenderSystem.activeTexture(GL13.GL_TEXTURE0 + unit);
        GL11.glBindTexture(GL31.GL_TEXTURE_BUFFER, 0);
    }

    public void cleanup() {
        if (texture != 0) GL11.glDeleteTextures(texture);
        if (buffer != 0) GL15.glDeleteBuffers(buffer);
        buffer = texture = 0;
        capacity = 0;
    }

    public boolean isInitialized() { return texture != 0; }
    public int getCapacity() { return stride > 0 ? capacity / stride : capacity; }
    public int getTextureId() { return texture; }
    public int getStride() { return stride; }
}
