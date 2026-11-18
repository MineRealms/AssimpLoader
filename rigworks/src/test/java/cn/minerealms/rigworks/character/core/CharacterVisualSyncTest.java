package cn.minerealms.rigworks.character.core;

import com.google.gson.JsonParser;
import org.junit.jupiter.api.Test;

import java.nio.charset.StandardCharsets;
import java.util.Map;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;

class CharacterVisualSyncTest {
    static CharacterVisualConfig config() {
        var profile=CharacterProfile.parse(JsonParser.parseString("""
                {"version":1,"model":"test:models/avatar.fbx","scale":0.8,"modelYawDegrees":90,
                 "humanoid":{"right_hand":{"node":"Palm","referenceRotation":[0,0,0,1],"nativeRotation":[0,1,0,0]}},
                 "animations":{"idle":"Stand","crouch":"Sneak"}}
                """).getAsJsonObject());
        var equipment=CharacterEquipmentDefinition.read(new java.io.StringReader("""
                {"version":1,"enabled":true,"right_hand":{"node":"Palm","position":[0.1,0.2,0.3],"rotation":[0,0,0,1],"scale":0.9},
                 "left_hand":null,"sockets":{"rifle":{"node":"Palm","position":[0,0,0],"rotation":[0,0,0,1],"scale":1}},
                 "overrides":[{"items":["minecraft:bow"],"socket":"rifle"}]}
                """));
        return new CharacterVisualConfig(profile,equipment,new CharacterVisualConfig.Transform(1,2,3,10,20,30,.8f,.9f,1.1f),"a".repeat(64),"{\"version\":1,\"ik\":{}}",null);
    }
    @Test void roundTripsAllVisibleMappingsAndIndependentRootAxes() {
        var value=config();var decoded=CharacterVisualConfig.parse(value.json());
        assertEquals(value,decoded);assertEquals(value.digest(),decoded.digest());
        assertEquals("Sneak",decoded.profile().animation("crouch"));
        assertEquals(1,decoded.profile().bone("right_hand").nativeRotation().y());
        assertEquals(.9f,decoded.equipment().rightHand().scale());
        assertEquals("Palm",decoded.equipment().socket("rifle").node());
        assertEquals(1,decoded.equipment().overrides().size());assertEquals(1.1f,decoded.transform().sz());
    }
    @Test void deepAndDuplicateUntrustedJsonFailsBeforeARecursiveParser() {
        assertThrows(IllegalArgumentException.class,()->CharacterVisualConfig.parseBounded("[".repeat(10000)+"]".repeat(10000)));
        assertThrows(IllegalArgumentException.class,()->CharacterVisualConfig.parseBounded("{\"a\":1,\"a\":2}"));
        assertThrows(IllegalArgumentException.class,()->CharacterVisualConfig.parseBounded("{\"a\":NaN}"));
        assertThrows(IllegalArgumentException.class,()->CharacterVisualConfig.parseBounded("{\"script\":\"x\"}".repeat(12000)));
    }
    @Test void staleEditsCannotReplaceAcceptedConfigurationAndFlyingDoesNotInvalidateDraft() {
        var state=new CharacterVisualLedger();UUID owner=UUID.randomUUID(),other=UUID.randomUUID();
        var selection=new CharacterAppearance(true,"test:profile.json","CLIPS",false,9999);
        var first=state.commit(owner,selection,config(),0);
        assertEquals(1,first.configRevision());assertEquals(1,first.appearance().revision());
        var metadata=state.metadata(owner,new CharacterAppearance(true,selection.profile(),"CLIPS",true,99999));
        assertEquals(1,metadata.configRevision());assertEquals(2,metadata.appearance().revision());
        var second=state.commit(owner,selection,config(),first.configRevision());
        assertEquals(3,second.configRevision());
        assertThrows(IllegalArgumentException.class,()->state.commit(owner,selection,config(),1));
        assertSame(second,state.get(owner));assertNull(state.get(other));
        var restored=new CharacterVisualLedger();restored.restore(owner,second);
        assertEquals(4,restored.commit(owner,selection,config(),3).appearance().revision());
    }
    @Test void packageAndEveryFileHaveStableContentIdentitiesWithoutRawAssetPaths() {
        var bundle=new CharacterVisualPackage("test:profile.json","test:controller",config(),Map.of("scripts/main.js","function tick(f) { f.idle(); }","settings.json","{\"enabled\":true}"));
        var decoded=CharacterVisualPackage.parse(bundle.json());
        assertEquals(bundle.hash(),decoded.hash());assertEquals(bundle.manifest(),decoded.manifest());assertTrue(decoded.hasScripts());
        assertEquals(2,decoded.files().size());
        assertThrows(UnsupportedOperationException.class,()->decoded.files().clear());
        assertThrows(IllegalArgumentException.class,()->new CharacterVisualPackage("test:profile.json",null,config(),Map.of("../client_scripts/evil.js","x")));
        assertThrows(IllegalArgumentException.class,()->new CharacterVisualPackage("test:profile.json",null,config(),Map.of("model.fbx","x")));
        assertThrows(IllegalArgumentException.class,()->new CharacterVisualPackage("test:profile.json",null,config(),Map.of("main.js","x".repeat(65537))));
    }
    @Test void withdrawingAppearanceClearsSharedConfigurationAndAdvancesItsRevision() {
        var ledger=new CharacterVisualLedger();var owner=UUID.randomUUID();
        var selected=new CharacterAppearance(true,"test:profile.json","CLIPS",false,0);
        var original=ledger.commit(owner,selected,config(),0);
        var withdrawn=ledger.metadata(owner,new CharacterAppearance(false,selected.profile(),"VANILLA",false,0));
        assertNull(withdrawn.config());assertFalse(withdrawn.appearance().enabled());
        assertTrue(withdrawn.configRevision()>original.configRevision());
        assertThrows(IllegalArgumentException.class,()->ledger.commit(owner,selected,config(),original.configRevision()));
    }
    @Test void assemblerRejectsMissingDuplicatedReorderedAndCorruptedChunks() {
        byte[] source="x".repeat(30000).getBytes(StandardCharsets.UTF_8);
        String hash=CharacterVisualConfig.hash(source);
        var good=new CharacterPackageTransfer(hash,source.length);
        assertFalse(good.append(0,java.util.Arrays.copyOfRange(source,0,16384)));
        assertThrows(IllegalStateException.class,good::finish);
        assertTrue(good.append(1,java.util.Arrays.copyOfRange(source,16384,source.length)));
        assertArrayEquals(source,good.finish());
        assertThrows(IllegalArgumentException.class,()->good.append(2,new byte[0]));
        var wrongOrder=new CharacterPackageTransfer(hash,source.length);
        assertThrows(IllegalArgumentException.class,()->wrongOrder.append(1,new byte[16384]));
        var corrupt=new CharacterPackageTransfer("f".repeat(64),2);corrupt.append(0,new byte[2]);
        assertThrows(IllegalArgumentException.class,corrupt::finish);
        assertThrows(IllegalArgumentException.class,()->new CharacterPackageTransfer(hash,CharacterPackageBinary.MAX_BYTES+1));
    }
    @Test void binaryCachePreservesPackageAndRejectsInvalidContainers()throws Exception {
        var value=new CharacterVisualPackage("test:profile.json","test:controller",config(),Map.of("main.js","function tick(frame) { frame.idle(); }"));
        byte[] bytes=CharacterPackageBinary.encode(value);
        assertEquals(value.hash(),CharacterPackageBinary.decode(bytes).hash());
        assertTrue(bytes.length<value.json().getBytes(StandardCharsets.UTF_8).length);
        assertThrows(java.io.IOException.class,()->CharacterPackageBinary.decode(new byte[10]));
        bytes[0]^=1;assertThrows(java.io.IOException.class,()->CharacterPackageBinary.decode(bytes));
    }
}
