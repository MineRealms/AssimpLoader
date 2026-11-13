package cn.minerealms.assimploader.core;

import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.*;

class AsyncModelLoaderTest {
    @Test
    void timeoutDoesNotStartAnotherParseAndConcurrentRequestsShareOwnership() throws Exception {
        String identity = "slow-test-" + UUID.randomUUID();
        CountDownLatch started = new CountDownLatch(1);
        CountDownLatch release = new CountDownLatch(1);
        AtomicInteger imports = new AtomicInteger();
        var first = AsyncModelLoader.decodeAsync(identity, () -> {
            imports.incrementAndGet();
            started.countDown();
            try {
                if (!release.await(5, TimeUnit.SECONDS)) throw new IOException("Test timed out");
            } catch (InterruptedException exception) {
                Thread.currentThread().interrupt();
                throw new IOException(exception);
            }
            return null;
        });
        try {
            assertTrue(started.await(5, TimeUnit.SECONDS));
            assertThrows(IOException.class, () -> AsyncModelLoader.await(first, 1, TimeUnit.MILLISECONDS));
            var second = AsyncModelLoader.decodeAsync(identity, () -> {
                imports.incrementAndGet();
                return null;
            });
            assertSame(first, second);
            release.countDown();
            first.get(5, TimeUnit.SECONDS);
            assertEquals(1, imports.get());
        } finally {
            release.countDown();
        }
    }
}
