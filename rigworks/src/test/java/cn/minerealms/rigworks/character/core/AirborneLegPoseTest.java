package cn.minerealms.rigworks.character.core;

import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class AirborneLegPoseTest {
    @Test void repeatedMovingHopsKeepContactTransitionContinuous() {
        var state = new AirborneLegPose();
        assertEquals(0, state.update(0, true, true));
        for (int tick = 1; tick <= 8; tick++) state.update(tick, false, true);
        assertEquals(1, state.update(9, false, true));
        assertEquals(1, state.update(10, true, true));
        assertEquals(1, state.update(11, false, true));
        assertEquals(1, state.update(12, true, true));
        assertEquals(1, state.update(13, true, true));
        assertEquals(.8f, state.update(14, true, true), 1e-6);
        assertEquals(.8f, state.update(14, false, true), 1e-6, "A second sampler must not advance the same tick");
        assertEquals(0, state.update(15, false, false), "Swimming/flying must release ordinary jump pose");
    }

    @Test void kneeBendRemainsBoundedForBothFlightPhases() {
        assertTrue(AirborneLegPose.knee(.42) > AirborneLegPose.knee(-.42));
        assertEquals(.57f, AirborneLegPose.knee(100), 1e-6);
        assertEquals(.33f, AirborneLegPose.knee(-100), 1e-6);
        assertEquals(.45f, AirborneLegPose.knee(Double.NaN), 1e-6);
    }

    @Test void sustainedContactEventuallyReturnsToTheUnmodifiedGroundPose() {
        var state = new AirborneLegPose();
        for (int tick = 0; tick < 10; tick++) state.update(tick, false, true);
        assertEquals(1, state.update(10, true, true));
        assertEquals(1, state.update(11, true, true));
        for (int tick = 12; tick < 20; tick++) state.update(tick, true, true);
        assertEquals(0, state.update(20, true, true), 1e-6);
    }

    @Test void disabledOrRewoundSamplersCannotLeakAnOldAirborneWeight() {
        var state = new AirborneLegPose();
        for (int tick = 20; tick < 25; tick++) state.update(tick, false, true);
        assertEquals(1, state.update(25, false, true));
        assertEquals(0, state.update(25, true, false), "A special state cancels even on the same sampled tick");
        assertEquals(0, state.update(26, true, true));
        for (int tick = 27; tick < 35; tick++) state.update(tick, false, true);
        assertEquals(0, state.update(0, true, true), "Respawn/time rewind starts from the observed ground pose");
        state.update(1, false, true);
        state.reset();
        assertEquals(0, state.update(2, true, true));
    }
}
