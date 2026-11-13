package cn.minerealms.assimploader.render.base;

import cn.minerealms.assimploader.core.AssimpModelLoader;
import cn.minerealms.assimploader.lod.LODManager;
import cn.minerealms.assimploader.render.SceneTransforms;
import com.mojang.blaze3d.vertex.PoseStack;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.blockentity.BlockEntityRenderer;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

/** Scene-level cached renderer with a vanilla batch fallback for active shader packs. */
public abstract class BaseBlockEntityRenderer<T extends BlockEntity> extends BaseAssimpRenderer
        implements BlockEntityRenderer<T> {
    @Override
    public void render(T entity, float partialTick, PoseStack poses, MultiBufferSource buffers, int light, int overlay) {
        var scene = AssimpModelLoader.getCachedModel(getModelName());
        if (scene == null) return;
        checkHotReload(scene, getModelName());
        if (enableLOD()) {
            Vec3 camera = Minecraft.getInstance().gameRenderer.getMainCamera().getPosition();
            if (LODManager.getLODLevel(camera, Vec3.atCenterOf(entity.getBlockPos())) == LODManager.LODLevel.CULLED) return;
        }
        poses.pushPose();
        try {
            applyTransformations(poses, entity, partialTick);
            renderScene(scene, null, poses, buffers, light, overlay);
        } finally {
            poses.popPose();
        }
    }

    protected AABB getBoundingBox(T entity) {
        var scene = AssimpModelLoader.getCachedModel(getModelName());
        if (scene == null) return new AABB(entity.getBlockPos());
        return SceneTransforms.bounds(scene).inflate(0.5).move(entity.getBlockPos());
    }

    protected void applyTransformations(PoseStack poses, T entity, float partialTick) {}
    protected boolean enableFrustumCulling() { return true; }
    protected boolean enableLOD() { return false; }
    protected boolean enableProfiling() { return false; }
    @Override public boolean shouldRenderOffScreen(T entity) {
        return !enableFrustumCulling() || !cn.minerealms.assimploader.config.AssimpLoaderConfig.getOrDefault(
                cn.minerealms.assimploader.config.AssimpLoaderConfig.CLIENT.frustumCullingEnabled::get, true);
    }
    @Override public int getViewDistance() { return 128; }
}
