package cn.minerealms.assimploader.cache;

import cn.minerealms.assimploader.AssimpLoader;
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

    private final Map<String, AssimpScene> cache = new ConcurrentHashMap<>();
    private long totalMemoryUsage = 0;

    public ModelCacheManager() {
        AssimpLoader.LOGGER.info("ModelCacheManager initialized");
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
        AssimpLoader.LOGGER.debug("Cached model: {}", resourcePath);
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
            AssimpLoader.LOGGER.debug("Removed from cache: {}", resourcePath);
        }
    }

    /**
     * Clear all cached models
     */
    public void clear() {
        for (AssimpScene scene : cache.values()) {
            scene.release();
        }
        cache.clear();
        totalMemoryUsage = 0;
        AssimpLoader.LOGGER.info("Model cache cleared");
    }

    /**
     * Get cache size
     */
    public int size() {
        return cache.size();
    }
}
