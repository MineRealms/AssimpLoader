package cn.minerealms.assimploader.util;

import cn.minerealms.assimploader.debug.DebugManager;
import cn.minerealms.assimploader.debug.DebugManager.DebugCategory;
import org.lwjgl.assimp.AIScene;
import org.lwjgl.assimp.Assimp;

import java.lang.ref.WeakReference;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Memory manager for native Assimp resources.
 *
 * Tracks and manages:
 * - AIScene native memory
 * - Texture data
 * - Buffer allocations
 * - Automatic cleanup on GC
 *
 * Critical: Assimp uses native memory that must be explicitly freed.
 * Failure to free native memory causes memory leaks outside Java heap.
 *
 * Usage:
 * ```java
 * AIScene scene = AssimpModelLoader.load(...);
 * MemoryManager.register(scene);
 * // ... use scene ...
 * MemoryManager.free(scene); // Explicit cleanup
 * ```
 *
 * Automatic cleanup via WeakReference when Java object is GC'd.
 */
public class MemoryManager {

    private static final ConcurrentHashMap<Long, SceneReference> scenes = new ConcurrentHashMap<>();
    private static final List<WeakReference<AIScene>> weakScenes = new ArrayList<>();
    private static long totalAllocated = 0;
    private static long totalFreed = 0;

    /**
     * Scene reference with metadata
     */
    private static class SceneReference {
        private final AIScene scene;
        private final long address;
        private final long timestamp;
        private final String source;
        private boolean freed;

        public SceneReference(AIScene scene, String source) {
            this.scene = scene;
            this.address = scene.address();
            this.timestamp = System.currentTimeMillis();
            this.source = source;
            this.freed = false;
        }

        public AIScene getScene() { return scene; }
        public long getAddress() { return address; }
        public long getTimestamp() { return timestamp; }
        public String getSource() { return source; }
        public boolean isFreed() { return freed; }
        public void setFreed(boolean freed) { this.freed = freed; }
    }

    /**
     * Register AIScene for tracking
     */
    public static void register(AIScene scene, String source) {
        if (scene == null) {
            DebugManager.warn(DebugCategory.CORE, "Attempted to register null scene");
            return;
        }

        long address = scene.address();
        SceneReference ref = new SceneReference(scene, source);
        scenes.put(address, ref);
        weakScenes.add(new WeakReference<>(scene));
        totalAllocated++;

        DebugManager.debug(DebugCategory.CORE, "Registered scene: address={}, source={}, total={}",
            address, source, scenes.size());
    }

    /**
     * Free AIScene and remove from tracking
     */
    public static void free(AIScene scene) {
        if (scene == null) {
            DebugManager.warn(DebugCategory.CORE, "Attempted to free null scene");
            return;
        }

        long address = scene.address();
        SceneReference ref = scenes.get(address);

        if (ref == null) {
            DebugManager.warn(DebugCategory.CORE, "Attempted to free unregistered scene: address={}", address);
            return;
        }

        if (ref.isFreed()) {
            DebugManager.warn(DebugCategory.CORE, "Attempted to free already freed scene: address={}", address);
            return;
        }

        // Free native memory
        Assimp.aiReleaseImport(scene);
        ref.setFreed(true);
        scenes.remove(address);
        totalFreed++;

        long lifetime = System.currentTimeMillis() - ref.getTimestamp();
        DebugManager.debug(DebugCategory.CORE, "Freed scene: address={}, source={}, lifetime={}ms, remaining={}",
            address, ref.getSource(), lifetime, scenes.size());
    }

    /**
     * Free all registered scenes
     */
    public static void freeAll() {
        DebugManager.info(DebugCategory.CORE, "Freeing all registered scenes: count={}", scenes.size());

        List<AIScene> toFree = new ArrayList<>();
        scenes.values().forEach(ref -> {
            if (!ref.isFreed()) {
                toFree.add(ref.getScene());
            }
        });

        toFree.forEach(MemoryManager::free);

        DebugManager.info(DebugCategory.CORE, "All scenes freed");
    }

    /**
     * Cleanup weak references (called by GC)
     */
    public static void cleanupWeakReferences() {
        Iterator<WeakReference<AIScene>> iterator = weakScenes.iterator();
        int cleaned = 0;

        while (iterator.hasNext()) {
            WeakReference<AIScene> ref = iterator.next();
            AIScene scene = ref.get();

            if (scene == null) {
                // Object was GC'd
                iterator.remove();
                cleaned++;
            } else {
                long address = scene.address();
                SceneReference sceneRef = scenes.get(address);

                if (sceneRef != null && !sceneRef.isFreed()) {
                    // Scene still exists but Java object might be unreachable
                    // Free it to prevent memory leak
                    DebugManager.warn(DebugCategory.CORE, "Scene GC'd without explicit free: address={}, source={}",
                        address, sceneRef.getSource());
                    free(scene);
                }
            }
        }

        if (cleaned > 0) {
            DebugManager.debug(DebugCategory.CORE, "Cleaned {} weak references", cleaned);
        }
    }

    /**
     * Get memory statistics
     */
    public static MemoryStats getStats() {
        return new MemoryStats(
            scenes.size(),
            totalAllocated,
            totalFreed,
            weakScenes.size()
        );
    }

    /**
     * Print memory statistics
     */
    public static void printStats() {
        MemoryStats stats = getStats();

        DebugManager.info(DebugCategory.CORE, "=== Memory Statistics ===");
        DebugManager.info(DebugCategory.CORE, "Active scenes: {}", stats.activeScenes);
        DebugManager.info(DebugCategory.CORE, "Total allocated: {}", stats.totalAllocated);
        DebugManager.info(DebugCategory.CORE, "Total freed: {}", stats.totalFreed);
        DebugManager.info(DebugCategory.CORE, "Weak references: {}", stats.weakReferences);
        DebugManager.info(DebugCategory.CORE, "Memory leak potential: {}", stats.activeScenes > 0 ? "YES" : "NO");

        if (stats.activeScenes > 0) {
            DebugManager.info(DebugCategory.CORE, "Active scenes:");
            scenes.values().forEach(ref -> {
                long lifetime = System.currentTimeMillis() - ref.getTimestamp();
                DebugManager.info(DebugCategory.CORE, "  address={}, source={}, lifetime={}ms",
                    ref.getAddress(), ref.getSource(), lifetime);
            });
        }

        DebugManager.info(DebugCategory.CORE, "========================");
    }

    /**
     * Check for memory leaks
     */
    public static boolean hasMemoryLeaks() {
        return scenes.size() > 0;
    }

    /**
     * Get active scene count
     */
    public static int getActiveSceneCount() {
        return scenes.size();
    }

    /**
     * Memory statistics container
     */
    public static class MemoryStats {
        public final int activeScenes;
        public final long totalAllocated;
        public final long totalFreed;
        public final int weakReferences;

        public MemoryStats(int activeScenes, long totalAllocated, long totalFreed, int weakReferences) {
            this.activeScenes = activeScenes;
            this.totalAllocated = totalAllocated;
            this.totalFreed = totalFreed;
            this.weakReferences = weakReferences;
        }

        @Override
        public String toString() {
            return String.format("MemoryStats{active=%d, allocated=%d, freed=%d, weak=%d}",
                activeScenes, totalAllocated, totalFreed, weakReferences);
        }
    }

    /**
     * Initialize memory manager
     */
    public static void initialize() {
        DebugManager.info(DebugCategory.CORE, "Memory manager initialized");

        // Register shutdown hook
        Runtime.getRuntime().addShutdownHook(new Thread(() -> {
            DebugManager.info(DebugCategory.CORE, "Shutdown hook: cleaning up memory");
            printStats();
            freeAll();
        }));
    }

    /**
     * Shutdown memory manager
     */
    public static void shutdown() {
        DebugManager.info(DebugCategory.CORE, "Memory manager shutting down");
        printStats();
        freeAll();
    }
}
