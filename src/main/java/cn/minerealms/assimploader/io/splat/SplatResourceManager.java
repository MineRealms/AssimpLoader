package cn.minerealms.assimploader.io.splat;

import cn.minerealms.assimploader.rendering.splat.data.GaussianSplatData;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.packs.resources.ResourceManager;

import java.io.IOException;
import java.util.HashMap;
import java.util.Map;

/**
 * Manages Gaussian splat resource loading and caching.
 *
 * Provides caching and hot-reload detection for splat data.
 */
public class SplatResourceManager {

    private final Map<ResourceLocation, GaussianSplatData> cache;
    private final ResourceManager resourceManager;

    public SplatResourceManager(ResourceManager resourceManager) {
        this.cache = new HashMap<>();
        this.resourceManager = resourceManager;
    }

    /**
     * Load splat data from resource location (with caching)
     *
     * @param location Resource location
     * @return Loaded splat data
     * @throws IOException If loading fails
     */
    public GaussianSplatData load(ResourceLocation location) throws IOException {
        // Check cache
        GaussianSplatData cached = cache.get(location);
        if (cached != null) {
            return cached;
        }

        // Load from resource
        GaussianSplatData data = SplatLoader.load(location, resourceManager);
        cache.put(location, data);
        return data;
    }

    /**
     * Clear cache (for resource pack reload)
     */
    public void clearCache() {
        cache.clear();
    }

    /**
     * Reload a specific resource
     */
    public void reload(ResourceLocation location) throws IOException {
        cache.remove(location);
        load(location);
    }

    /**
     * Check if a resource is cached
     */
    public boolean isCached(ResourceLocation location) {
        return cache.containsKey(location);
    }

    /**
     * Get cache size
     */
    public int getCacheSize() {
        return cache.size();
    }
}
