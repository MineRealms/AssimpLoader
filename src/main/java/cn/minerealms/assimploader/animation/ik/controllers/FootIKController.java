package cn.minerealms.assimploader.animation.ik.controllers;

import cn.minerealms.assimploader.animation.ik.IKChain;
import cn.minerealms.assimploader.animation.ik.IKManager;
import cn.minerealms.assimploader.animation.ik.IKSolver;
import org.jetbrains.annotations.Nullable;
import org.joml.Quaternionf;
import org.joml.Vector3f;

import java.util.Arrays;
import java.util.function.Function;

/**
 * FootIKController - High-level controller for foot IK (ground alignment).
 *
 * Features:
 * - Left and right foot IK chains
 * - Automatic ground detection via raycast callback
 * - Smooth foot placement transitions
 * - Configurable foot offset (heel height)
 * - Enable/disable per foot
 * - Blend weight control
 *
 * Phase 4: High-Level Controllers
 */
public class FootIKController {

    private final IKManager ikManager;
    private final IKChain leftFootChain;
    private final IKChain rightFootChain;

    private Vector3f leftFootTarget;
    private Vector3f rightFootTarget;
    private float footOffset = 0.0f; // Heel height offset
    private float transitionSpeed = 5.0f; // Units per second
    private boolean leftFootEnabled = false;
    private boolean rightFootEnabled = false;

    // Raycast callback for ground detection
    private Function<Vector3f, Vector3f> groundRaycast;

    /**
     * Create a foot IK controller
     *
     * @param ikManager IK manager
     * @param leftHipBone Left hip bone name
     * @param leftKneeBone Left knee bone name
     * @param leftAnkleBone Left ankle bone name
     * @param rightHipBone Right hip bone name
     * @param rightKneeBone Right knee bone name
     * @param rightAnkleBone Right ankle bone name
     */
    public FootIKController(
        IKManager ikManager,
        String leftHipBone,
        String leftKneeBone,
        String leftAnkleBone,
        String rightHipBone,
        String rightKneeBone,
        String rightAnkleBone
    ) {
        this.ikManager = ikManager;
        this.leftFootTarget = new Vector3f();
        this.rightFootTarget = new Vector3f();

        // Create left foot IK chain
        this.leftFootChain = ikManager.createChain(
            "leftFoot",
            Arrays.asList(leftHipBone, leftKneeBone, leftAnkleBone),
            IKSolver.Type.TWO_BONE
        );

        // Create right foot IK chain
        this.rightFootChain = ikManager.createChain(
            "rightFoot",
            Arrays.asList(rightHipBone, rightKneeBone, rightAnkleBone),
            IKSolver.Type.TWO_BONE
        );

        if (leftFootChain == null || rightFootChain == null) {
            throw new IllegalArgumentException("Failed to create foot IK chains");
        }

        // Initially disabled
        leftFootChain.setEnabled(false);
        rightFootChain.setEnabled(false);
    }

    /**
     * Set ground raycast callback.
     * The callback receives a foot position and returns the ground position below it.
     *
     * @param raycast Function that takes foot position and returns ground position
     */
    public void setGroundRaycast(Function<Vector3f, Vector3f> raycast) {
        this.groundRaycast = raycast;
    }

    /**
     * Set foot offset (heel height)
     *
     * @param offset Offset in world units
     */
    public void setFootOffset(float offset) {
        this.footOffset = offset;
    }

    /**
     * Set transition speed for smooth foot placement
     *
     * @param speed Units per second
     */
    public void setTransitionSpeed(float speed) {
        this.transitionSpeed = Math.max(0.1f, speed);
    }

    /**
     * Enable left foot IK
     */
    public void enableLeftFoot() {
        leftFootEnabled = true;
        leftFootChain.setEnabled(true);
    }

    /**
     * Disable left foot IK
     */
    public void disableLeftFoot() {
        leftFootEnabled = false;
        leftFootChain.setEnabled(false);
    }

    /**
     * Enable right foot IK
     */
    public void enableRightFoot() {
        rightFootEnabled = true;
        rightFootChain.setEnabled(true);
    }

    /**
     * Disable right foot IK
     */
    public void disableRightFoot() {
        rightFootEnabled = false;
        rightFootChain.setEnabled(false);
    }

    /**
     * Check if left foot IK is enabled
     */
    public boolean isLeftFootEnabled() {
        return leftFootEnabled;
    }

    /**
     * Check if right foot IK is enabled
     */
    public boolean isRightFootEnabled() {
        return rightFootEnabled;
    }

    /**
     * Set left foot blend weight (0.0 = keyframe, 1.0 = IK)
     */
    public void setLeftFootWeight(float weight) {
        leftFootChain.setWeight(weight);
    }

    /**
     * Set right foot blend weight (0.0 = keyframe, 1.0 = IK)
     */
    public void setRightFootWeight(float weight) {
        rightFootChain.setWeight(weight);
    }

    /**
     * Get left foot blend weight
     */
    public float getLeftFootWeight() {
        return leftFootChain.getWeight();
    }

    /**
     * Get right foot blend weight
     */
    public float getRightFootWeight() {
        return rightFootChain.getWeight();
    }

    /**
     * Manually set left foot target position
     */
    public void setLeftFootTarget(Vector3f position) {
        this.leftFootTarget.set(position);
        leftFootChain.setTarget(position, null);
    }

    /**
     * Manually set right foot target position
     */
    public void setRightFootTarget(Vector3f position) {
        this.rightFootTarget.set(position);
        rightFootChain.setTarget(position, null);
    }

    /**
     * Update controller with automatic ground detection (call every frame)
     *
     * @param deltaTime Time since last update (seconds)
     */
    public void update(float deltaTime) {
        if (groundRaycast == null) {
            return; // No raycast callback set
        }

        // Update left foot
        if (leftFootEnabled) {
            Vector3f leftFootPos = leftFootChain.getBones().get(2).getWorldPosition();
            Vector3f groundPos = groundRaycast.apply(leftFootPos);

            if (groundPos != null) {
                // Apply foot offset
                groundPos.y += footOffset;

                // Smooth transition
                leftFootTarget.lerp(groundPos, Math.min(1.0f, transitionSpeed * deltaTime));
                leftFootChain.setTarget(leftFootTarget, null);
            }
        }

        // Update right foot
        if (rightFootEnabled) {
            Vector3f rightFootPos = rightFootChain.getBones().get(2).getWorldPosition();
            Vector3f groundPos = groundRaycast.apply(rightFootPos);

            if (groundPos != null) {
                // Apply foot offset
                groundPos.y += footOffset;

                // Smooth transition
                rightFootTarget.lerp(groundPos, Math.min(1.0f, transitionSpeed * deltaTime));
                rightFootChain.setTarget(rightFootTarget, null);
            }
        }
    }

    /**
     * Get left foot IK chain (for advanced control)
     */
    public IKChain getLeftFootChain() {
        return leftFootChain;
    }

    /**
     * Get right foot IK chain (for advanced control)
     */
    public IKChain getRightFootChain() {
        return rightFootChain;
    }

    /**
     * Set pole vector for left knee
     */
    public void setLeftKneePole(Vector3f poleVector) {
        leftFootChain.setPoleVector(poleVector);
    }

    /**
     * Set pole vector for right knee
     */
    public void setRightKneePole(Vector3f poleVector) {
        rightFootChain.setPoleVector(poleVector);
    }

    /**
     * Reset foot targets to current bone positions
     */
    public void reset() {
        leftFootTarget.set(leftFootChain.getBones().get(2).getWorldPosition());
        rightFootTarget.set(rightFootChain.getBones().get(2).getWorldPosition());
        leftFootChain.setTarget(leftFootTarget, null);
        rightFootChain.setTarget(rightFootTarget, null);
    }
}
