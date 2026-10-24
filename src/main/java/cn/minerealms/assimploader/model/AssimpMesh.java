package cn.minerealms.assimploader.model;

import cn.minerealms.assimploader.animation.SkinnedMeshProcessor;
import cn.minerealms.assimploader.debug.DebugManager;
import cn.minerealms.assimploader.debug.DebugManager.DebugCategory;
import cn.minerealms.assimploader.util.CoordinateSystemConverter;
import org.joml.Matrix4f;
import org.joml.Vector3f;
import org.lwjgl.assimp.AIBone;
import org.lwjgl.assimp.AIColor4D;
import org.lwjgl.assimp.AIFace;
import org.lwjgl.assimp.AIMatrix4x4;
import org.lwjgl.assimp.AIMesh;
import org.lwjgl.assimp.AIVector3D;

import java.nio.IntBuffer;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Immutable snapshot of one Assimp mesh.
 *
 * <p>Everything is copied out of native memory in the constructor; after the owning
 * {@link AssimpScene} releases the {@code aiScene}, this object stays fully valid. Never
 * hold on to the {@code AIMesh} handle - it dies with the scene.
 */
public class AssimpMesh {

    private final int index;
    private final String name;
    private final int materialIndex;
    private final CoordinateSystemConverter.CoordinateSystem sourceCoordinateSystem;

    private final float[] vertices;
    private final float[] normals;
    private final float[] texCoords;
    private final float[] tangents;
    private final float[] bitangents;
    private final float[] vertexColors;
    private final int[] indices;
    private final int faceCount;

    /** Bone name -> inverse bind (offset) matrix, in stable declaration order. */
    private final Map<String, Matrix4f> boneOffsets;
    private final SkinnedMeshProcessor.SkinnedMeshData skinnedData;

    public AssimpMesh(AIMesh aiMesh, int index, CoordinateSystemConverter.CoordinateSystem coordinateSystem) {
        this.index = index;
        this.name = aiMesh.mName().dataString();
        this.materialIndex = aiMesh.mMaterialIndex();
        this.sourceCoordinateSystem = coordinateSystem;
        this.faceCount = aiMesh.mNumFaces();

        this.vertices = readVertices(aiMesh);
        this.normals = readNormals(aiMesh);
        this.texCoords = readTexCoords(aiMesh);
        this.tangents = readTangents(aiMesh);
        this.bitangents = readBitangents(aiMesh);
        this.vertexColors = readVertexColors(aiMesh);
        this.indices = readIndices(aiMesh);

        this.boneOffsets = readBones(aiMesh);
        this.skinnedData = this.boneOffsets.isEmpty()
                ? null
                : SkinnedMeshProcessor.extractBoneWeights(aiMesh, boneNameToIndex(boneOffsets));

        DebugManager.debug(DebugCategory.CORE,
                "Mesh '{}': {} vertices, {} indices, {} bones",
                name, getVertexCount(), indices.length, boneOffsets.size());
    }

    // ------------------------------------------------------------------ geometry

    private float[] readVertices(AIMesh aiMesh) {
        int vertexCount = aiMesh.mNumVertices();
        float[] out = new float[vertexCount * 3];
        AIVector3D.Buffer buffer = aiMesh.mVertices();
        if (buffer == null) {
            return out;
        }

        for (int i = 0; i < vertexCount; i++) {
            AIVector3D vertex = buffer.get(i);
            Vector3f converted = CoordinateSystemConverter.convertPosition(
                    new Vector3f(vertex.x(), vertex.y(), vertex.z()),
                    sourceCoordinateSystem,
                    CoordinateSystemConverter.CoordinateSystem.MINECRAFT_LEFT_HANDED);

            out[i * 3] = converted.x;
            out[i * 3 + 1] = converted.y;
            out[i * 3 + 2] = converted.z;
        }
        return out;
    }

    private float[] readNormals(AIMesh aiMesh) {
        AIVector3D.Buffer buffer = aiMesh.mNormals();
        if (buffer == null) {
            return null;
        }

        int vertexCount = aiMesh.mNumVertices();
        float[] out = new float[vertexCount * 3];
        for (int i = 0; i < vertexCount; i++) {
            AIVector3D normal = buffer.get(i);
            Vector3f converted = CoordinateSystemConverter.convertDirection(
                    new Vector3f(normal.x(), normal.y(), normal.z()),
                    sourceCoordinateSystem,
                    CoordinateSystemConverter.CoordinateSystem.MINECRAFT_LEFT_HANDED);

            out[i * 3] = converted.x;
            out[i * 3 + 1] = converted.y;
            out[i * 3 + 2] = converted.z;
        }
        return out;
    }

    private float[] readTexCoords(AIMesh aiMesh) {
        AIVector3D.Buffer buffer = aiMesh.mTextureCoords(0);
        if (buffer == null) {
            return null;
        }

        int vertexCount = aiMesh.mNumVertices();
        float[] out = new float[vertexCount * 2];
        for (int i = 0; i < vertexCount; i++) {
            AIVector3D uv = buffer.get(i);
            out[i * 2] = uv.x();
            out[i * 2 + 1] = uv.y();
        }
        return out;
    }

    private float[] readTangents(AIMesh aiMesh) {
        AIVector3D.Buffer buffer = aiMesh.mTangents();
        if (buffer == null) {
            return null;
        }

        int vertexCount = aiMesh.mNumVertices();
        float[] out = new float[vertexCount * 3];
        for (int i = 0; i < vertexCount; i++) {
            AIVector3D tangent = buffer.get(i);
            out[i * 3] = tangent.x();
            out[i * 3 + 1] = tangent.y();
            out[i * 3 + 2] = tangent.z();
        }
        return out;
    }

    private float[] readBitangents(AIMesh aiMesh) {
        AIVector3D.Buffer buffer = aiMesh.mBitangents();
        if (buffer == null) {
            return null;
        }

        int vertexCount = aiMesh.mNumVertices();
        float[] out = new float[vertexCount * 3];
        for (int i = 0; i < vertexCount; i++) {
            AIVector3D bitangent = buffer.get(i);
            out[i * 3] = bitangent.x();
            out[i * 3 + 1] = bitangent.y();
            out[i * 3 + 2] = bitangent.z();
        }
        return out;
    }

    private float[] readVertexColors(AIMesh aiMesh) {
        AIColor4D.Buffer buffer = aiMesh.mColors(0);
        if (buffer == null) {
            return null;
        }

        int vertexCount = aiMesh.mNumVertices();
        float[] out = new float[vertexCount * 4];
        for (int i = 0; i < vertexCount; i++) {
            AIColor4D color = buffer.get(i);
            out[i * 4] = color.r();
            out[i * 4 + 1] = color.g();
            out[i * 4 + 2] = color.b();
            out[i * 4 + 3] = color.a();
        }
        return out;
    }

    private int[] readIndices(AIMesh aiMesh) {
        int faceCount = aiMesh.mNumFaces();
        AIFace.Buffer faceBuffer = aiMesh.mFaces();
        if (faceBuffer == null) {
            return new int[0];
        }

        int totalIndices = 0;
        for (int i = 0; i < faceCount; i++) {
            totalIndices += faceBuffer.get(i).mNumIndices();
        }

        int[] out = new int[totalIndices];
        int cursor = 0;
        for (int i = 0; i < faceCount; i++) {
            AIFace face = faceBuffer.get(i);
            IntBuffer faceIndices = face.mIndices();
            int numIndices = face.mNumIndices();
            for (int j = 0; j < numIndices; j++) {
                out[cursor++] = faceIndices.get(j);
            }
        }
        return out;
    }

    // ------------------------------------------------------------------ skeleton

    private Map<String, Matrix4f> readBones(AIMesh aiMesh) {
        int boneCount = aiMesh.mNumBones();
        if (boneCount <= 0) {
            return new LinkedHashMap<>();
        }

        Map<String, Matrix4f> out = new LinkedHashMap<>(boneCount);
        for (int i = 0; i < boneCount; i++) {
            AIBone aiBone = AIBone.create(aiMesh.mBones().get(i));
            AIMatrix4x4 m = aiBone.mOffsetMatrix();
            out.put(aiBone.mName().dataString(), new Matrix4f(
                    m.a1(), m.a2(), m.a3(), m.a4(),
                    m.b1(), m.b2(), m.b3(), m.b4(),
                    m.c1(), m.c2(), m.c3(), m.c4(),
                    m.d1(), m.d2(), m.d3(), m.d4()));
        }
        return out;
    }

    private static Map<String, Integer> boneNameToIndex(Map<String, Matrix4f> boneOffsets) {
        Map<String, Integer> map = new LinkedHashMap<>(boneOffsets.size());
        int i = 0;
        for (String boneName : boneOffsets.keySet()) {
            map.put(boneName, i++);
        }
        return map;
    }

    // ------------------------------------------------------------------ accessors

    public float[] getVertices() {
        return vertices;
    }

    public float[] getNormals() {
        return normals;
    }

    public float[] getTexCoords() {
        return texCoords;
    }

    public float[] getTangents() {
        return tangents;
    }

    public float[] getBitangents() {
        return bitangents;
    }

    public float[] getVertexColors() {
        return vertexColors;
    }

    public int[] getIndices() {
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
        return vertices.length / 3;
    }

    public int getFaceCount() {
        return faceCount;
    }

    public boolean hasNormals() {
        return normals != null;
    }

    public boolean hasTexCoords() {
        return texCoords != null;
    }

    public boolean hasVertexColors() {
        return vertexColors != null;
    }

    public boolean hasTangentsAndBitangents() {
        return tangents != null && bitangents != null;
    }

    public boolean hasBones() {
        return !boneOffsets.isEmpty();
    }

    /** Bone names in the same order as the bone indices used by the vertex data. */
    public List<String> getBoneNames() {
        return new ArrayList<>(boneOffsets.keySet());
    }

    public Map<String, Matrix4f> getBoneOffsets() {
        return boneOffsets;
    }

    public SkinnedMeshProcessor.SkinnedMeshData getSkinnedData() {
        return skinnedData;
    }
}
