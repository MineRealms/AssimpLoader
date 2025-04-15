package cn.minerealms.iic.ai;

import cn.minerealms.iic.industrial.GTIntegration;
import cn.minerealms.iic.industrial.PollutionManager;
import net.minecraft.core.BlockPos;
import net.minecraft.world.entity.ai.goal.Goal;
import net.minecraft.world.entity.monster.Creeper;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.block.entity.BlockEntity;

import java.util.EnumSet;

public class CreeperTargetMachineGoal extends Goal {

    private final Creeper creeper;
    private BlockPos targetMachine;
    private int findCooldown = 0;

    // 污染阈值：HV 阶段 Creeper 才会攻击机器
    // 比僵尸更高的阈值，因为 Creeper 会炸毁机器
    private static final double HV_POLLUTION_THRESHOLD = 120.0;

    public CreeperTargetMachineGoal(Creeper creeper) {
        this.creeper = creeper;
        this.setFlags(EnumSet.of(Goal.Flag.MOVE, Goal.Flag.LOOK));
    }

    @Override
    public boolean canUse() {
        if (--findCooldown > 0) return false;
        this.findCooldown = 40;

        // 检查当前区块的污染等级
        ChunkPos chunkPos = new ChunkPos(this.creeper.blockPosition());
        double localPollution = PollutionManager.getTemporaryPollution(chunkPos);

        // HV 阶段：污染不够高，不触发
        if (localPollution < HV_POLLUTION_THRESHOLD) {
            return false;
        }

        BlockPos bestPos = null;
        double bestDist = Double.MAX_VALUE;
        BlockPos center = this.creeper.blockPosition();

        for (BlockPos pos : BlockPos.betweenClosed(center.offset(-16, -4, -16), center.offset(16, 4, 16))) {
            BlockEntity be = this.creeper.level().getBlockEntity(pos);
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
        return this.targetMachine != null && !this.creeper.isIgnited() && GTIntegration.isGTMachine(this.creeper.level().getBlockEntity(this.targetMachine));
    }

    @Override
    public void start() {
        this.creeper.getNavigation().moveTo(this.targetMachine.getX(), this.targetMachine.getY(), this.targetMachine.getZ(), 1.5);
    }

    @Override
    public void stop() {
        this.targetMachine = null;
        this.creeper.getNavigation().stop();
    }

    @Override
    public void tick() {
        if (this.targetMachine == null) return;

        double dist = this.creeper.distanceToSqr(this.targetMachine.getX() + 0.5, this.targetMachine.getY(), this.targetMachine.getZ() + 0.5);
        if (dist < 9.0) {
            this.creeper.ignite();
        } else {
            this.creeper.getNavigation().moveTo(this.targetMachine.getX(), this.targetMachine.getY(), this.targetMachine.getZ(), 1.5);
        }
    }
}