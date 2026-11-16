package cn.minerealms.assimploader.animation;

import cn.minerealms.assimploader.animation.ik.IKManager;
import cn.minerealms.assimploader.animation.ik.Skeleton;
import cn.minerealms.assimploader.model.AssimpAnimation;
import cn.minerealms.assimploader.model.AssimpNode;
import org.jetbrains.annotations.Nullable;
import org.joml.Matrix4f;
import org.joml.Quaternionf;
import org.joml.Vector3f;

import java.util.HashMap;
import java.util.Collections;
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
    private final Map<String, Matrix4f> bindTransforms = new HashMap<>();
    private final Map<String, Matrix4f> matrixTransforms = new HashMap<>();
    private final Map<String, Matrix4f> matrixView = Collections.unmodifiableMap(matrixTransforms);

    private double currentTime = 0.0;
    private boolean playing = false;
    private boolean looping = true;
    private float speed = 1.0f;
    private boolean synchronizedPhase;

    // IK integration (Phase 3)
    private Skeleton skeleton;
    private IKManager ikManager;

    public AnimationPlayer(AssimpAnimation animation) {
        this(animation, null);
    }

    public AnimationPlayer(AssimpAnimation animation, AssimpNode rootNode) {
        this.animation = animation;
        this.boneTransforms = new HashMap<>();
        if (rootNode != null) collectBindTransforms(rootNode);
        updateBoneTransforms();
    }

    private void collectBindTransforms(AssimpNode node) {
        bindTransforms.put(node.getName(), new Matrix4f(node.getTransformation()));
        for (AssimpNode child : node.getChildren()) collectBindTransforms(child);
    }

    /**
     * Update animation time and calculate bone transforms
     *
     * @param deltaTime Time elapsed since last update (in seconds)
     */
    public void update(double deltaTime) {
        if (!playing || !Double.isFinite(deltaTime)) {
            return;
        }

        if (!synchronizedPhase) {
            currentTime += deltaTime * speed * animation.getTicksPerSecond();

            double duration = animation.getDuration();
            if (!Double.isFinite(duration) || duration <= 0) {
                currentTime = 0;
                if (!looping) playing = false;
            } else if (currentTime >= duration || currentTime < 0) {
                if (looping) {
                    currentTime = ((currentTime % duration) + duration) % duration;
                } else {
                    currentTime = Math.max(0, Math.min(duration, currentTime));
                    playing = false;
                }
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
        for (BoneAnimationChannel channel : animation.getChannels()) {
            BoneTransform transform = boneTransforms.computeIfAbsent(channel.getBoneName(),
                    name -> new BoneTransform(bindTransforms.get(name)));

            if (channel.hasPositionKeys()) {
                transform.setPosition(channel.getPosition(currentTime));
            }
            if (channel.hasRotationKeys()) {
                transform.setRotation(channel.getRotation(currentTime));
            }
            if (channel.hasScaleKeys()) {
                transform.setScale(channel.getScale(currentTime));
            }
            transform.toMatrix(matrixTransforms.computeIfAbsent(channel.getBoneName(), name -> new Matrix4f()));
        }
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
        updateBoneTransforms();
    }

    public void pause() {
        playing = false;
    }

    public void stop() {
        playing = false;
        synchronizedPhase = false;
        currentTime = 0.0;
        updateBoneTransforms();
    }

    public void setLooping(boolean looping) {
        this.looping = looping;
    }

    public void setSpeed(float speed) {
        if (!Float.isFinite(speed)) throw new IllegalArgumentException("Animation speed must be finite");
        this.speed = speed;
    }

    public float getSpeed() { return speed; }

    /** Samples an externally owned action clock without advancing simulation time or changing playback speed. */
    public void synchronizePhase(double progress) {
        if (!Double.isFinite(progress) || progress < 0 || progress > 1)
            throw new IllegalArgumentException("Synchronized animation progress must be in [0,1]");
        double duration = animation.getDuration();
        currentTime = Double.isFinite(duration) && duration > 0 ? duration * progress : 0;
        synchronizedPhase = true;
        updateBoneTransforms();
    }

    public void releaseSynchronizedPhase() { synchronizedPhase = false; }
    public boolean hasSynchronizedPhase() { return synchronizedPhase; }

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
        return matrixView;
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
            this(null);
        }

        public BoneTransform(Matrix4f bindTransform) {
            this.position = new Vector3f(0, 0, 0);
            this.rotation = new Quaternionf();
            this.scale = new Vector3f(1, 1, 1);
            if (bindTransform != null) {
                bindTransform.getTranslation(position);
                bindTransform.getScale(scale);
                bindTransform.getUnnormalizedRotation(rotation).normalize();
            }
        }

        public Matrix4f toMatrix() {
            return toMatrix(new Matrix4f());
        }

        private Matrix4f toMatrix(Matrix4f target) {
            return target.translationRotateScale(position, rotation, scale);
        }

        public void setPosition(Vector3f position) {
            this.position.set(position);
        }

        public void setRotation(Quaternionf rotation) {
            this.rotation.set(rotation);
        }

        public void setScale(Vector3f scale) {
            this.scale.set(scale);
        }
    }
}
