package cn.minerealms.assimploader.rendering;

import cn.minerealms.assimploader.animation.SkinnedMeshProcessor;
import cn.minerealms.assimploader.debug.DebugManager;
import cn.minerealms.assimploader.debug.DebugManager.DebugCategory;
import org.lwjgl.opengl.GL15;
import org.lwjgl.opengl.GL20;
import org.lwjgl.opengl.GL30;

import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.IntBuffer;

/**
 * Manages VBO for skinned mesh data.
 *
 * Uploads vertex data with bone indices and weights to GPU.
 */
public class SkinnedMeshVBO {

    static final int BONES_PER_VERTEX = 4;
    static final int VERTEX_STRIDE = 64;

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

        int[] boneIndices = skinnedData.getBoneIndices();
        float[] boneWeights = skinnedData.getBoneWeights();
        ByteBuffer buffer = createInterleavedBuffer(positions, normals, texCoords, boneIndices, boneWeights);

        if (uploaded) {
            delete();
        }

        vertexCount = positions.length / 3;
        indexCount = indices.length;

        // Create VAO
        vaoId = GL30.glGenVertexArrays();
        GL30.glBindVertexArray(vaoId);

        // Create VBO
        vboId = GL15.glGenBuffers();
        GL15.glBindBuffer(GL15.GL_ARRAY_BUFFER, vboId);

        GL15.glBufferData(GL15.GL_ARRAY_BUFFER, buffer, GL15.GL_STATIC_DRAW);

        // Setup vertex attributes
        int stride = VERTEX_STRIDE;

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
        for (int i = 0; i + 2 < indices.length; i += 3) {
            indexBuffer.put(indices[i + 2]).put(indices[i + 1]).put(indices[i]);
        }
        indexBuffer.flip();
        GL15.glBufferData(GL15.GL_ELEMENT_ARRAY_BUFFER, indexBuffer, GL15.GL_STATIC_DRAW);

        // Unbind
        GL30.glBindVertexArray(0);
        GL15.glBindBuffer(GL15.GL_ARRAY_BUFFER, 0);

        uploaded = true;

        DebugManager.debug(DebugCategory.RENDER, "Uploaded skinned mesh VBO: {} vertices, {} indices", vertexCount, indexCount);
    }

    /**
     * Create interleaved vertex buffer
     */
    static ByteBuffer createInterleavedBuffer(float[] positions, float[] normals, float[] texCoords,
                                              int[] boneIndices, float[] boneWeights) {
        if (positions == null || positions.length % 3 != 0) {
            throw new IllegalArgumentException("Positions must contain complete vertices");
        }
        int vertexCount = positions.length / 3;
        if ((normals != null && normals.length != positions.length)
                || (texCoords != null && texCoords.length != vertexCount * 2)) {
            throw new IllegalArgumentException("Vertex data size mismatch");
        }
        if (boneIndices == null || boneWeights == null || boneIndices.length != boneWeights.length
                || (vertexCount == 0 ? boneIndices.length != 0 : boneIndices.length % vertexCount != 0)) {
            throw new IllegalArgumentException("Bone data size mismatch");
        }

        int sourceStride = vertexCount == 0 ? BONES_PER_VERTEX : boneIndices.length / vertexCount;
        if (sourceStride < 1 || sourceStride > 8) {
            throw new IllegalArgumentException("Expected between 1 and 8 bone influences per vertex");
        }
        ByteBuffer buffer = ByteBuffer.allocateDirect(Math.multiplyExact(vertexCount, VERTEX_STRIDE))
                .order(ByteOrder.nativeOrder());
        int[] retainedIndices = new int[BONES_PER_VERTEX];
        float[] retainedWeights = new float[BONES_PER_VERTEX];

        for (int i = 0; i < vertexCount; i++) {
            // Position
            buffer.putFloat(positions[i * 3]);
            buffer.putFloat(positions[i * 3 + 1]);
            buffer.putFloat(positions[i * 3 + 2]);

            // Normal
            buffer.putFloat(normals == null ? 0.0f : normals[i * 3]);
            buffer.putFloat(normals == null ? 1.0f : normals[i * 3 + 1]);
            buffer.putFloat(normals == null ? 0.0f : normals[i * 3 + 2]);

            // TexCoord
            buffer.putFloat(texCoords == null ? 0.0f : texCoords[i * 2]);
            buffer.putFloat(texCoords == null ? 0.0f : texCoords[i * 2 + 1]);

            for (int slot = 0; slot < BONES_PER_VERTEX; slot++) {
                retainedIndices[slot] = -1;
                retainedWeights[slot] = 0.0f;
            }
            double totalWeight = 0.0;
            // CPU data has a configurable stride; the shader always consumes four slots.
            for (int slot = 0; slot < sourceStride; slot++) {
                int sourceIndex = i * sourceStride + slot;
                float weight = boneWeights[sourceIndex];
                if (boneIndices[sourceIndex] >= 0 && Float.isFinite(weight) && weight > 0.0f) {
                    for (int target = 0; target < BONES_PER_VERTEX; target++) {
                        if (weight <= retainedWeights[target]) continue;
                        for (int shift = BONES_PER_VERTEX - 1; shift > target; shift--) {
                            retainedWeights[shift] = retainedWeights[shift - 1];
                            retainedIndices[shift] = retainedIndices[shift - 1];
                        }
                        retainedWeights[target] = weight;
                        retainedIndices[target] = boneIndices[sourceIndex];
                        break;
                    }
                }
            }
            for (float weight : retainedWeights) totalWeight += weight;
            for (int index : retainedIndices) {
                buffer.putInt(index);
            }
            for (float weight : retainedWeights) {
                buffer.putFloat(totalWeight > 0.0 ? (float) (weight / totalWeight) : 0.0f);
            }
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

    public void render(cn.minerealms.assimploader.model.AssimpMesh mesh) {
        var selection = MeshDrawSelection.current();
        if (selection == null) { render(); return; }
        bind();
        try { selection.drawBound(mesh, false); }
        finally { unbind(); }
    }

    /**
     * Delete GPU resources
     */
    public void delete() {
        if (vaoId != 0) GL30.glDeleteVertexArrays(vaoId);
        if (vboId != 0) GL15.glDeleteBuffers(vboId);
        if (eboId != 0) GL15.glDeleteBuffers(eboId);
        vaoId = vboId = eboId = 0;
        uploaded = false;
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
