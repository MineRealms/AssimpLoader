package cn.minerealms.rigworks.character.core;

import static cn.minerealms.rigworks.character.core.PlayerActionState.AirPhase.*;

/** Actual contacts drive air phases; holding jump does not retrigger a takeoff. */
public final class PlayerAirState {
    private long tick = -1;
    private boolean grounded = true, initialized;
    private int landingTicks;
    private PlayerActionState.AirPhase phase = GROUNDED;

    public PlayerActionState.AirPhase update(long sequence, boolean onGround, double verticalVelocity, boolean enabled) {
        if (!Double.isFinite(verticalVelocity) || sequence < 0) throw new IllegalArgumentException("Invalid air observation");
        if (sequence == tick) return phase;
        if (sequence < tick) reset();
        long elapsed = tick < 0 ? 1 : Math.max(1, Math.min(4, sequence - tick));
        tick = sequence;
        if (!enabled) { grounded = onGround; initialized = true; landingTicks = 0; return phase = GROUNDED; }
        if (onGround) {
            if (initialized && !grounded) landingTicks = 3;
            else landingTicks = Math.max(0, landingTicks - (int) elapsed);
            phase = landingTicks > 0 ? LANDING : GROUNDED;
        } else {
            landingTicks = 0;
            phase = initialized && grounded && verticalVelocity > .1 ? TAKEOFF
                    : verticalVelocity > .1 ? RISING : verticalVelocity < -.1 ? FALLING : APEX;
        }
        grounded = onGround; initialized = true;
        return phase;
    }

    public void reset() { tick = -1; grounded = true; initialized = false; landingTicks = 0; phase = GROUNDED; }
}
