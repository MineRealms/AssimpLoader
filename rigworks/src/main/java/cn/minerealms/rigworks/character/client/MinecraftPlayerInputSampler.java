package cn.minerealms.rigworks.character.client;

import cn.minerealms.rigworks.character.core.PlayerVisualInput;
import cn.minerealms.rigworks.character.core.PlayerActionState;
import cn.minerealms.rigworks.character.core.PlayerAirState;
import net.minecraft.client.player.AbstractClientPlayer;
import net.minecraft.world.phys.Vec3;

/** Reads client observations only. Movement, poses and collision dimensions remain owned by Minecraft. */
final class MinecraftPlayerInputSampler {
    private Vec3 previousPosition;
    private int previousTick = -1;
    private boolean previousSwing;
    private int previousSwingTime;
    private long swingGeneration;
    private final PlayerAirState air = new PlayerAirState();
    private PlayerVisualInput last;

    PlayerVisualInput sample(AbstractClientPlayer player) {
        return sample(player, player.getAbilities().flying);
    }

    PlayerVisualInput sample(AbstractClientPlayer player, boolean flying) {
        if (last != null && last.sequence() == player.tickCount) return last;
        Vec3 position = player.position();
        int elapsed = player.tickCount - previousTick;
        Vec3 velocity = player.getDeltaMovement().scale(20);
        if (previousPosition != null && elapsed > 0) {
            Vec3 displacement = position.subtract(previousPosition);
            velocity = displacement.lengthSqr() > 16 ? Vec3.ZERO : displacement.scale(20.0 / elapsed);
        }
        previousPosition = position;
        previousTick = player.tickCount;
        double yaw = Math.toRadians(player.yBodyRot);
        double forward = -velocity.x * Math.sin(yaw) + velocity.z * Math.cos(yaw);
        double right = -velocity.x * Math.cos(yaw) - velocity.z * Math.sin(yaw);
        boolean swingEvent = player.swinging && (!previousSwing || player.swingTime < previousSwingTime);
        if(swingEvent)swingGeneration++;
        previousSwing = player.swinging;
        previousSwingTime = player.swingTime;
        var phase = air.update(Math.max(0, player.tickCount), player.onGround(), velocity.y,
                !player.isPassenger() && !player.isSwimming() && !player.isFallFlying() && !flying
                && !player.onClimbable() && !player.isInWater() && !player.isInLava() && player.isAlive() && !player.isSleeping());
        var state = new PlayerActionState(player.getPose().name().toLowerCase(java.util.Locale.ROOT), phase,
                player.onClimbable(), player.isInWater(), player.isUnderWater(), player.isInLava(),
                player.isAutoSpinAttack(), player.isFullyFrozen(), Math.max(0, player.hurtTime),
                player.getUseItem().getUseAnimation().name().toLowerCase(java.util.Locale.ROOT),
                !player.getItemInHand(player.getMainArm() == net.minecraft.world.entity.HumanoidArm.LEFT
                        ? net.minecraft.world.InteractionHand.MAIN_HAND : net.minecraft.world.InteractionHand.OFF_HAND).isEmpty(),
                !player.getItemInHand(player.getMainArm() == net.minecraft.world.entity.HumanoidArm.RIGHT
                        ? net.minecraft.world.InteractionHand.MAIN_HAND : net.minecraft.world.InteractionHand.OFF_HAND).isEmpty());
        return last = PlayerVisualInput.builder(Math.max(0, player.tickCount), Math.max(0, player.tickCount) * .05)
                .motion(forward, right, velocity.y)
                .orientation(player.yBodyRot, player.yHeadRot, player.getXRot())
                .onGround(player.onGround()).crouching(player.isCrouching()).sprinting(player.isSprinting())
                .jumping(phase == PlayerActionState.AirPhase.TAKEOFF || phase == PlayerActionState.AirPhase.RISING)
                .falling(!player.onGround() && velocity.y < -.01)
                .swimming(player.isSwimming()).flying(flying).elytra(player.isFallFlying())
                .riding(player.isPassenger()).sleeping(player.isSleeping()).dead(!player.isAlive())
                .usingItem(player.isUsingItem()).swinging(swingEvent).actionState(state)
                .swingAction(swingGeneration,player.swinging,swingEvent?0:Math.max(0,Math.min(1,player.attackAnim))).build();
    }
}
