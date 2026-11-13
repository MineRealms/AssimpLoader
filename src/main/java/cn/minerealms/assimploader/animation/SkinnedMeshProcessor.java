package cn.minerealms.assimploader.animation;

import cn.minerealms.assimploader.config.AssimpLoaderConfig;
import cn.minerealms.assimploader.debug.DebugManager;
import cn.minerealms.assimploader.debug.DebugManager.DebugCategory;
import org.lwjgl.assimp.AIBone;
import org.lwjgl.assimp.AIMesh;
import org.lwjgl.assimp.AIVertexWeight;

import java.util.HashMap;
import java.util.Map;
import java.util.Arrays;

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
        return Math.max(1, Math.min(8,
                AssimpLoaderConfig.getIntOrDefault(AssimpLoaderConfig.COMMON.animMaxBonesPerVertex, 4)));
    }

    /**
     * Vertex bone data structure
     */
    static class VertexBoneData {
        final int[] boneIndices;
        final float[] boneWeights;
        int count = 0;

        VertexBoneData(int maxBones) {
            boneIndices = new int[maxBones];
            Arrays.fill(boneIndices, -1);
            boneWeights = new float[maxBones];
        }

        void addBoneData(int boneIndex, float weight) {
            if (boneIndex < 0 || !Float.isFinite(weight) || weight <= 0) return;
            int slot = Math.min(count, boneWeights.length - 1);
            if (count == boneWeights.length && weight <= boneWeights[slot]) return;
            while (slot > 0 && weight > boneWeights[slot - 1]) {
                boneIndices[slot] = boneIndices[slot - 1];
                boneWeights[slot] = boneWeights[slot - 1];
                slot--;
            }
            boneIndices[slot] = boneIndex;
            boneWeights[slot] = weight;
            count = Math.min(count + 1, boneWeights.length);
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
        int maxBones = getMaxBonesPerVertex();

        DebugManager.debug(DebugCategory.ANIMATION, "Extracting bone weights: {} vertices, {} bones", numVertices, numBones);

        // Initialize vertex bone data
        VertexBoneData[] vertexBoneData = new VertexBoneData[numVertices];
        for (int i = 0; i < numVertices; i++) {
            vertexBoneData[i] = new VertexBoneData(maxBones);
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
        return convertToFlatArrays(vertexBoneData, maxBones);
    }

    /**
     * Convert vertex bone data to flat arrays for GPU
     */
    private static SkinnedMeshData convertToFlatArrays(VertexBoneData[] vertexBoneData, int maxBones) {
        int numVertices = vertexBoneData.length;
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

        return new SkinnedMeshData(boneIndices, boneWeights, numVertices);
    }

    /**
     * Skinned mesh data container
     */
    public static class SkinnedMeshData {
        private final int[] boneIndices;
        private final float[] boneWeights;
        private final int vertexCount;

        public SkinnedMeshData(int[] boneIndices, float[] boneWeights) {
            this(boneIndices, boneWeights, boneIndices.length / getMaxBonesPerVertex());
        }

        public SkinnedMeshData(int[] boneIndices, float[] boneWeights, int vertexCount) {
            if (boneIndices.length != boneWeights.length || vertexCount < 0
                    || (vertexCount == 0 ? boneIndices.length != 0 : boneIndices.length % vertexCount != 0)) {
                throw new IllegalArgumentException("Invalid bone influence layout");
            }
            this.boneIndices = boneIndices;
            this.boneWeights = boneWeights;
            this.vertexCount = vertexCount;
        }

        public int[] getBoneIndices() {
            return boneIndices;
        }

        public float[] getBoneWeights() {
            return boneWeights;
        }

        public int getVertexCount() {
            return vertexCount;
        }
    }
}
