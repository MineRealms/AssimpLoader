package cn.minerealms.iic.util;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import java.io.BufferedWriter;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardOpenOption;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;

/**
 * Tracks which mixins have been successfully loaded and applied.
 *
 * <p>This tracker is called from outside mixins to avoid issues if mixins fail to load.
 * It writes to a separate log file for easy debugging.
 */
public class MixinLoadTracker {

    private static final Logger LOGGER = LogManager.getLogger("IIC-MixinTracker");
    private static final Path LOG_FILE = Paths.get("logs/iic-mixin-status.log");
    private static final DateTimeFormatter TIME_FORMAT = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");

    private static final ConcurrentMap<String, MixinStatus> mixinStatuses = new ConcurrentHashMap<>();
    private static boolean initialized = false;

    public enum MixinStatus {
        NOT_LOADED,
        LOADED,
        APPLIED,
        FAILED
    }

    /**
     * Initialize the tracker and create log file.
     */
    public static synchronized void init() {
        if (initialized) {
            return;
        }

        try {
            Files.createDirectories(LOG_FILE.getParent());

            // Overwrite existing log file
            try (BufferedWriter writer = Files.newBufferedWriter(LOG_FILE,
                    StandardOpenOption.CREATE,
                    StandardOpenOption.TRUNCATE_EXISTING)) {
                writer.write("=== IIC Mixin Load Status ===\n");
                writer.write("Started: " + LocalDateTime.now().format(TIME_FORMAT) + "\n");
                writer.write("================================\n\n");
            }

            initialized = true;
            LOGGER.info("[MixinLoadTracker] Initialized, logging to: {}", LOG_FILE.toAbsolutePath());
        } catch (IOException e) {
            LOGGER.error("[MixinLoadTracker] Failed to initialize log file", e);
        }
    }

    /**
     * Mark a mixin as loaded (class exists and was instantiated).
     */
    public static void markLoaded(String mixinName) {
        init();
        mixinStatuses.put(mixinName, MixinStatus.LOADED);
        log("LOADED", mixinName, "Mixin class loaded successfully");
        LOGGER.info("[MixinLoadTracker] Mixin loaded: {}", mixinName);
    }

    /**
     * Mark a mixin as applied (injection successful).
     */
    public static void markApplied(String mixinName) {
        init();
        mixinStatuses.put(mixinName, MixinStatus.APPLIED);
        log("APPLIED", mixinName, "Mixin applied successfully");
        LOGGER.info("[MixinLoadTracker] Mixin applied: {}", mixinName);
    }

    /**
     * Mark a mixin as failed.
     */
    public static void markFailed(String mixinName, String reason) {
        init();
        mixinStatuses.put(mixinName, MixinStatus.FAILED);
        log("FAILED", mixinName, "Reason: " + reason);
        LOGGER.error("[MixinLoadTracker] Mixin failed: {} - {}", mixinName, reason);
    }

    /**
     * Check if a mixin is loaded and applied.
     */
    public static boolean isApplied(String mixinName) {
        return mixinStatuses.getOrDefault(mixinName, MixinStatus.NOT_LOADED) == MixinStatus.APPLIED;
    }

    /**
     * Get the status of a mixin.
     */
    public static MixinStatus getStatus(String mixinName) {
        return mixinStatuses.getOrDefault(mixinName, MixinStatus.NOT_LOADED);
    }

    /**
     * Get all tracked mixins and their statuses.
     */
    public static ConcurrentMap<String, MixinStatus> getAllStatuses() {
        return new ConcurrentHashMap<>(mixinStatuses);
    }

    /**
     * Write a log entry to the mixin status file.
     */
    private static void log(String level, String mixinName, String message) {
        if (!initialized) {
            return;
        }

        try (BufferedWriter writer = Files.newBufferedWriter(LOG_FILE,
                StandardOpenOption.CREATE,
                StandardOpenOption.APPEND)) {
            String timestamp = LocalDateTime.now().format(TIME_FORMAT);
            writer.write(String.format("[%s] [%s] %s: %s\n", timestamp, level, mixinName, message));
        } catch (IOException e) {
            LOGGER.error("[MixinLoadTracker] Failed to write to log file", e);
        }
    }

    /**
     * Check if EnhancedVisuals mod is present.
     */
    public static boolean isEnhancedVisualsPresent() {
        try {
            Class.forName("team.creative.enhancedvisuals.client.VisualManager", false, MixinLoadTracker.class.getClassLoader());
            log("CHECK", "EnhancedVisuals", "Mod is present");
            return true;
        } catch (ClassNotFoundException e) {
            log("CHECK", "EnhancedVisuals", "Mod is NOT present");
            return false;
        }
    }

    /**
     * Verify EnhancedVisuals mixin is working by checking if the mixin method was called.
     */
    public static boolean verifyEnhancedVisualsMixin() {
        String mixinName = "VisualManagerMixin";
        MixinStatus status = getStatus(mixinName);

        if (status == MixinStatus.APPLIED) {
            log("VERIFY", mixinName, "Mixin is confirmed working");
            return true;
        } else {
            log("VERIFY", mixinName, "Mixin status: " + status);
            return false;
        }
    }

    /**
     * Generate a status report for all mixins.
     */
    public static String generateReport() {
        init();
        StringBuilder report = new StringBuilder();
        report.append("=== IIC Mixin Status Report ===\n");
        report.append("Time: ").append(LocalDateTime.now().format(TIME_FORMAT)).append("\n\n");

        report.append("EnhancedVisuals Mod: ").append(isEnhancedVisualsPresent() ? "PRESENT" : "NOT PRESENT").append("\n\n");

        if (mixinStatuses.isEmpty()) {
            report.append("No mixins tracked yet.\n");
        } else {
            report.append("Tracked Mixins:\n");
            mixinStatuses.forEach((name, status) -> {
                report.append(String.format("  - %s: %s\n", name, status));
            });
        }

        return report.toString();
    }
}
