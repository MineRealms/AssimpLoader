package cn.minerealms.assimploader.render;

import cn.minerealms.assimploader.AssimpLoader;
import cn.minerealms.assimploader.compat.OculusPBRSupport;
import cn.minerealms.assimploader.material.LabPbrPacker;
import cn.minerealms.assimploader.model.AssimpMaterial;
import cn.minerealms.assimploader.model.AssimpMesh;
import cn.minerealms.assimploader.model.AssimpScene;
import com.mojang.blaze3d.platform.NativeImage;
import com.mojang.blaze3d.systems.RenderSystem;
import net.minecraft.client.Minecraft;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.packs.resources.ResourceManager;

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.HashMap;
import java.util.IdentityHashMap;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.RejectedExecutionException;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import cn.minerealms.assimploader.util.ResourceLocations;
/** Material texture ownership shared by world, item and preview renderers, scoped to one resource generation. */
public final class ModelTextureCache {
    public static final ResourceLocation WHITE = ResourceLocations.of("minecraft", "textures/block/white_concrete.png");
    private static final int MAX_DIMENSION = 4096;
    private static final long MAX_PIXELS = 16L * 1024 * 1024;
    private static final Map<AssimpScene, SceneTextures> CACHE = new IdentityHashMap<>();
    private static final AtomicInteger WORKER_ID = new AtomicInteger();
    private static final ThreadPoolExecutor WORKERS = new ThreadPoolExecutor(1, 2, 30, TimeUnit.SECONDS,
            new ArrayBlockingQueue<>(64), task -> {
                Thread worker = new Thread(task, "AssimpLoader-textures-" + WORKER_ID.incrementAndGet());
                worker.setDaemon(true);
                return worker;
            }, new ThreadPoolExecutor.AbortPolicy());
    private static long nextTexture;

    private ModelTextureCache() {}

    public static ResourceLocation getTexture(AssimpScene scene, AssimpMesh mesh) {
        return getTexture(scene, material(scene, mesh));
    }

    public static ResourceLocation getTexture(AssimpScene scene, AssimpMaterial material) {
        if (material == null) return WHITE;
        RenderSystem.assertOnRenderThread();
        SceneTextures textures = CACHE.computeIfAbsent(scene, SceneTextures::new);
        Entry entry = textures.materials.computeIfAbsent(material,
                ignored -> textures.create(material, material.getDiffuseTexture()));
        entry.schedule();
        var view=cn.minerealms.assimploader.debug.MaterialDebugViews.current();
        if(view!=cn.minerealms.assimploader.config.AssimpLoaderConfig.MaterialView.DEFAULT) return entry.debug(view);
        OculusPBRSupport.prepareTexture(entry.location);
        return entry.location;
    }

    public static AssimpMaterial material(AssimpScene scene, AssimpMesh mesh) {
        int index = mesh.getMaterialIndex();
        AssimpMaterial override=cn.minerealms.assimploader.workbench.client.WorkbenchRuntime.material(scene,index);
        if(override!=null)return override;
        return index >= 0 && index < scene.getMaterials().size() ? scene.getMaterials().get(index) : null;
    }

    /** Release only an editor-owned variant, including an obsolete pending texture upload. */
    public static void releaseMaterial(AssimpScene scene, AssimpMaterial material) {
        RenderSystem.assertOnRenderThread();
        SceneTextures textures = CACHE.get(scene);
        Entry entry = textures == null ? null : textures.materials.remove(material);
        if (entry != null) entry.close(Minecraft.getInstance().getTextureManager());
    }

    public static ResourceLocation getEmbeddedTexture(AssimpScene scene, int index) {
        if (index < 0 || index >= scene.getTextureCount()) return null;
        RenderSystem.assertOnRenderThread();
        SceneTextures textures = CACHE.computeIfAbsent(scene, SceneTextures::new);
        Entry entry = textures.embedded.computeIfAbsent(index, ignored -> textures.create(null, "*" + index));
        entry.schedule();
        return entry.location;
    }

    /** Diagnostics distinguish prepared image channels from actual non-default Oculus bindings. */
    public static TextureStatus status(AssimpScene scene, AssimpMaterial material) {
        SceneTextures textures = CACHE.get(scene);
        Entry entry = textures == null ? null : textures.materials.get(material);
        if (entry == null || entry.texture == null) return new TextureStatus(WHITE, false, false, false, 0, 0, "none", "none", false);
        AssimpPbrTexture texture = entry.texture;
        return new TextureStatus(entry.location, texture.isReady(), texture.hasNormalMap(), texture.hasSpecularMap(),
                texture.normalTextureId(), texture.specularTextureId(), entry.normalSource, entry.specularSource, entry.failed);
    }

    public record TextureStatus(ResourceLocation albedo, boolean ready, boolean normalImage, boolean specularImage,
                                int normalTextureId, int specularTextureId, String normalSource,
                                String specularSource, boolean failed) {}

    /** Immutable prepared images also serve previews that do not use the Oculus pipeline. */
    public static cn.minerealms.assimploader.material.LabPbrPacker.PackedTextures packedTextures(AssimpScene scene,AssimpMaterial material) {
        RenderSystem.assertOnRenderThread();
        SceneTextures textures=CACHE.get(scene);
        Entry entry=textures==null?null:textures.materials.get(material);
        return entry==null||entry.texture==null?null:entry.texture.maps();
    }

    public static ResourceLocation resolveResource(ResourceLocation source, String texturePath) {
        if (texturePath == null || texturePath.isBlank() || texturePath.startsWith("*")) return null;
        String path = texturePath.replace('\\', '/');
        if (path.startsWith("/") || path.matches("^[A-Za-z]:.*")) return null;
        if (path.contains(":")) return ResourceLocation.tryParse(path);
        if (source == null) return ResourceLocation.tryParse(path);
        String parent = source.getPath().substring(0, source.getPath().lastIndexOf('/') + 1);
        Path resolved = Path.of(parent).resolve(path).normalize();
        if (resolved.isAbsolute() || resolved.startsWith("..")) return null;
        return ResourceLocation.tryParse(source.getNamespace() + ":" + resolved.toString().replace('\\', '/'));
    }

    /** Filesystem sources remain confined to the model's actual parent directory, including symlink resolution. */
    static Path resolveFile(Path source, String texturePath) throws IOException {
        return resolveFile(source,texturePath,false);
    }
    /** Only explicit editor material variants may use author-selected absolute files. */
    static Path resolveFile(Path source,String texturePath,boolean authored) throws IOException {
        if(authored&&texturePath!=null&&!texturePath.isBlank()){
            try{Path explicit=Path.of(texturePath.replace('\\','/'));if(explicit.isAbsolute())return Files.isRegularFile(explicit)?explicit.toAbsolutePath().normalize():null;}
            catch(java.nio.file.InvalidPathException invalid){return null;}
        }
        if (source == null || texturePath == null || texturePath.isBlank() || texturePath.startsWith("*")) return null;
        String path = texturePath.replace('\\', '/');
        if (path.startsWith("/") || path.contains(":")) return null;
        // Keep the lexical path for callers (important on Windows where the
        // input may use an 8.3 alias), while using canonical paths only for
        // the containment check so symlinks cannot escape the model folder.
        Path directory = source.toAbsolutePath().normalize().getParent();
        if (directory == null || !directory.startsWith(directory.getRoot())) return null;
        Path realDirectory = directory.toRealPath();
        Path candidate = directory.resolve(path).normalize();
        if (!candidate.startsWith(directory) || !Files.isRegularFile(candidate)) return null;
        return candidate.toRealPath().startsWith(realDirectory) ? candidate : null;
    }

    public static void clear() {
        if (!RenderSystem.isOnRenderThread()) {
            RenderSystem.recordRenderCall(ModelTextureCache::clear);
            return;
        }
        var manager = Minecraft.getInstance().getTextureManager();
        for (SceneTextures textures : CACHE.values()) {
            textures.closed = true;
            for (Entry entry : textures.materials.values()) entry.close(manager);
            for (Entry entry : textures.embedded.values()) entry.close(manager);
            textures.images.clear();
        }
        CACHE.clear();
        WORKERS.purge();
        OculusPBRSupport.reset();
    }

    private static final class SceneTextures {
        private final AssimpScene scene;
        private final ResourceManager resources;
        private final Map<AssimpMaterial, Entry> materials = new IdentityHashMap<>();
        private final Map<Integer, Entry> embedded = new HashMap<>();
        private final Map<String, Optional<LabPbrPacker.Image>> images = new ConcurrentHashMap<>();
        private volatile boolean closed;

        private SceneTextures(AssimpScene scene) {
            this.scene = scene;
            resources = Minecraft.getInstance().getResourceManager();
        }

        private Entry create(AssimpMaterial material, String path) {
            OculusPBRSupport.ensureLoaderRegistered();
            ResourceLocation location = ResourceLocations.of("assimploader", "runtime/material_" + nextTexture++);
            AssimpPbrTexture texture = null;
            try {
                texture = new AssimpPbrTexture();
                Minecraft.getInstance().getTextureManager().register(location, texture);
                return new Entry(this, material, path, location, texture);
            } catch (RuntimeException | LinkageError exception) {
                if (texture != null) { texture.close(); texture.releaseId(); }
                AssimpLoader.LOGGER.warn("Could not create model texture {}; using neutral albedo", path, exception);
                return new Entry(this, material, path, WHITE, null);
            }
        }

        private PbrTexturePreparation.Prepared prepare(AssimpMaterial material, String path, Path source) {
            String siblingBase = path;
            int embeddedIndex = scene.findEmbeddedTextureIndex(path);
            if (embeddedIndex >= 0) {
                String name = scene.getTextures().get(embeddedIndex).getFilename();
                if (name != null && !name.isBlank()) siblingBase = name.replace('\\', '/');
            }
            if (material != null && material.getPbrInputs().hasUnsupportedUv()) {
                AssimpLoader.LOGGER.warn("Model material {} references unsupported PBR UV sets; only UV0 channels will be used", material.getName());
            }
            return PbrTexturePreparation.prepare(path, siblingBase, material == null ? null : material.getPbrInputs(),
                    material == null ? cn.minerealms.assimploader.material.MaterialAlpha.SOURCE : material.getAlphaPolicy(),
                    reference -> image(reference,source,material!=null&&material.isDetached()));
        }

        private LabPbrPacker.Image image(String path,Path source,boolean authored) {
            if (closed || path == null || path.isBlank()) return null;
            int embedded=authoredAbsolute(path,authored)?-1:scene.findEmbeddedTextureIndex(path);
            String key=embedded>=0?"embedded:"+embedded:source+"\0"+authored+"\0"+path;
            return images.computeIfAbsent(key, ignored -> loadImage(path,source,authored)).orElse(null);
        }

        private static boolean authoredAbsolute(String path,boolean authored){
            if(!authored)return false;
            try{return Path.of(path.replace('\\','/')).isAbsolute();}catch(java.nio.file.InvalidPathException invalid){return false;}
        }

        private Optional<LabPbrPacker.Image> loadImage(String path,Path source,boolean authored) {
            try {
                if(authoredAbsolute(path,authored)) {
                    Path selected=resolveFile(source,path,true);if(selected==null)return Optional.empty();
                    try(InputStream stream=Files.newInputStream(selected);NativeImage image=NativeImage.read(stream)){return Optional.of(copy(image));}
                }
                int embeddedIndex = scene.findEmbeddedTextureIndex(path);
                if (embeddedIndex >= 0) {
                    try (NativeImage image = scene.getTextures().get(embeddedIndex).loadAsNativeImage()) {
                        return Optional.of(copy(image));
                    }
                }
                if(source!=null&&!java.util.Objects.equals(source,scene.getSourcePath())) {
                    Path local=resolveFile(source,path,authored);
                    if(local!=null)try(InputStream stream=Files.newInputStream(local);NativeImage image=NativeImage.read(stream)){return Optional.of(copy(image));}
                }
                ResourceLocation resource = resolveResource(scene.getSourceResource(), path);
                if (resource != null) {
                    var found = resources.getResource(resource);
                    if (found.isPresent()) {
                        try (InputStream stream = found.get().open(); NativeImage image = NativeImage.read(stream)) {
                            return Optional.of(copy(image));
                        }
                    }
                }
                Path file = resolveFile(source, path,authored);
                if (file != null) {
                    try (InputStream stream = Files.newInputStream(file); NativeImage image = NativeImage.read(stream)) {
                        return Optional.of(copy(image));
                    }
                }
            } catch (IOException | RuntimeException exception) {
                AssimpLoader.LOGGER.warn("Could not decode model image {}", path, exception);
            }
            return Optional.empty();
        }

        private static LabPbrPacker.Image copy(NativeImage image) throws IOException {
            int width = image.getWidth(), height = image.getHeight();
            if (width <= 0 || height <= 0 || width > MAX_DIMENSION || height > MAX_DIMENSION
                    || (long) width * height > MAX_PIXELS) throw new IOException("Model texture exceeds 4096x4096 limit");
            int[] pixels = new int[width * height];
            for (int y = 0; y < height; y++) {
                for (int x = 0; x < width; x++) pixels[y * width + x] = image.getPixelRGBA(x, y);
            }
            return new LabPbrPacker.Image(width, height, pixels);
        }
    }

    private static final class Entry {
        private final SceneTextures owner;
        private final AssimpMaterial material;
        private final String path;
        private final Path source;
        private final ResourceLocation location;
        private final AssimpPbrTexture texture;
        private CompletableFuture<PbrTexturePreparation.Prepared> pending;
        private PbrTexturePreparation.Prepared prepared;
        private final Map<cn.minerealms.assimploader.config.AssimpLoaderConfig.MaterialView,ResourceLocation> debugTextures=new java.util.EnumMap<>(cn.minerealms.assimploader.config.AssimpLoaderConfig.MaterialView.class);
        private final java.util.Set<cn.minerealms.assimploader.config.AssimpLoaderConfig.MaterialView> debugPending=java.util.EnumSet.noneOf(cn.minerealms.assimploader.config.AssimpLoaderConfig.MaterialView.class);
        private boolean failed;
        private boolean closed;
        private String normalSource = "pending";
        private String specularSource = "pending";

        private Entry(SceneTextures owner, AssimpMaterial material, String path, ResourceLocation location, AssimpPbrTexture texture) {
            this.owner = owner;
            this.material = material;
            this.path = path;
            this.source = cn.minerealms.assimploader.workbench.client.WorkbenchRuntime.textureSource(owner.scene);
            this.location = location;
            this.texture = texture;
            failed = texture == null;
        }

        private void schedule() {
            if (pending != null || failed || closed) return;
            try {
                pending = CompletableFuture.supplyAsync(() -> owner.prepare(material, path, source), WORKERS);
                pending.whenComplete((prepared, error) -> RenderSystem.recordRenderCall(() -> {
                    if (closed || owner.closed) return;
                    if (error != null) {
                        failed = true;
                        AssimpLoader.LOGGER.warn("Could not prepare model material {}", path, error);
                        return;
                    }
                    try {
                        OculusPBRSupport.invalidateTexture(texture);
                        texture.publish(prepared.albedo(), prepared.maps());
                        this.prepared=prepared;
                        normalSource = prepared.normalSource();
                        specularSource = prepared.specularSource();
                    } catch (RuntimeException | LinkageError exception) {
                        failed = true;
                        AssimpLoader.LOGGER.warn("Could not upload model material {}", path, exception);
                    }
                }));
            } catch (RejectedExecutionException busy) {
                // Queue capacity is bounded; retry scheduling on a later frame without doing work on the render thread.
                pending = null;
            }
        }

        private ResourceLocation debug(cn.minerealms.assimploader.config.AssimpLoaderConfig.MaterialView view) {
            ResourceLocation ready=debugTextures.get(view);
            if(ready!=null)return ready;
            if(prepared==null || debugPending.contains(view) || closed)return WHITE;
            var data=prepared;
            debugPending.add(view);
            try {
                CompletableFuture.supplyAsync(()->cn.minerealms.assimploader.debug.MaterialDebugViews.image(view,data.albedo(),data.maps()),WORKERS)
                    .whenComplete((image,error)->RenderSystem.recordRenderCall(()->{
                        debugPending.remove(view);
                        if(closed || owner.closed || error!=null)return;
                        var location=ResourceLocations.of("assimploader", "runtime/debug_"+nextTexture++);
                        var texture=new net.minecraft.client.renderer.texture.DynamicTexture(AssimpPbrTexture.nativeImage(image));
                        try {
                            Minecraft.getInstance().getTextureManager().register(location,texture);
                            texture.setFilter(false,false);debugTextures.put(view,location);
                        } catch(RuntimeException | Error failure) {texture.close();texture.releaseId();throw failure;}
                    }));
            } catch(RejectedExecutionException busy){debugPending.remove(view);}
            return WHITE;
        }

        private void close(net.minecraft.client.renderer.texture.TextureManager manager) {
            if (closed) return;
            closed = true;
            if (pending != null) pending.cancel(false);
            var retired = new java.util.ArrayList<>(debugTextures.values());
            if (texture != null) retired.add(location);
            debugTextures.clear();
            debugPending.clear();
            prepared = null;
            // Iris can still hold this location in a queued translucent/outline batch this frame.
            if (!retired.isEmpty()) ClientRenderResources.releaseAfterFrame(() -> retired.forEach(manager::release));
        }
    }
}
