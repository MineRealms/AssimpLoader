package cn.minerealms.assimpshowcase.showcase;

import cn.minerealms.assimploader.render.ModelVisibility;

import cn.minerealms.assimploader.config.AssimpLoaderConfig;
import cn.minerealms.assimploader.core.AssimpModelLoader;
import cn.minerealms.assimploader.render.ClientRenderResources;
import cn.minerealms.assimploader.render.base.BaseAssimpRenderer;
import cn.minerealms.assimploader.rendering.OculusEntityBufferCache;
import cn.minerealms.assimploader.animation.ClientAnimationManager;
import cn.minerealms.assimploader.render.AttachmentTrailRenderer;
import com.mojang.blaze3d.vertex.PoseStack;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.blockentity.BlockEntityRenderer;
import net.minecraft.world.phys.Vec3;

public class ShowcaseRenderer extends BaseAssimpRenderer implements BlockEntityRenderer<ShowcaseBlockEntity> {
    @Override protected String getModelName() { return "showcase"; }

    @Override
    public void render(ShowcaseBlockEntity entity, float partialTick, PoseStack poses,
                       MultiBufferSource buffers, int light, int overlay) {
        var scene = AssimpModelLoader.getCachedModel(entity.getModel().modelName());
        if (scene == null) return;
        ClientRenderResources.checkGeneration();
        var state=ClientAnimationManager.getInstance().getOrCreateAnimationState(entity,entity.getModel().modelName(),scene);
        var camera=net.minecraft.client.Minecraft.getInstance().gameRenderer.getMainCamera().getPosition();
        ClientAnimationManager.getInstance().markRendered(state, ModelVisibility.distanceSquared(entity.getRenderBoundingBox(),camera));
        var visual=state==null?null:state.getRenderPose(partialTick);
        if (visual!=null) entity.setVisualBounds(scene,visual.getBounds().minmax(state.getPoseSnapshot().getBounds()));
        if(state!=null && state.getModelInstance()!=null) cn.minerealms.assimploader.client.MinecraftAttachmentParticles.bind(
                state.getModelInstance(),entity.getLevel(),new Vec3(entity.getBlockPos().getX()+.5,entity.getBlockPos().getY(),entity.getBlockPos().getZ()+.5),new org.joml.Matrix4f());
        poses.pushPose();
        try {
            poses.translate(0.5, 0, 0.5);
            if (state!=null ? !OculusEntityBufferCache.renderPosed(state,visual,poses,light,overlay,true)
                    : !OculusEntityBufferCache.render(scene, poses, light, overlay))
                renderScene(scene, state, poses, buffers, light, overlay,partialTick);
            if (state!=null && state.getModelInstance()!=null) AttachmentTrailRenderer.render(state.getModelInstance(),poses,buffers,light);
        } finally {
            poses.popPose();
        }
    }

    @Override public int getViewDistance() {
        return AssimpLoaderConfig.getIntOrDefault(AssimpLoaderConfig.CLIENT.modelViewDistance, 128);
    }

    @Override public boolean shouldRender(ShowcaseBlockEntity entity, Vec3 camera) {
        return bypassCulling(entity) || ModelVisibility.withinDistance(entity.getRenderBoundingBox(), camera, getViewDistance());
    }

    //? if neoforge {
    /*
    // 1.21 moved the culling box from BlockEntity onto the renderer.
    @Override public net.minecraft.world.phys.AABB getRenderBoundingBox(ShowcaseBlockEntity entity) {
        return entity.getRenderBoundingBox();
    }
    *///?}

    @Override public boolean shouldRenderOffScreen(ShowcaseBlockEntity entity) {
        // Routing is compiled into the anchor section, while assets and visibility settings can reload.
        // Keep Showcase entities global; the engine still frustum-tests their real bounds every frame.
        return true;
    }

    private boolean bypassCulling(ShowcaseBlockEntity entity) {
        return ModelVisibility.bypassCulling(entity.getModelBounds(), AssimpLoaderConfig.getOrDefault(
                AssimpLoaderConfig.CLIENT.alwaysRenderLargeModels::get, false), AssimpLoaderConfig.getOrDefault(
                AssimpLoaderConfig.CLIENT.largeModelThreshold::get, 16.0));
    }
}
