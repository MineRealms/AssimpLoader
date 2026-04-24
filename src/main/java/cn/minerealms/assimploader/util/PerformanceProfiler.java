package cn.minerealms.assimploader.util;

import cn.minerealms.assimploader.debug.DebugManager;
import cn.minerealms.assimploader.debug.DebugManager.DebugCategory;

import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Performance profiler for AssimpLoader operations.
 *
 * Tracks:
 * - Model loading time
 * - Memory usage
 * - Cache hit/miss rates
 * - Rendering performance
 *
 * Usage:
 * ```java
 * PerformanceProfiler.startTimer("model_load");
 * // ... load model ...
 * PerformanceProfiler.endTimer("model_load");
 * PerformanceProfiler.printStats();
 * ```
 *
 * Thread-safe for concurrent profiling.
 */
public class PerformanceProfiler {

    private static final Map<String, TimerData> timers = new ConcurrentHashMap<>();
    private static final Map<String, Long> counters = new ConcurrentHashMap<>();
    private static boolean enabled = false;

    /**
     * Timer data container
     */
    private static class TimerData {
        private long startTime;
        private long totalTime;
        private long count;
        private long minTime = Long.MAX_VALUE;
        private long maxTime = 0;

        public void start() {
            startTime = System.nanoTime();
        }

        public void end() {
            long elapsed = System.nanoTime() - startTime;
            totalTime += elapsed;
            count++;
            minTime = Math.min(minTime, elapsed);
            maxTime = Math.max(maxTime, elapsed);
        }

        public double getAverageMs() {
            return count > 0 ? (totalTime / count) / 1_000_000.0 : 0.0;
        }

        public double getTotalMs() {
            return totalTime / 1_000_000.0;
        }

        public double getMinMs() {
            return minTime != Long.MAX_VALUE ? minTime / 1_000_000.0 : 0.0;
        }

        public double getMaxMs() {
            return maxTime / 1_000_000.0;
        }

        public long getCount() {
            return count;
        }
    }

    /**
     * Enable profiling
     */
    public static void enable() {
        enabled = true;
        DebugManager.info(DebugCategory.CORE, "Performance profiling enabled");
    }

    /**
     * Disable profiling
     */
    public static void disable() {
        enabled = false;
        DebugManager.info(DebugCategory.CORE, "Performance profiling disabled");
    }

    /**
     * Check if profiling is enabled
     */
    public static boolean isEnabled() {
        return enabled;
    }

    /**
     * Start timer
     */
    public static void startTimer(String name) {
        if (!enabled) return;

        TimerData timer = timers.computeIfAbsent(name, k -> new TimerData());
        timer.start();
    }

    /**
     * End timer
     */
    public static void endTimer(String name) {
        if (!enabled) return;

        TimerData timer = timers.get(name);
        if (timer != null) {
            timer.end();
        }
    }

    /**
     * Increment counter
     */
    public static void incrementCounter(String name) {
        if (!enabled) return;

        counters.merge(name, 1L, Long::sum);
    }

    /**
     * Increment counter by value
     */
    public static void incrementCounter(String name, long value) {
        if (!enabled) return;

        counters.merge(name, value, Long::sum);
    }

    /**
     * Get counter value
     */
    public static long getCounter(String name) {
        return counters.getOrDefault(name, 0L);
    }

    /**
     * Reset all statistics
     */
    public static void reset() {
        timers.clear();
        counters.clear();
        DebugManager.info(DebugCategory.CORE, "Performance statistics reset");
    }

    /**
     * Print statistics
     */
    public static void printStats() {
        if (timers.isEmpty() && counters.isEmpty()) {
            DebugManager.info(DebugCategory.CORE, "No performance data collected");
            return;
        }

        DebugManager.info(DebugCategory.CORE, "=== Performance Statistics ===");

        // Print timers
        if (!timers.isEmpty()) {
            DebugManager.info(DebugCategory.CORE, "Timers:");
            timers.forEach((name, timer) -> {
                DebugManager.info(DebugCategory.CORE, "  {}: count={}, avg={:.2f}ms, min={:.2f}ms, max={:.2f}ms, total={:.2f}ms",
                    name,
                    timer.getCount(),
                    timer.getAverageMs(),
                    timer.getMinMs(),
                    timer.getMaxMs(),
                    timer.getTotalMs()
                );
            });
        }

        // Print counters
        if (!counters.isEmpty()) {
            DebugManager.info(DebugCategory.CORE, "Counters:");
            counters.forEach((name, value) -> {
                DebugManager.info(DebugCategory.CORE, "  {}: {}", name, value);
            });
        }

        // Print memory usage
        printMemoryStats();

        DebugManager.info(DebugCategory.CORE, "==============================");
    }

    /**
     * Print memory statistics
     */
    public static void printMemoryStats() {
        Runtime runtime = Runtime.getRuntime();
        long totalMemory = runtime.totalMemory();
        long freeMemory = runtime.freeMemory();
        long usedMemory = totalMemory - freeMemory;
        long maxMemory = runtime.maxMemory();

        DebugManager.info(DebugCategory.CORE, "Memory:");
        DebugManager.info(DebugCategory.CORE, "  Used: {:.2f} MB", usedMemory / 1_048_576.0);
        DebugManager.info(DebugCategory.CORE, "  Free: {:.2f} MB", freeMemory / 1_048_576.0);
        DebugManager.info(DebugCategory.CORE, "  Total: {:.2f} MB", totalMemory / 1_048_576.0);
        DebugManager.info(DebugCategory.CORE, "  Max: {:.2f} MB", maxMemory / 1_048_576.0);
    }

    /**
     * Get timer statistics
     */
    public static Map<String, String> getTimerStats() {
        Map<String, String> stats = new HashMap<>();
        timers.forEach((name, timer) -> {
            stats.put(name, String.format("count=%d, avg=%.2fms, min=%.2fms, max=%.2fms, total=%.2fms",
                timer.getCount(),
                timer.getAverageMs(),
                timer.getMinMs(),
                timer.getMaxMs(),
                timer.getTotalMs()
            ));
        });
        return stats;
    }

    /**
     * Get counter statistics
     */
    public static Map<String, Long> getCounterStats() {
        return new HashMap<>(counters);
    }

    /**
     * Profile a runnable
     */
    public static void profile(String name, Runnable runnable) {
        startTimer(name);
        try {
            runnable.run();
        } finally {
            endTimer(name);
        }
    }

    /**
     * Profile a callable
     */
    public static <T> T profile(String name, java.util.concurrent.Callable<T> callable) throws Exception {
        startTimer(name);
        try {
            return callable.call();
        } finally {
            endTimer(name);
        }
    }
}
