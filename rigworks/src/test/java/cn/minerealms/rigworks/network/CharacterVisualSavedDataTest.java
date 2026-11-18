package cn.minerealms.rigworks.network;

import cn.minerealms.rigworks.character.core.*;
import com.google.gson.JsonParser;
import net.minecraft.nbt.CompoundTag;
import org.junit.jupiter.api.Test;
import java.io.StringReader;
import java.util.UUID;
import static org.junit.jupiter.api.Assertions.*;

class CharacterVisualSavedDataTest {
    @Test void visualPayloadAboveNbtUtfLimitUsesByteArrayStorage()throws Exception {
        var profile=CharacterProfile.parse(JsonParser.parseString("{\"version\":1,\"model\":\"test:models/player.fbx\"}").getAsJsonObject());
        var equipment=CharacterEquipmentDefinition.read(new StringReader("{\"version\":1,\"enabled\":false}"));
        String bone="{\"node\":\""+"x".repeat(180)+"\",\"translation\":[0,0,0]}";
        String rig="{\"version\":1,\"boneMorphs\":{\"one\":["+String.join(",",java.util.Collections.nCopies(220,bone))+"],\"two\":["+String.join(",",java.util.Collections.nCopies(160,bone))+"]}}";
        var config=new CharacterVisualConfig(profile,equipment,CharacterVisualConfig.Transform.IDENTITY,"a".repeat(64),rig,null);
        assertTrue(config.json().getBytes(java.nio.charset.StandardCharsets.UTF_8).length>65535);
        var state=new CharacterVisualSavedData();UUID owner=UUID.randomUUID();state.ledger.commit(owner,new CharacterAppearance(true,"test:profile.json","CLIPS",false,0),config,0);
        CompoundTag tag=state.save(new CompoundTag());
        try(var out=new java.io.DataOutputStream(new java.io.ByteArrayOutputStream())){net.minecraft.nbt.NbtIo.write(tag,out);}
        assertEquals(config,CharacterVisualSavedData.load(tag).ledger.get(owner).config());
    }
    @Test void acceptedConfigurationAndPackageIdentitySurviveWorldStorageRoundTrip() {
        var profile=CharacterProfile.parse(JsonParser.parseString("{\"version\":1,\"model\":\"test:models/player.fbx\"}").getAsJsonObject());
        var equipment=CharacterEquipmentDefinition.read(new StringReader("{\"version\":1,\"enabled\":false}"));
        var config=new CharacterVisualConfig(profile,equipment,CharacterVisualConfig.Transform.IDENTITY,"a".repeat(64),null,null);
        var state=new CharacterVisualSavedData();UUID owner=UUID.randomUUID();
        var appearance=new CharacterAppearance(true,"test:profile.json","CLIPS",true,999);
        var accepted=state.ledger.commit(owner,appearance,config,0);state.packages.put(owner,"b".repeat(64));
        var restored=CharacterVisualSavedData.load(state.save(new CompoundTag()));
        assertEquals(state.identity,restored.identity);
        assertEquals(config,restored.ledger.get(owner).config());
        assertEquals(accepted.configRevision(),restored.ledger.get(owner).configRevision());
        assertFalse(restored.ledger.get(owner).appearance().flying());
        assertEquals("b".repeat(64),restored.packages.get(owner));
        assertEquals(2,restored.ledger.commit(owner,appearance,config,accepted.configRevision()).appearance().revision());
    }
}
