package cn.minerealms.assimploader.animation.ik.controllers;

import cn.minerealms.assimploader.animation.ik.IKChain;
import cn.minerealms.assimploader.animation.ik.IKManager;
import cn.minerealms.assimploader.animation.ik.IKSolver;
import org.jetbrains.annotations.Nullable;
import org.joml.Quaternionf;
import org.joml.Vector3f;

import java.util.Arrays;

/**
 * HandIKController - High-level controller for hand IK (object grabbing).
 *
 * Features:
 * - Left and right hand IK chains
 * - Target position and rotation
 * - Smooth transitions (reach/release)
 * - Blend weight control
 * - Enable/disable per hand
 *
 * Phase 4: High-Level Controllers
 */
public class HandIKController {

    private final IKManager ikManager;
    private final IKChain leftHandChain;
    private final IKChain rightHandChain;

    private Vector3f leftHandTarget;
    private Quaternionf leftHandRotation;
    private Vector3f rightHandTarget;
    private Quaternionf rightHandRotation;

    private float reachSpeed = 3.0f; // Units per second
    private boolean leftHandEnabled = false;
    private boolean rightHandEnabled = false;

    /**
     * Create a hand IK controller
     *
     * @param ikManager IK manager
     * @param leftShoulderBone Left shoulder bone name
     * @param leftElbowBone Left elbow bone name
     * @param leftHandBone Left hand bone name
     * @param rightShoulderBone Right shoulder bone name
     * @param rightElbowBone Right elbow bone name
     * @param rightHandBone Right hand bone name
     */
    public HandIKController(
        IKManager ikManager,
        String leftShoulderBone,
        String leftElbowBone,
        String leftHandBone,
        String rightShoulderBone,
        String rightElbowBone,
        String rightHandBone
    ) {
        this.ikManager = ikManager;
        this.leftHandTarget = new Vector3f();
        this.leftHandRotation = new Quaternionf();
        this.rightHandTarget = new Vector3f();
        this.rightHandRotation = new Quaternionf();

        // Create left hand IK chain
        this.leftHandChain = ikManager.createChain(
            "leftHand",
            Arrays.asList(leftShoulderBone, leftElbowBone, leftHandBone),
            IKSolver.Type.TWO_BONE
        );

        // Create right hand IK chain
        this.rightHandChain = ikManager.createChain(
            "rightHand",
            Arrays.asList(rightShoulderBone, rightElbowBone, rightHandBone),
            IKSolver.Type.TWO_BONE
        );

        if (leftHandChain == null || rightHandChain == null) {
            throw new IllegalArgumentException("Failed to create hand IK chains");
        }

        // Initially disabled
        leftHandChain.setEnabled(false);
        rightHandChain.setEnabled(false);
    }

    /**
     * Set left hand target position and rotation
     */
    public void setLeftHandTarget(Vector3f position, @Nullable Quaternionf rotation) {
        this.leftHandTarget.set(position);
        if (rotation != null) {
            this.leftHandRotation.set(rotation);
        }
        leftHandChain.setTarget(position, rotation);
    }

    /**
     * Set right hand target position and rotation
     */
    public void setRightHandTarget(Vector3f position, @Nullable Quaternionf rotation) {
        this.rightHandTarget.set(position);
        if (rotation != null) {
            this.rightHandRotation.set(rotation);
        }
        rightHandChain.setTarget(position, rotation);
    }

    /**
     * Enable left hand IK
     */
    public void enableLeftHand() {
        leftHandEnabled = true;
        leftHandChain.setEnabled(true);
    }

    /**
     * Disable left hand IK
     */
    public void disableLeftHand() {
        leftHandEnabled = false;
        leftHandChain.setEnabled(false);
    }

    /**
     * Enable right hand IK
     */
    public void enableRightHand() {
        rightHandEnabled = true;
        rightHandChain.setEnabled(true);
    }

    /**
     * Disable right hand IK
     */
    public void disableRightHand() {
        rightHandEnabled = false;
        rightHandChain.setEnabled(false);
    }

    /**
     * Check if left hand IK is enabled
     */
    public boolean isLeftHandEnabled() {
        return leftHandEnabled;
    }

    /**
     * Check if right hand IK is enabled
     */
    public boolean isRightHandEnabled() {
        return rightHandEnabled;
    }

    /**
     * Set left hand blend weight (0.0 = keyframe, 1.0 = IK)
     */
    public void setLeftHandWeight(float weight) {
        leftHandChain.setWeight(weight);
    }

    /**
     * Set right hand blend weight (0.0 = keyframe, 1.0 = IK)
     */
    public void setRightHandWeight(float weight) {
        rightHandChain.setWeight(weight);
    }

    /**
     * Get left hand blend weight
     */
    public float getLeftHandWeight() {
        return leftHandChain.getWeight();
    }

    /**
     * Get right hand blend weight
     */
    public float getRightHandWeight() {
        return rightHandChain.getWeight();
    }

    /**
     * Set reach speed for smooth transitions
     *
     * @param speed Units per second
     */
    public void setReachSpeed(float speed) {
        this.reachSpeed = Math.max(0.1f, speed);
    }

    /**
     * Smoothly reach to target (call every frame for smooth transition)
     *
     * @param deltaTime Time since last update (seconds)
     * @param hand "left" or "right"
     * @param targetWeight Target blend weight (0.0 to 1.0)
     */
    public void smoothReach(float deltaTime, String hand, float targetWeight) {
        IKChain chain = hand.equals("left") ? leftHandChain : rightHandChain;
        float currentWeight = chain.getWeight();
        float step = reachSpeed * deltaTime;

        if (currentWeight < targetWeight) {
            chain.setWeight(Math.min(targetWeight, currentWeight + step));
        } else if (currentWeight > targetWeight) {
            chain.setWeight(Math.max(targetWeight, currentWeight - step));
        }
    }

    /**
     * Smoothly reach with left hand
     */
    public void smoothReachLeft(float deltaTime, float targetWeight) {
        smoothReach(deltaTime, "left", targetWeight);
    }

    /**
     * Smoothly reach with right hand
     */
    public void smoothReachRight(float deltaTime, float targetWeight) {
        smoothReach(deltaTime, "right", targetWeight);
    }

    /**
     * Update controller (call every frame)
     */
    public void update(float deltaTime) {
        // Controllers are passive - IK chains are updated by IKManager
        // This method can be used for future enhancements (e.g., collision detection)
    }

    /**
     * Get left hand IK chain (for advanced control)
     */
    public IKChain getLeftHandChain() {
        return leftHandChain;
    }

    /**
     * Get right hand IK chain (for advanced control)
     */
    public IKChain getRightHandChain() {
        return rightHandChain;
    }

    /**
     * Set pole vector for left elbow
     */
    public void setLeftElbowPole(Vector3f poleVector) {
        leftHandChain.setPoleVector(poleVector);
    }

    /**
     * Set pole vector for right elbow
     */
    public void setRightElbowPole(Vector3f poleVector) {
        rightHandChain.setPoleVector(poleVector);
    }
}
