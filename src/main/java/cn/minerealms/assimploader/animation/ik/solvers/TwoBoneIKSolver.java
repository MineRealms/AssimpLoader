package cn.minerealms.assimploader.animation.ik.solvers;

import cn.minerealms.assimploader.animation.Bone;
import cn.minerealms.assimploader.animation.ik.IKChain;
import cn.minerealms.assimploader.animation.ik.IKSolver;
import org.joml.Quaternionf;
import org.joml.Vector3f;

/**
 * Two-Bone IK Solver using analytical solution.
 * Optimized for arms and legs (3 bones: shoulder-elbow-hand or hip-knee-foot).
 *
 * Algorithm:
 * 1. Calculate triangle formed by root, mid, and target
 * 2. Use law of cosines to find joint angles
 * 3. Apply pole vector constraint for elbow/knee direction
 */
public class TwoBoneIKSolver implements IKSolver {

    @Override
    public Type getType() {
        return Type.TWO_BONE;
    }

    @Override
    public boolean solve(IKChain chain) {
        if (chain.getBones().size() != 3) {
            throw new IllegalArgumentException("TwoBoneIK requires exactly 3 bones");
        }

        Bone root = chain.getBones().get(0);
        Bone mid = chain.getBones().get(1);
        Bone end = chain.getBones().get(2);

        Vector3f target = chain.getTarget();
        Vector3f poleVector = chain.getPoleVector();

        // Get bone positions in world space
        Vector3f rootPos = root.getWorldPosition();
        Vector3f midPos = mid.getWorldPosition();
        Vector3f endPos = end.getWorldPosition();

        // Calculate bone lengths
        float upperLength = rootPos.distance(midPos);
        float lowerLength = midPos.distance(endPos);
        float totalLength = upperLength + lowerLength;

        // Calculate distance to target
        Vector3f toTarget = new Vector3f(target).sub(rootPos);
        float targetDistance = toTarget.length();

        // Clamp target to reachable distance
        if (targetDistance > totalLength * 0.999f) {
            targetDistance = totalLength * 0.999f;
            toTarget.normalize().mul(targetDistance);
            target = new Vector3f(rootPos).add(toTarget);
        }

        // Calculate angles using law of cosines
        float cosAngle = (upperLength * upperLength + targetDistance * targetDistance - lowerLength * lowerLength)
                       / (2.0f * upperLength * targetDistance);
        cosAngle = Math.max(-1.0f, Math.min(1.0f, cosAngle)); // Clamp to [-1, 1]
        float angle1 = (float) Math.acos(cosAngle);

        float cosMidAngle = (upperLength * upperLength + lowerLength * lowerLength - targetDistance * targetDistance)
                          / (2.0f * upperLength * lowerLength);
        cosMidAngle = Math.max(-1.0f, Math.min(1.0f, cosMidAngle));
        float midAngle = (float) Math.acos(cosMidAngle);

        // Calculate rotation plane using pole vector
        Vector3f targetDir = new Vector3f(toTarget).normalize();
        Vector3f toPole = new Vector3f(poleVector).sub(rootPos).normalize();

        // Project pole vector onto plane perpendicular to target direction
        Vector3f planeNormal = new Vector3f(targetDir).cross(toPole).normalize();
        if (planeNormal.lengthSquared() < 0.001f) {
            // Pole vector is parallel to target, use arbitrary perpendicular
            planeNormal = new Vector3f(0, 1, 0).cross(targetDir).normalize();
            if (planeNormal.lengthSquared() < 0.001f) {
                planeNormal = new Vector3f(1, 0, 0).cross(targetDir).normalize();
            }
        }

        Vector3f bendDir = new Vector3f(targetDir).cross(planeNormal).normalize();

        // Apply rotation to root bone
        Quaternionf rootRotation = new Quaternionf().rotationTo(
            new Vector3f(0, 0, 1), // Assume bones point along Z axis
            targetDir
        );
        Quaternionf bendRotation = new Quaternionf().fromAxisAngleRad(planeNormal, angle1);
        rootRotation.mul(bendRotation);

        root.setLocalRotation(rootRotation);

        // Apply rotation to mid bone
        Quaternionf midRotation = new Quaternionf().fromAxisAngleRad(planeNormal, (float) Math.PI - midAngle);
        mid.setLocalRotation(midRotation);

        return true;
    }

    @Override
    public void reset(IKChain chain) {
        for (Bone bone : chain.getBones()) {
            bone.setLocalRotation(new Quaternionf()); // Identity rotation
        }
    }
}
