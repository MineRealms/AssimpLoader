package cn.minerealms.assimploader.animation;

import cn.minerealms.assimploader.AssimpLoader;
import org.lwjgl.assimp.AIBone;
import org.lwjgl.assimp.AIMesh;
import org.lwjgl.assimp.AIVertexWeight;

import java.util.HashMap;
import java.util.Map;

/**
 * Processes skinned mesh data for skeletal animation.
 *
 * Extracts:
 * - Bone weights per vertex
 * - Bone indices per vertex
 * - Up to 4 bones per vertex (GPU limitation)
 *
 * Output format for GPU:
 * - boneIndices: int[vertexCount * 4]
 * - boneWeights: float[vertexCount * 4]
 *
 * References:
 * - https://learnopengl.com/Guest-Articles/2020/Skeletal-Animation
 */
public class SkinnedMeshProcessor {

    private static final int MAX_BONES_PER_VERTEX = 4;

    /**
     * Vertex bone data structure
     */
    private static class VertexBoneData {
        int[] boneIndices = new int[MAX_BONES_PER_VERTEX];
        float[] boneWeights = new float[MAX_BONES_PER_VERTEX];
        int count = 0;

        void addBoneData(int boneIndex, float weight) {
            if (count >= MAX_BONES_PER_VERTEX) {
                AssimpLoader.LOGGER.warn("Vertex has more than {} bones, ignoring extra bones", MAX_BONES_PER_VERTEX);
                return;
            }

            boneIndices[count] = boneIndex;
            boneWeights[count] = weight;
            count++;
        }

        void normalize() {
            float totalWeight = 0.0f;
            for (int i = 0; i < count; i++) {
                totalWeight += boneWeights[i];
            }

            if (totalWeight > 0.0f) {
                for (int i = 0; i < count; i++) {
                    boneWeights[i] /= totalWeight;
                }
            }
        }
    }

    /**
     * Extract bone weights from mesh
     */
    public static SkinnedMeshData extractBoneWeights(AIMesh aiMesh, Map<String, Integer> boneNameToIndexMap) {
        int numVertices = aiMesh.mNumVertices();
        int numBones = aiMesh.mNumBones();

        AssimpLoader.LOGGER.debug("Extracting bone weights: {} vertices, {} bones", numVertices, numBones);

        // Initialize vertex bone data
        VertexBoneData[] vertexBoneData = new VertexBoneData[numVertices];
        for (int i = 0; i < numVertices; i++) {
            vertexBoneData[i] = new VertexBoneData();
        }

        // Extract bone weights
        for (int boneIndex = 0; boneIndex < numBones; boneIndex++) {
            AIBone aiBone = AIBone.create(aiMesh.mBones().get(boneIndex));
            String boneName = aiBone.mName().dataString();

            // Get bone index from map
            Integer boneId = boneNameToIndexMap.get(boneName);
            if (boneId == null) {
                AssimpLoader.LOGGER.warn("Bone {} not found in bone map", boneName);
                continue;
            }

            // Process weights for this bone
            int numWeights = aiBone.mNumWeights();
            AIVertexWeight.Buffer weights = aiBone.mWeights();

            for (int weightIndex = 0; weightIndex < numWeights; weightIndex++) {
                AIVertexWeight weight = weights.get(weightIndex);
                int vertexId = weight.mVertexId();
                float weightValue = weight.mWeight();

                if (vertexId >= 0 && vertexId < numVertices) {
                    vertexBoneData[vertexId].addBoneData(boneId, weightValue);
                }
            }
        }

        // Normalize all weights
        for (VertexBoneData data : vertexBoneData) {
            data.normalize();
        }

        // Convert to flat arrays
        return convertToFlatArrays(vertexBoneData);
    }

    /**
     * Convert vertex bone data to flat arrays for GPU
     */
    private static SkinnedMeshData convertToFlatArrays(VertexBoneData[] vertexBoneData) {
        int numVertices = vertexBoneData.length;
        int[] boneIndices = new int[numVertices * MAX_BONES_PER_VERTEX];
        float[] boneWeights = new float[numVertices * MAX_BONES_PER_VERTEX];

        for (int i = 0; i < numVertices; i++) {
            VertexBoneData data = vertexBoneData[i];
            int baseIndex = i * MAX_BONES_PER_VERTEX;

            for (int j = 0; j < MAX_BONES_PER_VERTEX; j++) {
                boneIndices[baseIndex + j] = data.boneIndices[j];
                boneWeights[baseIndex + j] = data.boneWeights[j];
            }
        }

        return new SkinnedMeshData(boneIndices, boneWeights);
    }

    /**
     * Skinned mesh data container
     */
    public static class SkinnedMeshData {
        private final int[] boneIndices;
        private final float[] boneWeights;

        public SkinnedMeshData(int[] boneIndices, float[] boneWeights) {
            this.boneIndices = boneIndices;
            this.boneWeights = boneWeights;
        }

        public int[] getBoneIndices() {
            return boneIndices;
        }

        public float[] getBoneWeights() {
            return boneWeights;
        }

        public int getVertexCount() {
            return boneIndices.length / MAX_BONES_PER_VERTEX;
        }
    }
}
