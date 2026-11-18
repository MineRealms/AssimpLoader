package cn.minerealms.rigworks.character.client;

import cn.minerealms.rigworks.character.core.CharacterEquipmentDefinition;
import cn.minerealms.rigworks.character.core.CharacterProfile;
import cn.minerealms.rigworks.character.core.CharacterVisualConfig;

/** Imported project copies retain a character's behavior identity when the model bytes match. */
final class CharacterWorkbenchInheritance {
    record Selection(String profileId, CharacterVisualConfig visual, boolean repaired) {}

    private CharacterWorkbenchInheritance() {}

    static Selection resolve(String savedId, CharacterVisualConfig saved, String currentId,
                             CharacterVisualConfig current, String importedHash) {
        boolean sameModel = current != null && current.modelHash().equals(importedHash);
        if (saved != null) {
            if (sameModel && incompleteGenerated(savedId, saved) && !current.profile().humanoid().isEmpty()) {
                var base = current.profile();
                var edited = saved.profile();
                var profile = new CharacterProfile(base.model(), edited.scale(), base.humanoid(), base.animations(),
                        base.physicsDefinition(), base.blendSeconds(), base.movementThreshold(), base.headLook(),
                        base.solverDefinition(), edited.modelYawDegrees(), base.equipmentDefinition(), base.timing());
                var visual = new CharacterVisualConfig(profile, current.equipment(), saved.transform(), importedHash,
                        current.rig(), current.physics(), saved.options());
                return new Selection(currentId, visual, true);
            }
            return new Selection(savedId, withHash(saved, importedHash), false);
        }
        return sameModel ? new Selection(currentId, withHash(current, importedHash), false) : null;
    }

    private static boolean incompleteGenerated(String id, CharacterVisualConfig value) {
        if (id == null || !id.matches("rigworks:workbench/[a-f0-9]{24}/profile\\.json")) return false;
        String key = id.substring("rigworks:workbench/".length(), id.length() - "/profile.json".length());
        CharacterEquipmentDefinition equipment = value.equipment();
        return value.profile().model().equals("rigworks:models/workbench/" + key + "/model.fbx")
                && value.profile().humanoid().isEmpty() && value.profile().animations().isEmpty()
                && value.rig() == null && value.physics() == null && equipment.rightHand() == null
                && equipment.leftHand() == null && equipment.grips().isEmpty()
                && equipment.sockets().isEmpty() && equipment.overrides().isEmpty();
    }

    private static CharacterVisualConfig withHash(CharacterVisualConfig value, String hash) {
        return new CharacterVisualConfig(value.profile(), value.equipment(), value.transform(), hash,
                value.rig(), value.physics(), value.options());
    }
}
