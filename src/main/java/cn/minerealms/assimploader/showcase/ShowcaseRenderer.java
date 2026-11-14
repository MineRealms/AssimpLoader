package cn.minerealms.assimploader.showcase;

import cn.minerealms.assimploader.config.AssimpLoaderConfig;
import cn.minerealms.assimploader.core.AssimpModelLoader;
import cn.minerealms.assimploader.render.ClientRenderResources;
import cn.minerealms.assimploader.render.base.BaseAssimpRenderer;
import cn.minerealms.assimploader.rendering.OculusEntityBufferCache;
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
        poses.pushPose();
        try {
            poses.translate(0.5, 0, 0.5);
            ClientRenderResources.checkGeneration();
            if (!OculusEntityBufferCache.render(scene, poses, light, overlay))
                renderScene(scene, null, poses, buffers, light, overlay);
        } finally {
            poses.popPose();
        }
    }

    @Override public int getViewDistance() {
        return AssimpLoaderConfig.getIntOrDefault(AssimpLoaderConfig.CLIENT.modelViewDistance, 128);
    }

    @Override public boolean shouldRender(ShowcaseBlockEntity entity, Vec3 camera) {
        return bypassCulling(entity) || ShowcaseVisibility.withinDistance(entity.getRenderBoundingBox(), camera, getViewDistance());
    }

    @Override public boolean shouldRenderOffScreen(ShowcaseBlockEntity entity) {
        // Routing is compiled into the anchor section, while assets and visibility settings can reload.
        // Keep Showcase entities global; the engine still frustum-tests their real bounds every frame.
        return true;
    }

    private boolean bypassCulling(ShowcaseBlockEntity entity) {
        return ShowcaseVisibility.bypassCulling(entity.getModelBounds(), AssimpLoaderConfig.getOrDefault(
                AssimpLoaderConfig.CLIENT.alwaysRenderLargeModels::get, false), AssimpLoaderConfig.getOrDefault(
                AssimpLoaderConfig.CLIENT.largeModelThreshold::get, 16.0));
    }
}
