package cn.minerealms.assimploader.animation;

import cn.minerealms.assimploader.core.AssimpModelLoader;
import cn.minerealms.assimploader.debug.DebugManager;
import cn.minerealms.assimploader.debug.DebugManager.DebugCategory;
import cn.minerealms.assimploader.model.AssimpAnimation;
import cn.minerealms.assimploader.model.AssimpMesh;
import cn.minerealms.assimploader.model.AssimpScene;
import org.lwjgl.assimp.AIBone;
import org.lwjgl.assimp.AIMesh;

import java.util.HashMap;
import java.util.Map;

/**
 * Per-instance animation state for a BlockEntity or Entity.
 *
 * Each instance maintains:
 * - Its own AnimationPlayer instances
 * - Its own BoneHierarchyBuilder
 * - Independent animation playback state
 *
 * This allows multiple blocks/entities with the same model to have independent animations.
 */
public class AnimationState {

    private final String modelName;
    private final AssimpScene scene;
    private final Map<String, AnimationPlayer> animationPlayers;
    private final Map<AssimpMesh, BoneHierarchyBuilder> boneHierarchies;
    private AnimationPlayer currentAnimation;
    private boolean initialized = false;

    public AnimationState(String modelName) {
        this.modelName = modelName;
        this.scene = AssimpModelLoader.getCachedModel(modelName);
        this.animationPlayers = new HashMap<>();
        this.boneHierarchies = new HashMap<>();

        if (scene == null) {
            DebugManager.error(DebugCategory.ANIMATION, "Failed to create AnimationState: model {} not found", modelName);
        }
    }

    /**
     * Initialize animation system for this instance
     */
    public void initialize() {
        if (initialized || scene == null) {
            return;
        }

        DebugManager.info(DebugCategory.ANIMATION, "Initializing AnimationState for model: {}", modelName);

        // Build bone hierarchies for each skinned mesh
        for (AssimpMesh mesh : scene.getMeshes()) {
            if (mesh.hasBones()) {
                BoneHierarchyBuilder boneHierarchy = new BoneHierarchyBuilder();
                boneHierarchy.buildFromMesh(mesh.getNativeMesh(), scene.getRootNode().getNativeNode());
                boneHierarchies.put(mesh, boneHierarchy);

                DebugManager.debug(DebugCategory.ANIMATION, "Built bone hierarchy for mesh: {} ({} bones)",
                        mesh.getName(), boneHierarchy.getBoneCount());
            }
        }

        // Create animation players for each animation in the FBX
        for (AssimpAnimation animation : scene.getAnimations()) {
            AnimationPlayer player = new AnimationPlayer(animation);
            player.setLooping(true);
            animationPlayers.put(animation.getName(), player);

            DebugManager.debug(DebugCategory.ANIMATION, "Created animation player: {} (duration: {}s)",
                    animation.getName(), animation.getDuration());
        }

        // Set default animation if available
        if (!animationPlayers.isEmpty()) {
            currentAnimation = animationPlayers.values().iterator().next();
            currentAnimation.play();
            DebugManager.info(DebugCategory.ANIMATION, "Default animation set: {}", currentAnimation.getAnimation().getName());
        }

        initialized = true;
    }

    /**
     * Update animation state (call every tick)
     *
     * @param deltaTime Time since last update in seconds (typically 0.05 for 20 TPS)
     */
    public void update(float deltaTime) {
        if (!initialized || currentAnimation == null) {
            return;
        }

        // Update current animation
        currentAnimation.update(deltaTime);

        // Update bone hierarchies with new transforms
        for (Map.Entry<AssimpMesh, BoneHierarchyBuilder> entry : boneHierarchies.entrySet()) {
            BoneHierarchyBuilder boneHierarchy = entry.getValue();

            // Apply animation transforms to bones
            Map<String, org.joml.Matrix4f> boneTransforms = currentAnimation.getBoneTransforms();
            for (Map.Entry<String, org.joml.Matrix4f> transform : boneTransforms.entrySet()) {
                boneHierarchy.updateBoneTransform(transform.getKey(), transform.getValue());
            }

            // Recalculate final transforms
            boneHierarchy.updateTransforms();
        }
    }

    /**
     * Play a specific animation by name
     *
     * @param animationName Name of the animation to play
     * @return true if animation was found and started
     */
    public boolean playAnimation(String animationName) {
        AnimationPlayer player = animationPlayers.get(animationName);
        if (player == null) {
            DebugManager.warn(DebugCategory.ANIMATION, "Animation not found: {}", animationName);
            return false;
        }

        if (currentAnimation != null && currentAnimation != player) {
            currentAnimation.pause();
        }

        currentAnimation = player;
        currentAnimation.play();

        DebugManager.debug(DebugCategory.ANIMATION, "Playing animation: {}", animationName);
        return true;
    }

    /**
     * Get bone hierarchy for a specific mesh
     */
    public BoneHierarchyBuilder getBoneHierarchy(AssimpMesh mesh) {
        return boneHierarchies.get(mesh);
    }

    /**
     * Get all available animation names
     */
    public String[] getAvailableAnimations() {
        return animationPlayers.keySet().toArray(new String[0]);
    }

    /**
     * Get current animation player
     */
    public AnimationPlayer getCurrentAnimation() {
        return currentAnimation;
    }

    /**
     * Check if this state has any animations
     */
    public boolean hasAnimations() {
        return !animationPlayers.isEmpty();
    }

    /**
     * Check if this state has any skinned meshes
     */
    public boolean hasSkinnedMeshes() {
        return !boneHierarchies.isEmpty();
    }

    /**
     * Check if initialized
     */
    public boolean isInitialized() {
        return initialized;
    }

    /**
     * Get the scene
     */
    public AssimpScene getScene() {
        return scene;
    }

    /**
     * Get model name
     */
    public String getModelName() {
        return modelName;
    }
}
