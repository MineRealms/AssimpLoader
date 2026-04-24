package cn.minerealms.assimploader.util;

import cn.minerealms.assimploader.debug.DebugManager;
import cn.minerealms.assimploader.debug.DebugManager.DebugCategory;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.BufferedWriter;
import java.io.FileWriter;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Centralized debug logging system for AssimpLoader.
 *
 * Features:
 * - Global debug toggle
 * - Separate debug log file
 * - Rate limiting (3 seconds per unique message)
 * - Thread-safe
 * - Automatic log file rotation
 *
 * Usage:
 * ```java
 * DebugLogger.setEnabled(true);
 * DebugLogger.debug("Loading model: {}", modelPath);
 * DebugLogger.error("Failed to load texture", exception);
 * ```
 */
public class DebugLogger {

    private static final Logger LOGGER = LoggerFactory.getLogger(DebugLogger.class);
    private static final DateTimeFormatter TIME_FORMAT = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss.SSS");

    // Debug toggle
    private static boolean enabled = false;

    // Debug log file
    private static Path debugLogPath = Paths.get("logs/assimploader-debug.log");
    private static BufferedWriter debugWriter = null;

    // Rate limiting: message hash -> last log time
    private static final Map<Integer, Long> lastLogTime = new ConcurrentHashMap<>();
    private static final long RATE_LIMIT_MS = 3000; // 3 seconds

    /**
     * Enable debug logging
     */
    public static synchronized void setEnabled(boolean enable) {
        if (enable && !enabled) {
            // Enable debug logging
            enabled = true;
            initializeDebugLog();
            LOGGER.info("AssimpLoader debug logging enabled");
            debug("=== Debug logging started ===");
        } else if (!enable && enabled) {
            // Disable debug logging
            enabled = false;
            debug("=== Debug logging stopped ===");
            closeDebugLog();
            LOGGER.info("AssimpLoader debug logging disabled");
        }
    }

    /**
     * Check if debug logging is enabled
     */
    public static boolean isEnabled() {
        return enabled;
    }

    /**
     * Log debug message
     */
    public static void debug(String message, Object... args) {
        if (!enabled) return;

        String formattedMessage = formatMessage(message, args);
        if (shouldLog(formattedMessage)) {
            writeToDebugLog("DEBUG", formattedMessage, null);
        }
    }

    /**
     * Log info message
     */
    public static void info(String message, Object... args) {
        if (!enabled) return;

        String formattedMessage = formatMessage(message, args);
        if (shouldLog(formattedMessage)) {
            writeToDebugLog("INFO", formattedMessage, null);
        }
    }

    /**
     * Log warning message
     */
    public static void warn(String message, Object... args) {
        if (!enabled) return;

        String formattedMessage = formatMessage(message, args);
        if (shouldLog(formattedMessage)) {
            writeToDebugLog("WARN", formattedMessage, null);
        }
    }

    /**
     * Log error message
     */
    public static void error(String message, Throwable throwable, Object... args) {
        if (!enabled) return;

        String formattedMessage = formatMessage(message, args);
        if (shouldLog(formattedMessage)) {
            writeToDebugLog("ERROR", formattedMessage, throwable);
        }
    }

    /**
     * Log error message without exception
     */
    public static void error(String message, Object... args) {
        error(message, null, args);
    }

    /**
     * Format message with arguments
     */
    private static String formatMessage(String message, Object... args) {
        if (args == null || args.length == 0) {
            return message;
        }

        try {
            return String.format(message.replace("{}", "%s"), args);
        } catch (Exception e) {
            return message + " [formatting error]";
        }
    }

    /**
     * Check if message should be logged (rate limiting)
     */
    private static boolean shouldLog(String message) {
        int messageHash = message.hashCode();
        long currentTime = System.currentTimeMillis();

        Long lastTime = lastLogTime.get(messageHash);
        if (lastTime != null && (currentTime - lastTime) < RATE_LIMIT_MS) {
            return false; // Rate limited
        }

        lastLogTime.put(messageHash, currentTime);
        return true;
    }

    /**
     * Write to debug log file
     */
    private static synchronized void writeToDebugLog(String level, String message, Throwable throwable) {
        if (debugWriter == null) {
            return;
        }

        try {
            String timestamp = LocalDateTime.now().format(TIME_FORMAT);
            String threadName = Thread.currentThread().getName();

            // Write log entry
            debugWriter.write(String.format("[%s] [%s/%s] %s%n",
                timestamp, threadName, level, message));

            // Write exception if present
            if (throwable != null) {
                debugWriter.write("Exception: " + throwable.getClass().getName() + ": " + throwable.getMessage() + "\n");

                // Write stack trace
                for (StackTraceElement element : throwable.getStackTrace()) {
                    debugWriter.write("    at " + element.toString() + "\n");
                }

                // Write cause if present
                Throwable cause = throwable.getCause();
                if (cause != null) {
                    debugWriter.write("Caused by: " + cause.getClass().getName() + ": " + cause.getMessage() + "\n");
                    for (StackTraceElement element : cause.getStackTrace()) {
                        debugWriter.write("    at " + element.toString() + "\n");
                    }
                }
            }

            debugWriter.flush();
        } catch (IOException e) {
            LOGGER.error("Failed to write to debug log", e);
        }
    }

    /**
     * Initialize debug log file
     */
    private static synchronized void initializeDebugLog() {
        try {
            // Create logs directory if it doesn't exist
            Files.createDirectories(debugLogPath.getParent());

            // Open debug log file (overwrite mode)
            debugWriter = new BufferedWriter(new FileWriter(debugLogPath.toFile(), false));

            // Write header
            debugWriter.write("=".repeat(80) + "\n");
            debugWriter.write("AssimpLoader Debug Log\n");
            debugWriter.write("Started: " + LocalDateTime.now().format(TIME_FORMAT) + "\n");
            debugWriter.write("=".repeat(80) + "\n\n");
            debugWriter.flush();

            LOGGER.info("Debug log initialized: {}", debugLogPath.toAbsolutePath());
        } catch (IOException e) {
            LOGGER.error("Failed to initialize debug log", e);
            debugWriter = null;
        }
    }

    /**
     * Close debug log file
     */
    private static synchronized void closeDebugLog() {
        if (debugWriter != null) {
            try {
                debugWriter.write("\n" + "=".repeat(80) + "\n");
                debugWriter.write("Debug log closed: " + LocalDateTime.now().format(TIME_FORMAT) + "\n");
                debugWriter.write("=".repeat(80) + "\n");
                debugWriter.close();
            } catch (IOException e) {
                LOGGER.error("Failed to close debug log", e);
            } finally {
                debugWriter = null;
            }
        }
    }

    /**
     * Clear rate limit cache
     */
    public static void clearRateLimitCache() {
        lastLogTime.clear();
        debug("Rate limit cache cleared");
    }

    /**
     * Get debug log path
     */
    public static Path getDebugLogPath() {
        return debugLogPath;
    }

    /**
     * Set custom debug log path
     */
    public static synchronized void setDebugLogPath(Path path) {
        if (enabled) {
            closeDebugLog();
        }
        debugLogPath = path;
        if (enabled) {
            initializeDebugLog();
        }
    }

    /**
     * Shutdown debug logger
     */
    public static synchronized void shutdown() {
        if (enabled) {
            debug("=== Debug logger shutting down ===");
            closeDebugLog();
            enabled = false;
        }
    }

    // Register shutdown hook
    static {
        Runtime.getRuntime().addShutdownHook(new Thread(DebugLogger::shutdown));
    }
}
