package cn.minerealms.assimploader.animation;

import cn.minerealms.assimploader.debug.DebugManager;
import cn.minerealms.assimploader.debug.DebugManager.DebugCategory;
import net.minecraft.client.Minecraft;
import cn.minerealms.assimploader.core.AssimpModelLoader;

import java.util.HashMap;
import java.util.Map;

/**
 * Client-side singleton manager for item animation states.
 * Maintains one AnimationState per model for rendering items in hand/GUI.
 */
public class ClientAnimationManager {
    private static ClientAnimationManager instance;

    private final Map<String, AnimationState> animationStates = new HashMap<>();
    private long lastUpdateTime = 0;

    private ClientAnimationManager() {
    }

    public static ClientAnimationManager getInstance() {
        if (instance == null) {
            instance = new ClientAnimationManager();
        }
        return instance;
    }

    /**
     * Get or create animation state for a model.
     * @param modelName Model name (e.g., "circle", "models/block/circle")
     * @return AnimationState for this model
     */
    public AnimationState getOrCreateAnimationState(String modelName) {
        var scene = AssimpModelLoader.getCachedModel(modelName);
        if (scene == null || !scene.isDynamic() || (!scene.hasAnimations() && !scene.hasSkeleton())) {
            animationStates.remove(modelName);
            return null;
        }
        AnimationState existing = animationStates.get(modelName);
        if (existing != null && existing.getScene() == scene) return existing;
        AnimationState state = new AnimationState(modelName, scene);
        state.initialize();
        if (!state.isInitialized()) return null;
        animationStates.put(modelName, state);
        return state;
    }

    /**
     * Update all animation states. Should be called once per frame.
     */
    public void tick() {
        long currentTime = System.currentTimeMillis();

        if (lastUpdateTime == 0) {
            lastUpdateTime = currentTime;
            return;
        }

        float deltaTime = Math.max(0, Math.min(0.25f, (currentTime - lastUpdateTime) / 1000.0f));
        lastUpdateTime = currentTime;

        // Update all active animations
        for (AnimationState state : animationStates.values()) {
            if (state.isInitialized() && state.getCurrentAnimation() != null) {
                state.update(deltaTime);
            }
        }
    }

    /**
     * Clear all animation states (e.g., on resource reload).
     */
    public void clear() {
        animationStates.clear();
        lastUpdateTime = 0;
    }

    /**
     * Static method to clear cache (for hot-reload).
     */
    public static void clearCache() {
        getInstance().clear();
    }

    /**
     * Get animation state if it exists (without creating).
     */
    public AnimationState getAnimationState(String modelName) {
        return animationStates.get(modelName);
    }
}
