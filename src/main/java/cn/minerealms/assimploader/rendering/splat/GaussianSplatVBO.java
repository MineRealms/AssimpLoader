package cn.minerealms.assimploader.rendering.splat;

import cn.minerealms.assimploader.rendering.splat.data.GaussianSplatData;
import org.lwjgl.opengl.*;
import org.lwjgl.system.MemoryUtil;

import java.nio.FloatBuffer;
import java.nio.IntBuffer;

/** Immutable cloud storage; only the selected, sorted original indices are streamed each frame. */
public final class GaussianSplatVBO {
    private int vao;
    private int quadBuffer;
    private int quadIndices;
    private int dataBuffer;
    private int orderBuffer;
    private GaussianSplatTBO dataTexture;
    private GaussianSplatTBO orderTexture;
    private IntBuffer staging;
    private int splatCount;
    private int drawCount;
    private boolean ssbo;
    private boolean uploaded;

    public void upload(GaussianSplatData data, boolean useSSBO) {
        delete();
        ssbo = useSSBO;
        splatCount = data.getSplatCount();
        try {
            vao = GL30.glGenVertexArrays();
            GL30.glBindVertexArray(vao);
            quadBuffer = GL15.glGenBuffers();
            GL15.glBindBuffer(GL15.GL_ARRAY_BUFFER, quadBuffer);
            GL15.glBufferData(GL15.GL_ARRAY_BUFFER, new float[]{-1, -1, 1, -1, 1, 1, -1, 1}, GL15.GL_STATIC_DRAW);
            GL20.glEnableVertexAttribArray(0);
            GL20.glVertexAttribPointer(0, 2, GL11.GL_FLOAT, false, 2 * Float.BYTES, 0L);
            quadIndices = GL15.glGenBuffers();
            GL15.glBindBuffer(GL15.GL_ELEMENT_ARRAY_BUFFER, quadIndices);
            GL15.glBufferData(GL15.GL_ELEMENT_ARRAY_BUFFER, new int[]{0, 1, 2, 2, 3, 0}, GL15.GL_STATIC_DRAW);
            GaussianSplatBuffer packer = new GaussianSplatBuffer(data.getShDegree());
            int values = Math.multiplyExact(splatCount, packer.getStride());
            if (ssbo && (long) values * Float.BYTES > GL32.glGetInteger64(GL43.GL_MAX_SHADER_STORAGE_BLOCK_SIZE)) {
                throw new IllegalArgumentException("Splat storage buffer exceeds GPU capacity");
            }
            if (!ssbo && values > GL11.glGetInteger(GL31.GL_MAX_TEXTURE_BUFFER_SIZE)) {
                throw new IllegalArgumentException("Splat texture buffer exceeds GPU scalar capacity");
            }
            FloatBuffer packed = MemoryUtil.memAllocFloat(Math.max(1, values));
            try {
                packer.pack(data, packed);
                if (ssbo) {
                    dataBuffer = GL15.glGenBuffers();
                    GL15.glBindBuffer(GL43.GL_SHADER_STORAGE_BUFFER, dataBuffer);
                    GL15.glBufferData(GL43.GL_SHADER_STORAGE_BUFFER, packed, GL15.GL_STATIC_DRAW);
                    orderBuffer = GL15.glGenBuffers();
                    GL15.glBindBuffer(GL43.GL_SHADER_STORAGE_BUFFER, orderBuffer);
                    GL15.glBufferData(GL43.GL_SHADER_STORAGE_BUFFER, Math.max(1L, splatCount) * Integer.BYTES, GL15.GL_DYNAMIC_DRAW);
                } else {
                    dataTexture = new GaussianSplatTBO();
                    dataTexture.upload(data, packed);
                    orderTexture = new GaussianSplatTBO();
                    orderTexture.allocateIndices(splatCount);
                }
            } finally {
                MemoryUtil.memFree(packed);
            }
            staging = MemoryUtil.memAllocInt(Math.max(1, splatCount));
            uploaded = true;
            for (int i = 0; i < splatCount; i++) staging.put(i);
            staging.flip();
            uploadStaging();
        } catch (RuntimeException | Error failure) {
            delete();
            throw failure;
        }
    }

    public void uploadOrder(int[] indices) { uploadOrder(indices, indices.length); }

    public void uploadOrder(int[] indices, int count) {
        if (!uploaded) throw new IllegalStateException("Splat VBO not uploaded");
        if (count < 0 || count > indices.length || count > splatCount) throw new IllegalArgumentException("Invalid splat draw count");
        staging.clear();
        for (int i = 0; i < count; i++) {
            if (indices[i] < 0 || indices[i] >= splatCount) throw new IllegalArgumentException("Invalid original splat index");
            staging.put(indices[i]);
        }
        staging.flip();
        uploadStaging();
    }

    private void uploadStaging() {
        drawCount = staging.remaining();
        if (ssbo) {
            GL15.glBindBuffer(GL43.GL_SHADER_STORAGE_BUFFER, orderBuffer);
            if (drawCount > 0) GL15.glBufferSubData(GL43.GL_SHADER_STORAGE_BUFFER, 0, staging);
        } else orderTexture.updateIndices(staging);
    }

    public void bind() {
        if (!uploaded) throw new IllegalStateException("Splat VBO not uploaded");
        GL30.glBindVertexArray(vao);
        if (ssbo) {
            GL30.glBindBufferBase(GL43.GL_SHADER_STORAGE_BUFFER, 0, dataBuffer);
            GL30.glBindBufferBase(GL43.GL_SHADER_STORAGE_BUFFER, 1, orderBuffer);
        } else {
            dataTexture.bind(0);
            orderTexture.bind(1);
        }
    }

    public void unbind() { GL30.glBindVertexArray(0); }

    public void render() {
        if (drawCount == 0) return;
        bind();
        GL31.glDrawElementsInstanced(GL11.GL_TRIANGLES, 6, GL11.GL_UNSIGNED_INT, 0L, drawCount);
    }

    public void delete() {
        if (vao != 0) GL30.glDeleteVertexArrays(vao);
        if (quadBuffer != 0) GL15.glDeleteBuffers(quadBuffer);
        if (quadIndices != 0) GL15.glDeleteBuffers(quadIndices);
        if (dataBuffer != 0) GL15.glDeleteBuffers(dataBuffer);
        if (orderBuffer != 0) GL15.glDeleteBuffers(orderBuffer);
        if (dataTexture != null) dataTexture.cleanup();
        if (orderTexture != null) orderTexture.cleanup();
        if (staging != null) MemoryUtil.memFree(staging);
        vao = quadBuffer = quadIndices = dataBuffer = orderBuffer = 0;
        dataTexture = orderTexture = null;
        staging = null;
        splatCount = drawCount = 0;
        uploaded = false;
    }

    public int getSplatCount() { return splatCount; }
    public int getDrawCount() { return drawCount; }
    public boolean isUploaded() { return uploaded; }
}
