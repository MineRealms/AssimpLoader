package cn.minerealms.assimploader.animation.ik.solvers;

import cn.minerealms.assimploader.animation.Bone;
import cn.minerealms.assimploader.animation.ik.IKChain;
import cn.minerealms.assimploader.animation.ik.IKSolver;
import org.joml.Quaternionf;
import org.joml.Vector3f;

/**
 * CCD (Cyclic Coordinate Descent) IK Solver.
 * Iterative solver that adjusts each joint one at a time from end to root.
 *
 * CCD is simple and fast, but can produce unnatural poses if not constrained.
 * Works well for tentacles, tails, and other flexible chains.
 */
public class CCDSolver implements IKSolver {

    private static final int MAX_ITERATIONS = 10;
    private static final float DISTANCE_THRESHOLD = 0.01f;

    @Override
    public Type getType() {
        return Type.CCD;
    }

    @Override
    public boolean solve(IKChain chain) {
        if (chain.getBones().size() < 2) {
            return false;
        }

        Vector3f target = chain.getTarget();
        Bone endEffector = chain.getBones().get(chain.getBones().size() - 1);

        // Iteratively adjust each joint
        for (int iteration = 0; iteration < MAX_ITERATIONS; iteration++) {
            boolean solved = true;

            // Work backwards from end to root
            for (int i = chain.getBones().size() - 2; i >= 0; i--) {
                Bone bone = chain.getBones().get(i);

                // Get current positions
                Vector3f bonePos = bone.getWorldPosition();
                Vector3f endPos = endEffector.getWorldPosition();

                // Check if we're close enough
                float distanceToTarget = endPos.distance(target);
                if (distanceToTarget < DISTANCE_THRESHOLD) {
                    return true; // Solved!
                }

                solved = false;

                // Calculate vectors
                Vector3f toEnd = new Vector3f(endPos).sub(bonePos);
                Vector3f toTarget = new Vector3f(target).sub(bonePos);

                if (toEnd.lengthSquared() < 0.0001f || toTarget.lengthSquared() < 0.0001f) {
                    continue;
                }

                toEnd.normalize();
                toTarget.normalize();

                // Calculate rotation needed
                Quaternionf rotation = new Quaternionf().rotationTo(toEnd, toTarget);

                // Apply rotation to bone
                Quaternionf currentRotation = bone.getLocalRotation();
                Quaternionf newRotation = new Quaternionf(rotation).mul(currentRotation);

                // Apply weight
                float weight = chain.getWeight();
                if (weight < 1.0f) {
                    newRotation.slerp(currentRotation, 1.0f - weight);
                }

                bone.setLocalRotation(newRotation);

                // Update world transforms for next iteration
                updateWorldTransforms(chain, i);
            }

            if (solved) {
                return true;
            }
        }

        // Reached max iterations
        return false;
    }

    /**
     * Update world transforms for bones from the given index to the end.
     * This is needed because CCD modifies bones iteratively.
     */
    private void updateWorldTransforms(IKChain chain, int startIndex) {
        for (int i = startIndex; i < chain.getBones().size(); i++) {
            Bone bone = chain.getBones().get(i);
            // Force recalculation of world transform
            bone.getWorldPosition();
            bone.getWorldRotation();
        }
    }

    @Override
    public void reset(IKChain chain) {
        for (Bone bone : chain.getBones()) {
            bone.setLocalRotation(new Quaternionf());
        }
    }
}
