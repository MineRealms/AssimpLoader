package cn.minerealms.rigworks.character.core;

import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class CharacterAppearanceTest {
    @Test void selectionComparisonIgnoresTransportRevisionButIncludesFlightAndMode() {
        var original = new CharacterAppearance(true, "pack:player/profile.json", "SCRIPTED", false, 1);
        assertTrue(original.sameSelection(new CharacterAppearance(true, original.profile(), "SCRIPTED", false, 200)));
        assertFalse(original.sameSelection(new CharacterAppearance(true, original.profile(), "SCRIPTED", true, 2)));
        assertFalse(original.sameSelection(new CharacterAppearance(true, original.profile(), "VANILLA", false, 2)));
    }
    @Test void networkIdentityCannotNameAnExternalFileOrParentResource() {
        for (String profile : new String[]{"H:/private/model.json", "pack:../profile.json", "https://host/profile", "pack:models/../../x", "pack:"})
            assertThrows(IllegalArgumentException.class, () -> new CharacterAppearance(true, profile, "VANILLA", false, 0));
        assertThrows(IllegalArgumentException.class, () -> new CharacterAppearance(true, "pack:profile.json", "code", false, 0));
        assertThrows(IllegalArgumentException.class, () -> new CharacterAppearance(true, "pack:profile.json", "VANILLA", false, -1));
    }
}
