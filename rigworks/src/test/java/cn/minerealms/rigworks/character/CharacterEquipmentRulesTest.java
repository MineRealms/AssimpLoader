package cn.minerealms.rigworks.character;

import cn.minerealms.rigworks.character.core.CharacterEquipmentDefinition;
import org.junit.jupiter.api.Test;
import java.io.StringReader;
import java.util.Set;
import static org.junit.jupiter.api.Assertions.*;

class CharacterEquipmentRulesTest {
    private static final String JSON="""
        {"version":1,"right_hand":{"node":"Hand.R"},"left_hand":{"node":"Hand.L"},
         "sockets":{"eye":{"node":"Head","position":[0,0.1,0.05]}},
         "overrides":[{"arm":"left","items":["minecraft:spyglass"],"states":["using"],
                       "contexts":["head"],"socket":"eye"},
                      {"tags":["test:long_weapons"],"use_animations":["spear"],"mount":{"node":"Hand.R","scale":0.8}}]}
        """;
    @Test void rulesUsePhysicalSidesStatesAndContextAndRetainOldDefaults() {
        var definition=CharacterEquipmentDefinition.read(new StringReader(JSON));
        var selected=definition.select("left","minecraft:spyglass",Set.of(),"using","spyglass","head");
        assertSame(definition.socket("eye"),selected);
        assertSame(definition.leftHand(),definition.select("left","minecraft:spyglass",Set.of(),"held","spyglass","head"));
        assertSame(definition.rightHand(),definition.select("right","minecraft:spyglass",Set.of(),"using","spyglass","head"));
        assertEquals(.8,definition.select("right","test:spear",Set.of("test:long_weapons"),"held","spear","third_person_right_hand").scale(),1e-6);
        var legacy=CharacterEquipmentDefinition.read(new StringReader("{\"version\":1,\"left_hand\":{\"node\":\"Hand\"}}"));
        assertEquals("Hand",legacy.select("left","test:item",Set.of(),"held","none","third_person_left_hand").node());
        assertNull(legacy.rightHand());
    }
    @Test void editorHandReplacementAndJsonRoundTripPreserveNamedRules() {
        var original=CharacterEquipmentDefinition.read(new StringReader(JSON));
        var changed=original.withHands(original.leftHand(),original.rightHand());
        assertEquals(original.sockets(),changed.sockets());assertEquals(original.overrides(),changed.overrides());
        assertEquals(changed,CharacterEquipmentDefinition.read(new StringReader(changed.toJson().toString())));
    }
    @Test void rejectsUnresolvedSocketBadContextAndAmbiguousDestinations() {
        assertThrows(IllegalArgumentException.class,()->CharacterEquipmentDefinition.read(new StringReader(JSON.replace("\"socket\":\"eye\"","\"socket\":\"missing\""))));
        assertThrows(IllegalArgumentException.class,()->CharacterEquipmentDefinition.read(new StringReader(JSON.replace("\"head\"","\"unknown\""))));
        assertThrows(IllegalArgumentException.class,()->CharacterEquipmentDefinition.read(new StringReader(JSON.replace("\"socket\":\"eye\"","\"socket\":\"eye\",\"mount\":{\"node\":\"Head\"}"))));
    }
}
