package cn.minerealms.assimploader.debug;

import java.io.BufferedWriter;
import java.io.File;
import java.io.FileWriter;
import java.io.IOException;
import java.text.SimpleDateFormat;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicLong;

/**
 * Performance profiling system for AssimpLoader.
 *
 * Features:
 * - Per-operation timing and call counting
 * - Automatic statistics (min/max/avg/total)
 * - Separate log file for performance data
 * - Controlled by DebugManager.isEnabled()
 * - Thread-safe for concurrent rendering
 *
 * Usage:
 * <pre>
 * PerformanceProfiler.start("render_entity");
 * // ... do work ...
 * PerformanceProfiler.end("render_entity");
 *
 * // Or use auto-closeable:
 * try (var timer = PerformanceProfiler.time("render_entity")) {
 *     // ... do work ...
 * }
 * </pre>
 */
public class PerformanceProfiler {

    private static final String LOG_FILE = "logs/assimploader_performance.log";
    private static final SimpleDateFormat DATE_FORMAT = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss");

    // Performance counters
    private static final Map<String, PerformanceCounter> counters = new ConcurrentHashMap<>();
    private static final ThreadLocal<Map<String, Long>> activeTimers = ThreadLocal.withInitial(HashMap::new);

    // Statistics tracking
    private static long lastReportTime = System.currentTimeMillis();
    private static final long REPORT_INTERVAL_MS = 60_000; // Report every 60 seconds
    private static boolean autoReportEnabled = true;

    // File writer
    private static BufferedWriter logWriter = null;
    private static final Object writerLock = new Object();

    /**
     * Performance counter for a single operation
     */
    private static class PerformanceCounter {
        private final String name;
        private final AtomicLong callCount = new AtomicLong(0);
        private final AtomicLong totalTimeNanos = new AtomicLong(0);
        private volatile long minTimeNanos = Long.MAX_VALUE;
        private volatile long maxTimeNanos = Long.MIN_VALUE;

        public PerformanceCounter(String name) {
            this.name = name;
        }

        public void record(long durationNanos) {
            callCount.incrementAndGet();
            totalTimeNanos.addAndGet(durationNanos);

            // Update min/max (not perfectly thread-safe but good enough for profiling)
            if (durationNanos < minTimeNanos) {
                minTimeNanos = durationNanos;
            }
            if (durationNanos > maxTimeNanos) {
                maxTimeNanos = durationNanos;
            }
        }

        public long getCallCount() {
            return callCount.get();
        }

        public double getTotalTimeMs() {
            return totalTimeNanos.get() / 1_000_000.0;
        }

        public double getAverageTimeMs() {
            long count = callCount.get();
            return count > 0 ? (totalTimeNanos.get() / 1_000_000.0) / count : 0.0;
        }

        public double getMinTimeMs() {
            return minTimeNanos == Long.MAX_VALUE ? 0.0 : minTimeNanos / 1_000_000.0;
        }

        public double getMaxTimeMs() {
            return maxTimeNanos == Long.MIN_VALUE ? 0.0 : maxTimeNanos / 1_000_000.0;
        }

        public void reset() {
            callCount.set(0);
            totalTimeNanos.set(0);
            minTimeNanos = Long.MAX_VALUE;
            maxTimeNanos = Long.MIN_VALUE;
        }
    }

    /**
     * Auto-closeable timer for try-with-resources
     */
    public static class Timer implements AutoCloseable {
        private final String operation;

        private Timer(String operation) {
            this.operation = operation;
            start(operation);
        }

        @Override
        public void close() {
            end(operation);
        }
    }

    /**
     * Initialize the profiler (called on mod init)
     */
    public static void initialize() {
        if (!DebugManager.isEnabled()) {
            return;
        }

        synchronized (writerLock) {
            try {
                File logFile = new File(LOG_FILE);
                logFile.getParentFile().mkdirs();

                logWriter = new BufferedWriter(new FileWriter(logFile, true));
                writeLog("=".repeat(80));
                writeLog("AssimpLoader Performance Profiler Initialized");
                writeLog("Timestamp: " + DATE_FORMAT.format(new Date()));
                writeLog("=".repeat(80));

                DebugManager.info(DebugManager.DebugCategory.CORE,
                    "Performance profiler initialized: {}", LOG_FILE);

            } catch (IOException e) {
                DebugManager.error(DebugManager.DebugCategory.CORE,
                    "Failed to initialize performance log file", e);
            }
        }
    }

    /**
     * Shutdown the profiler (called on mod shutdown)
     */
    public static void shutdown() {
        // Write final report
        if (DebugManager.isEnabled()) {
            generateReport(true);
        }

        synchronized (writerLock) {
            if (logWriter != null) {
                try {
                    writeLog("=".repeat(80));
                    writeLog("Performance Profiler Shutdown");
                    writeLog("Timestamp: " + DATE_FORMAT.format(new Date()));
                    writeLog("=".repeat(80));
                    logWriter.close();
                    logWriter = null;
                } catch (IOException e) {
                    DebugManager.error(DebugManager.DebugCategory.CORE,
                        "Failed to close performance log file", e);
                }
            }
        }
    }

    /**
     * Start timing an operation
     */
    public static void start(String operation) {
        if (!DebugManager.isEnabled()) {
            return;
        }

        activeTimers.get().put(operation, System.nanoTime());
    }

    /**
     * End timing an operation and record the result
     */
    public static void end(String operation) {
        if (!DebugManager.isEnabled()) {
            return;
        }

        Long startTime = activeTimers.get().remove(operation);
        if (startTime == null) {
            DebugManager.warn(DebugManager.DebugCategory.CORE,
                "Performance timer '{}' was not started", operation);
            return;
        }

        long duration = System.nanoTime() - startTime;
        record(operation, duration);

        // Auto-report if interval elapsed
        checkAutoReport();
    }

    /**
     * Time an operation with auto-closeable timer
     */
    public static Timer time(String operation) {
        return new Timer(operation);
    }

    /**
     * Record a performance measurement
     */
    private static void record(String operation, long durationNanos) {
        counters.computeIfAbsent(operation, PerformanceCounter::new)
                .record(durationNanos);
    }

    /**
     * Check if auto-report should be triggered
     */
    private static void checkAutoReport() {
        if (!autoReportEnabled) {
            return;
        }

        long now = System.currentTimeMillis();
        if (now - lastReportTime >= REPORT_INTERVAL_MS) {
            lastReportTime = now;
            generateReport(false);
        }
    }

    /**
     * Generate performance report
     */
    public static void generateReport(boolean isFinal) {
        if (!DebugManager.isEnabled() || counters.isEmpty()) {
            return;
        }

        StringBuilder report = new StringBuilder();
        report.append("\n");
        report.append("=".repeat(80)).append("\n");
        report.append(isFinal ? "FINAL " : "").append("PERFORMANCE REPORT\n");
        report.append("Timestamp: ").append(DATE_FORMAT.format(new Date())).append("\n");
        report.append("=".repeat(80)).append("\n");
        report.append(String.format("%-40s %10s %12s %10s %10s %10s\n",
            "Operation", "Calls", "Total(ms)", "Avg(ms)", "Min(ms)", "Max(ms)"));
        report.append("-".repeat(80)).append("\n");

        // Sort by total time descending
        List<Map.Entry<String, PerformanceCounter>> sorted = new ArrayList<>(counters.entrySet());
        sorted.sort((a, b) -> Double.compare(b.getValue().getTotalTimeMs(), a.getValue().getTotalTimeMs()));

        for (Map.Entry<String, PerformanceCounter> entry : sorted) {
            PerformanceCounter counter = entry.getValue();
            report.append(String.format("%-40s %10d %12.2f %10.3f %10.3f %10.3f\n",
                entry.getKey(),
                counter.getCallCount(),
                counter.getTotalTimeMs(),
                counter.getAverageTimeMs(),
                counter.getMinTimeMs(),
                counter.getMaxTimeMs()));
        }

        report.append("=".repeat(80)).append("\n");

        // Write to log file
        writeLog(report.toString());

        // Also log to console if final report
        if (isFinal) {
            DebugManager.info(DebugManager.DebugCategory.CORE,
                "Performance report written to {}", LOG_FILE);
        }
    }

    /**
     * Reset all counters
     */
    public static void reset() {
        counters.values().forEach(PerformanceCounter::reset);
        lastReportTime = System.currentTimeMillis();

        if (DebugManager.isEnabled()) {
            writeLog("\n" + "=".repeat(80));
            writeLog("Performance Counters Reset");
            writeLog("Timestamp: " + DATE_FORMAT.format(new Date()));
            writeLog("=".repeat(80) + "\n");
        }
    }

    /**
     * Get current statistics for an operation
     */
    public static String getStats(String operation) {
        PerformanceCounter counter = counters.get(operation);
        if (counter == null) {
            return "No data for operation: " + operation;
        }

        return String.format("%s: calls=%d, total=%.2fms, avg=%.3fms, min=%.3fms, max=%.3fms",
            operation,
            counter.getCallCount(),
            counter.getTotalTimeMs(),
            counter.getAverageTimeMs(),
            counter.getMinTimeMs(),
            counter.getMaxTimeMs());
    }

    /**
     * Enable/disable auto-reporting
     */
    public static void setAutoReportEnabled(boolean enabled) {
        autoReportEnabled = enabled;
    }

    /**
     * Write to log file
     */
    private static void writeLog(String message) {
        synchronized (writerLock) {
            if (logWriter != null) {
                try {
                    logWriter.write(message);
                    logWriter.newLine();
                    logWriter.flush();
                } catch (IOException e) {
                    // Silently fail to avoid spam
                }
            }
        }
    }

    /**
     * Get all counter names
     */
    public static Set<String> getCounterNames() {
        return new HashSet<>(counters.keySet());
    }

    /**
     * Get call count for an operation
     */
    public static long getCallCount(String operation) {
        PerformanceCounter counter = counters.get(operation);
        return counter != null ? counter.getCallCount() : 0;
    }

    /**
     * Get average time for an operation
     */
    public static double getAverageTimeMs(String operation) {
        PerformanceCounter counter = counters.get(operation);
        return counter != null ? counter.getAverageTimeMs() : 0.0;
    }
}
