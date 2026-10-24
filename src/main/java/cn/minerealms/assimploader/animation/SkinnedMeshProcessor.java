package cn.minerealms.assimploader.animation;

import cn.minerealms.assimploader.config.AssimpLoaderConfig;
import cn.minerealms.assimploader.debug.DebugManager;
import cn.minerealms.assimploader.debug.DebugManager.DebugCategory;
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
 * - Configurable bones per vertex (default 4, max 8)
 *
 * Output format for GPU:
 * - boneIndices: int[vertexCount * maxBonesPerVertex]
 * - boneWeights: float[vertexCount * maxBonesPerVertex]
 *
 * References:
 * - https://learnopengl.com/Guest-Articles/2020/Skeletal-Animation
 */
public class SkinnedMeshProcessor {

    /**
     * Get max bones per vertex from config
     */
    private static int getMaxBonesPerVertex() {
        return AssimpLoaderConfig.getIntOrDefault(AssimpLoaderConfig.COMMON.animMaxBonesPerVertex, 4);
    }

    /**
     * Vertex bone data structure
     */
    private static class VertexBoneData {
        int[] boneIndices = new int[8]; // Max 8 bones (configurable)
        float[] boneWeights = new float[8];
        int count = 0;

        void addBoneData(int boneIndex, float weight) {
            int maxBones = getMaxBonesPerVertex();
            if (count >= maxBones) {
                DebugManager.warn(DebugCategory.ANIMATION, "Vertex has more than {} bones, ignoring extra bones", maxBones);
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

        DebugManager.debug(DebugCategory.ANIMATION, "Extracting bone weights: {} vertices, {} bones", numVertices, numBones);

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
                DebugManager.warn(DebugCategory.ANIMATION, "Bone {} not found in bone map", boneName);
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
        int maxBones = getMaxBonesPerVertex();
        int[] boneIndices = new int[numVertices * maxBones];
        float[] boneWeights = new float[numVertices * maxBones];

        for (int i = 0; i < numVertices; i++) {
            VertexBoneData data = vertexBoneData[i];
            int baseIndex = i * maxBones;

            for (int j = 0; j < maxBones; j++) {
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
            int maxBones = getMaxBonesPerVertex();
            return boneIndices.length / maxBones;
        }
    }
}
