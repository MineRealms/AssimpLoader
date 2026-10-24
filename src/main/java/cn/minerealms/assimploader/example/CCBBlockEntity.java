package cn.minerealms.assimploader.example;

import cn.minerealms.assimploader.animation.AnimationState;
import cn.minerealms.assimploader.debug.DebugManager;
import cn.minerealms.assimploader.debug.DebugManager.DebugCategory;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;

import javax.annotation.Nullable;

/**
 * Block entity for the CNC block.
 *
 * <p>Owns the per-instance {@link AnimationState}. The state is only created when the model
 * was imported in DYNAMIC mode and actually contains a skeleton, so static models cost
 * nothing.
 */
public class CCBBlockEntity extends BlockEntity {

    public static final String MODEL_NAME = "ccb";

    @Nullable
    private AnimationState animationState;
    private boolean animationStateResolved = false;

    public CCBBlockEntity(BlockPos pos, BlockState state) {
        super(ExampleRegistry.CNC_BLOCK_ENTITY.get(), pos, state);
    }

    /**
     * @return the animation state, or {@code null} when this model is not animated
     */
    @Nullable
    public AnimationState getAnimationState() {
        if (animationStateResolved) {
            return animationState;
        }
        animationStateResolved = true;

        AnimationState state = new AnimationState(MODEL_NAME);
        state.initialize();
        if (state.isInitialized() && state.hasSkinnedMeshes()) {
            animationState = state;
            DebugManager.info(DebugCategory.ANIMATION,
                    "Animated model '{}': {} skinned mesh(es), {} clip(s)",
                    MODEL_NAME, state.getBoneHierarchies().size(), state.getAvailableAnimations().length);
        }
        return animationState;
    }

    /** Client tick: advance the skeletal animation. */
    public void tick() {
        AnimationState state = getAnimationState();
        if (state != null) {
            state.update(1.0f / 20.0f);
        }
    }
}
