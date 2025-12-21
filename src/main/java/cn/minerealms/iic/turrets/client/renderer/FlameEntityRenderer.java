package cn.minerealms.iic.turrets.client.renderer;

import cn.minerealms.iic.IntegratedIndustrialCraft;
import cn.minerealms.iic.turrets.common.entity.FlameEntity;
import com.lowdragmc.shimmer.client.postprocessing.PostProcessing;
import com.lowdragmc.shimmer.client.shader.RenderUtils;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import com.mojang.math.Axis;
import net.minecraft.client.renderer.LightTexture;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.entity.EntityRenderer;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.Mth;
import org.joml.Matrix3f;
import org.joml.Matrix4f;

/**
 * 火焰弹渲染器 - 带Shimmer Bloom效果
 *
 * 渲染特性：
 * - 飞行阶段：火球效果
 * - 燃烧阶段：地面火焰区域
 * - 使用Shimmer bloom发光
 * - 照亮周围环境
 */
public class FlameEntityRenderer extends EntityRenderer<FlameEntity> {

    private static final ResourceLocation FLAME_TEXTURE = new ResourceLocation("minecraft", "textures/block/fire_0.png");
    private static final float FIREBALL_SIZE = 0.5F;
    private static final float BURN_AREA_RADIUS = 3.0F;

    private static boolean shimmerAvailable = false;
    private static boolean shimmerChecked = false;

    public FlameEntityRenderer(EntityRendererProvider.Context pContext) {
        super(pContext);
        checkShimmer();
    }

    private static void checkShimmer() {
        if (shimmerChecked) return;
        shimmerChecked = true;

        try {
            Class.forName("com.lowdragmc.shimmer.client.postprocessing.PostProcessing");
            shimmerAvailable = true;
            IntegratedIndustrialCraft.LOGGER.info("[IIC-Turrets] Shimmer detected, bloom effects enabled for flames");
        } catch (ClassNotFoundException e) {
            shimmerAvailable = false;
            IntegratedIndustrialCraft.LOGGER.info("[IIC-Turrets] Shimmer not found, using standard flame rendering");
        }
    }

    @Override
    public void render(FlameEntity pEntity, float pEntityYaw, float pPartialTick, PoseStack pPoseStack,
                      MultiBufferSource pBuffer, int pPackedLight) {
        pPoseStack.pushPose();

        float time = (pEntity.tickCount + pPartialTick) * 0.2F;

        if (pEntity.hasReachedGround()) {
            // 燃烧阶段：地面火焰区域
            if (shimmerAvailable) {
                renderBurnAreaWithBloom(pPoseStack, time);
            } else {
                renderBurnAreaStandard(pPoseStack, pBuffer, time);
            }
        } else {
            // 飞行阶段：火球
            if (shimmerAvailable) {
                renderFireballWithBloom(pPoseStack, time);
            } else {
                renderFireballStandard(pPoseStack, pBuffer, time);
            }
        }

        pPoseStack.popPose();
        super.render(pEntity, pEntityYaw, pPartialTick, pPoseStack, pBuffer, pPackedLight);
    }

    /**
     * 使用Shimmer渲染飞行中的火球
     */
    private void renderFireballWithBloom(PoseStack pPoseStack, float time) {
        try {
            PoseStack finalStack = RenderUtils.copyPoseStack(pPoseStack);
            PostProcessing.BLOOM_UNREAL.postEntity(bufferSource -> {
                VertexConsumer consumer = bufferSource.getBuffer(RenderType.energySwirl(FLAME_TEXTURE, 0, 0));
                renderFireball(finalStack, consumer, time);
            });
        } catch (Exception e) {
            IntegratedIndustrialCraft.LOGGER.warn("[IIC-Turrets] Shimmer bloom rendering failed for fireball", e);
        }
    }

    /**
     * 普通渲染飞行中的火球
     */
    private void renderFireballStandard(PoseStack pPoseStack, MultiBufferSource pBuffer, float time) {
        VertexConsumer consumer = pBuffer.getBuffer(RenderType.energySwirl(FLAME_TEXTURE, 0, 0));
        renderFireball(pPoseStack, consumer, time);
    }

    /**
     * 渲染火球（双层旋转的火焰面片）
     */
    private void renderFireball(PoseStack pPoseStack, VertexConsumer consumer, float time) {
        pPoseStack.pushPose();

        // 第一层火焰
        pPoseStack.mulPose(Axis.YP.rotationDegrees(time * 50));
        renderFlameQuad(pPoseStack, consumer, FIREBALL_SIZE, 255, 150, 50, 200);

        // 第二层火焰（旋转方向相反）
        pPoseStack.mulPose(Axis.YP.rotationDegrees(-time * 100));
        pPoseStack.mulPose(Axis.XP.rotationDegrees(90));
        renderFlameQuad(pPoseStack, consumer, FIREBALL_SIZE * 0.8F, 255, 100, 0, 180);

        pPoseStack.popPose();
    }

    /**
     * 使用Shimmer渲染燃烧区域
     */
    private void renderBurnAreaWithBloom(PoseStack pPoseStack, float time) {
        try {
            PoseStack finalStack = RenderUtils.copyPoseStack(pPoseStack);
            PostProcessing.BLOOM_UNREAL.postEntity(bufferSource -> {
                VertexConsumer consumer = bufferSource.getBuffer(RenderType.energySwirl(FLAME_TEXTURE, 0, 0));
                renderBurnArea(finalStack, consumer, time);
            });
        } catch (Exception e) {
            IntegratedIndustrialCraft.LOGGER.warn("[IIC-Turrets] Shimmer bloom rendering failed for burn area", e);
        }
    }

    /**
     * 普通渲染燃烧区域
     */
    private void renderBurnAreaStandard(PoseStack pPoseStack, MultiBufferSource pBuffer, float time) {
        VertexConsumer consumer = pBuffer.getBuffer(RenderType.energySwirl(FLAME_TEXTURE, 0, 0));
        renderBurnArea(pPoseStack, consumer, time);
    }

    /**
     * 渲染地面燃烧区域（多层火焰面片）
     */
    private void renderBurnArea(PoseStack pPoseStack, VertexConsumer consumer, float time) {
        pPoseStack.pushPose();
        pPoseStack.translate(0, 0.1, 0); // 稍微抬高避免z-fighting

        // 渲染多个火焰层，形成燃烧区域
        int layers = 8;
        for (int i = 0; i < layers; i++) {
            float angle = (360.0F / layers) * i + time * 30;
            float radius = BURN_AREA_RADIUS * (0.5F + 0.5F * Mth.sin(time + i));
            float height = 0.5F + 0.3F * Mth.sin(time * 2 + i);

            pPoseStack.pushPose();
            pPoseStack.mulPose(Axis.YP.rotationDegrees(angle));
            pPoseStack.translate(radius * 0.5F, height, 0);

            // 颜色随时间和位置变化
            int r = 255;
            int g = (int)(100 + 50 * Mth.sin(time + i));
            int b = 0;
            int a = (int)(150 + 50 * Mth.cos(time * 1.5F + i));

            renderFlameQuad(pPoseStack, consumer, 1.0F, r, g, b, a);
            pPoseStack.popPose();
        }

        pPoseStack.popPose();
    }

    /**
     * 渲染单个火焰面片（billboard）
     */
    private void renderFlameQuad(PoseStack pPoseStack, VertexConsumer consumer, float size, int r, int g, int b, int a) {
        PoseStack.Pose pose = pPoseStack.last();
        Matrix4f matrix = pose.pose();
        Matrix3f normal = pose.normal();

        // 使用最大亮度让火焰发光
        int light = LightTexture.FULL_BRIGHT;

        // 渲染正面
        consumer.vertex(matrix, -size, -size, 0).color(r, g, b, a).uv(0, 1).overlayCoords(OverlayTexture.NO_OVERLAY).uv2(light).normal(normal, 0, 1, 0).endVertex();
        consumer.vertex(matrix, -size, size, 0).color(r, g, b, a).uv(0, 0).overlayCoords(OverlayTexture.NO_OVERLAY).uv2(light).normal(normal, 0, 1, 0).endVertex();
        consumer.vertex(matrix, size, size, 0).color(r, g, b, a).uv(1, 0).overlayCoords(OverlayTexture.NO_OVERLAY).uv2(light).normal(normal, 0, 1, 0).endVertex();
        consumer.vertex(matrix, size, -size, 0).color(r, g, b, a).uv(1, 1).overlayCoords(OverlayTexture.NO_OVERLAY).uv2(light).normal(normal, 0, 1, 0).endVertex();

        // 渲染背面
        consumer.vertex(matrix, size, -size, 0).color(r, g, b, a).uv(1, 1).overlayCoords(OverlayTexture.NO_OVERLAY).uv2(light).normal(normal, 0, -1, 0).endVertex();
        consumer.vertex(matrix, size, size, 0).color(r, g, b, a).uv(1, 0).overlayCoords(OverlayTexture.NO_OVERLAY).uv2(light).normal(normal, 0, -1, 0).endVertex();
        consumer.vertex(matrix, -size, size, 0).color(r, g, b, a).uv(0, 0).overlayCoords(OverlayTexture.NO_OVERLAY).uv2(light).normal(normal, 0, -1, 0).endVertex();
        consumer.vertex(matrix, -size, -size, 0).color(r, g, b, a).uv(0, 1).overlayCoords(OverlayTexture.NO_OVERLAY).uv2(light).normal(normal, 0, -1, 0).endVertex();
    }

    @Override
    public ResourceLocation getTextureLocation(FlameEntity pEntity) {
        return FLAME_TEXTURE;
    }
}
