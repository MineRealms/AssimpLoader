package cn.minerealms.iic.turrets.client.renderer;

import cn.minerealms.iic.turrets.client.model.FlameThrowerTurretModel;
import cn.minerealms.iic.turrets.common.block_entity.FlameThrowerTurretBlockEntity;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.math.Axis;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.core.Direction;
import software.bernie.geckolib.renderer.GeoBlockRenderer;

/**
 * 火焰喷射器炮塔渲染器
 */
public class FlameThrowerTurretRenderer extends GeoBlockRenderer<FlameThrowerTurretBlockEntity> {

    public FlameThrowerTurretRenderer() {
        super(new FlameThrowerTurretModel());
    }

    @Override
    protected void rotateBlock(Direction facing, PoseStack poseStack) {
        switch (facing) {
            case UP -> {
                poseStack.translate(0, 1, 0);
                poseStack.mulPose(Axis.ZP.rotationDegrees(180));
            }
            case DOWN -> {
                // 默认朝向，不需要旋转
            }
            default -> {
                // 只支持上下放置
            }
        }
    }
}
