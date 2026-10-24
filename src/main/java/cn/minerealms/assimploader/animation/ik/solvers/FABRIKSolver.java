package cn.minerealms.assimploader.animation.ik.solvers;

import au.edu.federation.caliko.FabrikBone3D;
import au.edu.federation.caliko.FabrikChain3D;
import au.edu.federation.caliko.FabrikStructure3D;
import au.edu.federation.utils.Vec3f;
import cn.minerealms.assimploader.animation.Bone;
import cn.minerealms.assimploader.animation.ik.IKChain;
import cn.minerealms.assimploader.animation.ik.IKSolver;
import cn.minerealms.assimploader.config.AssimpLoaderConfig;
import org.joml.Quaternionf;
import org.joml.Vector3f;

/**
 * FABRIK (Forward And Backward Reaching Inverse Kinematics) Solver.
 * Wrapper around Caliko's FABRIK implementation.
 *
 * FABRIK is an iterative solver that works well for long chains (4+ bones).
 * It's fast and produces natural-looking results.
 */
public class FABRIKSolver implements IKSolver {

    private FabrikStructure3D structure;
    private FabrikChain3D calikoChain;

    /**
     * Get max iterations from config
     */
    private int getMaxIterations() {
        return AssimpLoaderConfig.getIntOrDefault(AssimpLoaderConfig.COMMON.animIkMaxIterationsFABRIK, 10);
    }

    /**
     * Get distance threshold from config
     */
    private float getDistanceThreshold() {
        return AssimpLoaderConfig.getOrDefault(() -> AssimpLoaderConfig.COMMON.animIkDistanceThreshold.get().floatValue(), 0.01f);
    }

    @Override
    public Type getType() {
        return Type.FABRIK;
    }

    @Override
    public boolean solve(IKChain chain) {
        if (chain.getBones().size() < 2) {
            return false;
        }

        // Initialize Caliko chain if needed
        if (calikoChain == null) {
            initializeCalikoChain(chain);
        }

        // Update target position
        Vector3f target = chain.getTarget();
        calikoChain.solveForTarget(new Vec3f(target.x, target.y, target.z));

        // Apply solved rotations back to our bones
        applySolutionToBones(chain);

        return true;
    }

    private void initializeCalikoChain(IKChain chain) {
        // Create Caliko chain
        calikoChain = new FabrikChain3D();

        // Add bones to chain
        for (int i = 0; i < chain.getBones().size(); i++) {
            Bone bone = chain.getBones().get(i);
            Vector3f startPos = bone.getWorldPosition();

            // Calculate bone length
            float boneLength;
            if (i < chain.getBones().size() - 1) {
                Bone nextBone = chain.getBones().get(i + 1);
                Vector3f endPos = nextBone.getWorldPosition();
                boneLength = startPos.distance(endPos);
            } else {
                // Last bone: use a default length
                boneLength = 1.0f;
            }

            // Create Caliko bone
            FabrikBone3D calikoBone = new FabrikBone3D(
                new Vec3f(startPos.x, startPos.y, startPos.z),
                new Vec3f(0, 0, 1), // Default direction
                boneLength
            );

            calikoChain.addBone(calikoBone);
        }

        // Set solver parameters
        calikoChain.setMaxIterationAttempts(getMaxIterations());
        calikoChain.setSolveDistanceThreshold(getDistanceThreshold());

        // Create structure (required by Caliko)
        structure = new FabrikStructure3D();
        structure.addChain(calikoChain);
    }

    private void applySolutionToBones(IKChain chain) {
        float weight = chain.getWeight();

        for (int i = 0; i < chain.getBones().size() - 1; i++) {
            Bone bone = chain.getBones().get(i);
            FabrikBone3D calikoBone = calikoChain.getBone(i);

            // Get solved direction from Caliko
            Vec3f calikoDir = calikoBone.getDirectionUV();
            Vector3f solvedDir = new Vector3f(calikoDir.x, calikoDir.y, calikoDir.z);

            // Calculate rotation to align bone with solved direction
            Vector3f currentDir = new Vector3f(0, 0, 1); // Assume bones point along Z
            Quaternionf targetRotation = new Quaternionf().rotationTo(currentDir, solvedDir);

            // Blend with current rotation based on weight
            if (weight < 1.0f) {
                Quaternionf currentRotation = bone.getLocalRotation();
                targetRotation.slerp(currentRotation, 1.0f - weight);
            }

            bone.setLocalRotation(targetRotation);
        }
    }

    @Override
    public void reset(IKChain chain) {
        calikoChain = null;
        structure = null;

        for (Bone bone : chain.getBones()) {
            bone.setLocalRotation(new Quaternionf());
        }
    }
}
