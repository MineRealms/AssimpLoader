package cn.minerealms.rigworks.character.client;

import cn.minerealms.assimploader.animation.AnimationState;
import cn.minerealms.assimploader.animation.PoseSnapshot;
import net.minecraft.client.Minecraft;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.world.entity.HumanoidArm;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.phys.Vec3;
import net.minecraft.util.Mth;

/** A temporary visual snapshot, independent of the authoritative player and inventory. */
public final class CharacterWorkbenchPose {
    private static Lease current;

    private CharacterWorkbenchPose() {}

    public static Lease freeze() {
        if (current != null) current.close();
        var minecraft = Minecraft.getInstance();
        var animation = LocalPlayerCharacter.currentAnimation();
        if (minecraft.player == null || animation == null || animation.isClosed())
            throw new IllegalStateException("No character pose is ready to freeze");
        current = new Lease(animation, minecraft.player, minecraft.getFrameTime());
        return current;
    }

    public static boolean frozen() {
        if (current == null) return false;
        if (!current.valid()) { current.close(); return false; }
        return true;
    }

    static PoseSnapshot pose(AnimationState animation, float partialTick) {
        return frozen() && current.animation == animation ? current.pose : animation.getRenderPose(partialTick);
    }

    static float yaw(float normal) { return frozen() ? current.bodyYaw : normal; }

    static Vec3 position(LocalPlayer player, float partialTick) {
        if (frozen() && current.player == player) return current.position;
        return interpolatedPosition(player, partialTick);
    }

    static Vec3 bodyFocus(LocalPlayer player, float partialTick) {
        float height = frozen() && current.player == player ? current.height : player.getBbHeight();
        return position(player, partialTick).add(0, height * .55, 0);
    }

    static Vec3 interpolatedPosition(LocalPlayer player, float partialTick) {
        return new Vec3(Mth.lerp(partialTick, player.xo, player.getX()), Mth.lerp(partialTick, player.yo, player.getY()),
                Mth.lerp(partialTick, player.zo, player.getZ()));
    }

    static ItemStack item(LocalPlayer player, HumanoidArm arm) {
        if (frozen() && current.player == player) return arm == HumanoidArm.RIGHT ? current.right : current.left;
        return player.getMainArm() == arm ? player.getMainHandItem() : player.getOffhandItem();
    }

    public static final class Lease implements AutoCloseable {
        private final AnimationState animation;
        private final LocalPlayer player;
        private final Object level;
        private final PoseSnapshot pose;
        private final float bodyYaw;
        private final Vec3 position;
        private final float height;
        private final ItemStack right, left;
        private final boolean paused;
        private boolean closed;

        private Lease(AnimationState animation, LocalPlayer player, float partialTick) {
            this.animation = animation;
            this.player = player;
            level = player.level();
            pose = animation.getPoseSnapshot();
            bodyYaw = net.minecraft.util.Mth.rotLerp(partialTick, player.yBodyRotO, player.yBodyRot);
            position = interpolatedPosition(player, partialTick);
            height = player.getBbHeight();
            right = (player.getMainArm() == HumanoidArm.RIGHT ? player.getMainHandItem() : player.getOffhandItem()).copy();
            left = (player.getMainArm() == HumanoidArm.LEFT ? player.getMainHandItem() : player.getOffhandItem()).copy();
            paused = animation.isPaused();
            animation.pause();
        }

        public boolean valid() {
            var minecraft = Minecraft.getInstance();
            return !closed && current == this && minecraft.player == player && minecraft.level == level
                    && !player.isRemoved() && LocalPlayerCharacter.currentAnimation() == animation && !animation.isClosed();
        }

        @Override public void close() {
            if (closed) return;
            closed = true;
            if (current == this) current = null;
            if (!animation.isClosed() && LocalPlayerCharacter.currentAnimation() == animation && !paused) animation.resume();
        }
    }
}
