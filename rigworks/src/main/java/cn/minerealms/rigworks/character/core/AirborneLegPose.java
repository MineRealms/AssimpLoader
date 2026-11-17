package cn.minerealms.rigworks.character.core;

/** Visual jump/contact smoothing for articulated legs; it never changes entity physics. */
public final class AirborneLegPose {
    private int lastTick = Integer.MIN_VALUE, groundedTicks = 3;
    private float weight;

    public float update(int tick, boolean onGround, boolean enabled) {
        if (!enabled) { reset(); lastTick = tick; return weight; }
        if (tick == lastTick) return weight;
        if (tick < lastTick) reset();
        lastTick = tick;
        float target;
        if (!onGround) { groundedTicks = 0; target = 1; }
        else { groundedTicks = Math.min(3, groundedTicks + 1); target = groundedTicks < 3 ? 1 : 0; }
        weight += Math.max(-.2f, Math.min(.2f, target - weight));
        return weight;
    }

    public void reset() { lastTick = Integer.MIN_VALUE; groundedTicks = 3; weight = 0; }

    /** Vertical velocity is blocks per game tick; the visual bend stays between .33 and .57 radians. */
    public static float knee(double verticalVelocity) {
        if (!Double.isFinite(verticalVelocity)) return .45f;
        return .45f + .12f * (float) Math.max(-1, Math.min(1, verticalVelocity / .42));
    }
}
