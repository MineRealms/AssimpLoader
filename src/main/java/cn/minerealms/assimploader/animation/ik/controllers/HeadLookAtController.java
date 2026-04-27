package cn.minerealms.assimploader.animation.ik.controllers;

import cn.minerealms.assimploader.animation.ik.IKChain;
import cn.minerealms.assimploader.animation.ik.IKManager;
import cn.minerealms.assimploader.animation.ik.IKSolver;
import org.joml.Quaternionf;
import org.joml.Vector3f;

import java.util.Arrays;

/**
 * HeadLookAtController - High-level controller for head look-at behavior.
 *
 * Features:
 * - Smooth head tracking of target position
 * - Configurable look speed (interpolation)
 * - Angle constraints (max rotation from forward)
 * - Enable/disable with smooth transitions
 * - Optional neck bone support
 *
 * Phase 4: High-Level Controllers
 */
public class HeadLookAtController {

    private final IKManager ikManager;
    private final IKChain headChain;

    private Vector3f targetPosition;
    private Vector3f currentLookTarget;
    private float lookSpeed = 5.0f; // Radians per second
    private float maxAngle = (float) Math.toRadians(80); // Max rotation from forward
    private boolean enabled = true;

    /**
     * Create a head look-at controller
     *
     * @param ikManager IK manager
     * @param headBoneName Name of the head bone
     */
    public HeadLookAtController(IKManager ikManager, String headBoneName) {
        this.ikManager = ikManager;
        this.targetPosition = new Vector3f();
        this.currentLookTarget = new Vector3f();

        // Create IK chain for head (single bone look-at)
        this.headChain = ikManager.createChain(
            "headLookAt",
            Arrays.asList(headBoneName),
            IKSolver.Type.LOOK_AT
        );

        if (headChain == null) {
            throw new IllegalArgumentException("Failed to create head IK chain for bone: " + headBoneName);
        }
    }

    /**
     * Create a head look-at controller with neck support
     *
     * @param ikManager IK manager
     * @param neckBoneName Name of the neck bone
     * @param headBoneName Name of the head bone
     */
    public HeadLookAtController(IKManager ikManager, String neckBoneName, String headBoneName) {
        this.ikManager = ikManager;
        this.targetPosition = new Vector3f();
        this.currentLookTarget = new Vector3f();

        // Create IK chain for neck + head
        this.headChain = ikManager.createChain(
            "headLookAt",
            Arrays.asList(neckBoneName, headBoneName),
            IKSolver.Type.LOOK_AT
        );

        if (headChain == null) {
            throw new IllegalArgumentException("Failed to create head IK chain");
        }
    }

    /**
     * Set the target position to look at
     */
    public void setTarget(Vector3f target) {
        this.targetPosition.set(target);
    }

    /**
     * Set look speed (interpolation rate)
     *
     * @param speed Radians per second
     */
    public void setLookSpeed(float speed) {
        this.lookSpeed = Math.max(0.1f, speed);
    }

    /**
     * Set maximum rotation angle from forward direction
     *
     * @param maxAngleDegrees Maximum angle in degrees
     */
    public void setMaxAngle(float maxAngleDegrees) {
        this.maxAngle = (float) Math.toRadians(Math.max(0, Math.min(180, maxAngleDegrees)));
    }

    /**
     * Enable or disable the controller
     */
    public void setEnabled(boolean enabled) {
        this.enabled = enabled;
        headChain.setEnabled(enabled);
    }

    /**
     * Check if controller is enabled
     */
    public boolean isEnabled() {
        return enabled;
    }

    /**
     * Set blend weight (0.0 = keyframe only, 1.0 = IK only)
     */
    public void setWeight(float weight) {
        headChain.setWeight(weight);
    }

    /**
     * Get current blend weight
     */
    public float getWeight() {
        return headChain.getWeight();
    }

    /**
     * Update the controller (call every frame)
     *
     * @param deltaTime Time since last update (seconds)
     */
    public void update(float deltaTime) {
        if (!enabled) {
            return;
        }

        // Get head bone position
        Vector3f headPos = headChain.getBones().get(headChain.getBones().size() - 1).getWorldPosition();

        // Calculate direction to target
        Vector3f toTarget = new Vector3f(targetPosition).sub(headPos);
        float distanceToTarget = toTarget.length();

        if (distanceToTarget < 0.001f) {
            return; // Target too close
        }

        toTarget.normalize();

        // Apply angle constraint
        Vector3f forward = new Vector3f(0, 0, 1); // Assume Z-forward
        float angle = (float) Math.acos(forward.dot(toTarget));

        if (angle > maxAngle) {
            // Clamp to max angle
            Vector3f axis = new Vector3f();
            forward.cross(toTarget, axis);
            if (axis.lengthSquared() > 0.001f) {
                axis.normalize();
                Quaternionf rotation = new Quaternionf().fromAxisAngleRad(axis, maxAngle);
                rotation.transform(forward, toTarget);
            }
        }

        // Smooth interpolation
        Vector3f desiredTarget = new Vector3f(headPos).add(toTarget.mul(distanceToTarget));
        currentLookTarget.lerp(desiredTarget, Math.min(1.0f, lookSpeed * deltaTime));

        // Update IK target
        headChain.setTarget(currentLookTarget, null);
    }

    /**
     * Get the IK chain (for advanced control)
     */
    public IKChain getChain() {
        return headChain;
    }

    /**
     * Reset to default forward direction
     */
    public void reset() {
        Vector3f headPos = headChain.getBones().get(0).getWorldPosition();
        currentLookTarget.set(headPos).add(0, 0, 1); // Look forward
        headChain.setTarget(currentLookTarget, null);
    }
}
