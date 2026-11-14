package cn.minerealms.assimploader.vehicle;

import net.minecraft.util.Mth;

/** Small server-side, tick-based vehicle model. Speed is in blocks per tick. */
public final class ArmoredCarPhysics {
    public static final float MAX_FORWARD_SPEED = 0.32f;
    public static final float MAX_REVERSE_SPEED = 0.12f;
    public static final float WHEEL_RADIUS = 0.38618326f * ArmoredCarEntity.MODEL_SCALE;
    public static final float MASS_KG = 7500.0f;
    private static final float ENGINE_ACCELERATION = 11400.0f / MASS_KG / 400.0f;
    private static final float BRAKE_DECELERATION = 36000.0f / MASS_KG / 400.0f;
    private static final float HANDBRAKE_DECELERATION = 60000.0f / MASS_KG / 400.0f;
    private static final float GROUND_COAST_MULTIPLIER = 0.989f;
    private static final float AIR_COAST_MULTIPLIER = 0.998f;
    private static final float GROUND_LATERAL_GRIP = 0.72f;
    private static final float AIR_LATERAL_GRIP = 0.995f;
    private static final float MAX_STEERING_ANGLE = 0.48f;
    private static final float STEERING_RESPONSE = 0.045f;
    private static final float MAX_YAW_PER_TICK = 0.045f;
    private static final float WHEELBASE = 2.56908f * ArmoredCarEntity.MODEL_SCALE;
    private static final float STOP_EPSILON = 0.0015f;

    private ArmoredCarPhysics() {}

    public record Motion(float forwardSpeed, float lateralSpeed, float steeringAngle,
                         float yawDeltaRadians) {}

    public static Motion step(float forwardSpeed, float lateralSpeed, float steeringAngle,
                              float throttle, float steeringInput, boolean handbrake,
                              boolean grounded) {
        forwardSpeed = finite(forwardSpeed);
        lateralSpeed = finite(lateralSpeed);
        steeringAngle = finite(steeringAngle);
        throttle = Mth.clamp(finite(throttle), -1.0f, 1.0f);
        steeringInput = Mth.clamp(finite(steeringInput), -1.0f, 1.0f);

        if (!grounded || throttle == 0.0f) {
            forwardSpeed *= grounded ? GROUND_COAST_MULTIPLIER : AIR_COAST_MULTIPLIER;
        } else if (forwardSpeed != 0.0f && Math.signum(forwardSpeed) != Math.signum(throttle)) {
            // An opposite direction request brakes first; reverse power starts on a later tick.
            forwardSpeed = approach(forwardSpeed, 0.0f, BRAKE_DECELERATION);
        } else {
            forwardSpeed += throttle * ENGINE_ACCELERATION;
        }

        if (handbrake && grounded) forwardSpeed = approach(forwardSpeed, 0.0f, HANDBRAKE_DECELERATION);
        forwardSpeed = Mth.clamp(forwardSpeed, -MAX_REVERSE_SPEED, MAX_FORWARD_SPEED);
        if (Math.abs(forwardSpeed) < STOP_EPSILON) forwardSpeed = 0.0f;

        float speedFactor = Mth.clamp(Math.abs(forwardSpeed) / MAX_FORWARD_SPEED, 0.0f, 1.0f);
        float targetSteering = steeringInput * MAX_STEERING_ANGLE * (1.0f - speedFactor * 0.38f);
        steeringAngle = approach(steeringAngle, targetSteering, STEERING_RESPONSE);
        if (Math.abs(steeringAngle) < 0.002f) steeringAngle = 0.0f;
        lateralSpeed *= grounded ? GROUND_LATERAL_GRIP : AIR_LATERAL_GRIP;
        if (Math.abs(lateralSpeed) < STOP_EPSILON) lateralSpeed = 0.0f;

        float yawDelta = grounded ? Mth.clamp(-forwardSpeed / WHEELBASE * (float) Math.tan(steeringAngle),
                -MAX_YAW_PER_TICK, MAX_YAW_PER_TICK) : 0.0f;
        return new Motion(forwardSpeed, lateralSpeed, steeringAngle, yawDelta);
    }

    public static float wheelRollForDistance(float currentRoll, double signedForwardDistance) {
        if (!Double.isFinite(signedForwardDistance)) return finite(currentRoll);
        return finite(currentRoll) + (float) (signedForwardDistance / WHEEL_RADIUS);
    }

    private static float approach(float value, float target, float amount) {
        if (value < target) return Math.min(value + amount, target);
        if (value > target) return Math.max(value - amount, target);
        return target;
    }

    private static float finite(float value) { return Float.isFinite(value) ? value : 0.0f; }
}
