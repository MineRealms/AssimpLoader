package cn.minerealms.rigworks.network;

import cn.minerealms.rigworks.character.core.CharacterAppearance;
import cn.minerealms.rigworks.character.core.CharacterVisualConfig;
import cn.minerealms.rigworks.character.core.CharacterVisualLedger;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;
import net.minecraft.server.MinecraftServer;
import net.minecraft.world.level.saveddata.SavedData;

/** World-scoped accepted configurations survive logout and server restarts. */
final class CharacterVisualSavedData extends SavedData {
    final CharacterVisualLedger ledger=new CharacterVisualLedger();
    final java.util.Map<java.util.UUID,String> packages=new java.util.HashMap<>();
    java.util.UUID identity=java.util.UUID.randomUUID();
    static CharacterVisualSavedData get(MinecraftServer server) {
        return server.overworld().getDataStorage().computeIfAbsent(CharacterVisualSavedData::load,CharacterVisualSavedData::new,"rigworks_visuals");
    }
    static CharacterVisualSavedData load(CompoundTag tag) {
        var data=new CharacterVisualSavedData();
        if(tag.hasUUID("identity"))data.identity=tag.getUUID("identity");
        ListTag rows=tag.getList("players",Tag.TAG_COMPOUND);
        for(int i=0;i<Math.min(2048,rows.size());i++) {
            CompoundTag row=rows.getCompound(i);
            try {
                var appearance=new CharacterAppearance(row.getBoolean("enabled"),row.getString("profile"),row.getString("mode"),false,row.getLong("revision"));
                CharacterVisualConfig config=null;
                if(row.contains("configBytes",Tag.TAG_BYTE_ARRAY)) {
                    byte[] bytes=row.getByteArray("configBytes");
                    if(bytes.length>CharacterVisualConfig.MAX_BYTES)throw new IllegalArgumentException("Saved character configuration exceeds its budget");
                    try {config=CharacterVisualConfig.parse(java.nio.charset.StandardCharsets.UTF_8.newDecoder().decode(java.nio.ByteBuffer.wrap(bytes)).toString());}
                    catch(java.nio.charset.CharacterCodingException invalid){throw new IllegalArgumentException("Invalid saved character UTF-8",invalid);}
                }else if(row.contains("config",Tag.TAG_STRING))config=CharacterVisualConfig.parse(row.getString("config"));
                data.ledger.restore(row.getUUID("player"),new CharacterVisualLedger.Entry(appearance,config,
                        row.contains("configRevision")?row.getLong("configRevision"):appearance.revision()));
                if(row.getString("package").matches("[a-f0-9]{64}"))data.packages.put(row.getUUID("player"),row.getString("package"));
            } catch(RuntimeException error) {
                cn.minerealms.assimploader.AssimpLoader.LOGGER.warn("Ignoring invalid saved RigWorks visual configuration at row {}",i,error);
            }
        }
        return data;
    }
    @Override public CompoundTag save(CompoundTag tag) {
        ListTag rows=new ListTag();
        ledger.snapshot().forEach((id,entry)-> {
            CompoundTag row=new CompoundTag();row.putUUID("player",id);
            var a=entry.appearance();row.putBoolean("enabled",a.enabled());row.putString("profile",a.profile());row.putString("mode",a.mode());
            row.putLong("revision",a.revision());row.putLong("configRevision",entry.configRevision());
            if(entry.config()!=null)row.putByteArray("configBytes",entry.config().json().getBytes(java.nio.charset.StandardCharsets.UTF_8));rows.add(row);
            if(packages.containsKey(id))row.putString("package",packages.get(id));
        });
        tag.putInt("version",1);tag.putUUID("identity",identity);tag.put("players",rows);return tag;
    }
}
