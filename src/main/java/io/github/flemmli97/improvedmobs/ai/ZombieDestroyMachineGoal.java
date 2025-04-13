package io.github.flemmli97.improvedmobs.ai;

import io.github.flemmli97.improvedmobs.industrial.GTIntegration;
import net.minecraft.core.BlockPos;
import net.minecraft.world.entity.ai.goal.Goal;
import net.minecraft.world.entity.monster.Zombie;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.entity.BlockEntity;

import java.util.EnumSet;

import io.github.flemmli97.improvedmobs.forge.network.PacketHandler;
import io.github.flemmli97.improvedmobs.industrial.TriAxisConfig;

public class ZombieDestroyMachineGoal extends Goal {

    private final Zombie zombie;
    private BlockPos targetMachine;
    private int findCooldown = 0;

    public ZombieDestroyMachineGoal(Zombie zombie) {
        this.zombie = zombie;
        this.setFlags(EnumSet.of(Goal.Flag.MOVE, Goal.Flag.LOOK));
    }

    @Override
    public boolean canUse() {
        if (--findCooldown > 0) return false;
        this.findCooldown = 40; // check every 2 seconds
        
        // Find nearest GT machine within 16 blocks
        BlockPos bestPos = null;
        double bestDist = Double.MAX_VALUE;
        BlockPos center = this.zombie.blockPosition();
        
        for (BlockPos pos : BlockPos.betweenClosed(center.offset(-16, -4, -16), center.offset(16, 4, 16))) {
            BlockEntity be = this.zombie.level().getBlockEntity(pos);
            if (GTIntegration.isGTMachine(be)) {
                double dist = pos.distSqr(center);
                if (dist < bestDist) {
                    bestDist = dist;
                    bestPos = pos.immutable();
                }
            }
        }
        
        if (bestPos != null) {
            this.targetMachine = bestPos;
            return true;
        }
        return false;
    }

    @Override
    public boolean canContinueToUse() {
        return this.targetMachine != null && GTIntegration.isGTMachine(this.zombie.level().getBlockEntity(this.targetMachine));
    }

    @Override
    public void start() {
        this.zombie.getNavigation().moveTo(this.targetMachine.getX(), this.targetMachine.getY(), this.targetMachine.getZ(), 1.2);
    }

    @Override
    public void stop() {
        this.targetMachine = null;
        this.zombie.getNavigation().stop();
    }

    @Override
    public void tick() {
        if (this.targetMachine == null) return;
        
        // 发送调试连线封包
        if (TriAxisConfig.enableDebugLines) {
            if (this.zombie.tickCount % 5 == 0) { // 每5 tick发送一次以保持连线
                PacketHandler.sendDebugLineToAll(this.zombie.getId(), this.targetMachine, this.zombie.level().getServer());
            }
        }
        
        double dist = this.zombie.distanceToSqr(this.targetMachine.getX() + 0.5, this.targetMachine.getY(), this.targetMachine.getZ() + 0.5);
        if (dist < 4.0) {
            // Adjacent to machine, start breaking or TNT
            if (this.zombie.getMainHandItem().isEmpty()) {
                this.zombie.setItemInHand(net.minecraft.world.InteractionHand.MAIN_HAND, new ItemStack(Items.IRON_PICKAXE));
            }
            // Simply destroy it after some ticks
            this.zombie.swing(net.minecraft.world.InteractionHand.MAIN_HAND);
            if (this.zombie.getRandom().nextInt(40) == 0) { // 2 seconds to break
                this.zombie.level().destroyBlock(this.targetMachine, true);
                this.targetMachine = null;
            }
        } else {
            this.zombie.getNavigation().moveTo(this.targetMachine.getX(), this.targetMachine.getY(), this.targetMachine.getZ(), 1.2);
        }
    }
}