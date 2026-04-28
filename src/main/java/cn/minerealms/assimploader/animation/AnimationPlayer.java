package cn.minerealms.assimploader.animation;

import cn.minerealms.assimploader.animation.ik.IKManager;
import cn.minerealms.assimploader.animation.ik.Skeleton;
import cn.minerealms.assimploader.model.AssimpAnimation;
import org.jetbrains.annotations.Nullable;
import org.joml.Matrix4f;
import org.joml.Quaternionf;
import org.joml.Vector3f;

import java.util.HashMap;
import java.util.Map;

/**
 * Animation player for skeletal animations.
 *
 * Handles:
 * - Animation playback with time tracking
 * - Bone transformation interpolation
 * - Multiple animation blending
 * - Loop and one-shot modes
 * - IK integration (Phase 3)
 *
 * Phase 4 implementation - Skeletal animation system
 */
public class AnimationPlayer {

    private final AssimpAnimation animation;
    private final Map<String, BoneTransform> boneTransforms;

    private double currentTime = 0.0;
    private boolean playing = false;
    private boolean looping = true;
    private float speed = 1.0f;

    // IK integration (Phase 3)
    private Skeleton skeleton;
    private IKManager ikManager;

    public AnimationPlayer(AssimpAnimation animation) {
        this.animation = animation;
        this.boneTransforms = new HashMap<>();
    }

    /**
     * Update animation time and calculate bone transforms
     *
     * @param deltaTime Time elapsed since last update (in seconds)
     */
    public void update(double deltaTime) {
        if (!playing) {
            return;
        }

        currentTime += deltaTime * speed * animation.getTicksPerSecond();

        double duration = animation.getDuration();
        if (currentTime > duration) {
            if (looping) {
                currentTime = currentTime % duration;
            } else {
                currentTime = duration;
                playing = false;
            }
        }

        // 1. Update keyframe transforms
        updateBoneTransforms();

        // 2. Apply IK (Phase 3 integration)
        if (ikManager != null && ikManager.isEnabled()) {
            ikManager.updateAll();
        }

        // 3. Update skeleton transforms (if skeleton is set)
        if (skeleton != null) {
            skeleton.updateTransforms();
        }
    }

    /**
     * Calculate bone transformations at current time
     */
    private void updateBoneTransforms() {
        // Use AnimationDataExtractor to get interpolated transforms at current time
        var aiChannels = animation.getNativeAnimation().mChannels();
        if (aiChannels == null) {
            return;
        }

        int numChannels = aiChannels.limit();
        for (int i = 0; i < numChannels; i++) {
            org.lwjgl.assimp.AINodeAnim channel = org.lwjgl.assimp.AINodeAnim.create(aiChannels.get(i));
            String nodeName = channel.mNodeName().dataString();

            // Debug: Log node animation info (disabled to reduce log spam)
            // if (currentTime < 0.1 && i == 0) {
            //     cn.minerealms.assimploader.debug.DebugManager.info(
            //         cn.minerealms.assimploader.debug.DebugManager.DebugCategory.ANIMATION,
            //         "Node animation channel: node='{}', posKeys={}, rotKeys={}, scaleKeys={}",
            //         nodeName,
            //         channel.mPositionKeys() != null ? channel.mPositionKeys().limit() : 0,
            //         channel.mRotationKeys() != null ? channel.mRotationKeys().limit() : 0,
            //         channel.mScalingKeys() != null ? channel.mScalingKeys().limit() : 0
            //     );
            // }

            // Get or create bone transform
            BoneTransform transform = boneTransforms.computeIfAbsent(nodeName, k -> new BoneTransform());

            // Interpolate position
            Vector3f position = interpolatePosition(channel, currentTime);
            if (position != null) {
                transform.setPosition(position);
            }

            // Interpolate rotation
            Quaternionf rotation = interpolateRotation(channel, currentTime);
            if (rotation != null) {
                transform.setRotation(rotation);
            }

            // Interpolate scale
            Vector3f scale = interpolateScale(channel, currentTime);
            if (scale != null) {
                transform.setScale(scale);
            }
        }
    }

    /**
     * Interpolate position at given time
     */
    private Vector3f interpolatePosition(org.lwjgl.assimp.AINodeAnim channel, double time) {
        var keys = channel.mPositionKeys();
        if (keys == null || keys.limit() == 0) {
            return null;
        }

        // Single key - no interpolation needed
        if (keys.limit() == 1) {
            var key = keys.get(0);
            return new Vector3f(key.mValue().x(), key.mValue().y(), key.mValue().z());
        }

        // Find surrounding keyframes
        int index = 0;
        for (int i = 0; i < keys.limit() - 1; i++) {
            if (time < keys.get(i + 1).mTime()) {
                index = i;
                break;
            }
        }

        var key0 = keys.get(index);
        var key1 = keys.get(Math.min(index + 1, keys.limit() - 1));

        // Calculate interpolation factor
        double deltaTime = key1.mTime() - key0.mTime();
        float factor = deltaTime > 0 ? (float)((time - key0.mTime()) / deltaTime) : 0.0f;
        factor = Math.max(0.0f, Math.min(1.0f, factor));

        // Linear interpolation
        Vector3f pos0 = new Vector3f(key0.mValue().x(), key0.mValue().y(), key0.mValue().z());
        Vector3f pos1 = new Vector3f(key1.mValue().x(), key1.mValue().y(), key1.mValue().z());
        return pos0.lerp(pos1, factor);
    }

    /**
     * Interpolate rotation at given time
     */
    private Quaternionf interpolateRotation(org.lwjgl.assimp.AINodeAnim channel, double time) {
        var keys = channel.mRotationKeys();
        if (keys == null || keys.limit() == 0) {
            return null;
        }

        // Single key - no interpolation needed
        if (keys.limit() == 1) {
            var key = keys.get(0);
            return new Quaternionf(key.mValue().x(), key.mValue().y(), key.mValue().z(), key.mValue().w());
        }

        // Find surrounding keyframes
        int index = 0;
        for (int i = 0; i < keys.limit() - 1; i++) {
            if (time < keys.get(i + 1).mTime()) {
                index = i;
                break;
            }
        }

        var key0 = keys.get(index);
        var key1 = keys.get(Math.min(index + 1, keys.limit() - 1));

        // Calculate interpolation factor
        double deltaTime = key1.mTime() - key0.mTime();
        float factor = deltaTime > 0 ? (float)((time - key0.mTime()) / deltaTime) : 0.0f;
        factor = Math.max(0.0f, Math.min(1.0f, factor));

        // Spherical linear interpolation (SLERP)
        Quaternionf rot0 = new Quaternionf(key0.mValue().x(), key0.mValue().y(), key0.mValue().z(), key0.mValue().w());
        Quaternionf rot1 = new Quaternionf(key1.mValue().x(), key1.mValue().y(), key1.mValue().z(), key1.mValue().w());
        return rot0.slerp(rot1, factor);
    }

    /**
     * Interpolate scale at given time
     */
    private Vector3f interpolateScale(org.lwjgl.assimp.AINodeAnim channel, double time) {
        var keys = channel.mScalingKeys();
        if (keys == null || keys.limit() == 0) {
            return null;
        }

        // Single key - no interpolation needed
        if (keys.limit() == 1) {
            var key = keys.get(0);
            return new Vector3f(key.mValue().x(), key.mValue().y(), key.mValue().z());
        }

        // Find surrounding keyframes
        int index = 0;
        for (int i = 0; i < keys.limit() - 1; i++) {
            if (time < keys.get(i + 1).mTime()) {
                index = i;
                break;
            }
        }

        var key0 = keys.get(index);
        var key1 = keys.get(Math.min(index + 1, keys.limit() - 1));

        // Calculate interpolation factor
        double deltaTime = key1.mTime() - key0.mTime();
        float factor = deltaTime > 0 ? (float)((time - key0.mTime()) / deltaTime) : 0.0f;
        factor = Math.max(0.0f, Math.min(1.0f, factor));

        // Linear interpolation
        Vector3f scale0 = new Vector3f(key0.mValue().x(), key0.mValue().y(), key0.mValue().z());
        Vector3f scale1 = new Vector3f(key1.mValue().x(), key1.mValue().y(), key1.mValue().z());
        return scale0.lerp(scale1, factor);
    }

    /**
     * Get transformation matrix for a specific bone
     *
     * @param boneName Name of the bone
     * @return Transformation matrix, or identity if bone not found
     */
    public Matrix4f getBoneTransform(String boneName) {
        BoneTransform transform = boneTransforms.get(boneName);
        if (transform != null) {
            return transform.toMatrix();
        }
        return new Matrix4f(); // Identity matrix
    }

    public void play() {
        playing = true;
    }

    public void pause() {
        playing = false;
    }

    public void stop() {
        playing = false;
        currentTime = 0.0;
    }

    public void setLooping(boolean looping) {
        this.looping = looping;
    }

    public void setSpeed(float speed) {
        this.speed = speed;
    }

    public double getCurrentTime() {
        return currentTime;
    }

    public boolean isPlaying() {
        return playing;
    }

    /**
     * Get bone position at current time
     */
    public Vector3f getBonePosition(String boneName) {
        BoneTransform transform = boneTransforms.get(boneName);
        return transform != null ? new Vector3f(transform.position) : null;
    }

    /**
     * Get bone rotation at current time
     */
    public Quaternionf getBoneRotation(String boneName) {
        BoneTransform transform = boneTransforms.get(boneName);
        return transform != null ? new Quaternionf(transform.rotation) : null;
    }

    /**
     * Get bone scale at current time
     */
    public Vector3f getBoneScale(String boneName) {
        BoneTransform transform = boneTransforms.get(boneName);
        return transform != null ? new Vector3f(transform.scale) : null;
    }

    /**
     * Get all bone transforms as matrices
     * Used by AnimationState to update BoneHierarchyBuilder
     */
    public Map<String, Matrix4f> getBoneTransforms() {
        Map<String, Matrix4f> transforms = new HashMap<>();
        for (Map.Entry<String, BoneTransform> entry : boneTransforms.entrySet()) {
            transforms.put(entry.getKey(), entry.getValue().toMatrix());
        }
        return transforms;
    }

    /**
     * Get the animation
     */
    public AssimpAnimation getAnimation() {
        return animation;
    }

    // ========== IK Integration (Phase 3) ==========

    /**
     * Initialize IK system with a root bone.
     * Creates a Skeleton and IKManager for this animation player.
     *
     * @param rootBone Root bone of the skeleton hierarchy
     */
    public void initializeIK(Bone rootBone) {
        this.skeleton = new Skeleton(rootBone);
        this.ikManager = new IKManager(skeleton);
    }

    /**
     * Get the IK manager (null if IK not initialized)
     */
    @Nullable
    public IKManager getIKManager() {
        return ikManager;
    }

    /**
     * Get the skeleton (null if IK not initialized)
     */
    @Nullable
    public Skeleton getSkeleton() {
        return skeleton;
    }

    /**
     * Enable IK solving
     */
    public void enableIK() {
        if (ikManager != null) {
            ikManager.setEnabled(true);
        }
    }

    /**
     * Disable IK solving
     */
    public void disableIK() {
        if (ikManager != null) {
            ikManager.setEnabled(false);
        }
    }

    /**
     * Check if IK is enabled
     */
    public boolean isIKEnabled() {
        return ikManager != null && ikManager.isEnabled();
    }

    /**
     * Bone transformation data (position, rotation, scale)
     */
    public static class BoneTransform {
        private Vector3f position;
        private Quaternionf rotation;
        private Vector3f scale;

        public BoneTransform() {
            this.position = new Vector3f(0, 0, 0);
            this.rotation = new Quaternionf();
            this.scale = new Vector3f(1, 1, 1);
        }

        public Matrix4f toMatrix() {
            Matrix4f matrix = new Matrix4f();
            matrix.translationRotateScale(position, rotation, scale);
            return matrix;
        }

        public void setPosition(Vector3f position) {
            this.position = position;
        }

        public void setRotation(Quaternionf rotation) {
            this.rotation = rotation;
        }

        public void setScale(Vector3f scale) {
            this.scale = scale;
        }
    }
}
