package cn.minerealms.assimploader.animation;

import cn.minerealms.assimploader.debug.DebugManager;
import cn.minerealms.assimploader.debug.DebugManager.DebugCategory;
import cn.minerealms.assimploader.model.AssimpMesh;
import cn.minerealms.assimploader.model.AssimpNode;
import org.joml.Matrix4f;

import java.util.LinkedHashMap;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
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
    private final Map<String, Bone> hierarchyNodes = new LinkedHashMap<>();
    private final List<Bone> roots = new ArrayList<>();
    private Bone rootBone;

    /**
     * Build the bone hierarchy for a skinned mesh.
     *
     * <p>Bones are inserted in the mesh's own bone order, so the index used by the vertex
     * weights (see {@code AssimpMesh#getSkinnedData()}) is exactly the index of the matrix
     * returned by {@link #getFinalTransforms()}.
     */
    public void buildFromMesh(AssimpMesh mesh, AssimpNode rootNode) {
        buildFromOffsets(mesh.getBoneOffsets(), rootNode);
    }

    void buildFromOffsets(Map<String, Matrix4f> offsets, AssimpNode rootNode) {
        boneMap.clear();
        hierarchyNodes.clear();
        roots.clear();
        rootBone = null;
        if (offsets.isEmpty()) {
            return;
        }

        DebugManager.debug(DebugCategory.ANIMATION, "Building bone hierarchy from {} bones", offsets.size());

        for (Map.Entry<String, Matrix4f> entry : offsets.entrySet()) {
            boneMap.put(entry.getKey(), new Bone(entry.getKey(), entry.getValue()));
        }

        if (rootNode != null) {
            buildHierarchy(rootNode, null);
        }

        for (Bone bone : boneMap.values()) {
            if (!hierarchyNodes.containsKey(bone.getName())) {
                hierarchyNodes.put(bone.getName(), bone);
                roots.add(bone);
            }
        }
        rootBone = roots.isEmpty() ? null : roots.get(0);
        updateTransforms();
    }

    private void buildHierarchy(AssimpNode node, Bone parentBone) {
        // Non-weighted parents still carry bind transforms and animation channels.
        Bone currentBone = boneMap.getOrDefault(node.getName(), new Bone(node.getName(), new Matrix4f()));
        hierarchyNodes.put(node.getName(), currentBone);
        currentBone.setBindTransform(node.getTransformation());
        if (parentBone != null) {
            parentBone.addChild(currentBone);
        } else {
            roots.add(currentBone);
        }

        for (AssimpNode child : node.getChildren()) {
            buildHierarchy(child, currentBone);
        }
    }

    /**
     * Get bone by name
     */
    public Bone getBone(String name) {
        return hierarchyNodes.get(name);
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
        return Collections.unmodifiableMap(boneMap);
    }

    public Map<String, Bone> getHierarchyNodes() {
        return Collections.unmodifiableMap(hierarchyNodes);
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
        Bone bone = hierarchyNodes.get(boneName);
        if (bone != null) {
            bone.setLocalTransform(localTransform);
        }
    }

    /**
     * Update all bone transformations
     * Call this after setting animation transforms
     */
    public void updateTransforms() {
        for (Bone root : roots) {
            root.updateTransforms();
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
        for (Bone bone : hierarchyNodes.values()) {
            bone.resetToBindPose();
        }
        updateTransforms();
    }

    /**
     * Print bone hierarchy for debugging
     */
    public void printHierarchy() {
        if (!roots.isEmpty()) {
            DebugManager.info(DebugCategory.ANIMATION, "Bone Hierarchy:");
            for (Bone root : roots) {
                printBoneRecursive(root, 0);
            }
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
