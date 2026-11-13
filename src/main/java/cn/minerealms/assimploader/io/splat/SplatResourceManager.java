package cn.minerealms.assimploader.io.splat;

import cn.minerealms.assimploader.rendering.splat.data.GaussianSplatData;
import cn.minerealms.assimploader.config.AssimpLoaderConfig;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.packs.resources.ResourceManager;

import java.io.IOException;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Manages Gaussian splat resource loading and caching.
 *
 * Bounded cache; its owner calls clearCache on resource reload.
 */
public class SplatResourceManager {

    private final Map<ResourceLocation, GaussianSplatData> cache;
    private final ResourceManager resourceManager;

    public SplatResourceManager(ResourceManager resourceManager) {
        this.cache = new LinkedHashMap<>(16, 0.75f, true);
        this.resourceManager = resourceManager;
    }

    /**
     * Load splat data from resource location (with caching)
     *
     * @param location Resource location
     * @return Loaded splat data
     * @throws IOException If loading fails
     */
    public synchronized GaussianSplatData load(ResourceLocation location) throws IOException {
        // Check cache
        GaussianSplatData cached = cache.get(location);
        if (cached != null) {
            return cached;
        }

        // Load from resource
        GaussianSplatData data = SplatLoader.load(location, resourceManager);
        cache.put(location, data);
        int limit;
        try { limit = AssimpLoaderConfig.CLIENT.splatCacheSize.get(); }
        catch (IllegalStateException unloaded) { limit = AssimpLoaderConfig.CLIENT.splatCacheSize.getDefault(); }
        while (cache.size() > Math.max(1, limit)) cache.remove(cache.keySet().iterator().next());
        return data;
    }

    /**
     * Clear cache (for resource pack reload)
     */
    public synchronized void clearCache() {
        cache.clear();
    }

    /**
     * Reload a specific resource
     */
    public synchronized void reload(ResourceLocation location) throws IOException {
        cache.remove(location);
        load(location);
    }

    /**
     * Check if a resource is cached
     */
    public synchronized boolean isCached(ResourceLocation location) {
        return cache.containsKey(location);
    }

    /**
     * Get cache size
     */
    public synchronized int getCacheSize() {
        return cache.size();
    }
}
