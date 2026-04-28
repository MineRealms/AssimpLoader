package cn.minerealms.assimploader.debug;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Centralized debug logging system for AssimpLoader.
 *
 * All debug output goes through this class with a unified prefix [ASSIMPLOADER].
 * Debug output can be toggled on/off via the enabled flag.
 * Includes rate limiting to prevent log spam from high-frequency operations.
 */
public class DebugManager {

    private static final Logger LOGGER = LoggerFactory.getLogger("AssimpLoader");
    private static final String PREFIX = "[ASSIMPLOADER] ";

    /**
     * Debug categories for different subsystems
     */
    public enum DebugCategory {
        CORE,       // Core loading and initialization
        RENDER,     // Rendering system
        MATERIAL,   // Material system
        ANIMATION,  // Animation system
        LOOKDEV,    // LookDev viewer
        MODERNUI,   // ModernUI integration
        CACHE,      // Cache system
        LIGHT,      // Lighting system
        PERF        // Performance profiling
    }

    /**
     * Global debug flag - controls all debug output
     * Default: enabled
     */
    private static boolean enabled = true;

    /**
     * Performance profiling flag - controls PerformanceProfiler
     * Default: disabled (enable for profiling sessions)
     */
    private static boolean profilingEnabled = false;

    /**
     * Rate limiting for high-frequency log messages
     * Key: message hash, Value: last log timestamp
     */
    private static final Map<Integer, Long> rateLimitCache = new ConcurrentHashMap<>();
    private static final long RATE_LIMIT_MS = 1000; // Log same message max once per second

    /**
     * Enable debug output
     */
    public static void enable() {
        enabled = true;
        info("Debug output enabled");
    }

    /**
     * Disable debug output
     */
    public static void disable() {
        info("Debug output disabled");
        enabled = false;
    }

    /**
     * Check if debug is enabled
     */
    public static boolean isEnabled() {
        return enabled;
    }

    /**
     * Set debug enabled state
     */
    public static void setEnabled(boolean state) {
        if (state) {
            enable();
        } else {
            disable();
        }
    }

    /**
     * Enable performance profiling
     */
    public static void enableProfiling() {
        profilingEnabled = true;
        PerformanceProfiler.initialize();
        info(DebugCategory.PERF, "Performance profiling enabled");
    }

    /**
     * Disable performance profiling
     */
    public static void disableProfiling() {
        info(DebugCategory.PERF, "Performance profiling disabled");
        PerformanceProfiler.shutdown();
        profilingEnabled = false;
    }

    /**
     * Check if profiling is enabled
     */
    public static boolean isProfilingEnabled() {
        return profilingEnabled && enabled;
    }

    /**
     * Set profiling enabled state
     */
    public static void setProfilingEnabled(boolean state) {
        if (state) {
            enableProfiling();
        } else {
            disableProfiling();
        }
    }

    // ==================== Debug Logging Methods ====================

    /**
     * Log debug message with category (rate-limited)
     */
    public static void debug(DebugCategory category, String message, Object... args) {
        if (enabled && shouldLog(message)) {
            LOGGER.debug(PREFIX + "[" + category.name() + "] " + message, args);
        }
    }

    /**
     * Log debug message with category (no rate limit)
     */
    public static void debugUnlimited(DebugCategory category, String message, Object... args) {
        if (enabled) {
            LOGGER.debug(PREFIX + "[" + category.name() + "] " + message, args);
        }
    }

    /**
     * Log info message with category
     */
    public static void info(DebugCategory category, String message, Object... args) {
        if (enabled) {
            LOGGER.info(PREFIX + "[" + category.name() + "] " + message, args);
        }
    }

    /**
     * Log warning message with category
     */
    public static void warn(DebugCategory category, String message, Object... args) {
        if (enabled) {
            LOGGER.warn(PREFIX + "[" + category.name() + "] " + message, args);
        }
    }

    /**
     * Log error message with category
     */
    public static void error(DebugCategory category, String message, Object... args) {
        if (enabled) {
            LOGGER.error(PREFIX + "[" + category.name() + "] " + message, args);
        }
    }

    /**
     * Log error with exception and category
     */
    public static void error(DebugCategory category, String message, Throwable throwable) {
        if (enabled) {
            LOGGER.error(PREFIX + "[" + category.name() + "] " + message, throwable);
        }
    }

    /**
     * Log debug message
     */
    public static void debug(String message, Object... args) {
        if (enabled) {
            LOGGER.debug(PREFIX + message, args);
        }
    }

    /**
     * Log info message
     */
    public static void info(String message, Object... args) {
        if (enabled) {
            LOGGER.info(PREFIX + message, args);
        }
    }

    /**
     * Log warning message
     */
    public static void warn(String message, Object... args) {
        if (enabled) {
            LOGGER.warn(PREFIX + message, args);
        }
    }

    /**
     * Log error message
     */
    public static void error(String message, Object... args) {
        if (enabled) {
            LOGGER.error(PREFIX + message, args);
        }
    }

    /**
     * Log error with exception
     */
    public static void error(String message, Throwable throwable) {
        if (enabled) {
            LOGGER.error(PREFIX + message, throwable);
        }
    }

    // ==================== Category-Specific Logging ====================

    /**
     * Log model loading debug info
     */
    public static void logModelLoading(String message, Object... args) {
        debug("[MODEL] " + message, args);
    }

    /**
     * Log material debug info
     */
    public static void logMaterial(String message, Object... args) {
        debug("[MATERIAL] " + message, args);
    }

    /**
     * Log rendering debug info
     */
    public static void logRendering(String message, Object... args) {
        debug("[RENDER] " + message, args);
    }

    /**
     * Log animation debug info
     */
    public static void logAnimation(String message, Object... args) {
        debug("[ANIMATION] " + message, args);
    }

    /**
     * Log LookDev viewer debug info
     */
    public static void logLookDev(String message, Object... args) {
        debug("[LOOKDEV] " + message, args);
    }

    /**
     * Log Oculus integration debug info
     */
    public static void logOculus(String message, Object... args) {
        debug("[OCULUS] " + message, args);
    }

    /**
     * Log texture loading debug info
     */
    public static void logTexture(String message, Object... args) {
        debug("[TEXTURE] " + message, args);
    }

    // ==================== Exception Handling Utilities ====================

    /**
     * Execute code with exception handling and logging
     */
    public static <T> T tryCatch(String operation, ThrowingSupplier<T> supplier, T defaultValue) {
        try {
            return supplier.get();
        } catch (Exception e) {
            error("Exception during {}: {}", operation, e.getMessage(), e);
            return defaultValue;
        }
    }

    /**
     * Execute code with exception handling and logging (void return)
     */
    public static void tryCatch(String operation, ThrowingRunnable runnable) {
        try {
            runnable.run();
        } catch (Exception e) {
            error("Exception during {}: {}", operation, e.getMessage(), e);
        }
    }

    /**
     * Functional interface for operations that may throw exceptions
     */
    @FunctionalInterface
    public interface ThrowingSupplier<T> {
        T get() throws Exception;
    }

    /**
     * Functional interface for void operations that may throw exceptions
     */
    @FunctionalInterface
    public interface ThrowingRunnable {
        void run() throws Exception;
    }

    // ==================== Performance Timing ====================

    /**
     * Time an operation and log the duration
     */
    public static <T> T timeOperation(String operation, ThrowingSupplier<T> supplier) {
        if (!enabled) {
            try {
                return supplier.get();
            } catch (Exception e) {
                error("Exception during {}: {}", operation, e.getMessage(), e);
                return null;
            }
        }

        long startTime = System.nanoTime();
        try {
            T result = supplier.get();
            long duration = System.nanoTime() - startTime;
            debug("[TIMING] {} took {:.2f}ms", operation, duration / 1_000_000.0);
            return result;
        } catch (Exception e) {
            long duration = System.nanoTime() - startTime;
            error("[TIMING] {} failed after {:.2f}ms: {}", operation, duration / 1_000_000.0, e.getMessage(), e);
            return null;
        }
    }

    /**
     * Time a void operation and log the duration
     */
    public static void timeOperation(String operation, ThrowingRunnable runnable) {
        if (!enabled) {
            try {
                runnable.run();
            } catch (Exception e) {
                error("Exception during {}: {}", operation, e.getMessage(), e);
            }
            return;
        }

        long startTime = System.nanoTime();
        try {
            runnable.run();
            long duration = System.nanoTime() - startTime;
            debug("[TIMING] {} took {:.2f}ms", operation, duration / 1_000_000.0);
        } catch (Exception e) {
            long duration = System.nanoTime() - startTime;
            error("[TIMING] {} failed after {:.2f}ms: {}", operation, duration / 1_000_000.0, e.getMessage(), e);
        }
    }

    // ==================== Rate Limiting ====================

    /**
     * Check if a message should be logged based on rate limiting
     */
    private static boolean shouldLog(String message) {
        int hash = message.hashCode();
        long now = System.currentTimeMillis();

        Long lastLog = rateLimitCache.get(hash);
        if (lastLog == null || now - lastLog >= RATE_LIMIT_MS) {
            rateLimitCache.put(hash, now);
            return true;
        }

        return false;
    }

    /**
     * Clear rate limit cache
     */
    public static void clearRateLimitCache() {
        rateLimitCache.clear();
    }
}
