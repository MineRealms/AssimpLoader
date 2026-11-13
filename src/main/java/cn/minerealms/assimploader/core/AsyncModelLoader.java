package cn.minerealms.assimploader.core;

import cn.minerealms.assimploader.config.AssimpLoaderConfig;
import cn.minerealms.assimploader.model.AssimpScene;

import java.io.IOException;
import java.nio.file.Path;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicInteger;

/** Bounded background decoding. A timeout reports failure; it never starts a duplicate native import. */
public final class AsyncModelLoader {
    @FunctionalInterface
    public interface Decoder {
        AssimpScene decode() throws IOException;
    }

    private static final int POOL_SIZE = Math.max(1, Math.min(4, Runtime.getRuntime().availableProcessors() / 2));
    private static final long WAIT_SECONDS = 60;
    private static final Map<String, CompletableFuture<AssimpScene>> IN_FLIGHT = new HashMap<>();
    private static final ThreadLocal<Boolean> WORKER = ThreadLocal.withInitial(() -> false);
    private static final ThreadPoolExecutor EXECUTOR = new ThreadPoolExecutor(POOL_SIZE, POOL_SIZE,
            0, TimeUnit.SECONDS, new ArrayBlockingQueue<>(64), new ThreadFactory() {
        private final AtomicInteger counter = new AtomicInteger();

        @Override
        public Thread newThread(Runnable runnable) {
            Thread thread = new Thread(runnable, "assimploader-model-loader-" + counter.incrementAndGet());
            thread.setDaemon(true);
            thread.setPriority(Thread.NORM_PRIORITY - 1);
            return thread;
        }
    }, new ThreadPoolExecutor.AbortPolicy());

    private AsyncModelLoader() {}

    public static boolean isEnabled() {
        try {
            return AssimpLoaderConfig.CLIENT.asyncLoadingEnabled.get();
        } catch (IllegalStateException exception) {
            return true;
        }
    }

    public static CompletableFuture<AssimpScene> loadAsync(Path path, ModelImportMode mode) {
        String identity = path.toAbsolutePath().normalize() + "#" + mode;
        return decodeAsync(identity, () -> new AssimpModelLoader().loadModel(path, mode));
    }

    public static AssimpScene loadBlocking(Path path, ModelImportMode mode) throws IOException {
        String identity = path.toAbsolutePath().normalize() + "#" + mode;
        return decodeBlocking(identity, () -> new AssimpModelLoader().loadModel(path, mode));
    }

    public static AssimpScene decodeBlocking(String identity, Decoder decoder) throws IOException {
        if (!isEnabled() || WORKER.get()) return decoder.decode();
        return await(decodeAsync(identity, decoder), WAIT_SECONDS, TimeUnit.SECONDS);
    }

    public static CompletableFuture<AssimpScene> decodeAsync(String identity, Decoder decoder) {
        CompletableFuture<AssimpScene> future;
        synchronized (IN_FLIGHT) {
            future = IN_FLIGHT.get(identity);
            if (future != null) return future;
            future = new CompletableFuture<>();
            IN_FLIGHT.put(identity, future);
        }
        CompletableFuture<AssimpScene> result = future;
        try {
            EXECUTOR.execute(() -> {
                WORKER.set(true);
                try {
                    result.complete(decoder.decode());
                } catch (Throwable exception) {
                    result.completeExceptionally(exception);
                } finally {
                    WORKER.remove();
                    synchronized (IN_FLIGHT) {
                        IN_FLIGHT.remove(identity, result);
                    }
                }
            });
        } catch (RejectedExecutionException exception) {
            synchronized (IN_FLIGHT) {
                IN_FLIGHT.remove(identity, result);
            }
            result.completeExceptionally(new IOException("Model loading queue is full or shut down: " + identity, exception));
        }
        return result;
    }

    static AssimpScene await(CompletableFuture<AssimpScene> future, long timeout, TimeUnit unit) throws IOException {
        try {
            return future.get(timeout, unit);
        } catch (InterruptedException exception) {
            Thread.currentThread().interrupt();
            throw new IOException("Interrupted while waiting for model", exception);
        } catch (ExecutionException exception) {
            Throwable cause = exception.getCause();
            if (cause instanceof IOException ioException) throw ioException;
            throw new IOException("Failed to decode model", cause);
        } catch (TimeoutException exception) {
            // Native parsing cannot be safely interrupted. Its result stays owned by the original task.
            throw new IOException("Model decoding timed out after " + timeout + " " + unit.toString().toLowerCase(), exception);
        }
    }

    /** Stops new work. Already running native imports finish and release their own resources. */
    public static void shutdown() {
        EXECUTOR.shutdown();
        synchronized (IN_FLIGHT) {
            IN_FLIGHT.values().forEach(future -> future.completeExceptionally(new IOException("Client is shutting down")));
            IN_FLIGHT.clear();
        }
    }
}
