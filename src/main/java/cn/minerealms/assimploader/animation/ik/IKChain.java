package cn.minerealms.assimploader.animation.ik;

import cn.minerealms.assimploader.animation.Bone;
import org.joml.Matrix4f;
import org.joml.Quaternionf;
import org.joml.Vector3f;

import java.util.List;

/**
 * IKChain - Represents a chain of bones to be solved by IK.
 *
 * Features:
 * - Target position/rotation
 * - Blend weight (0.0 = keyframe only, 1.0 = IK only)
 * - Enable/disable flag
 * - Dirty flag for optimization
 *
 * Phase 1: IK Foundation
 */
public class IKChain {

    private final String name;
    private final List<Bone> bones;
    private final IKSolver solver;

    private Vector3f targetPosition;
    private Quaternionf targetRotation;
    private Vector3f poleVector; // For TwoBoneIK
    private float weight = 1.0f;
    private boolean enabled = true;
    private boolean dirty = true;

    // Cached original transforms for blending
    private final Matrix4f[] originalTransforms;

    /**
     * Create an IK chain
     *
     * @param name Chain name for identification
     * @param bones List of bones in the chain (ordered from root to tip)
     * @param solver IK solver to use
     */
    public IKChain(String name, List<Bone> bones, IKSolver solver) {
        if (bones.isEmpty()) {
            throw new IllegalArgumentException("IK chain must have at least one bone");
        }

        this.name = name;
        this.bones = bones;
        this.solver = solver;
        this.targetPosition = new Vector3f();
        this.targetRotation = new Quaternionf();
        this.poleVector = new Vector3f(0, 1, 0); // Default pole vector (up)
        this.originalTransforms = new Matrix4f[bones.size()];

        // Initialize original transforms cache
        for (int i = 0; i < bones.size(); i++) {
            originalTransforms[i] = new Matrix4f();
        }
    }

    /**
     * Solve the IK chain.
     * Should be called after keyframe animation update.
     */
    public void solve() {
        if (!enabled || !dirty) {
            return;
        }

        // Cache original transforms (from keyframe animation)
        for (int i = 0; i < bones.size(); i++) {
            originalTransforms[i].set(bones.get(i).getLocalTransform());
        }

        // Solve IK using the new solver interface
        solver.solve(this);

        // Blend IK result with original keyframe animation
        if (weight < 1.0f) {
            blendWithOriginal();
        }

        dirty = false;
    }

    /**
     * Blend IK result with original keyframe transforms
     */
    private void blendWithOriginal() {
        for (int i = 0; i < bones.size(); i++) {
            Bone bone = bones.get(i);
            Matrix4f ikTransform = bone.getLocalTransform();
            Matrix4f originalTransform = originalTransforms[i];

            // Decompose matrices to position/rotation/scale
            Vector3f ikPos = new Vector3f();
            Quaternionf ikRot = new Quaternionf();
            Vector3f ikScale = new Vector3f();
            ikTransform.getTranslation(ikPos);
            ikTransform.getUnnormalizedRotation(ikRot);
            ikTransform.getScale(ikScale);

            Vector3f origPos = new Vector3f();
            Quaternionf origRot = new Quaternionf();
            Vector3f origScale = new Vector3f();
            originalTransform.getTranslation(origPos);
            originalTransform.getUnnormalizedRotation(origRot);
            originalTransform.getScale(origScale);

            // Blend
            Vector3f blendedPos = origPos.lerp(ikPos, weight);
            Quaternionf blendedRot = origRot.slerp(ikRot, weight);
            Vector3f blendedScale = origScale.lerp(ikScale, weight);

            // Reconstruct matrix
            Matrix4f blendedTransform = new Matrix4f();
            blendedTransform.translationRotateScale(blendedPos, blendedRot, blendedScale);
            bone.setLocalTransform(blendedTransform);
        }
    }

    /**
     * Set IK target position
     */
    public void setTarget(Vector3f position, Quaternionf rotation) {
        if (!this.targetPosition.equals(position) ||
            (rotation != null && !this.targetRotation.equals(rotation))) {
            this.targetPosition.set(position);
            if (rotation != null) {
                this.targetRotation.set(rotation);
            }
            this.dirty = true;
        }
    }

    /**
     * Set IK target position only
     */
    public void setTargetPosition(Vector3f position) {
        setTarget(position, null);
    }

    /**
     * Set pole vector (for TwoBoneIK solver)
     */
    public void setPoleVector(Vector3f poleVector) {
        this.poleVector.set(poleVector);
        this.dirty = true;
    }

    /**
     * Get pole vector
     */
    public Vector3f getPoleVector() {
        return new Vector3f(poleVector);
    }

    /**
     * Get target (returns the target position, used by solvers)
     */
    public Vector3f getTarget() {
        return new Vector3f(targetPosition);
    }

    /**
     * Set blend weight (0.0 = keyframe only, 1.0 = IK only)
     */
    public void setWeight(float weight) {
        this.weight = Math.max(0.0f, Math.min(1.0f, weight));
        if (this.weight > 0.0f) {
            this.dirty = true;
        }
    }

    /**
     * Enable or disable the IK chain
     */
    public void setEnabled(boolean enabled) {
        this.enabled = enabled;
        if (enabled) {
            this.dirty = true;
        }
    }

    /**
     * Mark the chain as dirty (needs re-solving)
     */
    public void markDirty() {
        this.dirty = true;
    }

    // Getters

    public String getName() {
        return name;
    }

    public List<Bone> getBones() {
        return bones;
    }

    public IKSolver getSolver() {
        return solver;
    }

    public Vector3f getTargetPosition() {
        return new Vector3f(targetPosition);
    }

    public Quaternionf getTargetRotation() {
        return new Quaternionf(targetRotation);
    }

    public float getWeight() {
        return weight;
    }

    public boolean isEnabled() {
        return enabled;
    }

    public boolean isDirty() {
        return dirty;
    }

    /**
     * Get the tip bone (last bone in the chain)
     */
    public Bone getTipBone() {
        return bones.get(bones.size() - 1);
    }

    /**
     * Get the root bone (first bone in the chain)
     */
    public Bone getRootBone() {
        return bones.get(0);
    }

    /**
     * Get chain length (number of bones)
     */
    public int getLength() {
        return bones.size();
    }
}
