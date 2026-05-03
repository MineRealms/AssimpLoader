package cn.minerealms.assimploader.util;

import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicLong;

/**
 * Throttles repetitive error messages to prevent log spam.
 * Each unique error message is only logged once, or at a specified interval.
 */
public class ErrorThrottler {
    private static final ConcurrentHashMap<String, AtomicLong> lastLogTime = new ConcurrentHashMap<>();
    private static final long DEFAULT_THROTTLE_MS = 5000; // 5 seconds

    /**
     * Check if a message should be logged based on throttling rules.
     *
     * @param messageKey Unique key for this error message
     * @param throttleMs Minimum milliseconds between logs of the same message
     * @return true if the message should be logged, false if it should be suppressed
     */
    public static boolean shouldLog(String messageKey, long throttleMs) {
        long now = System.currentTimeMillis();
        AtomicLong lastTime = lastLogTime.computeIfAbsent(messageKey, k -> new AtomicLong(0));

        long last = lastTime.get();
        if (now - last >= throttleMs) {
            lastTime.set(now);
            return true;
        }
        return false;
    }

    /**
     * Check if a message should be logged (using default 5 second throttle).
     */
    public static boolean shouldLog(String messageKey) {
        return shouldLog(messageKey, DEFAULT_THROTTLE_MS);
    }

    /**
     * Check if a message should be logged only once (never repeat).
     */
    public static boolean shouldLogOnce(String messageKey) {
        return shouldLog(messageKey, Long.MAX_VALUE);
    }

    /**
     * Reset throttling for a specific message key.
     */
    public static void reset(String messageKey) {
        lastLogTime.remove(messageKey);
    }

    /**
     * Clear all throttling state.
     */
    public static void resetAll() {
        lastLogTime.clear();
    }
}
