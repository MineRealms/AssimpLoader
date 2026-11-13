package cn.minerealms.assimploader.hotreload;

import cn.minerealms.assimploader.config.AssimpLoaderConfig;
import cn.minerealms.assimploader.core.AssimpModelLoader;
import cn.minerealms.assimploader.core.AsyncModelLoader;
import cn.minerealms.assimploader.core.ModelImportMode;
import cn.minerealms.assimploader.model.AssimpScene;
import cn.minerealms.assimploader.util.AssimpLoaderPaths;
import net.minecraft.resources.ResourceLocation;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.*;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionException;

/** Manual and automatic external overrides share the canonical repository and publish complete batches. */
public final class ModelHotReloadManager {
    private static final Logger LOGGER = LoggerFactory.getLogger(ModelHotReloadManager.class);
    private static final Object LOCK = new Object();
    private static Map<String, AssimpScene> originals = Map.of();
    private static Set<String> overrideKeys = Set.of();
    private static CompletableFuture<Integer> pending;

    private ModelHotReloadManager() {}

    public static boolean isEnabled() {
        return AssimpLoaderConfig.getOrDefault(AssimpLoaderConfig.CLIENT.hotReloadEnabled::get, true);
    }

    public static Path getResourceRoot() {
        String configured = AssimpLoaderConfig.getOrDefault(AssimpLoaderConfig.CLIENT.hotReloadResourcePath::get, "AssimpLoader/Resource");
        return AssimpLoaderPaths.resolveGamePath(configured, "AssimpLoader/Resource");
    }

    /** This compatibility entry point blocks its caller; commands and the watcher use reloadModelsAsync(). */
    public static int reloadModels() {
        try {
            return reloadModelsAsync().join();
        } catch (CompletionException exception) {
            LOGGER.error("External model reload failed", exception.getCause());
            return 0;
        }
    }

    public static CompletableFuture<Integer> reloadModelsAsync() {
        if (!isEnabled()) return CompletableFuture.failedFuture(new IOException("Model hot reload is disabled in configuration"));
        Path root;
        try {
            root = getResourceRoot();
        } catch (RuntimeException exception) {
            return CompletableFuture.failedFuture(new IOException("Invalid external model resource path", exception));
        }
        synchronized (LOCK) {
            if (pending != null && !pending.isDone()) return pending;
            long generation = AssimpModelLoader.getCacheGeneration();
            Map<String, AssimpScene> before = AssimpModelLoader.getLoadedModels();
            Map<String, AssimpScene> originalSnapshot = new HashMap<>(originals);
            Set<String> previousKeys = Set.copyOf(overrideKeys);
            List<String> extensions = new ArrayList<>(AssimpLoaderConfig.getOrDefault(
                    AssimpLoaderConfig.CLIENT.hotReloadSupportedExtensions::get, List.of("fbx", "gltf", "glb", "dae", "obj", "blend")));
            pending = CompletableFuture.supplyAsync(() -> {
                try {
                    return reload(root, extensions, generation, before, originalSnapshot, previousKeys);
                } catch (IOException exception) {
                    throw new CompletionException(exception);
                }
            });
            return pending;
        }
    }

    private static int reload(Path root, List<String> extensions, long generation,
                              Map<String, AssimpScene> before, Map<String, AssimpScene> base,
                              Set<String> previous) throws IOException {
        Files.createDirectories(root.resolve("models"));
        Path realRoot = root.toRealPath();
        Map<String, AssimpScene> replacement = new HashMap<>();
        Map<String, AssimpScene> nextOriginals = new HashMap<>();
        Set<String> nextKeys = new HashSet<>();
        List<Path> paths;
        try (var files = Files.walk(root)) {
            paths = files.filter(Files::isRegularFile).sorted().toList();
        }
        for (Path path : paths) {
            String filename = path.getFileName().toString();
            int dot = filename.lastIndexOf('.');
            if (dot < 0 || extensions.stream().noneMatch(extension -> extension.equalsIgnoreCase(filename.substring(dot + 1)))) continue;
            if (!path.toRealPath().startsWith(realRoot)) throw new IOException("External model symlink escapes configured root: " + path);
            ResourceLocation location = resourceLocation(root.relativize(path).toString().replace('\\', '/'));
            String identity = AssimpModelLoader.canonicalIdentity(location);
            List<String> existingKeys = before.keySet().stream().filter(key -> key.startsWith(identity + "#")).toList();
            if (existingKeys.isEmpty()) existingKeys = List.of(identity + "#static");
            for (String key : existingKeys) {
                ModelImportMode mode = key.endsWith("#dynamic") ? ModelImportMode.DYNAMIC : ModelImportMode.STATIC;
                AssimpScene scene = AsyncModelLoader.loadBlocking(path, mode);
                if (scene == null) throw new IOException("Failed to load external model: " + path);
                if (replacement.put(key, scene) != null) throw new IOException("Duplicate external model identity: " + key);
                nextKeys.add(key);
                AssimpScene original = previous.contains(key) ? base.get(key) : before.get(key);
                if (original != null) nextOriginals.put(key, original);
            }
        }
        Set<String> removed = new HashSet<>(previous);
        removed.removeAll(nextKeys);
        for (String deleted : removed) {
            AssimpScene original = base.get(deleted);
            if (original != null) replacement.put(deleted, original);
        }
        synchronized (LOCK) {
            if (!isEnabled()) throw new IOException("Model hot reload was disabled while parsing");
            if (!AssimpModelLoader.replaceCachedModels(generation, replacement, removed)) {
                throw new IOException("Model resources changed during external reload; retry after the resource reload finishes");
            }
            originals = Map.copyOf(nextOriginals);
            overrideKeys = Set.copyOf(nextKeys);
        }
        LOGGER.info("Reloaded {} external model variants from {}", nextKeys.size(), root);
        return nextKeys.size();
    }

    static ResourceLocation resourceLocation(String relative) throws IOException {
        String path = relative;
        String namespace = "assimploader";
        int slash = path.indexOf('/');
        if (slash > 0 && path.substring(slash + 1).startsWith("models/")) {
            namespace = path.substring(0, slash);
            path = path.substring(slash + 1);
        } else if (!path.startsWith("models/")) {
            path = "models/" + path;
        }
        try {
            return new ResourceLocation(namespace, path);
        } catch (RuntimeException exception) {
            throw new IOException("Invalid external model resource name: " + relative, exception);
        }
    }

    public static AssimpScene getModel(String name) {
        AssimpScene scene = AssimpModelLoader.getCachedModel(name);
        if (scene == null) return null;
        synchronized (LOCK) {
            return overrideKeys.stream().anyMatch(key -> AssimpModelLoader.getCachedModel(key) == scene) ? scene : null;
        }
    }

    public static boolean hasModel(String name) {
        return getModel(name) != null;
    }

    public static void forgetOverrides() {
        synchronized (LOCK) {
            originals = Map.of();
            overrideKeys = Set.of();
        }
    }
}
