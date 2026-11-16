package cn.minerealms.assimploader.render.base;

import cn.minerealms.assimploader.animation.AnimationState;
import cn.minerealms.assimploader.core.AssimpModelLoader;
import cn.minerealms.assimploader.render.AttachmentTrailRenderer;
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
        cn.minerealms.assimploader.animation.ClientAnimationManager.getInstance().markRendered(state,
                cn.minerealms.assimploader.showcase.ShowcaseVisibility.distanceSquared(getBoundingBox(entity),
                        net.minecraft.client.Minecraft.getInstance().gameRenderer.getMainCamera().getPosition()));
        poses.pushPose();
        try {
            applyTransformations(poses, entity, partialTick);
            renderScene(scene, state, poses, buffers, light, overlay);
            if (state != null && state.getModelInstance() != null) AttachmentTrailRenderer.render(state.getModelInstance(),poses,buffers,light);
        } finally {
            poses.popPose();
        }
    }

    @Override protected net.minecraft.world.phys.AABB getBoundingBox(T entity) {
        AnimationState state=getAnimationState(entity);
        if (state != null && state.getPoseSnapshot()!=null) return state.getPoseSnapshot().getBounds().move(entity.getBlockPos());
        return super.getBoundingBox(entity);
    }
}
