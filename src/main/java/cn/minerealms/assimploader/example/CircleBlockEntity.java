package cn.minerealms.assimploader.example;

import cn.minerealms.assimploader.animation.AnimationState;
import cn.minerealms.assimploader.debug.DebugManager;
import cn.minerealms.assimploader.debug.DebugManager.DebugCategory;
import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;

/**
 * BlockEntity for CircleBlock with automatic animation playback.
 *
 * Features:
 * - Automatically detects and plays the first animation in the FBX file
 * - Loops animation continuously
 * - Independent animation state per block instance
 */
public class CircleBlockEntity extends BlockEntity {

    private AnimationState animationState;
    private boolean initialized = false;

    public CircleBlockEntity(BlockPos pos, BlockState state) {
        super(ExampleRegistry.CIRCLE_BLOCK_ENTITY.get(), pos, state);
    }

    /**
     * Initialize animation state (lazy initialization)
     */
    private void initializeAnimation() {
        if (!initialized) {
            // Model name matches the FBX filename (without extension)
            String modelName = "circle";

            DebugManager.info(DebugCategory.ANIMATION, "Initializing CircleBlock animation at {}", worldPosition);

            animationState = new AnimationState(modelName);
            animationState.initialize();

            if (animationState.isInitialized()) {
                // Get available animations
                String[] animations = animationState.getAvailableAnimations();

                if (animations.length > 0) {
                    // Play the first animation found (loop enabled by default)
                    String firstAnimation = animations[0];
                    animationState.playAnimation(firstAnimation);

                    DebugManager.info(DebugCategory.ANIMATION,
                        "CircleBlock playing animation: '{}' (total animations: {})",
                        firstAnimation, animations.length);

                    // Log all available animations
                    if (animations.length > 1) {
                        DebugManager.info(DebugCategory.ANIMATION, "Available animations: {}",
                            String.join(", ", animations));
                    }
                } else {
                    DebugManager.warn(DebugCategory.ANIMATION,
                        "CircleBlock: No animations found in model '{}'", modelName);
                }
            } else {
                DebugManager.error(DebugCategory.ANIMATION,
                    "CircleBlock: Failed to initialize animation state for model '{}'", modelName);
            }

            initialized = true;
        }
    }

    /**
     * Tick method - update animation every tick
     */
    public void tick() {
        if (!initialized) {
            initializeAnimation();
        }

        if (animationState != null && animationState.isInitialized()) {
            // Update animation (1 tick = 0.05 seconds at 20 TPS)
            animationState.update(0.05f);
        }
    }

    /**
     * Get animation state for rendering
     */
    public AnimationState getAnimationState() {
        if (!initialized) {
            initializeAnimation();
        }
        return animationState;
    }

    @Override
    public void load(CompoundTag tag) {
        super.load(tag);
        // Load animation state if needed (currently not persisted)
    }

    @Override
    protected void saveAdditional(CompoundTag tag) {
        super.saveAdditional(tag);
        // Save animation state if needed (currently not persisted)
    }
}
