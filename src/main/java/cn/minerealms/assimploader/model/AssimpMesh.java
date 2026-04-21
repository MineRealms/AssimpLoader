package cn.minerealms.assimploader.model;

import cn.minerealms.assimploader.util.CoordinateSystemConverter;
import org.lwjgl.assimp.AIFace;
import org.lwjgl.assimp.AIMesh;
import org.lwjgl.assimp.AIVector3D;

import java.nio.IntBuffer;

/**
 * Wrapper for Assimp AIMesh containing vertex data.
 *
 * Provides convenient access to:
 * - Vertex positions
 * - Normals
 * - Texture coordinates (UVs)
 * - Tangents and bitangents (for normal mapping)
 * - Vertex colors
 * - Bone weights (for skeletal animation)
 * - Face indices
 */
public class AssimpMesh {

    private final AIMesh aiMesh;
    private final int index;
    private final String name;
    private final int materialIndex;
    private final CoordinateSystemConverter.CoordinateSystem sourceCoordinateSystem;

    // Cached data
    private float[] vertices;
    private float[] normals;
    private float[] texCoords;
    private float[] tangents;
    private float[] bitangents;
    private int[] indices;

    public AssimpMesh(AIMesh aiMesh, int index, CoordinateSystemConverter.CoordinateSystem coordinateSystem) {
        this.aiMesh = aiMesh;
        this.index = index;
        this.name = aiMesh.mName().dataString();
        this.materialIndex = aiMesh.mMaterialIndex();
        this.sourceCoordinateSystem = coordinateSystem;
    }

    /**
     * Get vertex positions as flat float array [x,y,z, x,y,z, ...]
     */
    public float[] getVertices() {
        if (vertices == null) {
            int vertexCount = aiMesh.mNumVertices();
            vertices = new float[vertexCount * 3];

            AIVector3D.Buffer vertexBuffer = aiMesh.mVertices();
            for (int i = 0; i < vertexCount; i++) {
                AIVector3D vertex = vertexBuffer.get(i);
                vertices[i * 3] = vertex.x();
                vertices[i * 3 + 1] = vertex.y();
                vertices[i * 3 + 2] = vertex.z();
            }
        }
        return vertices;
    }

    /**
     * Get normals as flat float array [nx,ny,nz, nx,ny,nz, ...]
     */
    public float[] getNormals() {
        if (normals == null && aiMesh.mNormals() != null) {
            int vertexCount = aiMesh.mNumVertices();
            normals = new float[vertexCount * 3];

            AIVector3D.Buffer normalBuffer = aiMesh.mNormals();
            for (int i = 0; i < vertexCount; i++) {
                AIVector3D normal = normalBuffer.get(i);
                normals[i * 3] = normal.x();
                normals[i * 3 + 1] = normal.y();
                normals[i * 3 + 2] = normal.z();
            }
        }
        return normals;
    }

    /**
     * Get texture coordinates (UVs) as flat float array [u,v, u,v, ...]
     * Returns first UV channel (index 0)
     */
    public float[] getTexCoords() {
        if (texCoords == null && aiMesh.mTextureCoords(0) != null) {
            int vertexCount = aiMesh.mNumVertices();
            texCoords = new float[vertexCount * 2];

            AIVector3D.Buffer texCoordBuffer = aiMesh.mTextureCoords(0);
            for (int i = 0; i < vertexCount; i++) {
                AIVector3D texCoord = texCoordBuffer.get(i);
                texCoords[i * 2] = texCoord.x();
                texCoords[i * 2 + 1] = texCoord.y();
            }
        }
        return texCoords;
    }

    /**
     * Get tangents for normal mapping
     */
    public float[] getTangents() {
        if (tangents == null && aiMesh.mTangents() != null) {
            int vertexCount = aiMesh.mNumVertices();
            tangents = new float[vertexCount * 3];

            AIVector3D.Buffer tangentBuffer = aiMesh.mTangents();
            for (int i = 0; i < vertexCount; i++) {
                AIVector3D tangent = tangentBuffer.get(i);
                tangents[i * 3] = tangent.x();
                tangents[i * 3 + 1] = tangent.y();
                tangents[i * 3 + 2] = tangent.z();
            }
        }
        return tangents;
    }

    /**
     * Get bitangents for normal mapping
     */
    public float[] getBitangents() {
        if (bitangents == null && aiMesh.mBitangents() != null) {
            int vertexCount = aiMesh.mNumVertices();
            bitangents = new float[vertexCount * 3];

            AIVector3D.Buffer bitangentBuffer = aiMesh.mBitangents();
            for (int i = 0; i < vertexCount; i++) {
                AIVector3D bitangent = bitangentBuffer.get(i);
                bitangents[i * 3] = bitangent.x();
                bitangents[i * 3 + 1] = bitangent.y();
                bitangents[i * 3 + 2] = bitangent.z();
            }
        }
        return bitangents;
    }

    /**
     * Get face indices as flat int array [i0,i1,i2, i0,i1,i2, ...]
     * All faces are triangulated by Assimp post-processing
     */
    public int[] getIndices() {
        if (indices == null) {
            int faceCount = aiMesh.mNumFaces();
            AIFace.Buffer faceBuffer = aiMesh.mFaces();

            // Count total indices (should be faceCount * 3 after triangulation)
            int totalIndices = 0;
            for (int i = 0; i < faceCount; i++) {
                totalIndices += faceBuffer.get(i).mNumIndices();
            }

            indices = new int[totalIndices];
            int idx = 0;

            for (int i = 0; i < faceCount; i++) {
                AIFace face = faceBuffer.get(i);
                IntBuffer indicesBuffer = face.mIndices();
                int numIndices = face.mNumIndices();

                for (int j = 0; j < numIndices; j++) {
                    indices[idx++] = indicesBuffer.get(j);
                }
            }
        }
        return indices;
    }

    public String getName() {
        return name;
    }

    public int getIndex() {
        return index;
    }

    public int getMaterialIndex() {
        return materialIndex;
    }

    public CoordinateSystemConverter.CoordinateSystem getSourceCoordinateSystem() {
        return sourceCoordinateSystem;
    }

    public int getVertexCount() {
        return aiMesh.mNumVertices();
    }

    public int getFaceCount() {
        return aiMesh.mNumFaces();
    }

    public boolean hasNormals() {
        return aiMesh.mNormals() != null;
    }

    public boolean hasTexCoords() {
        return aiMesh.mTextureCoords(0) != null;
    }

    public boolean hasTangentsAndBitangents() {
        return aiMesh.mTangents() != null && aiMesh.mBitangents() != null;
    }

    public boolean hasBones() {
        return aiMesh.mNumBones() > 0;
    }

    public AIMesh getNativeMesh() {
        return aiMesh;
    }
}
