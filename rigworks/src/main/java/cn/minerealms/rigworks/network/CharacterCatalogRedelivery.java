package cn.minerealms.rigworks.network;

import java.util.*;

/** Bounded retry metadata only. Publication always reads the current authoritative catalog. */
final class CharacterCatalogRedelivery {
    static final int MAX_ENTRIES=4096,MAX_ATTEMPTS=8;
    record Key(UUID viewer,UUID owner){}
    record Pending(Key key,Object connection,long revision,long request,long due,int attempts){}
    private final Map<Key,Pending> pending=new LinkedHashMap<>();
    long request(Key key,Object connection,long request) {
        var old=pending.get(key);return request!=0||old==null||old.connection!=connection?request:old.request;
    }
    boolean failed(Key key,Object connection,long revision,long request,long now) {
        var old=pending.get(key);
        int attempts=old==null||old.connection!=connection||old.revision!=revision?1:old.attempts+1;
        if(attempts>MAX_ATTEMPTS){pending.remove(key);return false;}
        if(!pending.containsKey(key)&&pending.size()>=MAX_ENTRIES) {
            var victim=pending.keySet().stream().filter(candidate->!candidate.viewer.equals(candidate.owner)).findFirst().orElse(pending.keySet().iterator().next());
            pending.remove(victim);
        }
        long response=request(key,connection,request);
        pending.put(key,new Pending(key,connection,revision,response,now+Math.min(200,4L<<(attempts-1)),attempts));return true;
    }
    List<Pending> due(long now,int limit) {
        return pending.values().stream().filter(value->value.due<=now).limit(limit).toList();
    }
    void sent(Key key){pending.remove(key);}
    void disconnect(UUID owner){pending.keySet().removeIf(key->key.owner.equals(owner)||key.viewer.equals(owner));}
    void clear(){pending.clear();}
    int size(){return pending.size();}
}
