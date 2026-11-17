package cn.minerealms.rigworks.character.client;

import cn.minerealms.rigworks.character.core.PlayerVisualInput;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.world.phys.Vec3;

/** Reads client observations only. Movement, poses and collision dimensions remain owned by Minecraft. */
final class MinecraftPlayerInputSampler {
    private Vec3 previousPosition;
    private int previousTick = -1;
    private boolean previousSwing;
    private int previousSwingTime;
    private long swingGeneration;

    PlayerVisualInput sample(LocalPlayer player) {
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
        return PlayerVisualInput.builder(Math.max(0, player.tickCount), Math.max(0, player.tickCount) * .05)
                .motion(forward, right, velocity.y)
                .orientation(player.yBodyRot, player.yHeadRot, player.getXRot())
                .onGround(player.onGround()).crouching(player.isCrouching()).sprinting(player.isSprinting())
                .jumping(player.input != null && player.input.jumping)
                .falling(!player.onGround() && velocity.y < -.01)
                .swimming(player.isSwimming()).flying(player.getAbilities().flying).elytra(player.isFallFlying())
                .riding(player.isPassenger()).sleeping(player.isSleeping()).dead(!player.isAlive())
                .usingItem(player.isUsingItem()).swinging(swingEvent)
                .swingAction(swingGeneration,player.swinging,swingEvent?0:Math.max(0,Math.min(1,player.attackAnim))).build();
    }
}
