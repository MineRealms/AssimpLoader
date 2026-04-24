package cn.minerealms.assimploader.cache;

import cn.minerealms.assimploader.debug.DebugManager;
import cn.minerealms.assimploader.debug.DebugManager.DebugCategory;
import cn.minerealms.assimploader.model.AssimpScene;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Model cache manager for efficient model reuse.
 *
 * Caches loaded models to avoid redundant file I/O and parsing.
 * Thread-safe for async loading.
 *
 * Phase 5 implementation - resource loading and caching
 */
public class ModelCacheManager {

    private static final ModelCacheManager INSTANCE = new ModelCacheManager();
    private final Map<String, AssimpScene> cache = new ConcurrentHashMap<>();
    private long totalMemoryUsage = 0;

    private ModelCacheManager() {
        DebugManager.info(DebugCategory.CACHE, "ModelCacheManager initialized");
    }

    public static ModelCacheManager getInstance() {
        return INSTANCE;
    }

    /**
     * Load a model from path (with caching)
     */
    public AssimpScene loadModel(String resourcePath) {
        // Check cache first
        if (cache.containsKey(resourcePath)) {
            DebugManager.debug(DebugCategory.CACHE, "Model loaded from cache: {}", resourcePath);
            return cache.get(resourcePath);
        }

        // Load model (implementation depends on resource system)
        // For now, return null - will be implemented with resource pack integration
        DebugManager.warn(DebugCategory.CACHE, "Model loading not yet implemented: {}", resourcePath);
        return null;
    }

    /**
     * Get cached model by resource path
     */
    public AssimpScene get(String resourcePath) {
        return cache.get(resourcePath);
    }

    /**
     * Cache a loaded model
     */
    public void put(String resourcePath, AssimpScene scene) {
        cache.put(resourcePath, scene);
        DebugManager.debug(DebugCategory.CACHE, "Cached model: {}", resourcePath);
    }

    /**
     * Check if model is cached
     */
    public boolean contains(String resourcePath) {
        return cache.containsKey(resourcePath);
    }

    /**
     * Remove model from cache and release memory
     */
    public void remove(String resourcePath) {
        AssimpScene scene = cache.remove(resourcePath);
        if (scene != null) {
            scene.release();
            DebugManager.debug(DebugCategory.CACHE, "Removed from cache: {}", resourcePath);
        }
    }

    /**
     * Clear all cached models
     */
    public void clearCache() {
        for (AssimpScene scene : cache.values()) {
            scene.release();
        }
        cache.clear();
        totalMemoryUsage = 0;
        DebugManager.info(DebugCategory.CACHE, "Model cache cleared");
    }

    /**
     * Get cache size
     */
    public int size() {
        return cache.size();
    }
}
