package cn.minerealms.assimploader.animation;

import cn.minerealms.assimploader.debug.DebugManager;
import cn.minerealms.assimploader.debug.DebugManager.DebugCategory;
import net.minecraft.client.Minecraft;

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
        return animationStates.computeIfAbsent(modelName, name -> {
            DebugManager.info(DebugCategory.ANIMATION,
                "ClientAnimationManager: Creating AnimationState for item model '{}'", name);
            AnimationState state = new AnimationState(name);

            // Initialize the animation state
            state.initialize();

            // Auto-play first animation if available
            if (state.isInitialized()) {
                String[] availableAnimations = state.getAvailableAnimations();
                if (availableAnimations != null && availableAnimations.length > 0) {
                    String firstAnim = availableAnimations[0];
                    state.playAnimation(firstAnim);
                    DebugManager.info(DebugCategory.ANIMATION,
                        "ClientAnimationManager: Auto-playing animation '{}' for item model '{}'",
                        firstAnim, name);
                }
            }

            return state;
        });
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

        float deltaTime = (currentTime - lastUpdateTime) / 1000.0f;
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
        DebugManager.info(DebugCategory.ANIMATION,
            "ClientAnimationManager: Clearing {} animation states", animationStates.size());
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
