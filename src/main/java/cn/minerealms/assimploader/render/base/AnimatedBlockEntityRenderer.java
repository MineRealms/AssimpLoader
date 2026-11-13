package cn.minerealms.assimploader.render.base;

import cn.minerealms.assimploader.animation.AnimationState;
import cn.minerealms.assimploader.core.AssimpModelLoader;
import com.mojang.blaze3d.vertex.PoseStack;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.world.level.block.entity.BlockEntity;

import javax.annotation.Nullable;

/** Static, node-animated and skinned geometry share one hierarchy-correct rendering path. */
public abstract class AnimatedBlockEntityRenderer<T extends BlockEntity> extends BaseBlockEntityRenderer<T> {
    @Nullable protected abstract AnimationState getAnimationState(T entity);

    @Override
    public void render(T entity, float partialTick, PoseStack poses, MultiBufferSource buffers, int light, int overlay) {
        var scene = AssimpModelLoader.getCachedModel(getModelName());
        if (scene == null) return;
        checkHotReload(scene, getModelName());
        AnimationState state = getAnimationState(entity);
        poses.pushPose();
        try {
            applyTransformations(poses, entity, partialTick);
            renderScene(scene, state, poses, buffers, light, overlay);
        } finally {
            poses.popPose();
        }
    }
}
