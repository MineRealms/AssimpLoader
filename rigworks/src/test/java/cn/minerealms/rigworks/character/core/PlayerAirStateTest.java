package cn.minerealms.rigworks.character.core;

import org.junit.jupiter.api.Test;
import static cn.minerealms.rigworks.character.core.PlayerActionState.AirPhase.*;
import static org.junit.jupiter.api.Assertions.*;

class PlayerAirStateTest {
    @Test void repeatedMovingHopsUseContactsRatherThanHeldInput() {
        var state = new PlayerAirState();
        assertEquals(GROUNDED, state.update(0, true, 0, true));
        for (int hop = 0; hop < 4; hop++) {
            int tick = hop * 5 + 1;
            assertEquals(TAKEOFF, state.update(tick, false, 7, true));
            assertEquals(RISING, state.update(tick + 1, false, 2, true));
            assertEquals(APEX, state.update(tick + 2, false, 0, true));
            assertEquals(FALLING, state.update(tick + 3, false, -3, true));
            assertEquals(LANDING, state.update(tick + 4, true, 0, true));
        }
        assertEquals(LANDING, state.update(21, true, 0, true));
        assertEquals(LANDING, state.update(21, true, 0, true));
        assertEquals(LANDING, state.update(22, true, 0, true));
        assertEquals(GROUNDED, state.update(23, true, 0, true));
    }

    @Test void ledgesSpecialStatesAndSequenceRewindsDoNotInventTakeoffs() {
        var state = new PlayerAirState();
        assertEquals(GROUNDED, state.update(30, true, 0, true));
        assertEquals(FALLING, state.update(31, false, -2, true));
        assertEquals(GROUNDED, state.update(32, false, 2, false));
        assertEquals(GROUNDED, state.update(0, true, 0, true));
        assertEquals(TAKEOFF, state.update(1, false, 2, true));
        assertThrows(IllegalArgumentException.class, () -> state.update(2, false, Double.NaN, true));
    }

    @Test void crawlingIsAPoseAndDoesNotRequireWaterOrSwimming() {
        var action = new PlayerActionState("swimming", GROUNDED, false, false, false, false,
                false, false, 0, "none", false, false);
        assertTrue(PlayerVisualInput.builder(0, 0).actionState(action).build().crawling());
        assertFalse(PlayerVisualInput.builder(0, 0).actionState(action).swimming(true).build().crawling());
    }
}
