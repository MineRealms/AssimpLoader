package cn.minerealms.rigworks.character.core;

import java.util.Locale;
import java.util.Objects;

/** Semantic game observations shared by native, clip and script policies. No render-thread sampling. */
public record PlayerActionState(String pose, AirPhase airPhase, boolean climbing, boolean inWater,
                                boolean underWater, boolean inLava, boolean riptide, boolean frozen,
                                int hurtTicks, String useAnimation, boolean leftHeld, boolean rightHeld) {
    public enum AirPhase { GROUNDED, TAKEOFF, RISING, APEX, FALLING, LANDING }

    public static final PlayerActionState DEFAULT = new PlayerActionState("standing", AirPhase.GROUNDED,
            false, false, false, false, false, false, 0, "none", false, false);

    public PlayerActionState {
        Objects.requireNonNull(pose); Objects.requireNonNull(airPhase); Objects.requireNonNull(useAnimation);
        if (!pose.matches("[a-z_]{1,40}") || !useAnimation.matches("[a-z_]{1,40}") || hurtTicks < 0)
            throw new IllegalArgumentException("Invalid player action state");
    }

    public boolean crawling(boolean swimming) { return pose.equals("swimming") && !swimming; }
    public String airPhaseName() { return airPhase.name().toLowerCase(Locale.ROOT); }
}
