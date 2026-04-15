package io.github.flemmli97.improvedmobs.industrial;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.apache.logging.log4j.core.LoggerContext;
import org.apache.logging.log4j.core.appender.FileAppender;
import org.apache.logging.log4j.core.config.Configuration;
import org.apache.logging.log4j.core.layout.PatternLayout;

import java.io.File;
import java.nio.charset.StandardCharsets;

/**
 * Unified logging system for industrial integration features.
 * <p>
 * This logger provides a centralized logging facility for all industrial integration components,
 * including GregTech CEu integration, Spore integration, pollution system, and threat management.
 * <p>
 * Features:
 * <ul>
 *   <li>Uses Log4j with [IndustrialIntegrationCraft] tag for easy filtering</li>
 *   <li>Separate debug log file: logs/iic-debug.log (overwritten on each server start)</li>
 *   <li>Optional output to latest.log (disabled by default for performance)</li>
 *   <li>Performance optimization: debug logs only execute string formatting when enabled</li>
 *   <li>Categorized debug methods for different subsystems (GT, Spore, Machine, Pollution, Difficulty)</li>
 * </ul>
 * <p>
 * Debug mode is disabled by default and can be enabled via the command:
 * <code>/im industrial debug on</code>
 *
 * @see GTIntegration
 * @see SporeIntegration
 * @see PollutionManager
 * @see ThreatManager
 * @author ImprovedMobs Team
 */
public class IndustrialLogger {

    private static final Logger LOGGER = LogManager.getLogger("IndustrialIntegrationCraft");
    private static final String LOG_FILE = "logs/iic-debug.log";

    /**
     * Debug mode toggle (controlled via commands).
     * Default: false
     */
    private static boolean debugEnabled = false;

    /**
     * Whether to also output to latest.log (disabled by default for performance).
     */
    private static boolean outputToMainLog = false;

    /**
     * Whether the separate log file has been initialized.
     */
    private static boolean fileAppenderInitialized = false;

    static {
        initializeFileAppender();
    }

    /**
     * Initializes the separate debug log file.
     * <p>
     * This method is called automatically during class initialization. It creates
     * a dedicated log file at logs/iic-debug.log that is overwritten on each server start.
     * The file appender is configured with immediate flush to ensure logs are not lost.
     */
    private static void initializeFileAppender() {
        try {
            // Ensure logs directory exists
            File logsDir = new File("logs");
            if (!logsDir.exists()) {
                logsDir.mkdirs();
            }

            // Delete old log file (overwrite on each start)
            File logFile = new File(LOG_FILE);
            if (logFile.exists()) {
                logFile.delete();
            }

            // Get Log4j configuration
            LoggerContext context = (LoggerContext) LogManager.getContext(false);
            Configuration config = context.getConfiguration();

            // Create log pattern
            PatternLayout layout = PatternLayout.newBuilder()
                    .withPattern("[%d{HH:mm:ss}] [%t/%level] [%logger]: %msg%n")
                    .withCharset(StandardCharsets.UTF_8)
                    .withConfiguration(config)
                    .build();

            // Create file appender
            FileAppender appender = FileAppender.newBuilder()
                    .setName("IICDebugFile")
                    .withFileName(LOG_FILE)
                    .withAppend(false) // Don't append, overwrite each time
                    .withImmediateFlush(true) // Immediate flush to ensure logs are not lost
                    .setLayout(layout)
                    .setConfiguration(config)
                    .build();

            appender.start();

            // Add to logger
            org.apache.logging.log4j.core.Logger coreLogger = (org.apache.logging.log4j.core.Logger) LOGGER;
            coreLogger.addAppender(appender);

            fileAppenderInitialized = true;
            // Don't output initialization info to console, keep it quiet
            // Only record in file
            LOGGER.info("=== Industrial Integration Craft Debug Log ===");
            LOGGER.info("Log file: " + LOG_FILE);
            LOGGER.info("This file is overwritten on each server start");
            LOGGER.info("Use /im industrial debug on to enable debug logging");
            LOGGER.info("==============================================");

        } catch (Exception e) {
            System.err.println("[IndustrialLogger] Failed to initialize file appender: " + e.getMessage());
            e.printStackTrace();
        }
    }

    /**
     * Enables or disables debug mode.
     * <p>
     * When enabled, all debug log methods will output their messages.
     * When disabled, debug log methods are no-ops for performance.
     *
     * @param enabled true to enable debug logging, false to disable
     */
    public static void setDebugEnabled(boolean enabled) {
        debugEnabled = enabled;
        if (enabled) {
            info("Debug mode ENABLED");
        } else {
            info("Debug mode DISABLED");
        }
    }

    /**
     * Checks if debug mode is currently enabled.
     *
     * @return true if debug logging is enabled
     */
    public static boolean isDebugEnabled() {
        return debugEnabled;
    }

    /**
     * Sets whether to also output logs to the main latest.log file.
     * <p>
     * Disabled by default for performance. All logs are always written to
     * the dedicated iic-debug.log file regardless of this setting.
     *
     * @param enabled true to also output to latest.log
     */
    public static void setOutputToMainLog(boolean enabled) {
        outputToMainLog = enabled;
    }

    // === Standard logging methods (always output) ===

    /**
     * Logs an informational message.
     *
     * @param message the message to log
     */
    public static void info(String message) {
        LOGGER.info(message);
    }

    /**
     * Logs a warning message.
     *
     * @param message the warning message to log
     */
    public static void warn(String message) {
        LOGGER.warn(message);
    }

    /**
     * Logs an error message.
     *
     * @param message the error message to log
     */
    public static void error(String message) {
        LOGGER.error(message);
    }

    /**
     * Logs an error message with an exception.
     *
     * @param message the error message to log
     * @param throwable the exception to log
     */
    public static void error(String message, Throwable throwable) {
        LOGGER.error(message, throwable);
    }

    // === Debug logging methods (only output when debug mode is enabled) ===

    /**
     * Logs a debug message.
     * <p>
     * Performance optimization: Only outputs when debug mode is enabled.
     * This method should be used for general debug information.
     *
     * @param message the debug message to log
     */
    public static void debug(String message) {
        if (debugEnabled) {
            LOGGER.info("[DEBUG] " + message);
        }
    }

    /**
     * Logs a formatted debug message.
     * <p>
     * Performance optimization: String formatting only occurs when debug mode is enabled.
     * Use this method when you need to format debug messages with parameters.
     *
     * @param format the format string (uses String.format syntax)
     * @param args the arguments for the format string
     */
    public static void debugFormat(String format, Object... args) {
        if (debugEnabled) {
            LOGGER.info("[DEBUG] " + String.format(format, args));
        }
    }

    // === Categorized debug logging (only output when debug mode is enabled) ===

    /**
     * Logs a debug message related to GregTech CEu integration.
     *
     * @param message the debug message to log
     */
    public static void debugGT(String message) {
        if (debugEnabled) {
            LOGGER.info("[DEBUG][GT] " + message);
        }
    }

    /**
     * Logs a debug message related to Spore mod integration.
     *
     * @param message the debug message to log
     */
    public static void debugSpore(String message) {
        if (debugEnabled) {
            LOGGER.info("[DEBUG][Spore] " + message);
        }
    }

    /**
     * Logs a debug message related to machine scanning and detection.
     *
     * @param message the debug message to log
     */
    public static void debugMachine(String message) {
        if (debugEnabled) {
            LOGGER.info("[DEBUG][Machine] " + message);
        }
    }

    /**
     * Logs a debug message related to pollution system.
     *
     * @param message the debug message to log
     */
    public static void debugPollution(String message) {
        if (debugEnabled) {
            LOGGER.info("[DEBUG][Pollution] " + message);
        }
    }

    /**
     * Logs a debug message related to difficulty calculation.
     *
     * @param message the debug message to log
     */
    public static void debugDifficulty(String message) {
        if (debugEnabled) {
            LOGGER.info("[DEBUG][Difficulty] " + message);
        }
    }

    /**
     * Logs performance monitoring information.
     * <p>
     * Use this method to track the execution time of operations.
     *
     * @param operation the name of the operation being measured
     * @param timeMs the time taken in milliseconds
     */
    public static void debugPerformance(String operation, long timeMs) {
        if (debugEnabled) {
            LOGGER.info(String.format("[DEBUG][Performance] %s took %d ms", operation, timeMs));
        }
    }

    /**
     * Gets the path to the debug log file.
     *
     * @return the log file path
     */
    public static String getLogFilePath() {
        return LOG_FILE;
    }

    /**
     * Checks if the file appender has been successfully initialized.
     *
     * @return true if the file appender is initialized
     */
    public static boolean isFileAppenderInitialized() {
        return fileAppenderInitialized;
    }
}
