package cn.minerealms.rigworks.character.core;

import com.google.gson.JsonParser;
import org.junit.jupiter.api.Test;

import java.io.StringReader;

import static org.junit.jupiter.api.Assertions.*;

class CharacterGripDefinitionTest {
    @Test void selectorsBoundsAndOrientationOffsetsAreStrictAndImmutable() {
        var grips=CharacterGripDefinition.read(JsonParser.parseString("""
                [{"id":"rifle","tags":["example:two_handed"],"states":["held","using"],
                  "socket":[0.1,0,0.2],"pole":[0.3,-0.5,0],"hand_offset":[0.02,0,0],
                  "orientation_weight":1,"min_bend":10,"max_bend":130,"weight":0.75,"free_support":true}]
                """));
        assertEquals(.75f,grips.get(0).weight());assertTrue(grips.get(0).freeSupport());
        assertThrows(UnsupportedOperationException.class,()->grips.clear());
        assertThrows(UnsupportedOperationException.class,()->grips.get(0).tags().clear());
        var vector=grips.get(0).socketVector();vector.zero();assertEquals(.1f,grips.get(0).socketVector().x);
    }

    @Test void malformedAndAmbiguousRulesFailBeforeTheyCanReplaceEquipment() {
        for(String json:new String[]{
                "[{\"id\":\"a\"},{\"id\":\"a\"}]",
                "[{\"id\":\"a\",\"items\":[\"MissingNamespace\"]}]",
                "[{\"id\":\"a\",\"states\":[\"firing\"]}]",
                "[{\"id\":\"a\",\"states\":[]}]",
                "[{\"id\":\"a\",\"min_bend\":100,\"max_bend\":50}]",
                "[{\"id\":\"a\",\"max_bend\":180}]",
                "[{\"id\":\"a\",\"weight\":1.1}]",
                "[{\"id\":\"a\",\"hand_offset\":[0.1,0,0]}]",
                "[{\"id\":\"a\",\"socket\":[11,0,0]}]",
                "[{\"id\":\"a\",\"max_bend\":\"120\"}]",
                "[{\"id\":\"a\",\"unknown\":true}]"})
            assertThrows(IllegalArgumentException.class,()->CharacterGripDefinition.read(JsonParser.parseString(json)),json);
    }

    @Test void olderEquipmentWithoutGripsRetainsItsExistingContract() {
        var equipment=CharacterEquipmentDefinition.read(new StringReader("{\"version\":1,\"enabled\":false}"));
        assertFalse(equipment.enabled());assertTrue(equipment.grips().isEmpty());
    }
}
