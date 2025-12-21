package cn.minerealms.iic.turrets.common.events;

import cn.minerealms.iic.IntegratedIndustrialCraft;
import cn.minerealms.iic.turrets.common.block.EnergyPedestalBlock;
import cn.minerealms.iic.turrets.common.block.FlameThrowerTurretBlock;
import cn.minerealms.iic.turrets.common.block.LaserTurretBlock;
import cn.minerealms.iic.turrets.common.block_entity.EnergyPedestalBlockEntity;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraftforge.event.entity.player.PlayerInteractEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

/**
 * 炮塔放置限制事件处理器
 *
 * 功能：
 * - 限制炮塔只能放置在能量底座上
 * - 激光炮塔：可以放置在能量底座的非输入面
 * - 火焰喷射器：只能放置在能量底座的上面或下面
 */
@Mod.EventBusSubscriber(modid = IntegratedIndustrialCraft.MODID, bus = Mod.EventBusSubscriber.Bus.FORGE)
public class TurretPlacementHandler {

    @SubscribeEvent
    public static void onBlockPlace(PlayerInteractEvent.RightClickBlock event) {
        Player player = event.getEntity();
        Level level = event.getLevel();
        BlockPos pos = event.getPos();
        Direction face = event.getFace();

        // 检查玩家手持的是否是炮塔方块
        Block heldBlock = Block.byItem(player.getMainHandItem().getItem());
        boolean isLaserTurret = heldBlock instanceof LaserTurretBlock;
        boolean isFlameThrower = heldBlock instanceof FlameThrowerTurretBlock;

        if (!isLaserTurret && !isFlameThrower) {
            return;
        }

        // 检查点击的方块是否是能量底座
        BlockState clickedState = level.getBlockState(pos);
        if (!(clickedState.getBlock() instanceof EnergyPedestalBlock)) {
            // 不是能量底座，取消放置
            event.setCanceled(true);
            if (level.isClientSide) {
                player.displayClientMessage(
                    Component.translatable("message.integratedindustrialcraft.turret_needs_pedestal"),
                    true
                );
            }
            return;
        }

        // 火焰喷射器特殊限制：只能放置在上面或下面
        if (isFlameThrower && face != Direction.UP && face != Direction.DOWN) {
            event.setCanceled(true);
            if (level.isClientSide) {
                player.displayClientMessage(
                    Component.translatable("message.integratedindustrialcraft.flamethrower_vertical_only"),
                    true
                );
            }
            return;
        }

        // 检查是否在能量输入面
        BlockEntity blockEntity = level.getBlockEntity(pos);
        if (blockEntity instanceof EnergyPedestalBlockEntity pedestal) {
            if (!pedestal.canPlaceTurretOnSide(face)) {
                // 在能量输入面，不能放置炮塔
                event.setCanceled(true);
                if (level.isClientSide) {
                    player.displayClientMessage(
                        Component.translatable("message.integratedindustrialcraft.turret_wrong_side"),
                        true
                    );
                }
            }
        }
    }
}
