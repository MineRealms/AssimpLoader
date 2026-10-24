package cn.minerealms.assimploader.core;

import cn.minerealms.assimploader.AssimpLoader;
import cn.minerealms.assimploader.config.AssimpLoaderConfig;
import cn.minerealms.assimploader.model.AssimpScene;

import javax.annotation.Nullable;
import java.nio.file.Path;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.ThreadFactory;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * Background model decoding.
 *
 * <p>Assimp parsing is pure CPU work on a byte buffer, so it is safe to run off-thread;
 * nothing here touches OpenGL or the client state. The pool is daemon-only and small - a
 * resource reload rarely has more than a handful of models to decode.
 *
 * <p>Controlled by {@code performance.asyncLoadingEnabled}.
 */
public final class AsyncModelLoader {

    private static final int POOL_SIZE = Math.max(1, Math.min(4, Runtime.getRuntime().availableProcessors() / 2));

    /** Upper bound on how long a caller waits for a background load before doing it inline. */
    private static final long WAIT_SECONDS = 15L;

    private static final ExecutorService EXECUTOR = Executors.newFixedThreadPool(POOL_SIZE, new ThreadFactory() {
        private final AtomicInteger counter = new AtomicInteger();

        @Override
        public Thread newThread(Runnable runnable) {
            Thread thread = new Thread(runnable, "assimploader-model-loader-" + counter.incrementAndGet());
            thread.setDaemon(true);
            thread.setPriority(Thread.MIN_PRIORITY);
            return thread;
        }
    });

    private AsyncModelLoader() {
    }

    public static boolean isEnabled() {
        return AssimpLoaderConfig.CLIENT.asyncLoadingEnabled.get();
    }

    /**
     * Decode a model in the background.
     */
    public static CompletableFuture<AssimpScene> loadAsync(Path path, ModelImportMode mode) {
        return CompletableFuture.supplyAsync(() -> loadNow(path, mode), EXECUTOR);
    }

    /**
     * Decode a model, on the pool when async loading is enabled.
     *
     * <p>Falls back to decoding on the calling thread when the pool is disabled, when the
     * future fails, or when it takes unreasonably long - callers such as the geometry loader
     * must always get a scene or a failure, never a half-initialised model.
     */
    @Nullable
    public static AssimpScene loadBlocking(Path path, ModelImportMode mode) throws java.io.IOException {
        if (!isEnabled()) {
            return new AssimpModelLoader().loadModel(path, mode);
        }

        try {
            return loadAsync(path, mode).get(WAIT_SECONDS, TimeUnit.SECONDS);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            return null;
        } catch (java.util.concurrent.ExecutionException e) {
            Throwable cause = e.getCause();
            if (cause instanceof java.io.IOException ioException) {
                throw ioException;
            }
            throw new java.io.IOException("Failed to load model " + path, cause);
        } catch (java.util.concurrent.TimeoutException e) {
            AssimpLoader.LOGGER.warn("Background model load timed out after {}s, loading inline: {}",
                    WAIT_SECONDS, path);
            return new AssimpModelLoader().loadModel(path, mode);
        }
    }

    @Nullable
    private static AssimpScene loadNow(Path path, ModelImportMode mode) {
        try {
            return new AssimpModelLoader().loadModel(path, mode);
        } catch (java.io.IOException e) {
            AssimpLoader.LOGGER.error("Failed to load model: {}", path, e);
            return null;
        }
    }

    /** Stops the pool; called on client shutdown. */
    public static void shutdown() {
        EXECUTOR.shutdownNow();
    }
}
