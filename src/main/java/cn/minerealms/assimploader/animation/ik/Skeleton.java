package cn.minerealms.assimploader.animation.ik;

import cn.minerealms.assimploader.animation.Bone;
import org.jetbrains.annotations.Nullable;

import java.util.*;

/**
 * Skeleton - Enhanced bone hierarchy manager for IK system.
 *
 * Wraps the existing Bone hierarchy and provides:
 * - Fast bone lookup by name
 * - Bone chain extraction (start -> end)
 * - Hierarchy traversal utilities
 * - Transform update coordination
 *
 * Phase 1: IK Foundation
 */
public class Skeleton {

    private final Map<String, Bone> boneMap;
    private final Bone rootBone;
    private final List<Bone> allBones;

    /**
     * Create a skeleton from a root bone.
     * Automatically builds the bone map by traversing the hierarchy.
     */
    public Skeleton(Bone rootBone) {
        this.rootBone = rootBone;
        this.boneMap = new HashMap<>();
        this.allBones = new ArrayList<>();
        buildBoneMap(rootBone);
    }

    /**
     * Recursively build bone map from hierarchy
     */
    private void buildBoneMap(Bone bone) {
        boneMap.put(bone.getName(), bone);
        allBones.add(bone);
        for (Bone child : bone.getChildren()) {
            buildBoneMap(child);
        }
    }

    /**
     * Get a bone by name
     *
     * @param name Bone name
     * @return Bone instance, or null if not found
     */
    @Nullable
    public Bone getBone(String name) {
        return boneMap.get(name);
    }

    /**
     * Extract a bone chain from start to end bone.
     *
     * Algorithm:
     * 1. Find path from end bone to root
     * 2. Find path from start bone to root
     * 3. Find common ancestor
     * 4. Build chain: start -> ... -> common ancestor -> ... -> end
     *
     * @param startBoneName Start bone name (e.g., "LeftHip")
     * @param endBoneName End bone name (e.g., "LeftAnkle")
     * @return List of bones forming the chain, or empty list if invalid
     */
    public List<Bone> getBoneChain(String startBoneName, String endBoneName) {
        Bone startBone = getBone(startBoneName);
        Bone endBone = getBone(endBoneName);

        if (startBone == null || endBone == null) {
            return Collections.emptyList();
        }

        // Special case: same bone
        if (startBone == endBone) {
            return Collections.singletonList(startBone);
        }

        // Get path from end to root
        List<Bone> endToRoot = getPathToRoot(endBone);

        // Get path from start to root
        List<Bone> startToRoot = getPathToRoot(startBone);

        // Find common ancestor
        Bone commonAncestor = findCommonAncestor(startToRoot, endToRoot);
        if (commonAncestor == null) {
            return Collections.emptyList();
        }

        // Build chain: start -> common ancestor
        List<Bone> chain = new ArrayList<>();
        Bone current = startBone;
        while (current != null && current != commonAncestor) {
            chain.add(current);
            current = current.getParent();
        }
        chain.add(commonAncestor);

        // Add: common ancestor -> end (reversed)
        List<Bone> ancestorToEnd = new ArrayList<>();
        current = endBone;
        while (current != null && current != commonAncestor) {
            ancestorToEnd.add(current);
            current = current.getParent();
        }
        Collections.reverse(ancestorToEnd);
        chain.addAll(ancestorToEnd);

        return chain;
    }

    /**
     * Get path from bone to root
     */
    private List<Bone> getPathToRoot(Bone bone) {
        List<Bone> path = new ArrayList<>();
        Bone current = bone;
        while (current != null) {
            path.add(current);
            current = current.getParent();
        }
        return path;
    }

    /**
     * Find common ancestor of two bones
     */
    @Nullable
    private Bone findCommonAncestor(List<Bone> path1, List<Bone> path2) {
        Set<Bone> ancestors1 = new HashSet<>(path1);
        for (Bone bone : path2) {
            if (ancestors1.contains(bone)) {
                return bone;
            }
        }
        return null;
    }

    /**
     * Update all bone transforms in the hierarchy.
     * Should be called after IK solving.
     */
    public void updateTransforms() {
        rootBone.updateTransforms();
    }

    /**
     * Get root bone of the skeleton
     */
    public Bone getRootBone() {
        return rootBone;
    }

    /**
     * Get all bones in the skeleton (flat list)
     */
    public List<Bone> getAllBones() {
        return Collections.unmodifiableList(allBones);
    }

    /**
     * Get number of bones in the skeleton
     */
    public int getBoneCount() {
        return allBones.size();
    }

    /**
     * Check if a bone exists in the skeleton
     */
    public boolean hasBone(String name) {
        return boneMap.containsKey(name);
    }

    /**
     * Get all bone names
     */
    public Set<String> getBoneNames() {
        return Collections.unmodifiableSet(boneMap.keySet());
    }
}
