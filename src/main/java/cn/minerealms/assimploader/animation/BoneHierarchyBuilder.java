package cn.minerealms.assimploader.animation;

import cn.minerealms.assimploader.debug.DebugManager;
import cn.minerealms.assimploader.debug.DebugManager.DebugCategory;
import cn.minerealms.assimploader.model.AssimpMesh;
import cn.minerealms.assimploader.model.AssimpNode;
import org.joml.Matrix4f;

import java.util.LinkedHashMap;
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

    /**
     * Insertion ordered: index N in {@link #getFinalTransforms()} must match bone index N in
     * the vertex data, which is why the map is filled in the mesh's bone order.
     */
    private final Map<String, Bone> boneMap = new LinkedHashMap<>();
    private Bone rootBone;

    /**
     * Build the bone hierarchy for a skinned mesh.
     *
     * <p>Bones are inserted in the mesh's own bone order, so the index used by the vertex
     * weights (see {@code AssimpMesh#getSkinnedData()}) is exactly the index of the matrix
     * returned by {@link #getFinalTransforms()}.
     */
    public void buildFromMesh(AssimpMesh mesh, AssimpNode rootNode) {
        if (!mesh.hasBones()) {
            return;
        }

        DebugManager.debug(DebugCategory.ANIMATION, "Building bone hierarchy from {} bones", mesh.getBoneOffsets().size());

        for (Map.Entry<String, Matrix4f> entry : mesh.getBoneOffsets().entrySet()) {
            boneMap.put(entry.getKey(), new Bone(entry.getKey(), entry.getValue()));
        }

        if (rootNode != null) {
            buildHierarchy(rootNode, null);
        }

        // Every bone that never appeared in the scene graph becomes a root candidate;
        // the first parentless bone is the root.
        for (Bone bone : boneMap.values()) {
            if (bone.getParent() == null) {
                rootBone = bone;
                DebugManager.debug(DebugCategory.ANIMATION, "Root bone: {}", bone.getName());
                break;
            }
        }
    }

    private void buildHierarchy(AssimpNode node, Bone parentBone) {
        Bone currentBone = boneMap.get(node.getName());

        if (currentBone != null) {
            if (parentBone != null && currentBone != parentBone) {
                parentBone.addChild(currentBone);
            }
            currentBone.setLocalTransform(node.getTransformation());
        }

        for (AssimpNode child : node.getChildren()) {
            buildHierarchy(child, currentBone != null ? currentBone : parentBone);
        }
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
     * Update a specific bone's local transform
     * Used by animation system to apply animated transforms
     */
    public void updateBoneTransform(String boneName, Matrix4f localTransform) {
        Bone bone = boneMap.get(boneName);
        if (bone != null) {
            bone.setLocalTransform(localTransform);
        }
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
