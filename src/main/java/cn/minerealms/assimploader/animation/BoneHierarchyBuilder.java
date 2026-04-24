package cn.minerealms.assimploader.animation;

import cn.minerealms.assimploader.debug.DebugManager;
import cn.minerealms.assimploader.debug.DebugManager.DebugCategory;
import cn.minerealms.assimploader.model.AssimpNode;
import org.joml.Matrix4f;
import org.lwjgl.assimp.AIBone;
import org.lwjgl.assimp.AIMesh;
import org.lwjgl.assimp.AINode;

import java.util.HashMap;
import java.util.Map;

/**
 * Builds bone hierarchy from Assimp scene graph.
 *
 * Process:
 * 1. Extract bones from meshes
 * 2. Build bone tree from scene graph
 * 3. Calculate bind pose matrices
 * 4. Store offset matrices
 *
 * Bone transformation formula:
 * FinalTransform = GlobalTransform × OffsetMatrix
 * GlobalTransform = ParentGlobalTransform × LocalTransform
 *
 * References:
 * - https://learnopengl.com/Guest-Articles/2020/Skeletal-Animation
 * - https://github.com/assimp/assimp/blob/master/code/AssetLib/FBX/FBXImporter.cpp
 */
public class BoneHierarchyBuilder {

    private final Map<String, Bone> boneMap = new HashMap<>();
    private Bone rootBone;

    /**
     * Build bone hierarchy from Assimp mesh bones
     */
    public void buildFromMesh(AIMesh aiMesh, AINode rootNode) {
        int numBones = aiMesh.mNumBones();
        if (numBones == 0) {
            return;
        }

        DebugManager.debug(DebugCategory.ANIMATION, "Building bone hierarchy from {} bones", numBones);

        // Extract all bones from mesh
        for (int i = 0; i < numBones; i++) {
            AIBone aiBone = AIBone.create(aiMesh.mBones().get(i));
            String boneName = aiBone.mName().dataString();

            // Create bone with offset matrix
            Matrix4f offsetMatrix = convertMatrix(aiBone.mOffsetMatrix());
            Bone bone = new Bone(boneName, offsetMatrix);

            boneMap.put(boneName, bone);
            DebugManager.debug(DebugCategory.ANIMATION, "  Bone: {} (offset matrix extracted)", boneName);
        }

        // Build hierarchy from scene graph
        if (rootNode != null) {
            buildHierarchy(rootNode, null);
        }

        // Find root bone (bone with no parent)
        for (Bone bone : boneMap.values()) {
            if (bone.getParent() == null) {
                rootBone = bone;
                DebugManager.debug(DebugCategory.ANIMATION, "Root bone: {}", bone.getName());
                break;
            }
        }
    }

    /**
     * Recursively build bone hierarchy from scene graph
     */
    private void buildHierarchy(AINode aiNode, Bone parentBone) {
        String nodeName = aiNode.mName().dataString();

        // Check if this node is a bone
        Bone currentBone = boneMap.get(nodeName);

        if (currentBone != null) {
            // This node is a bone
            if (parentBone != null) {
                parentBone.addChild(currentBone);
            }

            // Set local transformation
            Matrix4f localTransform = convertMatrix(aiNode.mTransformation());
            currentBone.setLocalTransform(localTransform);

            DebugManager.debug(DebugCategory.ANIMATION, "  Bone hierarchy: {} -> {}",
                parentBone != null ? parentBone.getName() : "ROOT", nodeName);
        }

        // Process children
        int numChildren = aiNode.mNumChildren();
        for (int i = 0; i < numChildren; i++) {
            AINode childNode = AINode.create(aiNode.mChildren().get(i));
            buildHierarchy(childNode, currentBone != null ? currentBone : parentBone);
        }
    }

    /**
     * Convert Assimp matrix to JOML matrix
     */
    private Matrix4f convertMatrix(org.lwjgl.assimp.AIMatrix4x4 aiMatrix) {
        return new Matrix4f(
            aiMatrix.a1(), aiMatrix.a2(), aiMatrix.a3(), aiMatrix.a4(),
            aiMatrix.b1(), aiMatrix.b2(), aiMatrix.b3(), aiMatrix.b4(),
            aiMatrix.c1(), aiMatrix.c2(), aiMatrix.c3(), aiMatrix.c4(),
            aiMatrix.d1(), aiMatrix.d2(), aiMatrix.d3(), aiMatrix.d4()
        );
    }

    /**
     * Get bone by name
     */
    public Bone getBone(String name) {
        return boneMap.get(name);
    }

    /**
     * Get root bone
     */
    public Bone getRootBone() {
        return rootBone;
    }

    /**
     * Get all bones
     */
    public Map<String, Bone> getAllBones() {
        return boneMap;
    }

    /**
     * Get bone count
     */
    public int getBoneCount() {
        return boneMap.size();
    }

    /**
     * Update all bone transformations
     * Call this after setting animation transforms
     */
    public void updateTransforms() {
        if (rootBone != null) {
            rootBone.updateTransforms();
        }
    }

    /**
     * Get final transformation matrices for all bones
     * Used for GPU skinning (uniform buffer)
     */
    public Matrix4f[] getFinalTransforms() {
        Matrix4f[] transforms = new Matrix4f[boneMap.size()];
        int index = 0;

        for (Bone bone : boneMap.values()) {
            transforms[index++] = new Matrix4f(bone.getFinalTransform());
        }

        return transforms;
    }

    /**
     * Get final transformation matrices as flat float array
     * Format: [m00, m01, m02, m03, m10, m11, ...] for each matrix
     */
    public float[] getFinalTransformsFlat() {
        int boneCount = boneMap.size();
        float[] transforms = new float[boneCount * 16]; // 16 floats per matrix

        int index = 0;
        for (Bone bone : boneMap.values()) {
            Matrix4f matrix = bone.getFinalTransform();
            matrix.get(transforms, index);
            index += 16;
        }

        return transforms;
    }

    /**
     * Reset all bones to bind pose
     */
    public void resetToBindPose() {
        for (Bone bone : boneMap.values()) {
            bone.setLocalTransform(new Matrix4f().identity());
        }
        updateTransforms();
    }

    /**
     * Print bone hierarchy for debugging
     */
    public void printHierarchy() {
        if (rootBone != null) {
            DebugManager.info(DebugCategory.ANIMATION, "Bone Hierarchy:");
            printBoneRecursive(rootBone, 0);
        }
    }

    private void printBoneRecursive(Bone bone, int depth) {
        StringBuilder indent = new StringBuilder();
        for (int i = 0; i < depth; i++) {
            indent.append("  ");
        }

        DebugManager.info(DebugCategory.ANIMATION, "{}|- {}", indent, bone.getName());

        for (Bone child : bone.getChildren()) {
            printBoneRecursive(child, depth + 1);
        }
    }
}
