package cn.minerealms.assimploader.rendering.splat;

import cn.minerealms.assimploader.debug.DebugManager;
import cn.minerealms.assimploader.debug.DebugManager.DebugCategory;
import cn.minerealms.assimploader.rendering.splat.data.GaussianSplatData;
import org.lwjgl.opengl.*;

import java.nio.FloatBuffer;
import java.nio.IntBuffer;

/**
 * Manages VAO/VBO/SSBO for Gaussian splat rendering.
 *
 * Uses a single quad (4 vertices, 6 indices) with instanced rendering.
 * Splat data is stored in SSBO binding 0, sorted indices in SSBO binding 1.
 */
public class GaussianSplatVBO {

    private int vaoId;
    private int quadVboId;
    private int quadEboId;
    private int splatSsboId;
    private int orderSsboId;

    private int splatCount;
    private boolean uploaded = false;
    private boolean useSSBO = true;

    /**
     * Upload splat data to GPU
     *
     * @param data Gaussian splat data
     * @param useSSBO Whether to use SSBO (true) or fallback to TBO (false)
     */
    public void upload(GaussianSplatData data, boolean useSSBO) {
        if (uploaded) {
            delete();
        }

        this.splatCount = data.getSplatCount();
        this.useSSBO = useSSBO;

        // Create VAO
        vaoId = GL30.glGenVertexArrays();
        GL30.glBindVertexArray(vaoId);

        // Create quad VBO (single quad for instancing)
        quadVboId = GL15.glGenBuffers();
        GL15.glBindBuffer(GL15.GL_ARRAY_BUFFER, quadVboId);

        // Quad vertices: position (2D) + texcoord
        float[] quadVertices = {
            -1.0f, -1.0f, 0.0f, 0.0f,
             1.0f, -1.0f, 1.0f, 0.0f,
             1.0f,  1.0f, 1.0f, 1.0f,
            -1.0f,  1.0f, 0.0f, 1.0f
        };
        FloatBuffer quadBuffer = FloatBuffer.allocate(quadVertices.length);
        quadBuffer.put(quadVertices);
        quadBuffer.flip();
        GL15.glBufferData(GL15.GL_ARRAY_BUFFER, quadBuffer, GL15.GL_STATIC_DRAW);

        // Position (location 0)
        GL20.glEnableVertexAttribArray(0);
        GL20.glVertexAttribPointer(0, 2, GL11.GL_FLOAT, false, 4 * Float.BYTES, 0);

        // TexCoord (location 1)
        GL20.glEnableVertexAttribArray(1);
        GL20.glVertexAttribPointer(1, 2, GL11.GL_FLOAT, false, 4 * Float.BYTES, 2 * Float.BYTES);

        // Create quad EBO
        quadEboId = GL15.glGenBuffers();
        GL15.glBindBuffer(GL15.GL_ELEMENT_ARRAY_BUFFER, quadEboId);
        int[] quadIndices = {0, 1, 2, 2, 3, 0};
        IntBuffer indexBuffer = IntBuffer.allocate(quadIndices.length);
        indexBuffer.put(quadIndices);
        indexBuffer.flip();
        GL15.glBufferData(GL15.GL_ELEMENT_ARRAY_BUFFER, indexBuffer, GL15.GL_STATIC_DRAW);

        // Create SSBO for splat data
        if (useSSBO) {
            splatSsboId = GL15.glGenBuffers();
            GL15.glBindBuffer(GL43.GL_SHADER_STORAGE_BUFFER, splatSsboId);

            GaussianSplatBuffer packer = new GaussianSplatBuffer(data.getShDegree());
            FloatBuffer splatBuffer = packer.pack(data);
            GL15.glBufferData(GL43.GL_SHADER_STORAGE_BUFFER, splatBuffer, GL15.GL_STATIC_DRAW);
            GL30.glBindBufferBase(GL43.GL_SHADER_STORAGE_BUFFER, 0, splatSsboId);

            // Create SSBO for sorted indices
            orderSsboId = GL15.glGenBuffers();
            GL15.glBindBuffer(GL43.GL_SHADER_STORAGE_BUFFER, orderSsboId);
            IntBuffer orderBuffer = IntBuffer.allocate(splatCount);
            for (int i = 0; i < splatCount; i++) {
                orderBuffer.put(i);
            }
            orderBuffer.flip();
            GL15.glBufferData(GL43.GL_SHADER_STORAGE_BUFFER, orderBuffer, GL15.GL_DYNAMIC_DRAW);
            GL30.glBindBufferBase(GL43.GL_SHADER_STORAGE_BUFFER, 1, orderSsboId);

            GL15.glBindBuffer(GL43.GL_SHADER_STORAGE_BUFFER, 0);
        } else {
            // TODO: TBO fallback for Oculus compatibility
            DebugManager.warn(DebugCategory.RENDER, "TBO fallback not yet implemented");
        }

        // Unbind
        GL30.glBindVertexArray(0);
        GL15.glBindBuffer(GL15.GL_ARRAY_BUFFER, 0);
        GL15.glBindBuffer(GL15.GL_ELEMENT_ARRAY_BUFFER, 0);

        uploaded = true;

        DebugManager.debug(DebugCategory.RENDER, "Uploaded Gaussian splat VBO: {} splats", splatCount);
    }

    /**
     * Upload sorted indices to GPU
     */
    public void uploadOrder(int[] sortedIndices) {
        if (!uploaded || !useSSBO) {
            return;
        }

        GL15.glBindBuffer(GL43.GL_SHADER_STORAGE_BUFFER, orderSsboId);
        IntBuffer orderBuffer = IntBuffer.allocate(sortedIndices.length);
        orderBuffer.put(sortedIndices);
        orderBuffer.flip();
        GL15.glBufferSubData(GL43.GL_SHADER_STORAGE_BUFFER, 0, orderBuffer);
        GL15.glBindBuffer(GL43.GL_SHADER_STORAGE_BUFFER, 0);
    }

    /**
     * Bind VAO for rendering
     */
    public void bind() {
        if (!uploaded) {
            throw new IllegalStateException("VBO not uploaded");
        }
        GL30.glBindVertexArray(vaoId);

        if (useSSBO) {
            GL30.glBindBufferBase(GL43.GL_SHADER_STORAGE_BUFFER, 0, splatSsboId);
            GL30.glBindBufferBase(GL43.GL_SHADER_STORAGE_BUFFER, 1, orderSsboId);
        }
    }

    /**
     * Unbind VAO
     */
    public void unbind() {
        GL30.glBindVertexArray(0);
    }

    /**
     * Render instanced splats
     */
    public void render() {
        if (!uploaded) {
            throw new IllegalStateException("VBO not uploaded");
        }
        bind();
        GL31.glDrawElementsInstanced(GL11.GL_TRIANGLES, 6, GL11.GL_UNSIGNED_INT, 0, splatCount);
        unbind();
    }

    /**
     * Delete GPU resources
     */
    public void delete() {
        if (uploaded) {
            GL30.glDeleteVertexArrays(vaoId);
            GL15.glDeleteBuffers(quadVboId);
            GL15.glDeleteBuffers(quadEboId);
            if (useSSBO) {
                GL15.glDeleteBuffers(splatSsboId);
                GL15.glDeleteBuffers(orderSsboId);
            }
            uploaded = false;
        }
    }

    public int getSplatCount() {
        return splatCount;
    }

    public boolean isUploaded() {
        return uploaded;
    }
}
