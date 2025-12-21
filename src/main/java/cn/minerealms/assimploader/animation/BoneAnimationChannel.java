package cn.minerealms.assimploader.animation;

import org.joml.Quaternionf;
import org.joml.Vector3f;

/**
 * Animation channel for a single bone.
 *
 * Contains keyframes for position, rotation, and scale transformations.
 * Handles interpolation between keyframes at any given time.
 */
public class BoneAnimationChannel {

    private final String boneName;
    private final Keyframe<Vector3f>[] positionKeys;
    private final Keyframe<Quaternionf>[] rotationKeys;
    private final Keyframe<Vector3f>[] scaleKeys;

    public BoneAnimationChannel(
            String boneName,
            Keyframe<Vector3f>[] positionKeys,
            Keyframe<Quaternionf>[] rotationKeys,
            Keyframe<Vector3f>[] scaleKeys) {
        this.boneName = boneName;
        this.positionKeys = positionKeys;
        this.rotationKeys = rotationKeys;
        this.scaleKeys = scaleKeys;
    }

    /**
     * Get interpolated position at given time
     */
    public Vector3f getPosition(double time) {
        if (positionKeys == null || positionKeys.length == 0) {
            return new Vector3f(0, 0, 0);
        }

        Keyframe<Vector3f>[] surrounding = Keyframe.findSurroundingKeyframes(positionKeys, time);
        if (surrounding == null) {
            return new Vector3f(0, 0, 0);
        }

        return Keyframe.interpolatePosition(surrounding[0], surrounding[1], time);
    }

    /**
     * Get interpolated rotation at given time
     */
    public Quaternionf getRotation(double time) {
        if (rotationKeys == null || rotationKeys.length == 0) {
            return new Quaternionf();
        }

        Keyframe<Quaternionf>[] surrounding = Keyframe.findSurroundingKeyframes(rotationKeys, time);
        if (surrounding == null) {
            return new Quaternionf();
        }

        return Keyframe.interpolateRotation(surrounding[0], surrounding[1], time);
    }

    /**
     * Get interpolated scale at given time
     */
    public Vector3f getScale(double time) {
        if (scaleKeys == null || scaleKeys.length == 0) {
            return new Vector3f(1, 1, 1);
        }

        Keyframe<Vector3f>[] surrounding = Keyframe.findSurroundingKeyframes(scaleKeys, time);
        if (surrounding == null) {
            return new Vector3f(1, 1, 1);
        }

        return Keyframe.interpolateScale(surrounding[0], surrounding[1], time);
    }

    public String getBoneName() {
        return boneName;
    }
}
