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

    // Dirty flag system for caching
    private boolean transformsDirty = true;
    private final Map<AssimpMesh, org.joml.Matrix4f[]> cachedBoneTransforms = new HashMap<>();

    public AnimationState(String modelName) {
        this.modelName = modelName;
        // IMPORTANT: Always use DYNAMIC mode for animation - STATIC mode removes bones!
        this.scene = AssimpModelLoader.getCachedModel(modelName, cn.minerealms.assimploader.core.ModelImportMode.DYNAMIC);
        this.animationPlayers = new HashMap<>();
        this.boneHierarchies = new HashMap<>();

        if (scene == null) {
            DebugManager.error(DebugCategory.ANIMATION, "Failed to create AnimationState: model {} not found in DYNAMIC mode", modelName);
        } else {
            DebugManager.info(DebugCategory.ANIMATION, "AnimationState created for model '{}': mode={}, meshes={}, animations={}",
                modelName, scene.getImportMode(), scene.getMeshes().size(), scene.getAnimations().size());
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

            // Debug: Check animation channel types
            var aiAnim = animation.getNativeAnimation();
            int numChannels = aiAnim.mChannels() != null ? aiAnim.mChannels().limit() : 0;
            int numMeshChannels = aiAnim.mMeshChannels() != null ? aiAnim.mMeshChannels().limit() : 0;
            int numMorphMeshChannels = aiAnim.mMorphMeshChannels() != null ? aiAnim.mMorphMeshChannels().limit() : 0;

            DebugManager.info(DebugCategory.ANIMATION, "Animation '{}': duration={}s, channels={}, meshChannels={}, morphChannels={}",
                    animation.getName(), animation.getDuration(), numChannels, numMeshChannels, numMorphMeshChannels);
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

        // Mark transforms as dirty - need recalculation
        transformsDirty = true;

        // Log animation time occasionally (every 60 frames = ~3 seconds at 20 TPS)
        if (updateCounter++ % 60 == 0) {
            DebugManager.info(DebugCategory.ANIMATION,
                "AnimationState update: animation='{}', time={}s, meshes with bones={}",
                currentAnimation.getAnimation().getName(),
                currentAnimation.getCurrentTime(),
                boneHierarchies.size());
        }

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

    private int updateCounter = 0;

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
     * Get cached bone transforms for a mesh (with dirty flag optimization)
     */
    public org.joml.Matrix4f[] getCachedBoneTransforms(AssimpMesh mesh) {
        if (transformsDirty) {
            // Recalculate all bone transforms
            for (Map.Entry<AssimpMesh, BoneHierarchyBuilder> entry : boneHierarchies.entrySet()) {
                BoneHierarchyBuilder hierarchy = entry.getValue();
                cachedBoneTransforms.put(entry.getKey(), hierarchy.getFinalTransforms());
            }
            transformsDirty = false;
        }
        return cachedBoneTransforms.get(mesh);
    }

    /**
     * Invalidate transform cache (call on hot-reload or animation change)
     */
    public void invalidateCache() {
        transformsDirty = true;
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
