package cn.minerealms.assimploader.example;

import cn.minerealms.assimploader.animation.AnimationState;
import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;

/**
 * BlockEntity for TestModelBlock to enable custom rendering with animation support.
 *
 * Each instance maintains its own independent animation state.
 */
public class TestModelBlockEntity extends BlockEntity {

    private AnimationState animationState;
    private boolean initialized = false;

    public TestModelBlockEntity(BlockPos pos, BlockState state) {
        super(ExampleRegistry.TEST_MODEL_BLOCK_ENTITY.get(), pos, state);
    }

    /**
     * Initialize animation state (lazy initialization)
     */
    private void initializeAnimation() {
        if (!initialized) {
            // Get model name from renderer
            String modelName = "cnc"; // This should match TestModelBlockEntityRenderer.getModelName()
            animationState = new AnimationState(modelName);
            animationState.initialize();
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
        // Load animation state if needed
    }

    @Override
    protected void saveAdditional(CompoundTag tag) {
        super.saveAdditional(tag);
        // Save animation state if needed
    }
}
