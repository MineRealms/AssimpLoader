package cn.minerealms.rigworks.character.core;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class PlayerVisualInputTest {
    @Test void bodyRelativeMotionAndImmutableSnapshotsRemainIndependentOfBuilderChanges() {
        var builder = PlayerVisualInput.builder(7, .35).motion(3, 4, -2).orientation(10, 30, -15).jumping(true);
        var input = builder.build();
        builder.motion(0, 0, 0).jumping(false);
        assertEquals(5, input.horizontalSpeed());
        assertEquals(-2, input.verticalVelocity());
        assertEquals(30, input.headYawDegrees());
        assertTrue(input.jumping());
        assertEquals(0, builder.build().horizontalSpeed());
    }

    @Test void invalidMotionCannotReachTheAnimationRuntime() {
        assertThrows(IllegalArgumentException.class, () -> PlayerVisualInput.builder(-1, 0).build());
        assertThrows(IllegalArgumentException.class, () -> PlayerVisualInput.builder(0, -1).build());
        assertThrows(IllegalArgumentException.class, () -> PlayerVisualInput.builder(0, Double.NaN).build());
        assertThrows(IllegalArgumentException.class, () -> PlayerVisualInput.builder(0, 0).motion(Double.NaN, 0, 0).build());
        assertThrows(IllegalArgumentException.class, () -> PlayerVisualInput.builder(0, 0).motion(0, 0, Double.POSITIVE_INFINITY).build());
        assertThrows(IllegalArgumentException.class, () -> PlayerVisualInput.builder(0, 0).orientation(0, Float.NaN, 0).build());
    }
}
