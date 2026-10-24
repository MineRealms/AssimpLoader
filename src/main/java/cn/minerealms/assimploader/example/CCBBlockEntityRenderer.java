package cn.minerealms.assimploader.example;

import cn.minerealms.assimploader.animation.AnimationState;
import cn.minerealms.assimploader.render.base.AnimatedBlockEntityRenderer;
import net.minecraft.client.renderer.blockentity.BlockEntityRendererProvider;

import javax.annotation.Nullable;

/**
 * Renderer for the CNC block.
 *
 * <p>Uses GPU skinning when the model carries a skeleton, and falls back to the standard
 * static path otherwise - so the same class serves both kinds of model.
 */
public class CCBBlockEntityRenderer extends AnimatedBlockEntityRenderer<CCBBlockEntity> {

    public CCBBlockEntityRenderer(BlockEntityRendererProvider.Context context) {
    }

    @Override
    protected String getModelName() {
        return CCBBlockEntity.MODEL_NAME;
    }

    @Nullable
    @Override
    protected AnimationState getAnimationState(CCBBlockEntity blockEntity) {
        return blockEntity.getAnimationState();
    }
}
