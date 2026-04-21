package cn.minerealms.iic.turrets.client.model;

import cn.minerealms.iic.IntegratedIndustrialCraft;
import cn.minerealms.iic.turrets.common.block.FlameThrowerTurretBlock;
import cn.minerealms.iic.turrets.common.block_entity.FlameThrowerTurretBlockEntity;
import net.minecraft.core.Direction;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.Mth;
import net.minecraft.world.phys.Vec3;
import org.joml.Quaternionf;
import org.joml.Vector3f;
import software.bernie.geckolib.core.animatable.model.CoreGeoBone;
import software.bernie.geckolib.core.animation.AnimationState;
import software.bernie.geckolib.model.DefaultedBlockGeoModel;

/**
 * 火焰喷射器炮塔GeckoLib模型
 *
 * 节点结构：
 * - BASE: 底座，不移动
 * - turret: 控制yaw（水平旋转）
 *   - flamegun: 控制pitch（垂直旋转）
 */
public class FlameThrowerTurretModel extends DefaultedBlockGeoModel<FlameThrowerTurretBlockEntity> {

    private static final ResourceLocation MODEL = new ResourceLocation(IntegratedIndustrialCraft.MODID, "geo/block/flamethrower.geo.json");
    private static final ResourceLocation TEXTURE = new ResourceLocation(IntegratedIndustrialCraft.MODID, "textures/block/flamethrower.png");
    private static final ResourceLocation ANIMATION = new ResourceLocation(IntegratedIndustrialCraft.MODID, "animations/block/flamethrower_anim.json");

    public FlameThrowerTurretModel() {
        super(new ResourceLocation(IntegratedIndustrialCraft.MODID, "flamethrower_turret"));
    }

    @Override
    public ResourceLocation getModelResource(FlameThrowerTurretBlockEntity animatable) {
        return MODEL;
    }

    @Override
    public ResourceLocation getTextureResource(FlameThrowerTurretBlockEntity animatable) {
        return TEXTURE;
    }

    @Override
    public ResourceLocation getAnimationResource(FlameThrowerTurretBlockEntity animatable) {
        return ANIMATION;
    }

    @Override
    public void setCustomAnimations(FlameThrowerTurretBlockEntity animatable, long instanceId,
                                    AnimationState<FlameThrowerTurretBlockEntity> animationState) {
        CoreGeoBone turret = getAnimationProcessor().getBone("turret");
        CoreGeoBone flamegun = getAnimationProcessor().getBone("flamegun");

        if (turret != null && flamegun != null) {
            if (hasTarget(animatable)) {
                Vec3 targetPos = new Vec3(targetX(animatable), targetY(animatable), targetZ(animatable));
                Direction direction = animatable.getBlockState().getValue(FlameThrowerTurretBlock.FACING);
                Vec3 center = animatable.getBlockPos().getCenter();

                // flamegun的pivot在[-1.5, 27, -1]（方块坐标系）
                // 转换为世界坐标：相对于方块中心的偏移
                Vec3 turretPivot = center.add(-1.5/16.0, 27.0/16.0 - 0.5, -1.0/16.0);
                Vector3f deltaPos = getTransform(direction).transform(new Vec3(
                    targetPos.x - turretPivot.x,
                    targetPos.y - turretPivot.y,
                    targetPos.z - turretPivot.z
                ).toVector3f());

                double deltaHorizontal = Math.sqrt(deltaPos.x * deltaPos.x + deltaPos.z * deltaPos.z);

                // bone有rotation [0, -90, 0]，所以flamegun实际朝向+Z（不是+X）
                // 但GeckoLib的旋转是相对于节点的局部坐标系
                // flamegun在自己的局部坐标系中沿+X延伸
                // 要控制pitch（上下俯仰），需要绕垂直于延伸方向的轴
                // 在flamegun的局部坐标系中，这是Z轴
                float pitchAngle = (float) -Math.atan2(deltaPos.y, deltaHorizontal);
                float yawAngle = (float) Math.atan2(deltaPos.x, deltaPos.z); // atan2(x, z)因为朝向+Z

                float xRot = lerp(animatable.xRot0, pitchAngle);
                float yRot = lerp(animatable.yRot0, yawAngle);

                animatable.xRot0 = xRot;
                animatable.yRot0 = yRot;

                // flamegun在局部坐标系中沿+X延伸，用Z轴旋转控制pitch
                flamegun.setRotZ(xRot);
                turret.setRotY(yRot);
            } else {
                // 无目标时回到默认位置（水平朝前）
                float xRot = lerp(animatable.xRot0, 0);
                float yRot = lerp(animatable.yRot0, 0);

                animatable.xRot0 = xRot;
                animatable.yRot0 = yRot;

                flamegun.setRotZ(xRot);
                turret.setRotY(yRot);
            }
        }
    }

    /**
     * 根据方块朝向获取坐标变换
     */
    private Quaternionf getTransform(Direction direction) {
        return switch (direction) {
            case UP -> new Quaternionf().rotationZ((float) Math.PI);
            case DOWN -> new Quaternionf();
            default -> new Quaternionf(); // 只支持上下放置
        };
    }

    /**
     * 平滑插值旋转角度
     */
    private float lerp(float start, float end) {
        return Mth.rotLerp(0.1F, start * Mth.RAD_TO_DEG, end * Mth.RAD_TO_DEG) * Mth.DEG_TO_RAD;
    }

    private boolean hasTarget(FlameThrowerTurretBlockEntity animatable) {
        return Boolean.TRUE.equals(animatable.getAnimData(FlameThrowerTurretBlockEntity.HAS_TARGET));
    }

    private double targetX(FlameThrowerTurretBlockEntity animatable) {
        Double targetX = animatable.getAnimData(FlameThrowerTurretBlockEntity.TARGET_POS_X);
        return targetX != null ? targetX : 0;
    }

    private double targetY(FlameThrowerTurretBlockEntity animatable) {
        Double targetY = animatable.getAnimData(FlameThrowerTurretBlockEntity.TARGET_POS_Y);
        return targetY != null ? targetY : 0;
    }

    private double targetZ(FlameThrowerTurretBlockEntity animatable) {
        Double targetZ = animatable.getAnimData(FlameThrowerTurretBlockEntity.TARGET_POS_Z);
        return targetZ != null ? targetZ : 0;
    }
}
