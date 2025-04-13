package io.github.flemmli97.improvedmobs.mekanism_turrets.client.renderer;

import io.github.flemmli97.improvedmobs.ImprovedMobs;
import io.github.flemmli97.improvedmobs.mekanism_turrets.common.entity.LaserEntity;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.entity.EntityRenderer;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.phys.Vec3;
import org.joml.Matrix3f;
import org.joml.Matrix4f;

public class LaserRenderer<T extends LaserEntity> extends EntityRenderer<T> {

    public static final ResourceLocation LASER_TEXTURE = new ResourceLocation(ImprovedMobs.MODID, "textures/entity/laser.png");
    private static final float BEAM_RADIUS = 0.1F;

    public LaserRenderer(EntityRendererProvider.Context pContext) {
        super(pContext);
    }

    @Override
    public void render(T pEntity, float pEntityYaw, float pPartialTick, PoseStack pPoseStack, MultiBufferSource pBuffer, int pPackedLight) {
        Vec3 start = Vec3.ZERO;
        Vec3 end = pEntity.getTargetPos().subtract(pEntity.position());

        pPoseStack.pushPose();

        VertexConsumer vertexConsumer = pBuffer.getBuffer(RenderType.energySwirl(LASER_TEXTURE, 0, 0));

        renderBeam(start, end, pPoseStack, vertexConsumer, 240);

        pPoseStack.popPose();
        super.render(pEntity, pEntityYaw, pPartialTick, pPoseStack, pBuffer, pPackedLight);
    }

    private void renderBeam(Vec3 start, Vec3 end, PoseStack poseStack, VertexConsumer consumer, int light) {
        Vec3 delta = end.subtract(start);
        double length = delta.length();

        if (length < 0.01) return;

        Vec3 direction = delta.normalize();

        PoseStack.Pose pose = poseStack.last();
        Matrix4f matrix4f = pose.pose();
        Matrix3f matrix3f = pose.normal();

        Vec3 perpendicular1 = getPerpendicular(direction);
        Vec3 perpendicular2 = direction.cross(perpendicular1).normalize();

        int segments = Math.max(1, (int)(length * 2));
        float segmentLength = (float)(length / segments);

        for (int i = 0; i < segments; i++) {
            float t0 = (float)i / segments;
            float t1 = (float)(i + 1) / segments;

            Vec3 p0 = start.add(delta.scale(t0));
            Vec3 p1 = start.add(delta.scale(t1));

            renderBeamSegment(matrix4f, matrix3f, consumer, p0, p1, perpendicular1, perpendicular2,
                            t0, t1, light);
        }
    }

    private void renderBeamSegment(Matrix4f matrix, Matrix3f normal, VertexConsumer consumer,
                                   Vec3 p0, Vec3 p1, Vec3 perp1, Vec3 perp2,
                                   float u0, float u1, int light) {
        int sides = 8;
        for (int i = 0; i < sides; i++) {
            float angle0 = (float)(i * 2 * Math.PI / sides);
            float angle1 = (float)((i + 1) * 2 * Math.PI / sides);

            Vec3 offset0 = perp1.scale(Math.cos(angle0) * BEAM_RADIUS)
                          .add(perp2.scale(Math.sin(angle0) * BEAM_RADIUS));
            Vec3 offset1 = perp1.scale(Math.cos(angle1) * BEAM_RADIUS)
                          .add(perp2.scale(Math.sin(angle1) * BEAM_RADIUS));

            Vec3 v0 = p0.add(offset0);
            Vec3 v1 = p0.add(offset1);
            Vec3 v2 = p1.add(offset1);
            Vec3 v3 = p1.add(offset0);

            float v = (float)i / sides;
            float vNext = (float)(i + 1) / sides;

            addVertex(consumer, matrix, normal, v0, u0, v, light);
            addVertex(consumer, matrix, normal, v1, u0, vNext, light);
            addVertex(consumer, matrix, normal, v2, u1, vNext, light);
            addVertex(consumer, matrix, normal, v3, u1, v, light);
        }
    }

    private void addVertex(VertexConsumer consumer, Matrix4f matrix, Matrix3f normal,
                          Vec3 pos, float u, float v, int light) {
        consumer.vertex(matrix, (float)pos.x, (float)pos.y, (float)pos.z)
                .color(255, 50, 50, 255)
                .uv(u, v)
                .overlayCoords(OverlayTexture.NO_OVERLAY)
                .uv2(240)
                .normal(normal, 0, 1, 0)
                .endVertex();
    }

    private Vec3 getPerpendicular(Vec3 v) {
        if (Math.abs(v.y) < 0.9) {
            return new Vec3(0, 1, 0).cross(v).normalize();
        } else {
            return new Vec3(1, 0, 0).cross(v).normalize();
        }
    }

    @Override
    public ResourceLocation getTextureLocation(LaserEntity pEntity) {
        return LASER_TEXTURE;
    }
}
