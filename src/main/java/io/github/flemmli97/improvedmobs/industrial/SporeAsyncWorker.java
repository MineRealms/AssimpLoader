package io.github.flemmli97.improvedmobs.industrial;

import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.Mob;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.ThreadFactory;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

public class SporeAsyncWorker {
    private static final Logger LOGGER = LogManager.getLogger();
    
    // Thread pool for async scanning
    private static final ExecutorService THREAD_POOL = Executors.newFixedThreadPool(
            Math.max(2, Runtime.getRuntime().availableProcessors() / 2),
            new ThreadFactory() {
                private final AtomicInteger counter = new AtomicInteger(1);
                @Override
                public Thread newThread(Runnable r) {
                    Thread t = new Thread(r, "Spore-Async-Worker-" + counter.getAndIncrement());
                    t.setDaemon(true);
                    return t;
                }
            }
    );
    
    // Watchdog
    private static final ScheduledExecutorService WATCHDOG = Executors.newSingleThreadScheduledExecutor(r -> {
        Thread t = new Thread(r, "Spore-Watchdog");
        t.setDaemon(true);
        return t;
    });

    // Debug toggle - controlled by IndustrialLogger
    private static boolean isDebugEnabled() {
        return IndustrialLogger.isDebugEnabled();
    }

    public static void processSporeBuffAsync(Mob mob, double pollutionLevel) {
        if (!SporeIntegration.isSporeLoaded()) return;

        ServerLevel level = (ServerLevel) mob.level();
        BlockPos pos = mob.blockPosition();

        THREAD_POOL.submit(() -> {
            ScheduledFuture<?> watchdogTask = null;
            try {
                if (isDebugEnabled()) {
                    LOGGER.info("[SporeAsyncWorker] Start scanning for mob at {}", pos);
                }
                
                long startTime = System.currentTimeMillis();
                
                // Watchdog schedule
                watchdogTask = WATCHDOG.schedule(() -> {
                    LOGGER.warn("[Spore Watchdog] Task for mob at {} is taking too long! Possible deadlock or huge chunk load.", pos);
                }, 5, TimeUnit.SECONDS);

                // Safe scan
                double nearbyVoltageTier = MachineScanner.scanNearbyVoltageTierSafely(level, pos);
                
                if (watchdogTask != null && !watchdogTask.isDone()) {
                    watchdogTask.cancel(false);
                }
                
                if (isDebugEnabled()) {
                    long duration = System.currentTimeMillis() - startTime;
                    LOGGER.info("[SporeAsyncWorker] Finished scanning for mob at {} in {} ms. Tier: {}", pos, duration, nearbyVoltageTier);
                }

                // Return to main thread
                level.getServer().execute(() -> {
                    if (mob.isAlive()) {
                        SporeIntegration.buffSporeMob(mob, pollutionLevel, nearbyVoltageTier, level);
                    }
                });
            } catch (Exception e) {
                LOGGER.error("[SporeAsyncWorker] Error processing Spore buff for mob at " + pos, e);
            } finally {
                if (watchdogTask != null && !watchdogTask.isDone()) {
                    watchdogTask.cancel(false);
                }
            }
        });
    }

    public static void shutdown() {
        THREAD_POOL.shutdown();
        WATCHDOG.shutdown();
    }
}
