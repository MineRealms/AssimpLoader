package cn.minerealms.iic.integration.alexscaves;

import cn.minerealms.iic.integration.gregtech.GTIntegration;
import net.minecraft.core.BlockPos;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.ai.goal.Goal;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.chunk.LevelChunk;

import java.util.ArrayList;
import java.util.EnumSet;
import java.util.List;

/**
 * AI Goal for Nucleeper to target and move towards GT machines.
 *
 * <p>This goal makes Nucleepers prioritize attacking industrial machines,
 * especially high-tier ones. The Nucleeper will navigate to the nearest
 * machine and explode when it gets close, potentially destroying the machine.
 *
 * <h2>Behavior</h2>
 * <ul>
 *   <li>Scans for GT machines in a 32-block radius</li>
 *   <li>Prioritizes higher voltage tier machines</li>
 *   <li>Navigates to the machine using pathfinding</li>
 *   <li>Triggers explosion when within 3 blocks</li>
 * </ul>
 *
 * <h2>Priority</h2>
 * <p>This goal should be added with priority 1 (highest) to ensure
 * Nucleepers prioritize machines over players.
 *
 * @author I3C Team
 * @since 1.0.0
 */
public class NucleeperTargetMachineGoal extends Goal {

    private final Mob mob;
    private BlockPos targetMachine;
    private int scanCooldown = 0;
    private int stuckTicks = 0;
    private BlockPos lastPos;

    /** Scan radius for finding machines (in blocks) */
    private static final int SCAN_RADIUS = 32;

    /** Distance to trigger explosion (in blocks) */
    private static final double EXPLOSION_DISTANCE = 3.0;

    /** Cooldown between scans (in ticks) */
    private static final int SCAN_COOLDOWN = 40; // 2 seconds

    /** Movement speed multiplier when targeting machines */
    private static final double SPEED_MULTIPLIER = 1.2;

    public NucleeperTargetMachineGoal(Mob mob) {
        this.mob = mob;
        this.setFlags(EnumSet.of(Goal.Flag.MOVE, Goal.Flag.LOOK));
    }

    @Override
    public boolean canUse() {
        // Only scan periodically to save performance
        if (scanCooldown > 0) {
            scanCooldown--;
            return targetMachine != null && isValidTarget(targetMachine);
        }

        scanCooldown = SCAN_COOLDOWN;

        // Find nearest machine
        targetMachine = findNearestMachine();
        return targetMachine != null;
    }

    @Override
    public boolean canContinueToUse() {
        if (targetMachine == null) {
            return false;
        }

        // Check if target is still valid
        if (!isValidTarget(targetMachine)) {
            targetMachine = null;
            return false;
        }

        // Check if stuck
        if (lastPos != null && lastPos.equals(mob.blockPosition())) {
            stuckTicks++;
            if (stuckTicks > 100) { // 5 seconds
                targetMachine = null;
                stuckTicks = 0;
                return false;
            }
        } else {
            stuckTicks = 0;
        }
        lastPos = mob.blockPosition();

        return true;
    }

    @Override
    public void start() {
        if (targetMachine != null) {
            mob.getNavigation().moveTo(
                targetMachine.getX() + 0.5,
                targetMachine.getY(),
                targetMachine.getZ() + 0.5,
                SPEED_MULTIPLIER
            );
        }
    }

    @Override
    public void stop() {
        targetMachine = null;
        stuckTicks = 0;
        lastPos = null;
        mob.getNavigation().stop();
    }

    @Override
    public void tick() {
        if (targetMachine == null) {
            return;
        }

        // Look at target
        mob.getLookControl().setLookAt(
            targetMachine.getX() + 0.5,
            targetMachine.getY() + 0.5,
            targetMachine.getZ() + 0.5
        );

        // Check distance to target
        double distance = mob.position().distanceTo(
            new net.minecraft.world.phys.Vec3(
                targetMachine.getX() + 0.5,
                targetMachine.getY() + 0.5,
                targetMachine.getZ() + 0.5
            )
        );

        // Trigger explosion when close enough
        if (distance <= EXPLOSION_DISTANCE) {
            triggerExplosion();
            return;
        }

        // Update navigation every 20 ticks
        if (mob.tickCount % 20 == 0) {
            mob.getNavigation().moveTo(
                targetMachine.getX() + 0.5,
                targetMachine.getY(),
                targetMachine.getZ() + 0.5,
                SPEED_MULTIPLIER
            );
        }
    }

    /**
     * Finds the nearest GT machine within scan radius.
     * Prioritizes higher voltage tier machines.
     *
     * @return the position of the nearest machine, or null if none found
     */
    private BlockPos findNearestMachine() {
        if (!GTIntegration.isGTLoaded()) {
            return null;
        }

        BlockPos mobPos = mob.blockPosition();
        List<MachineInfo> machines = new ArrayList<>();

        // Scan chunks in radius
        int chunkRadius = SCAN_RADIUS / 16 + 1;
        int mobChunkX = mobPos.getX() >> 4;
        int mobChunkZ = mobPos.getZ() >> 4;

        for (int dx = -chunkRadius; dx <= chunkRadius; dx++) {
            for (int dz = -chunkRadius; dz <= chunkRadius; dz++) {
                int chunkX = mobChunkX + dx;
                int chunkZ = mobChunkZ + dz;

                if (!mob.level().hasChunk(chunkX, chunkZ)) {
                    continue;
                }

                LevelChunk chunk = mob.level().getChunk(chunkX, chunkZ);
                for (BlockEntity be : chunk.getBlockEntities().values()) {
                    if (GTIntegration.isGTMachine(be)) {
                        BlockPos machinePos = be.getBlockPos();
                        double distance = mobPos.distSqr(machinePos);

                        // Check if within radius
                        if (distance > SCAN_RADIUS * SCAN_RADIUS) {
                            continue;
                        }

                        // Check if machine has energy or is active
                        if (!GTIntegration.hasEnergyOrActive(be)) {
                            continue;
                        }

                        int tier = GTIntegration.getVoltageTier(be);
                        machines.add(new MachineInfo(machinePos, distance, tier));
                    }
                }
            }
        }

        if (machines.isEmpty()) {
            return null;
        }

        // Sort by priority: higher tier first, then closer distance
        machines.sort((m1, m2) -> {
            // Prioritize higher tier
            if (m1.tier != m2.tier) {
                return Integer.compare(m2.tier, m1.tier);
            }
            // Then closer distance
            return Double.compare(m1.distance, m2.distance);
        });

        return machines.get(0).pos;
    }

    /**
     * Checks if a machine position is still a valid target.
     *
     * @param pos the machine position
     * @return true if valid
     */
    private boolean isValidTarget(BlockPos pos) {
        if (!mob.level().hasChunkAt(pos)) {
            return false;
        }

        BlockEntity be = mob.level().getBlockEntity(pos);
        if (be == null) {
            return false;
        }

        if (!GTIntegration.isGTMachine(be)) {
            return false;
        }

        if (!GTIntegration.hasEnergyOrActive(be)) {
            return false;
        }

        // Check if still within reasonable distance
        double distance = mob.blockPosition().distSqr(pos);
        return distance <= (SCAN_RADIUS * 2) * (SCAN_RADIUS * 2);
    }

    /**
     * Triggers the Nucleeper's explosion.
     * This method should trigger the entity's natural explosion behavior.
     */
    private void triggerExplosion() {
        // Try to trigger Nucleeper's explosion via reflection
        try {
            // Nucleeper has a method to start exploding
            // We need to set its "triggered" state
            java.lang.reflect.Method setTriggeredMethod = mob.getClass().getMethod("setTriggered", boolean.class);
            setTriggeredMethod.invoke(mob, true);
        } catch (Exception e) {
            // Fallback: just stop navigation and let natural behavior take over
            mob.getNavigation().stop();
        }
    }

    /**
     * Helper class to store machine information for sorting.
     */
    private static class MachineInfo {
        final BlockPos pos;
        final double distance;
        final int tier;

        MachineInfo(BlockPos pos, double distance, int tier) {
            this.pos = pos;
            this.distance = distance;
            this.tier = tier;
        }
    }
}
