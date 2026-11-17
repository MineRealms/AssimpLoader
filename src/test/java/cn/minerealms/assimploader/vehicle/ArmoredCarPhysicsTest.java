package cn.minerealms.assimpshowcase.vehicle;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class ArmoredCarPhysicsTest {
    @Test
    void massProducesGradualAccelerationAndSpeedLimits() {
        var motion = ArmoredCarPhysics.step(0, 0, 0, 1, 0, false, true);
        assertTrue(motion.forwardSpeed() > 0);
        assertTrue(motion.forwardSpeed() < 0.005f);
        for (int i = 0; i < 500; i++)
            motion = ArmoredCarPhysics.step(motion.forwardSpeed(), 0, 0, 1, 0, false, true);
        assertEquals(ArmoredCarPhysics.MAX_FORWARD_SPEED, motion.forwardSpeed());
    }

    @Test
    void reverseBrakesToZeroBeforeApplyingReversePower() {
        var motion = ArmoredCarPhysics.step(0.02f, 0, 0, -1, 0, false, true);
        assertTrue(motion.forwardSpeed() > 0);
        motion = ArmoredCarPhysics.step(motion.forwardSpeed(), 0, 0, -1, 0, false, true);
        assertEquals(0, motion.forwardSpeed());
        motion = ArmoredCarPhysics.step(motion.forwardSpeed(), 0, 0, -1, 0, false, true);
        assertTrue(motion.forwardSpeed() < 0);
        for (int i = 0; i < 500; i++)
            motion = ArmoredCarPhysics.step(motion.forwardSpeed(), 0, 0, -1, 0, false, true);
        assertEquals(-ArmoredCarPhysics.MAX_REVERSE_SPEED, motion.forwardSpeed());
    }

    @Test
    void coastingRetainsInertiaAndHandbrakeStopsMoreQuickly() {
        var coast = ArmoredCarPhysics.step(0.2f, 0.05f, 0, 0, 0, false, true);
        var brake = ArmoredCarPhysics.step(0.2f, 0.05f, 0, 0, 0, true, true);
        assertTrue(coast.forwardSpeed() < 0.2f && coast.forwardSpeed() > 0.19f);
        assertTrue(brake.forwardSpeed() < coast.forwardSpeed());
        assertTrue(coast.lateralSpeed() < 0.05f);
    }

    @Test
    void steeringIsGradualAndReversingChangesTurnDirection() {
        var forward = ArmoredCarPhysics.step(0.15f, 0, 0, 0, 1, false, true);
        var reverse = ArmoredCarPhysics.step(-0.10f, 0, 0, 0, 1, false, true);
        assertTrue(forward.steeringAngle() > 0 && forward.steeringAngle() < 0.1f);
        assertTrue(forward.yawDeltaRadians() < 0);
        assertTrue(reverse.yawDeltaRadians() > 0);
        assertEquals(0, ArmoredCarPhysics.step(0, 0, 0, 0, 1, false, true).yawDeltaRadians(), 1.0e-8f);
    }

    @Test
    void wheelsFollowRealDistanceAndStopOnZeroDisplacement() {
        assertEquals(2.0f, ArmoredCarPhysics.wheelRollForDistance(2.0f, 0));
        assertEquals(2.5f, ArmoredCarPhysics.wheelRollForDistance(2.0f, ArmoredCarPhysics.WHEEL_RADIUS * 0.5));
        assertEquals(1.5f, ArmoredCarPhysics.wheelRollForDistance(2.0f, -ArmoredCarPhysics.WHEEL_RADIUS * 0.5));
    }

    @Test
    void airborneControlsDoNotCreateTractionOrYaw() {
        var motion = ArmoredCarPhysics.step(0.1f, 0, 0, 1, 1, false, false);
        assertTrue(motion.forwardSpeed() < 0.1f);
        assertEquals(0, motion.yawDeltaRadians());
    }
}
