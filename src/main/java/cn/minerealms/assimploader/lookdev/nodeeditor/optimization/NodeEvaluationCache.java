package cn.minerealms.assimploader.lookdev.nodeeditor.optimization;

import cn.minerealms.assimploader.debug.DebugManager;
import cn.minerealms.assimploader.debug.DebugManager.DebugCategory;
import cn.minerealms.assimploader.lookdev.nodeeditor.Node;
import cn.minerealms.assimploader.lookdev.nodeeditor.NodeSocket;

import java.util.HashMap;
import java.util.Map;

/**
 * Node Evaluation Cache
 *
 * Caches node computation results to avoid redundant calculations.
 *
 * Features:
 * - Per-socket value caching
 * - Automatic invalidation on node changes
 * - Cache hit/miss statistics
 * - Memory-efficient storage
 *
 * Usage:
 *   NodeEvaluationCache cache = new NodeEvaluationCache();
 *   Object value = cache.getCachedValue(socket);
 *   if (value == null) {
 *       value = computeValue(socket);
 *       cache.cacheValue(socket, value);
 *   }
 */
public class NodeEvaluationCache {

    private final Map<String, CacheEntry> cache = new HashMap<>();
    private long cacheHits = 0;
    private long cacheMisses = 0;
    private long totalEvaluations = 0;

    /**
     * Get cached value for a socket
     * @return cached value or null if not cached
     */
    public Object getCachedValue(NodeSocket socket) {
        String key = getCacheKey(socket);
        CacheEntry entry = cache.get(key);

        if (entry != null && !entry.isExpired()) {
            cacheHits++;
            return entry.value;
        }

        cacheMisses++;
        return null;
    }

    /**
     * Cache a value for a socket
     */
    public void cacheValue(NodeSocket socket, Object value) {
        String key = getCacheKey(socket);
        cache.put(key, new CacheEntry(value, System.currentTimeMillis()));
        totalEvaluations++;

        // Log cache stats occasionally
        if (totalEvaluations % 100 == 0) {
            logCacheStats();
        }
    }

    /**
     * Invalidate cache for a specific node
     */
    public void invalidateNode(Node node) {
        // Remove all cache entries for this node's sockets
        cache.keySet().removeIf(key -> key.startsWith(getNodePrefix(node)));

        DebugManager.debug(DebugCategory.LOOKDEV,
            "[NodeEvaluationCache] Invalidated cache for node: {}", node.getTitle());
    }

    /**
     * Invalidate cache for a specific socket
     */
    public void invalidateSocket(NodeSocket socket) {
        String key = getCacheKey(socket);
        cache.remove(key);
    }

    /**
     * Clear entire cache
     */
    public void clear() {
        int size = cache.size();
        cache.clear();
        cacheHits = 0;
        cacheMisses = 0;
        totalEvaluations = 0;

        DebugManager.debug(DebugCategory.LOOKDEV,
            "[NodeEvaluationCache] Cleared cache ({} entries)", size);
    }

    /**
     * Get cache statistics
     */
    public CacheStats getStats() {
        return new CacheStats(cacheHits, cacheMisses, cache.size(), totalEvaluations);
    }

    /**
     * Log cache statistics
     */
    private void logCacheStats() {
        if (totalEvaluations == 0) return;

        float hitRate = (float) cacheHits / (cacheHits + cacheMisses) * 100;
        DebugManager.debug(DebugCategory.LOOKDEV,
            "[NodeEvaluationCache] Stats - Entries: {}, Hit rate: {:.1f}%, Total evals: {}",
            cache.size(), hitRate, totalEvaluations);
    }

    /**
     * Generate cache key for a socket
     */
    private String getCacheKey(NodeSocket socket) {
        Node node = socket.getNode();
        return getNodePrefix(node) + ":" + socket.getName();
    }

    /**
     * Get node prefix for cache keys
     */
    private String getNodePrefix(Node node) {
        return node.getTitle() + "@" + System.identityHashCode(node);
    }

    /**
     * Cache entry with timestamp
     */
    private static class CacheEntry {
        final Object value;
        final long timestamp;
        static final long EXPIRY_TIME_MS = 5000; // 5 seconds

        CacheEntry(Object value, long timestamp) {
            this.value = value;
            this.timestamp = timestamp;
        }

        boolean isExpired() {
            return System.currentTimeMillis() - timestamp > EXPIRY_TIME_MS;
        }
    }

    /**
     * Cache statistics
     */
    public static class CacheStats {
        public final long hits;
        public final long misses;
        public final int size;
        public final long totalEvaluations;

        CacheStats(long hits, long misses, int size, long totalEvaluations) {
            this.hits = hits;
            this.misses = misses;
            this.size = size;
            this.totalEvaluations = totalEvaluations;
        }

        public float getHitRate() {
            long total = hits + misses;
            return total == 0 ? 0 : (float) hits / total * 100;
        }
    }
}
