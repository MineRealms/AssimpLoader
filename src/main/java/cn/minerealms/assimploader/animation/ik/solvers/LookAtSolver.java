package cn.minerealms.assimploader.animation.ik.solvers;

import cn.minerealms.assimploader.animation.Bone;
import cn.minerealms.assimploader.animation.ik.IKChain;
import cn.minerealms.assimploader.animation.ik.IKSolver;
import org.joml.Quaternionf;
import org.joml.Vector3f;

/**
 * Look-At IK Solver.
 * Rotates a single bone to point towards a target position.
 * Commonly used for head tracking, eye tracking, or turret aiming.
 */
public class LookAtSolver implements IKSolver {

    @Override
    public Type getType() {
        return Type.LOOK_AT;
    }

    @Override
    public boolean solve(IKChain chain) {
        if (chain.getBones().isEmpty()) {
            return false;
        }

        // Look-At only affects the first bone in the chain
        Bone bone = chain.getBones().get(0);
        Vector3f target = chain.getTarget();

        // Get bone position in world space
        Vector3f bonePos = bone.getWorldPosition();

        // Calculate direction to target
        Vector3f toTarget = new Vector3f(target).sub(bonePos);
        if (toTarget.lengthSquared() < 0.0001f) {
            return false; // Target is too close
        }
        toTarget.normalize();

        // Assume bone's forward direction is along Z axis
        Vector3f forward = new Vector3f(0, 0, 1);

        // Calculate rotation from forward to target direction
        Quaternionf rotation = new Quaternionf().rotationTo(forward, toTarget);

        // Apply rotation with weight
        float weight = chain.getWeight();
        if (weight < 1.0f) {
            Quaternionf currentRotation = bone.getLocalRotation();
            rotation.slerp(currentRotation, 1.0f - weight);
        }

        bone.setLocalRotation(rotation);

        return true;
    }

    @Override
    public void reset(IKChain chain) {
        if (!chain.getBones().isEmpty()) {
            chain.getBones().get(0).setLocalRotation(new Quaternionf());
        }
    }
}
