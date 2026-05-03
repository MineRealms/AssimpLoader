package cn.minerealms.assimploader.rendering;

import cn.minerealms.assimploader.animation.SkinnedMeshProcessor;
import cn.minerealms.assimploader.debug.DebugManager;
import cn.minerealms.assimploader.debug.DebugManager.DebugCategory;
import org.lwjgl.opengl.GL15;
import org.lwjgl.opengl.GL20;
import org.lwjgl.opengl.GL30;

import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.FloatBuffer;
import java.nio.IntBuffer;

/**
 * Manages VBO for skinned mesh data.
 *
 * Uploads vertex data with bone indices and weights to GPU.
 */
public class SkinnedMeshVBO {

    private int vaoId;
    private int vboId;
    private int eboId;
    private int vertexCount;
    private int indexCount;

    private boolean uploaded = false;

    /**
     * Upload skinned mesh data to GPU
     *
     * @param positions Vertex positions [x,y,z, x,y,z, ...]
     * @param normals Vertex normals [nx,ny,nz, nx,ny,nz, ...]
     * @param texCoords Texture coordinates [u,v, u,v, ...]
     * @param skinnedData Bone indices and weights
     * @param indices Face indices
     */
    public void upload(float[] positions, float[] normals, float[] texCoords,
                       SkinnedMeshProcessor.SkinnedMeshData skinnedData, int[] indices) {

        if (uploaded) {
            delete();
        }

        vertexCount = positions.length / 3;
        indexCount = indices.length;

        int[] boneIndices = skinnedData.getBoneIndices();
        float[] boneWeights = skinnedData.getBoneWeights();

        // Validate data
        if (normals.length / 3 != vertexCount || texCoords.length / 2 != vertexCount) {
            throw new IllegalArgumentException("Vertex data size mismatch");
        }
        if (boneIndices.length / 4 != vertexCount || boneWeights.length / 4 != vertexCount) {
            throw new IllegalArgumentException("Bone data size mismatch");
        }

        // Create VAO
        vaoId = GL30.glGenVertexArrays();
        GL30.glBindVertexArray(vaoId);

        // Create VBO
        vboId = GL15.glGenBuffers();
        GL15.glBindBuffer(GL15.GL_ARRAY_BUFFER, vboId);

        // Interleave vertex data
        ByteBuffer buffer = createInterleavedBuffer(positions, normals, texCoords, boneIndices, boneWeights);
        GL15.glBufferData(GL15.GL_ARRAY_BUFFER, buffer, GL15.GL_STATIC_DRAW);

        // Setup vertex attributes
        int stride = (3 + 3 + 2) * Float.BYTES + 4 * Integer.BYTES + 4 * Float.BYTES;

        // Position (location 0)
        GL20.glEnableVertexAttribArray(0);
        GL20.glVertexAttribPointer(0, 3, GL20.GL_FLOAT, false, stride, 0);

        // Normal (location 1)
        GL20.glEnableVertexAttribArray(1);
        GL20.glVertexAttribPointer(1, 3, GL20.GL_FLOAT, false, stride, 3 * Float.BYTES);

        // TexCoord (location 2)
        GL20.glEnableVertexAttribArray(2);
        GL20.glVertexAttribPointer(2, 2, GL20.GL_FLOAT, false, stride, 6 * Float.BYTES);

        // BoneIndices (location 3)
        GL20.glEnableVertexAttribArray(3);
        GL30.glVertexAttribIPointer(3, 4, GL20.GL_INT, stride, 8 * Float.BYTES);

        // BoneWeights (location 4)
        GL20.glEnableVertexAttribArray(4);
        GL20.glVertexAttribPointer(4, 4, GL20.GL_FLOAT, false, stride, 8 * Float.BYTES + 4 * Integer.BYTES);

        // Create EBO
        eboId = GL15.glGenBuffers();
        GL15.glBindBuffer(GL15.GL_ELEMENT_ARRAY_BUFFER, eboId);

        // IMPORTANT: Must use direct buffer for OpenGL
        ByteBuffer byteBuffer = ByteBuffer.allocateDirect(indices.length * Integer.BYTES)
                .order(ByteOrder.nativeOrder());
        IntBuffer indexBuffer = byteBuffer.asIntBuffer();
        indexBuffer.put(indices);
        indexBuffer.flip();
        GL15.glBufferData(GL15.GL_ELEMENT_ARRAY_BUFFER, indexBuffer, GL15.GL_STATIC_DRAW);

        // Unbind
        GL30.glBindVertexArray(0);
        GL15.glBindBuffer(GL15.GL_ARRAY_BUFFER, 0);
        GL15.glBindBuffer(GL15.GL_ELEMENT_ARRAY_BUFFER, 0);

        uploaded = true;

        DebugManager.debug(DebugCategory.RENDER, "Uploaded skinned mesh VBO: {} vertices, {} indices", vertexCount, indexCount);
    }

    /**
     * Create interleaved vertex buffer
     */
    private ByteBuffer createInterleavedBuffer(float[] positions, float[] normals, float[] texCoords,
                                               int[] boneIndices, float[] boneWeights) {
        int vertexCount = positions.length / 3;
        int stride = (3 + 3 + 2 + 4) * Float.BYTES + 4 * Integer.BYTES;
        ByteBuffer buffer = ByteBuffer.allocateDirect(vertexCount * stride).order(ByteOrder.nativeOrder());

        for (int i = 0; i < vertexCount; i++) {
            // Position
            buffer.putFloat(positions[i * 3]);
            buffer.putFloat(positions[i * 3 + 1]);
            buffer.putFloat(positions[i * 3 + 2]);

            // Normal
            buffer.putFloat(normals[i * 3]);
            buffer.putFloat(normals[i * 3 + 1]);
            buffer.putFloat(normals[i * 3 + 2]);

            // TexCoord
            buffer.putFloat(texCoords[i * 2]);
            buffer.putFloat(texCoords[i * 2 + 1]);

            // BoneIndices
            buffer.putInt(boneIndices[i * 4]);
            buffer.putInt(boneIndices[i * 4 + 1]);
            buffer.putInt(boneIndices[i * 4 + 2]);
            buffer.putInt(boneIndices[i * 4 + 3]);

            // BoneWeights
            buffer.putFloat(boneWeights[i * 4]);
            buffer.putFloat(boneWeights[i * 4 + 1]);
            buffer.putFloat(boneWeights[i * 4 + 2]);
            buffer.putFloat(boneWeights[i * 4 + 3]);
        }

        buffer.flip();
        return buffer;
    }

    /**
     * Bind VAO for rendering
     */
    public void bind() {
        if (!uploaded) {
            throw new IllegalStateException("VBO not uploaded");
        }
        GL30.glBindVertexArray(vaoId);
    }

    /**
     * Unbind VAO
     */
    public void unbind() {
        GL30.glBindVertexArray(0);
    }

    /**
     * Render the mesh
     */
    public void render() {
        if (!uploaded) {
            throw new IllegalStateException("VBO not uploaded");
        }
        bind();
        GL20.glDrawElements(GL20.GL_TRIANGLES, indexCount, GL20.GL_UNSIGNED_INT, 0);
        unbind();
    }

    /**
     * Delete GPU resources
     */
    public void delete() {
        if (uploaded) {
            GL30.glDeleteVertexArrays(vaoId);
            GL15.glDeleteBuffers(vboId);
            GL15.glDeleteBuffers(eboId);
            uploaded = false;
        }
    }

    public int getVertexCount() {
        return vertexCount;
    }

    public int getIndexCount() {
        return indexCount;
    }

    public boolean isUploaded() {
        return uploaded;
    }
}
