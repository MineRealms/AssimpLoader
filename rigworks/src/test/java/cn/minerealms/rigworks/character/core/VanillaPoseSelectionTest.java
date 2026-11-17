package cn.minerealms.rigworks.character.core;

import com.google.gson.JsonParser;
import org.junit.jupiter.api.Test;

import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

class VanillaPoseSelectionTest {
    @Test void defaultsKeepExistingFullAnimationAndMotionPolicies() {
        assertEquals(VanillaPoseSelection.ALL, VanillaPoseSelection.fromMap(Map.of()));
        assertTrue(VanillaPoseSelection.ALL.attack());
        assertTrue(VanillaPoseSelection.ALL.items());
        assertFalse(VanillaPoseSelection.MOTION.attack());
        assertFalse(VanillaPoseSelection.MOTION.items());
        assertEquals(1, VanillaPoseSelection.MOTION.weight("left_forearm"));
    }

    @Test void samplingAndAnatomicalWeightsAreIndependent() {
        var value = VanillaPoseSelection.fromMap(Map.of("attack", false, "items", true, "leftArm", .25, "rightLeg", 0, "root", .5));
        assertFalse(value.attack()); assertTrue(value.items());
        assertEquals(.25, value.weight("left_hand"));
        assertEquals(0, value.weight("right_shin"));
        assertEquals(1, value.weight("head"));
        assertEquals(.5, value.root());
        assertEquals(0, value.weight("cape"));
    }

    @Test void malformedOptionsNeverCoerceStringsBooleansOrOutOfRangeNumbers() {
        for (String json : new String[]{"null", "[]", "{\"head\":\"1\"}", "{\"attack\":1}",
                "{\"items\":null}", "{\"root\":-0.1}", "{\"leftArm\":1.00000000001}",
                "{\"version\":1.00000000001}", "{\"head\":1.00000000000000000000001}",
                "{\"version\":\"1\"}", "{\"unknown\":1}"})
            assertThrows(IllegalArgumentException.class, () -> VanillaPoseSelection.parse(JsonParser.parseString(json)), json);
        assertThrows(IllegalArgumentException.class, () -> VanillaPoseSelection.fromMap(Map.of("head", Double.NaN)));
    }
}
