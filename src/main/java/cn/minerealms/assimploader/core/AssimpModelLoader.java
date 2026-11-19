package cn.minerealms.assimploader.core;

import cn.minerealms.assimploader.debug.DebugManager;
import cn.minerealms.assimploader.debug.DebugManager.DebugCategory;
import cn.minerealms.assimploader.model.AssimpScene;
import cn.minerealms.assimploader.util.CoordinateSystemConverter;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.packs.resources.ResourceManager;
import org.lwjgl.assimp.AIScene;
import org.lwjgl.system.MemoryUtil;

import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Set;

import static org.lwjgl.assimp.Assimp.*;

/** Assimp imports copy native scenes into Java and release native ownership before returning. */
public class AssimpModelLoader {
    // The importer publishes its file-unit scale; GlobalScale applies it consistently to
    // vertices, node translations, animation keys and bone offsets before the Java copy.
    private static final int BASE_FLAGS = aiProcess_Triangulate | aiProcess_GenSmoothNormals
            | aiProcess_FlipUVs | aiProcess_GlobalScale;
    private volatile boolean autoConvertCoordinates = true;

    public AssimpScene loadModel(Path path) throws IOException {
        return loadModel(path, ModelImportMode.STATIC);
    }

    public AssimpScene loadModel(Path path, boolean autoDetect) throws IOException {
        if (!autoDetect) return loadModel(path);
        AIScene nativeScene = aiImportFile(path.toAbsolutePath().toString(), aiProcess_Triangulate);
        if (nativeScene == null) throw new IOException("Failed to analyze " + path + ": " + aiGetErrorString());
        ModelImportMode mode;
        try {
            mode = ModelImportMode.detectFromScene(nativeScene);
        } finally {
            aiReleaseImport(nativeScene);
        }
        return loadModel(path, mode);
    }

    public AssimpScene loadModel(Path path, ModelImportMode mode) throws IOException {
        return loadModel(path, flags(mode), mode);
    }

    public AssimpScene loadModel(Path path, int flags) throws IOException {
        ModelImportMode mode = (flags & aiProcess_PreTransformVertices) == 0 ? ModelImportMode.DYNAMIC : ModelImportMode.STATIC;
        return loadModel(path, flags, mode);
    }

    private AssimpScene loadModel(Path path, int flags, ModelImportMode mode) throws IOException {
        Path absolute = path.toAbsolutePath().normalize();
        if (!Files.isRegularFile(absolute)) throw new IOException("Model file not found: " + absolute);
        AIScene scene = aiImportFile(absolute.toString(), flags);
        if (scene == null) throw new IOException("Failed to import " + absolute + ": " + aiGetErrorString());
        return new AssimpScene(scene, absolute, sourceSystem(absolute.toString()), mode);
    }

    public AssimpScene loadModelFromMemory(String path, byte[] data) {
        return loadModelFromMemory(path, data, ModelImportMode.STATIC);
    }

    public AssimpScene loadModelFromMemory(String path, byte[] data, ModelImportMode mode) {
        if (data == null || data.length == 0 || extension(path).isEmpty()) {
            DebugManager.error(DebugCategory.CORE, "Cannot import empty model or model without extension: {}", path);
            return null;
        }
        ByteBuffer buffer = MemoryUtil.memAlloc(data.length);
        try {
            buffer.put(data).flip();
            AIScene scene = aiImportFileFromMemory(buffer, flags(mode), extension(path));
            if (scene == null) {
                DebugManager.error(DebugCategory.CORE, "Cannot import {}: {}", path, aiGetErrorString());
                return null;
            }
            return new AssimpScene(scene, null, sourceSystem(path), mode);
        } finally {
            MemoryUtil.memFree(buffer);
        }
    }

    /** Imports the main resource and every side file through the same read-only resource namespace. */
    public AssimpScene loadModelFromResources(ResourceLocation location, ResourceFileIO.ResourceReader reader,
                                              ModelImportMode mode) throws IOException {
        try (ResourceFileIO files = new ResourceFileIO(location, reader)) {
            AIScene scene = aiImportFileEx(location.getPath(), flags(mode), files.nativeIO());
            if (scene == null) {
                throw new IOException("Cannot import resource " + location + ": " + aiGetErrorString(), files.getLastFailure());
            }
            return new AssimpScene(scene, null, location, sourceSystem(location.getPath()), mode);
        }
    }

    public AssimpScene loadModelFromResources(ResourceManager manager, ResourceLocation location,
                                              ModelImportMode mode) throws IOException {
        return loadModelFromResources(location, resource -> {
            var entry = manager.getResource(resource).orElseThrow(() -> new IOException("Missing model side file: " + resource));
            try (var stream = entry.open()) {
                return stream.readAllBytes();
            }
        }, mode);
    }

    public static AssimpScene loadCachedResource(ResourceManager manager, ResourceLocation location,
                                                 ModelImportMode mode) throws IOException {
        String identity = ModelRepository.identity(location);
        long generation = getCacheGeneration();
        AssimpScene scene = ModelRepository.load(identity, mode, true, () -> AsyncModelLoader.decodeBlocking(
                identity + "#" + mode + "@" + generation,
                () -> new AssimpModelLoader().loadModelFromResources(manager, location, mode)));
        if (scene.getSourceResource() != null && !scene.getSourceResource().equals(location)) {
            throw new IOException("Conflicting resource files share model identity " + identity + ": "
                    + scene.getSourceResource() + " and " + location);
        }
        return scene;
    }

    public static AssimpScene loadCachedFile(Path path, ModelImportMode mode) throws IOException {
        return loadCachedFile(fileIdentity(path), path, mode, false);
    }

    public static AssimpScene loadCachedFile(String identity, Path path, ModelImportMode mode, boolean pinned) throws IOException {
        String canonical = ModelRepository.canonical(identity);
        long generation = getCacheGeneration();
        AssimpScene scene = ModelRepository.load(canonical, mode, pinned, () -> AsyncModelLoader.decodeBlocking(
                canonical + "#" + mode + "@" + generation,
                () -> new AssimpModelLoader().loadModel(path, mode)));
        if (scene.getSourcePath() == null || !scene.getSourcePath().equals(path.toAbsolutePath().normalize())) {
            throw new IOException("Conflicting model sources share identity " + canonical);
        }
        return scene;
    }

    /** Namespace and full path define identity. Formats deliberately share the same extensionless model identity. */
    public static String canonicalIdentity(ResourceLocation resource) {
        return ModelRepository.identity(resource);
    }

    public static String fileIdentity(Path path) {
        String absolute = path.toAbsolutePath().normalize().toString();
        try {
            byte[] digest = MessageDigest.getInstance("SHA-256").digest(absolute.getBytes(StandardCharsets.UTF_8));
            String hash = HexFormat.of().formatHex(digest, 0, 12);
            String filename = path.getFileName().toString().toLowerCase(Locale.ROOT).replaceAll("[^a-z0-9._-]", "_");
            int dot = filename.lastIndexOf('.');
            if (dot > 0) filename = filename.substring(0, dot);
            return "assimploader:models/external/" + hash + "/" + filename;
        } catch (NoSuchAlgorithmException exception) {
            throw new IllegalStateException(exception);
        }
    }

    public static void updateModelCache(String name, AssimpScene scene) {
        Objects.requireNonNull(scene);
        String identity = scene.getSourceResource() != null
                ? canonicalIdentity(scene.getSourceResource()) : ModelRepository.canonical(name);
        ModelRepository.put(identity, scene, false);
    }

    public static void registerModel(ResourceLocation source, AssimpScene scene) {
        ModelRepository.put(canonicalIdentity(source), Objects.requireNonNull(scene), true);
    }

    public static AssimpScene getCachedModel(String name) {
        return name == null ? null : ModelRepository.get(name, null);
    }

    public static AssimpScene getCachedModel(String name, ModelImportMode mode) {
        return name == null ? null : ModelRepository.get(name, mode);
    }

    /** Immutable canonical snapshot. Keys are namespace:path#static or namespace:path#dynamic, without aliases. */
    public static Map<String, AssimpScene> getLoadedModels() {
        return ModelRepository.snapshot();
    }

    public static Map<String, AssimpScene> getAllCachedModels() {
        return getLoadedModels();
    }

    public static long getCacheGeneration() {
        return ModelRepository.generation();
    }
    /** Changes only when existing model resources become invalid, not when another asset is added. */
    public static long getResourceGeneration() { return ModelRepository.invalidationGeneration(); }

    /** Listeners may run on a loading worker. GL consumers must schedule their own render-thread work. */
    public static void addCacheInvalidationListener(Runnable listener) {
        ModelRepository.addListener(listener);
    }

    public static void removeCacheInvalidationListener(Runnable listener) {
        ModelRepository.removeListener(listener);
    }

    public static void removeCachedModel(String name) {
        ModelRepository.remove(name);
    }

    public static boolean replaceCachedModels(long expectedGeneration, Map<String, AssimpScene> replacements, Set<String> removals) {
        return ModelRepository.replace(expectedGeneration, replacements, removals);
    }

    public static void clearCache() {
        ModelRepository.clear();
    }

    public static AssimpScene loadModelStatic(String path) {
        try {
            return loadCachedFile(Path.of(path), ModelImportMode.STATIC);
        } catch (IOException exception) {
            DebugManager.error(DebugCategory.CORE, "Cannot load model {}", path, exception);
            return null;
        }
    }

    public boolean isFormatSupported(String extension) {
        return aiIsExtensionSupported(extension.startsWith(".") ? extension : "." + extension);
    }

    public static String getVersion() {
        return aiGetVersionMajor() + "." + aiGetVersionMinor() + "." + aiGetVersionRevision();
    }

    public void setAutoConvertCoordinates(boolean enabled) {
        autoConvertCoordinates = enabled;
    }

    public boolean isAutoConvertCoordinates() {
        return autoConvertCoordinates;
    }

    private CoordinateSystemConverter.CoordinateSystem sourceSystem(String path) {
        return autoConvertCoordinates ? CoordinateSystemConverter.detectCoordinateSystem(extension(path))
                : CoordinateSystemConverter.CoordinateSystem.MINECRAFT_LEFT_HANDED;
    }

    private static int flags(ModelImportMode mode) {
        return BASE_FLAGS | (mode == ModelImportMode.STATIC ? aiProcess_PreTransformVertices : 0);
    }

    private static String extension(String path) {
        int dot = path.lastIndexOf('.');
        return dot > path.lastIndexOf('/') && dot < path.length() - 1 ? path.substring(dot + 1).toLowerCase(Locale.ROOT) : "";
    }
}
