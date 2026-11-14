package cn.minerealms.assimploader.vehicle.client;

import cn.minerealms.assimploader.core.AssimpModelLoader;
import cn.minerealms.assimploader.core.ModelImportMode;
import cn.minerealms.assimploader.model.AssimpScene;
import cn.minerealms.assimploader.render.ClientRenderResources;
import cn.minerealms.assimploader.render.ModelTextureCache;
import cn.minerealms.assimploader.render.base.BaseAssimpRenderer;
import cn.minerealms.assimploader.rendering.CachedMeshRenderer;
import cn.minerealms.assimploader.rendering.OculusEntityBufferCache;
import cn.minerealms.assimploader.vehicle.ArmoredCarEntity;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.math.Axis;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.entity.EntityRenderer;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.Mth;

public final class ArmoredCarRenderer extends EntityRenderer<ArmoredCarEntity> {
    public static final String MODEL_ID = "assimploader:models/entity/armored_car/armored_car";
    private final BaseAssimpRenderer fallback = new BaseAssimpRenderer() {
        @Override protected String getModelName() { return ArmoredCarEntity.MODEL_NAME; }
    };
    private AssimpScene lastScene;
    private ArmoredCarGeometry geometry;

    public ArmoredCarRenderer(EntityRendererProvider.Context context) {
        super(context);
        shadowRadius = 1.4f * ArmoredCarEntity.MODEL_SCALE;
    }

    @Override
    public void render(ArmoredCarEntity car, float yaw, float partialTick, PoseStack poses,
                       MultiBufferSource buffers, int light) {
        AssimpScene scene = AssimpModelLoader.getCachedModel(MODEL_ID, ModelImportMode.DYNAMIC);
        if (scene == null) return;
        ClientRenderResources.checkGeneration();
        if (scene != lastScene) {
            geometry = new ArmoredCarGeometry(scene);
            lastScene = scene;
        }
        var instances = geometry.pose(car.getWheelAngle(partialTick), car.getSteeringAngle(partialTick));
        poses.pushPose();
        try {
            // Minecraft yaw grows toward -X; the asset faces +Z with its root at ground center.
            poses.mulPose(Axis.YP.rotationDegrees(-Mth.rotLerp(partialTick, car.yRotO, car.getYRot())));
            poses.scale(ArmoredCarEntity.MODEL_SCALE, ArmoredCarEntity.MODEL_SCALE, ArmoredCarEntity.MODEL_SCALE);
            if (!OculusEntityBufferCache.renderEntity(scene, instances, poses, light, OverlayTexture.NO_OVERLAY)
                    && !CachedMeshRenderer.render(scene, instances, poses, buffers, light, OverlayTexture.NO_OVERLAY))
                fallback.renderInstances(scene, instances, poses, buffers, light, OverlayTexture.NO_OVERLAY);
        } finally {
            poses.popPose();
        }
        super.render(car, yaw, partialTick, poses, buffers, light);
    }

    @Override
    public ResourceLocation getTextureLocation(ArmoredCarEntity entity) {
        return ModelTextureCache.WHITE;
    }
}
