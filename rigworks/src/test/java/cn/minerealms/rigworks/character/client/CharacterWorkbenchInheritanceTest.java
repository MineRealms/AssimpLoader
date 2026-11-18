package cn.minerealms.rigworks.character.client;

import cn.minerealms.rigworks.character.core.CharacterEquipmentDefinition;
import cn.minerealms.rigworks.character.core.CharacterProfile;
import cn.minerealms.rigworks.character.core.CharacterVisualConfig;
import com.google.gson.JsonParser;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class CharacterWorkbenchInheritanceTest {
    private static final String HASH="a".repeat(64), ID="test:models/avatar/profile.json";

    private static CharacterVisualConfig configured() {
        var profile=CharacterProfile.parse(JsonParser.parseString("""
                {"version":1,"model":"test:models/avatar/model.fbx","scale":0.8,
                 "humanoid":{"right_hand":"Palm"},"animations":{"walk":"Walk","idle":"Stand"}}
                """).getAsJsonObject());
        return new CharacterVisualConfig(profile,CharacterEquipmentDefinition.defaults(profile),
                new CharacterVisualConfig.Transform(1,2,3,10,20,30,.8f,.9f,1.1f),HASH,"{\"version\":1,\"ik\":{}}",null);
    }

    @Test void importedCopyKeepsOriginalProfileAndAllBehaviorData() {
        var current=configured();
        var result=CharacterWorkbenchInheritance.resolve(null,null,ID,current,HASH);
        assertNotNull(result);assertEquals(ID,result.profileId());assertEquals(current,result.visual());
        assertEquals("Walk",result.visual().profile().animation("walk"));assertNotNull(result.visual().rig());
    }

    @Test void unrelatedModelCannotInheritCurrentPlayersRig() {
        assertNull(CharacterWorkbenchInheritance.resolve(null,null,ID,configured(),"b".repeat(64)));
    }

    @Test void reopeningSavedProjectRetainsEditsEvenWithAnotherCharacterSelected() {
        var base=configured();
        var saved=new CharacterVisualConfig(base.profile(),base.equipment(),
                new CharacterVisualConfig.Transform(0,1,0,0,45,0,1.2f,1.3f,1.4f),HASH,base.rig(),base.physics(),base.options());
        var result=CharacterWorkbenchInheritance.resolve(ID,saved,"other:profile.json",null,HASH);
        assertEquals(ID,result.profileId());assertEquals(saved,result.visual());assertFalse(result.repaired());
        assertEquals(saved,CharacterVisualConfig.parse(result.visual().json()));
    }

    @Test void knownEmptyGeneratedProfileCanRecoverWithoutLosingSavedScaleAndRoot() {
        String stem="1".repeat(24), generated="rigworks:workbench/"+stem+"/profile.json";
        var profile=CharacterProfile.parse(JsonParser.parseString("{\"version\":1,\"model\":\"rigworks:models/workbench/"+stem+
                "/model.fbx\",\"scale\":1.2,\"modelYawDegrees\":90,\"humanoid\":{},\"animations\":{}}").getAsJsonObject());
        var root=new CharacterVisualConfig.Transform(1,2,3,4,5,6,1.1f,1.2f,1.3f);
        var saved=new CharacterVisualConfig(profile,CharacterEquipmentDefinition.defaults(profile),root,HASH,null,null);
        var result=CharacterWorkbenchInheritance.resolve(generated,saved,ID,configured(),HASH);
        assertTrue(result.repaired());assertEquals(ID,result.profileId());assertEquals(1.2,result.visual().profile().scale());
        assertEquals(90,result.visual().profile().modelYawDegrees());assertEquals(root,result.visual().transform());
        assertEquals(configured().profile().humanoid(),result.visual().profile().humanoid());
        assertEquals(configured().profile().animations(),result.visual().profile().animations());
    }

    @Test void authoredEmptyProfileIsNotSilentlyReplaced() {
        var profile=CharacterProfile.parse(JsonParser.parseString("""
                {"version":1,"model":"test:empty.fbx","humanoid":{},"animations":{}}
                """).getAsJsonObject());
        var saved=new CharacterVisualConfig(profile,CharacterEquipmentDefinition.defaults(profile),CharacterVisualConfig.Transform.IDENTITY,HASH,null,null);
        assertEquals(saved,CharacterWorkbenchInheritance.resolve("test:empty/profile.json",saved,ID,configured(),HASH).visual());
    }
}
