package cn.minerealms.assimploader.showcase;

import cn.minerealms.assimploader.core.AssimpModelLoader;
import cn.minerealms.assimploader.render.base.BaseAssimpRenderer;
import com.mojang.blaze3d.vertex.PoseStack;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.blockentity.BlockEntityRenderer;

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
            renderScene(scene, null, poses, buffers, light, overlay);
        } finally {
            poses.popPose();
        }
    }

    @Override public int getViewDistance() { return 128; }
    @Override public boolean shouldRenderOffScreen(ShowcaseBlockEntity entity) {
        return !cn.minerealms.assimploader.config.AssimpLoaderConfig.getOrDefault(
                cn.minerealms.assimploader.config.AssimpLoaderConfig.CLIENT.frustumCullingEnabled::get, true);
    }
}
