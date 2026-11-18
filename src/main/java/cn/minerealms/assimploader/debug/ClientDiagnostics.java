package cn.minerealms.assimploader.debug;

import cn.minerealms.assimploader.character.physics.BulletCharacterPhysics;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Supplier;

/** Optional host integrations. Providers are read on the client thread and own their lifetimes. */
public final class ClientDiagnostics {
    private record Provider(Supplier<BulletCharacterPhysics> physics,Supplier<List<String>> text) {}
    private static final Map<String,Provider> providers=new LinkedHashMap<>();
    private ClientDiagnostics() {}
    public static void register(String id,Supplier<BulletCharacterPhysics> physics,Supplier<List<String>> text) {
        if(!providers.containsKey(id) && providers.size()>=16)throw new IllegalStateException("Too many diagnostic providers");
        providers.put(java.util.Objects.requireNonNull(id),new Provider(java.util.Objects.requireNonNull(physics),java.util.Objects.requireNonNull(text)));
    }
    public static List<BulletCharacterPhysics> physics() {
        return providers.values().stream().map(provider->provider.physics.get()).filter(java.util.Objects::nonNull)
                .filter(physics->!physics.isClosed()).toList();
    }
    public static void unregister(String id) {providers.remove(id);}
    public static List<String> lines() {
        return providers.values().stream().flatMap(provider->provider.text.get().stream()).limit(8).toList();
    }
}
