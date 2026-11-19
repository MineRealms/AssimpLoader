package cn.minerealms.assimploader.model;

import cn.minerealms.assimploader.animation.SkinnedMeshProcessor;
import cn.minerealms.assimploader.animation.MorphTarget;
import cn.minerealms.assimploader.debug.DebugManager;
import cn.minerealms.assimploader.debug.DebugManager.DebugCategory;
import cn.minerealms.assimploader.util.CoordinateSystemConverter;
import org.joml.Matrix4f;
import org.joml.Vector3f;
import org.lwjgl.assimp.AIBone;
import org.lwjgl.assimp.AIAnimMesh;
import org.lwjgl.assimp.AIColor4D;
import org.lwjgl.assimp.AIFace;
import org.lwjgl.assimp.AIMesh;
import org.lwjgl.assimp.AIVector3D;

import java.nio.IntBuffer;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.HashSet;

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
    private final List<MorphTarget> morphTargets;
    private final Map<String, String> morphTargetAliases;
    private final int morphMethod;

    /** Bone name -> inverse bind (offset) matrix, in stable declaration order. */
    private final Map<String, Matrix4f> boneOffsets;
    private final SkinnedMeshProcessor.SkinnedMeshData skinnedData;

    public AssimpMesh(AIMesh aiMesh, int index, CoordinateSystemConverter.CoordinateSystem coordinateSystem) {
        this(aiMesh, index, coordinateSystem, false);
    }

    AssimpMesh(AIMesh aiMesh, int index, CoordinateSystemConverter.CoordinateSystem coordinateSystem,
               boolean blenderFbx) {
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
        this.morphMethod = aiMesh.mMethod();
        this.morphTargets = readMorphTargets(aiMesh);
        this.morphTargetAliases = morphAliases(morphTargets.stream().map(MorphTarget::getName).toList(), blenderFbx);

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
        if (faceCount == 0) return new int[0];
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

    private List<MorphTarget> readMorphTargets(AIMesh mesh) {
        List<MorphTarget> targets = new ArrayList<>();
        if (mesh.mNumAnimMeshes() > 0 && mesh.mAnimMeshes() == null)
            throw new IllegalArgumentException("Missing morph target array for " + name);
        for (int i = 0; i < mesh.mNumAnimMeshes(); i++) {
            AIAnimMesh target = AIAnimMesh.create(mesh.mAnimMeshes().get(i));
            if (target.mNumVertices() != getVertexCount())
                throw new IllegalArgumentException("Morph vertex count mismatch for " + name + ": " + i);
            if (target.mNormals() != null && normals == null)
                throw new IllegalArgumentException("Morph normals require base normals for " + name);
            targets.add(new MorphTarget(target.mName().dataString(), target.mWeight(),
                    readMorphDeltas(target.mVertices(), vertices, false),
                    readMorphDeltas(target.mNormals(), normals, true)));
        }
        return List.copyOf(targets);
    }

    static Map<String, String> morphAliases(List<String> names, boolean blenderFbx) {
        if (!blenderFbx) return Map.of();
        var occupied = new HashSet<>(names);
        var aliases = new LinkedHashMap<String, String>();
        for (String name : names) {
            int dot = name.indexOf('.');
            if (dot <= 0 || dot != name.lastIndexOf('.')) continue;
            String shortName = name.substring(0, dot);
            if (shortName.equals(name.substring(dot + 1)) && !occupied.contains(shortName))
                aliases.put(name, shortName);
        }
        // Assimp may combine a Blender FBX channel and shape as Blink.Blink.
        // Keep the imported name: an authored Blink.Blink is indistinguishable here.
        return Map.copyOf(aliases);
    }

    private float[] readMorphDeltas(AIVector3D.Buffer stream, float[] base, boolean direction) {
        if (stream == null) return null;
        float[] deltas = new float[base.length];
        for (int i = 0; i < getVertexCount(); i++) {
            AIVector3D source = stream.get(i);
            Vector3f vector = new Vector3f(source.x(), source.y(), source.z());
            Vector3f converted = direction
                    ? CoordinateSystemConverter.convertDirection(vector, sourceCoordinateSystem,
                    CoordinateSystemConverter.CoordinateSystem.MINECRAFT_LEFT_HANDED)
                    : CoordinateSystemConverter.convertPosition(vector, sourceCoordinateSystem,
                    CoordinateSystemConverter.CoordinateSystem.MINECRAFT_LEFT_HANDED);
            deltas[i * 3] = converted.x - base[i * 3];
            deltas[i * 3 + 1] = converted.y - base[i * 3 + 1];
            deltas[i * 3 + 2] = converted.z - base[i * 3 + 2];
            if (!Float.isFinite(deltas[i * 3]) || !Float.isFinite(deltas[i * 3 + 1]) || !Float.isFinite(deltas[i * 3 + 2]))
                throw new IllegalArgumentException("Non-finite morph stream for " + name);
        }
        return deltas;
    }

    private Map<String, Matrix4f> readBones(AIMesh aiMesh) {
        int boneCount = aiMesh.mNumBones();
        if (boneCount <= 0) {
            return new LinkedHashMap<>();
        }

        Map<String, Matrix4f> out = new LinkedHashMap<>(boneCount);
        for (int i = 0; i < boneCount; i++) {
            AIBone aiBone = AIBone.create(aiMesh.mBones().get(i));
            out.put(aiBone.mName().dataString(), CoordinateSystemConverter.convertMatrix(
                    aiBone.mOffsetMatrix(), sourceCoordinateSystem,
                    CoordinateSystemConverter.CoordinateSystem.MINECRAFT_LEFT_HANDED));
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

    public List<MorphTarget> getMorphTargets() { return morphTargets; }
    /** Imported FBX name -> optional unambiguous legacy shorthand; original names remain valid. */
    public Map<String, String> getMorphTargetAliases() { return morphTargetAliases; }
    public boolean hasMorphTargets() { return !morphTargets.isEmpty(); }
    public int getMorphMethod() { return morphMethod; }
    public float[] getDefaultMorphWeights() {
        float[] weights = new float[morphTargets.size()];
        for (int i = 0; i < weights.length; i++) weights[i] = morphTargets.get(i).getDefaultWeight();
        return weights;
    }
}
