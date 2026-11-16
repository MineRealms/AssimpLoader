package cn.minerealms.assimploader.character.core;

/** One client observation. Velocities are blocks/second in body-relative forward/right/up axes; angles are degrees. */
public record PlayerVisualInput(long sequence, double timeSeconds,
                                double forwardVelocity, double strafeVelocity, double verticalVelocity,
                                float bodyYawDegrees, float headYawDegrees, float headPitchDegrees,
                                boolean onGround, boolean crouching, boolean sprinting,
                                boolean jumping, boolean falling, boolean swimming, boolean flying,
                                boolean elytra, boolean riding, boolean sleeping, boolean dead,
                                boolean usingItem, boolean swinging) {
    public PlayerVisualInput {
        if (sequence < 0 || !Double.isFinite(timeSeconds) || timeSeconds < 0
                || !Double.isFinite(forwardVelocity) || !Double.isFinite(strafeVelocity)
                || !Double.isFinite(verticalVelocity) || !Double.isFinite(Math.hypot(forwardVelocity, strafeVelocity))
                || !Float.isFinite(bodyYawDegrees) || !Float.isFinite(headYawDegrees) || !Float.isFinite(headPitchDegrees))
            throw new IllegalArgumentException("Player visual input requires a non-negative sequence/time and finite motion/angles");
    }

    public double horizontalSpeed() { return Math.hypot(forwardVelocity, strafeVelocity); }
    public static Builder builder(long sequence, double timeSeconds) { return new Builder(sequence, timeSeconds); }

    public static final class Builder {
        private final long sequence;
        private final double time;
        private double forward, strafe, vertical;
        private float bodyYaw, headYaw, headPitch;
        private boolean onGround = true, crouching, sprinting, jumping, falling, swimming, flying,
                elytra, riding, sleeping, dead, usingItem, swinging;

        private Builder(long sequence, double time) { this.sequence = sequence; this.time = time; }
        public Builder motion(double forward, double strafe, double vertical) {
            this.forward = forward; this.strafe = strafe; this.vertical = vertical; return this;
        }
        public Builder orientation(float bodyYaw, float headYaw, float headPitch) {
            this.bodyYaw = bodyYaw; this.headYaw = headYaw; this.headPitch = headPitch; return this;
        }
        public Builder onGround(boolean value) { onGround = value; return this; }
        public Builder crouching(boolean value) { crouching = value; return this; }
        public Builder sprinting(boolean value) { sprinting = value; return this; }
        public Builder jumping(boolean value) { jumping = value; return this; }
        public Builder falling(boolean value) { falling = value; return this; }
        public Builder swimming(boolean value) { swimming = value; return this; }
        public Builder flying(boolean value) { flying = value; return this; }
        public Builder elytra(boolean value) { elytra = value; return this; }
        public Builder riding(boolean value) { riding = value; return this; }
        public Builder sleeping(boolean value) { sleeping = value; return this; }
        public Builder dead(boolean value) { dead = value; return this; }
        public Builder usingItem(boolean value) { usingItem = value; return this; }
        public Builder swinging(boolean value) { swinging = value; return this; }
        public PlayerVisualInput build() {
            return new PlayerVisualInput(sequence, time, forward, strafe, vertical, bodyYaw, headYaw, headPitch,
                    onGround, crouching, sprinting, jumping, falling, swimming, flying, elytra, riding,
                    sleeping, dead, usingItem, swinging);
        }
    }
}
