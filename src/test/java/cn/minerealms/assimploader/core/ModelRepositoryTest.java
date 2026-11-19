package cn.minerealms.assimploader.core;

import cn.minerealms.assimploader.model.AssimpScene;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.*;

class ModelRepositoryTest {
    @AfterEach
    void clearRepository() { AssimpModelLoader.clearCache(); }

    @Test
    void addingAnAssetChangesCatalogWithoutInvalidatingLiveResources() {
        AssimpModelLoader.clearCache();
        long resources=AssimpModelLoader.getResourceGeneration(),catalog=AssimpModelLoader.getCacheGeneration();
        AssimpScene original=triangle(ModelImportMode.DYNAMIC);
        AssimpModelLoader.updateModelCache("test:first",original);
        AssimpModelLoader.updateModelCache("test:second",triangle(ModelImportMode.DYNAMIC));
        assertTrue(AssimpModelLoader.getCacheGeneration()>catalog);
        assertEquals(resources,AssimpModelLoader.getResourceGeneration());
        assertSame(original,AssimpModelLoader.getCachedModel("test:models/first",ModelImportMode.DYNAMIC));
        AssimpModelLoader.updateModelCache("test:first",triangle(ModelImportMode.DYNAMIC));
        assertTrue(AssimpModelLoader.getResourceGeneration()>resources);
    }

    @Test
    void namespaceAndPathCollisionsDoNotOverwriteOrCreateAmbiguousAliases() {
        AssimpModelLoader.clearCache();
        AssimpScene first = triangle(ModelImportMode.STATIC);
        AssimpScene second = triangle(ModelImportMode.STATIC);
        AssimpModelLoader.updateModelCache("first:models/block/shared", first);
        AssimpModelLoader.updateModelCache("second:models/block/shared", second);
        assertSame(first, AssimpModelLoader.getCachedModel("first:models/block/shared"));
        assertSame(second, AssimpModelLoader.getCachedModel("second:models/block/shared"));
        assertNull(AssimpModelLoader.getCachedModel("shared"));
        assertNull(AssimpModelLoader.getCachedModel("models/block/shared"));
        assertEquals(2, AssimpModelLoader.getLoadedModels().size());
    }

    @Test
    void modesAreDistinctAndLegacyAliasesRemainWhenUnique() {
        AssimpModelLoader.clearCache();
        AssimpScene fixed = triangle(ModelImportMode.STATIC);
        AssimpScene moving = triangle(ModelImportMode.DYNAMIC);
        AssimpModelLoader.updateModelCache("assimploader:models/block/display", fixed);
        AssimpModelLoader.updateModelCache("assimploader:models/block/display", moving);
        assertSame(fixed, AssimpModelLoader.getCachedModel("display", ModelImportMode.STATIC));
        assertSame(moving, AssimpModelLoader.getCachedModel("models/block/display", ModelImportMode.DYNAMIC));
        assertSame(moving, AssimpModelLoader.getCachedModel("assimploader:models/block/display#dynamic"));
        assertEquals(2, AssimpModelLoader.getLoadedModels().size());
        assertTrue(AssimpModelLoader.getLoadedModels().keySet().stream().allMatch(key -> key.contains("#")));
    }

    @Test
    void snapshotsAreImmutableAndGenerationTracksReplacementAndRemoval() {
        AssimpModelLoader.clearCache();
        AtomicInteger invalidations = new AtomicInteger();
        Runnable listener = invalidations::incrementAndGet;
        AssimpModelLoader.addCacheInvalidationListener(listener);
        try {
            AssimpModelLoader.updateModelCache("display", triangle(ModelImportMode.STATIC));
            long generation = AssimpModelLoader.getCacheGeneration();
            var snapshot = AssimpModelLoader.getLoadedModels();
            assertThrows(UnsupportedOperationException.class, snapshot::clear);
            AssimpModelLoader.updateModelCache("display", triangle(ModelImportMode.STATIC));
            assertTrue(AssimpModelLoader.getCacheGeneration() > generation);
            assertEquals(1, invalidations.get());
            AssimpModelLoader.removeCachedModel("display");
            assertEquals(2, invalidations.get());
            assertTrue(AssimpModelLoader.getLoadedModels().isEmpty());
            assertEquals(1, snapshot.size());
        } finally {
            AssimpModelLoader.removeCacheInvalidationListener(listener);
        }
    }

    @Test
    void aLoadFromThePreviousResourceGenerationCannotRepublish() throws Exception {
        AssimpModelLoader.clearCache();
        AssimpScene scene = triangle(ModelImportMode.STATIC);
        CountDownLatch started = new CountDownLatch(1);
        CountDownLatch release = new CountDownLatch(1);
        CompletableFuture<AssimpScene> loading = CompletableFuture.supplyAsync(() -> {
            try {
                return ModelRepository.load("example:models/delayed", ModelImportMode.STATIC, true, () -> {
                    started.countDown();
                    try {
                        if (!release.await(5, TimeUnit.SECONDS)) throw new IOException("Test timed out");
                    } catch (InterruptedException exception) {
                        Thread.currentThread().interrupt();
                        throw new IOException(exception);
                    }
                    return scene;
                });
            } catch (IOException exception) {
                throw new CompletionException(exception);
            }
        });
        try {
            assertTrue(started.await(5, TimeUnit.SECONDS));
            AssimpModelLoader.clearCache();
            release.countDown();
            assertThrows(ExecutionException.class, () -> loading.get(5, TimeUnit.SECONDS));
            assertTrue(AssimpModelLoader.getLoadedModels().isEmpty());
        } finally {
            release.countDown();
        }
    }

    @Test
    void repeatedJsonReferencesShareOneSceneImport() throws Exception {
        AssimpModelLoader.clearCache();
        AtomicInteger imports = new AtomicInteger();
        AssimpScene expected = triangle(ModelImportMode.DYNAMIC);
        var first = ModelRepository.load("example:models/display", ModelImportMode.DYNAMIC, true, () -> {
            imports.incrementAndGet();
            return expected;
        });
        var second = ModelRepository.load("example:models/display", ModelImportMode.DYNAMIC, true, () -> {
            imports.incrementAndGet();
            return triangle(ModelImportMode.DYNAMIC);
        });
        assertSame(expected, first);
        assertSame(first, second);
        assertEquals(1, imports.get());
    }

    @Test
    void deletingAnExternalOverrideRestoresTheOriginalInOneInvalidation() {
        AssimpModelLoader.clearCache();
        AssimpScene base = triangle(ModelImportMode.STATIC);
        AssimpScene override = triangle(ModelImportMode.STATIC);
        String key = "example:models/display#static";
        AssimpModelLoader.updateModelCache("example:models/display", base);
        assertTrue(AssimpModelLoader.replaceCachedModels(AssimpModelLoader.getCacheGeneration(),
                java.util.Map.of(key, override), java.util.Set.of()));
        assertSame(override, AssimpModelLoader.getCachedModel(key));
        assertTrue(AssimpModelLoader.replaceCachedModels(AssimpModelLoader.getCacheGeneration(),
                java.util.Map.of(key, base), java.util.Set.of(key)));
        assertSame(base, AssimpModelLoader.getCachedModel(key));
        assertEquals(1, AssimpModelLoader.getLoadedModels().size());
    }

    private static AssimpScene triangle(ModelImportMode mode) {
        var scene = new AssimpModelLoader().loadModelFromMemory("triangle.obj",
                "o Triangle\nv 0 0 0\nv 1 0 0\nv 0 1 0\nf 1 2 3\n".getBytes(StandardCharsets.UTF_8), mode);
        assertNotNull(scene);
        return scene;
    }
}
