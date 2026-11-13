package cn.minerealms.assimploader.core;

import cn.minerealms.assimploader.config.AssimpLoaderConfig;
import cn.minerealms.assimploader.model.AssimpScene;
import net.minecraft.resources.ResourceLocation;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.util.*;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.atomic.AtomicLong;

/** One owner for Java model data; aliases never own additional scenes. No client or GL classes are referenced here. */
final class ModelRepository {
    private static final Logger LOGGER = LoggerFactory.getLogger(ModelRepository.class);
    private static final Object LOCK = new Object();
    private static final LinkedHashMap<String, Entry> MODELS = new LinkedHashMap<>(16, 0.75f, true);
    private static final Map<String, CompletableFuture<AssimpScene>> IN_FLIGHT = new HashMap<>();
    private static final Map<String, Long> VERSIONS = new HashMap<>();
    private static final CopyOnWriteArrayList<Runnable> INVALIDATION_LISTENERS = new CopyOnWriteArrayList<>();
    private static final AtomicLong GENERATION = new AtomicLong();
    private static final Set<String> EXTENSIONS = Set.of("fbx", "gltf", "glb", "obj", "dae", "blend", "3ds", "ply", "stl", "x", "lwo", "lws", "md2", "md3", "md5mesh", "smd", "vta", "ase", "ac", "ifc", "b3d", "dxf", "ogex", "irr", "irrmesh", "ms3d", "nff", "off", "raw", "ter", "cob", "scn", "q3d", "q3s", "xgl", "zgl", "assbin");
    private static Map<String, Set<String>> aliases = Map.of();
    private static Map<String, String> preferredKeys = Map.of();
    private static long epoch;

    private ModelRepository() {}

    static String identity(ResourceLocation resource) {
        return resource.getNamespace() + ":" + withoutExtension(resource.getPath());
    }

    static String key(String identity, ModelImportMode mode) {
        return identity + "#" + mode.name().toLowerCase(Locale.ROOT);
    }

    static String canonical(String name) {
        String base = stripMode(name);
        int colon = base.indexOf(':');
        String namespace = colon >= 0 ? base.substring(0, colon) : "assimploader";
        String path = colon >= 0 ? base.substring(colon + 1) : base;
        path = stripKnownExtension(path.replace('\\', '/'));
        if (!path.startsWith("models/")) path = "models/" + path;
        return new ResourceLocation(namespace, path).toString();
    }

    static AssimpScene load(String identity, ModelImportMode mode, boolean pinned,
                            AsyncModelLoader.Decoder decoder) throws IOException {
        String key = key(identity, mode);
        CompletableFuture<AssimpScene> pending;
        long loadEpoch;
        long loadVersion;
        boolean owner;
        synchronized (LOCK) {
            Entry existing = MODELS.get(key);
            if (existing != null) return existing.scene;
            pending = IN_FLIGHT.get(key);
            owner = pending == null;
            if (owner) {
                pending = new CompletableFuture<>();
                IN_FLIGHT.put(key, pending);
            }
            loadEpoch = epoch;
            loadVersion = VERSIONS.getOrDefault(key, 0L);
        }
        if (!owner) return await(pending);
        try {
            AssimpScene scene = decoder.decode();
            if (scene == null) throw new IOException("Model decoder returned no scene for " + identity);
            boolean invalidated;
            synchronized (LOCK) {
                if (loadEpoch != epoch || loadVersion != VERSIONS.getOrDefault(key, 0L)) {
                    throw new IOException("Model load superseded by replacement or resource reload: " + identity);
                }
                invalidated = putLocked(identity, scene, pinned);
            }
            if (invalidated) notifyInvalidation();
            pending.complete(scene);
            return scene;
        } catch (Throwable exception) {
            pending.completeExceptionally(exception);
            if (exception instanceof IOException ioException) throw ioException;
            if (exception instanceof Error error) throw error;
            throw new IOException("Failed to load model " + identity, exception);
        } finally {
            synchronized (LOCK) {
                IN_FLIGHT.remove(key, pending);
            }
        }
    }

    static void put(String identity, AssimpScene scene, boolean pinned) {
        boolean invalidated;
        synchronized (LOCK) {
            bumpVersion(key(identity, scene.getImportMode()));
            invalidated = putLocked(identity, scene, pinned);
        }
        if (invalidated) notifyInvalidation();
    }

    private static boolean putLocked(String identity, AssimpScene scene, boolean pinned) {
        String key = key(identity, scene.getImportMode());
        Entry previous = MODELS.get(key);
        MODELS.put(key, new Entry(identity, scene, pinned || (previous != null && previous.pinned)));
        boolean changed = previous == null || previous.scene != scene;
        boolean invalidated = previous != null && previous.scene != scene;
        // JSON-backed scenes are pinned: evicting one would make an already baked world block disappear.
        int limit = configuredLimit();
        long unpinned = MODELS.values().stream().filter(entry -> !entry.pinned).count();
        if (unpinned > limit) {
            Iterator<Entry> entries = MODELS.values().iterator();
            while (entries.hasNext() && unpinned > limit) {
                if (!entries.next().pinned) {
                    entries.remove();
                    unpinned--;
                    invalidated = true;
                }
            }
        }
        if (changed || invalidated) {
            rebuildAliases();
            GENERATION.incrementAndGet();
        }
        return invalidated;
    }

    static AssimpScene get(String name, ModelImportMode requestedMode) {
        synchronized (LOCK) {
            ModelImportMode mode = requestedMode != null ? requestedMode : modeSuffix(name);
            String reference = stripMode(name);
            String identity = resolveIdentity(reference);
            if (identity == null) return null;
            if (mode != null) {
                Entry entry = MODELS.get(key(identity, mode));
                return entry == null ? null : entry.scene;
            }
            // Preserve the old mode-unspecified lookup contract without duplicating the scene in the cache.
            String preferred = preferredKeys.get(identity);
            Entry found = preferred == null ? null : MODELS.get(preferred);
            return found == null ? null : found.scene;
        }
    }

    private static String resolveIdentity(String name) {
        String reference = stripKnownExtension(name.replace('\\', '/'));
        Set<String> candidates = aliases.get(reference);
        return candidates != null && candidates.size() == 1 ? candidates.iterator().next() : null;
    }

    static void remove(String name) {
        boolean removed;
        synchronized (LOCK) {
            String reference = stripMode(name);
            Set<String> candidates = aliases.get(stripKnownExtension(reference));
            if (candidates != null && candidates.size() > 1) return;
            String resolved = resolveIdentity(reference);
            String identity = resolved != null ? resolved : canonical(name);
            ModelImportMode mode = modeSuffix(name);
            if (mode != null) {
                bumpVersion(key(identity, mode));
            } else {
                bumpVersion(key(identity, ModelImportMode.STATIC));
                bumpVersion(key(identity, ModelImportMode.DYNAMIC));
            }
            removed = MODELS.entrySet().removeIf(entry -> entry.getValue().identity.equals(identity)
                    && (mode == null || entry.getValue().scene.getImportMode() == mode));
            if (removed) {
                rebuildAliases();
                GENERATION.incrementAndGet();
            }
        }
        if (removed) notifyInvalidation();
    }

    static boolean replace(long expectedGeneration, Map<String, AssimpScene> replacements, Set<String> removals) {
        synchronized (LOCK) {
            if (GENERATION.get() != expectedGeneration) return false;
            removals.forEach(key -> {
                bumpVersion(key);
                MODELS.remove(key);
            });
            replacements.forEach((key, scene) -> {
                String identity = canonical(key);
                String canonicalKey = key(identity, scene.getImportMode());
                bumpVersion(canonicalKey);
                MODELS.put(canonicalKey, new Entry(identity, scene, true));
            });
            rebuildAliases();
            GENERATION.incrementAndGet();
        }
        notifyInvalidation();
        return true;
    }

    static void clear() {
        synchronized (LOCK) {
            epoch++;
            MODELS.clear();
            aliases = Map.of();
            preferredKeys = Map.of();
            IOException superseded = new IOException("Model load superseded by resource reload");
            IN_FLIGHT.values().forEach(future -> future.completeExceptionally(superseded));
            IN_FLIGHT.clear();
            VERSIONS.clear();
            GENERATION.incrementAndGet();
        }
        notifyInvalidation();
    }

    static Map<String, AssimpScene> snapshot() {
        synchronized (LOCK) {
            Map<String, AssimpScene> snapshot = new TreeMap<>();
            MODELS.forEach((key, value) -> snapshot.put(key, value.scene));
            return Collections.unmodifiableMap(snapshot);
        }
    }

    static long generation() {
        return GENERATION.get();
    }

    static void addListener(Runnable listener) {
        INVALIDATION_LISTENERS.addIfAbsent(Objects.requireNonNull(listener));
    }

    static void removeListener(Runnable listener) {
        INVALIDATION_LISTENERS.remove(listener);
    }

    private static void notifyInvalidation() {
        for (Runnable listener : INVALIDATION_LISTENERS) {
            try {
                listener.run();
            } catch (RuntimeException exception) {
                LOGGER.error("Model cache invalidation listener failed", exception);
            }
        }
    }

    private static void rebuildAliases() {
        Map<String, Set<String>> index = new HashMap<>();
        Map<String, String> preferred = new HashMap<>();
        for (Map.Entry<String, Entry> cached : MODELS.entrySet()) {
            Entry entry = cached.getValue();
            String identity = entry.identity;
            preferred.put(identity, cached.getKey());
            String path = identity.substring(identity.indexOf(':') + 1);
            indexAlias(index, identity, identity);
            indexAlias(index, path, identity);
            if (path.startsWith("models/")) indexAlias(index, path.substring(7), identity);
            indexAlias(index, path.substring(path.lastIndexOf('/') + 1), identity);
        }
        aliases = index;
        preferredKeys = preferred;
    }

    private static void bumpVersion(String key) {
        VERSIONS.merge(key, 1L, Long::sum);
        CompletableFuture<AssimpScene> pending = IN_FLIGHT.remove(key);
        if (pending != null) pending.completeExceptionally(new IOException("Model load superseded by replacement: " + key));
    }

    private static void indexAlias(Map<String, Set<String>> index, String alias, String identity) {
        index.computeIfAbsent(alias, ignored -> new HashSet<>()).add(identity);
    }

    private static int configuredLimit() {
        try {
            return AssimpLoaderConfig.CLIENT.modelCacheSize.get();
        } catch (IllegalStateException exception) {
            return 100;
        }
    }

    private static AssimpScene await(CompletableFuture<AssimpScene> future) throws IOException {
        try {
            return future.get();
        } catch (InterruptedException exception) {
            Thread.currentThread().interrupt();
            throw new IOException("Interrupted while waiting for model", exception);
        } catch (ExecutionException exception) {
            Throwable cause = exception.getCause();
            if (cause instanceof IOException ioException) throw ioException;
            throw new IOException("Model decoding failed", cause);
        }
    }

    private static String withoutExtension(String path) {
        int dot = path.lastIndexOf('.');
        return dot > path.lastIndexOf('/') ? path.substring(0, dot) : path;
    }

    private static String stripKnownExtension(String path) {
        int dot = path.lastIndexOf('.');
        if (dot <= path.lastIndexOf('/')) return path;
        String suffix = path.substring(dot + 1).toLowerCase(Locale.ROOT);
        return EXTENSIONS.contains(suffix)
                ? path.substring(0, dot) : path;
    }

    private static String stripMode(String name) {
        int hash = name.lastIndexOf('#');
        if (hash >= 0) return name.substring(0, hash);
        if (name.endsWith(":STATIC")) return name.substring(0, name.length() - 7);
        if (name.endsWith(":DYNAMIC")) return name.substring(0, name.length() - 8);
        return name;
    }

    private static ModelImportMode modeSuffix(String name) {
        int hash = name.lastIndexOf('#');
        if (hash < 0 && !name.endsWith(":STATIC") && !name.endsWith(":DYNAMIC")) return null;
        String suffix = hash >= 0 ? name.substring(hash + 1) : name.substring(name.lastIndexOf(':') + 1);
        if (suffix.equalsIgnoreCase("static")) return ModelImportMode.STATIC;
        if (suffix.equalsIgnoreCase("dynamic")) return ModelImportMode.DYNAMIC;
        return null;
    }

    private record Entry(String identity, AssimpScene scene, boolean pinned) {}
}
