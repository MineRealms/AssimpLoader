package cn.minerealms.assimploader.animation.ik;

import cn.minerealms.assimploader.animation.Bone;
import org.jetbrains.annotations.Nullable;

import java.util.*;

/**
 * IKManager - Manages IK chains and integrates with the animation system.
 *
 * Responsibilities:
 * - Create/remove IK chains
 * - Update all IK chains after keyframe animation
 * - Enable/disable IK globally
 * - Provide chain lookup
 *
 * Phase 1: IK Foundation
 */
public class IKManager {

    private final Skeleton skeleton;
    private final Map<String, IKChain> ikChains;
    private boolean enabled = true;

    /**
     * Create an IK manager for a skeleton
     */
    public IKManager(Skeleton skeleton) {
        this.skeleton = skeleton;
        this.ikChains = new LinkedHashMap<>();
    }

    /**
     * Create an IK chain
     *
     * @param name Chain name (must be unique)
     * @param boneNames List of bone names forming the chain (ordered from root to tip)
     * @param solverType Type of IK solver to use
     * @return Created IK chain, or null if creation failed
     */
    @Nullable
    public IKChain createChain(String name, List<String> boneNames, IKSolver.Type solverType) {
        if (ikChains.containsKey(name)) {
            throw new IllegalArgumentException("IK chain already exists: " + name);
        }

        if (boneNames.isEmpty()) {
            throw new IllegalArgumentException("IK chain must have at least one bone");
        }

        // Extract bones from skeleton
        List<Bone> bones = new ArrayList<>();
        for (String boneName : boneNames) {
            Bone bone = skeleton.getBone(boneName);
            if (bone == null) {
                throw new IllegalArgumentException("Bone not found in skeleton: " + boneName);
            }
            bones.add(bone);
        }

        // Create solver
        IKSolver solver = createSolver(solverType);
        if (solver == null) {
            throw new IllegalArgumentException("Failed to create solver: " + solverType);
        }

        // Create chain
        IKChain chain = new IKChain(name, bones, solver);
        ikChains.put(name, chain);

        return chain;
    }

    /**
     * Create an IK chain from start bone to end bone (auto-extract chain)
     *
     * @param name Chain name
     * @param startBoneName Start bone name
     * @param endBoneName End bone name
     * @param solverType Solver type
     * @return Created IK chain, or null if creation failed
     */
    @Nullable
    public IKChain createChainFromRange(String name, String startBoneName, String endBoneName, IKSolver.Type solverType) {
        List<Bone> bones = skeleton.getBoneChain(startBoneName, endBoneName);
        if (bones.isEmpty()) {
            throw new IllegalArgumentException("Failed to extract bone chain from " + startBoneName + " to " + endBoneName);
        }

        List<String> boneNames = new ArrayList<>();
        for (Bone bone : bones) {
            boneNames.add(bone.getName());
        }

        return createChain(name, boneNames, solverType);
    }

    /**
     * Remove an IK chain
     */
    public boolean removeChain(String name) {
        return ikChains.remove(name) != null;
    }

    /**
     * Get an IK chain by name
     */
    @Nullable
    public IKChain getChain(String name) {
        return ikChains.get(name);
    }

    /**
     * Update all IK chains.
     * Should be called after keyframe animation update, before skeleton transform update.
     */
    public void updateAll() {
        if (!enabled) {
            return;
        }

        for (IKChain chain : ikChains.values()) {
            if (chain.isEnabled()) {
                chain.solve();
            }
        }
    }

    /**
     * Enable or disable all IK chains
     */
    public void setEnabled(boolean enabled) {
        this.enabled = enabled;
    }

    /**
     * Check if IK is enabled
     */
    public boolean isEnabled() {
        return enabled;
    }

    /**
     * Get all IK chain names
     */
    public Set<String> getChainNames() {
        return Collections.unmodifiableSet(ikChains.keySet());
    }

    /**
     * Get all IK chains
     */
    public Collection<IKChain> getAllChains() {
        return Collections.unmodifiableCollection(ikChains.values());
    }

    /**
     * Get number of IK chains
     */
    public int getChainCount() {
        return ikChains.size();
    }

    /**
     * Clear all IK chains
     */
    public void clear() {
        ikChains.clear();
    }

    /**
     * Get the skeleton
     */
    public Skeleton getSkeleton() {
        return skeleton;
    }

    /**
     * Create a solver instance based on type.
     */
    private IKSolver createSolver(IKSolver.Type type) {
        switch (type) {
            case TWO_BONE:
                return new cn.minerealms.assimploader.animation.ik.solvers.TwoBoneIKSolver();
            case FABRIK:
                return new cn.minerealms.assimploader.animation.ik.solvers.FABRIKSolver();
            case CCD:
                return new cn.minerealms.assimploader.animation.ik.solvers.CCDSolver();
            case LOOK_AT:
                return new cn.minerealms.assimploader.animation.ik.solvers.LookAtSolver();
            default:
                throw new IllegalArgumentException("Unknown solver type: " + type);
        }
    }
}
