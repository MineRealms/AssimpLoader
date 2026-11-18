package cn.minerealms.rigworks.character.core;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

/** Server-owned revisions; a draft can replace only the version it was based on. */
public final class CharacterVisualLedger {
    public record Entry(CharacterAppearance appearance, CharacterVisualConfig config, long configRevision) {
        public Entry {
            java.util.Objects.requireNonNull(appearance);
            if(configRevision<0||configRevision>appearance.revision())throw new IllegalArgumentException("Invalid visual revision");
        }
        public Entry(CharacterAppearance appearance,CharacterVisualConfig config){this(appearance,config,appearance.revision());}
    }
    private final Map<UUID,Entry> entries=new HashMap<>();
    private long revision;
    public Entry get(UUID id){return entries.get(id);}
    public Map<UUID,Entry> snapshot(){return Map.copyOf(entries);}
    public long revision(){return revision;}
    public Entry metadata(UUID id,CharacterAppearance request) {
        Entry old=entries.get(id);
        boolean keep=request.enabled()&&old!=null&&old.appearance.profile().equals(request.profile());
        CharacterVisualConfig config=keep?old.config:null;
        return put(id,request,config,keep?old.configRevision:-1);
    }
    public Entry commit(UUID id,CharacterAppearance request,CharacterVisualConfig config,long expectedRevision) {
        Entry old=entries.get(id);
        long actual=old==null?0:old.configRevision;
        if(expectedRevision!=actual)throw new IllegalArgumentException("Configuration changed on the server; receive the current version before sharing again");
        return put(id,request,java.util.Objects.requireNonNull(config),-1);
    }
    private Entry put(UUID id,CharacterAppearance request,CharacterVisualConfig config,long configRevision) {
        if(entries.size()>=2048&&!entries.containsKey(id))throw new IllegalArgumentException("Shared character storage is full");
        if(revision==Long.MAX_VALUE)throw new IllegalStateException("Character revision exhausted");
        var accepted=new Entry(new CharacterAppearance(request.enabled(),request.profile(),request.mode(),request.flying(),++revision),config,configRevision<0?revision:configRevision);
        entries.put(id,accepted);return accepted;
    }
    public void restore(UUID id,Entry entry) {
        if(entries.size()>=2048&&!entries.containsKey(id))throw new IllegalArgumentException("Shared character storage is full");
        var old=entries.get(id);if(old==null||entry.appearance.revision()>old.appearance.revision())entries.put(id,entry);
        revision=Math.max(revision,entry.appearance.revision());
    }
}
