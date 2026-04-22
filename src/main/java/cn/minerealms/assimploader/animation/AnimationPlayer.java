package cn.minerealms.assimploader.animation;

import cn.minerealms.assimploader.model.AssimpAnimation;
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

        // Update bone transforms based on current time
        updateBoneTransforms();
    }

    /**
     * Calculate bone transformations at current time
     */
    private void updateBoneTransforms() {
        // Phase 4 TODO: Implement bone transformation calculation
        // - Read AINodeAnim channels from animation
        // - Interpolate position, rotation, scale keyframes
        // - Build transformation matrices for each bone
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
