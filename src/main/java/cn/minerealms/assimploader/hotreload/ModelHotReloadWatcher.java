package cn.minerealms.assimploader.hotreload;

import cn.minerealms.assimploader.config.AssimpLoaderConfig;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.concurrent.CompletableFuture;

/** Optional polling runs filesystem scans on a worker, never inside the render/client tick. */
@Mod.EventBusSubscriber(modid = "assimploader", value = Dist.CLIENT)
public final class ModelHotReloadWatcher {
    private static final Logger LOGGER = LoggerFactory.getLogger(ModelHotReloadWatcher.class);
    private static CompletableFuture<Void> scan;
    private static long nextScan;
    private static volatile String lastFingerprint;

    private ModelHotReloadWatcher() {}

    @SubscribeEvent
    public static void tick(TickEvent.ClientTickEvent event) {
        if (event.phase != TickEvent.Phase.END) return;
        boolean automatic = ModelHotReloadManager.isEnabled() && AssimpLoaderConfig.getOrDefault(
                AssimpLoaderConfig.CLIENT.hotReloadAutoEnabled::get, false);
        if (!automatic) {
            lastFingerprint = null;
            return;
        }
        long now = System.nanoTime();
        if (now < nextScan || (scan != null && !scan.isDone())) return;
        int interval = AssimpLoaderConfig.getIntOrDefault(AssimpLoaderConfig.CLIENT.hotReloadWatcherPollInterval, 1000);
        nextScan = now + interval * 1_000_000L;
        scan = CompletableFuture.runAsync(() -> {
            try {
                String fingerprint = fingerprint(ModelHotReloadManager.getResourceRoot());
                String previous = lastFingerprint;
                lastFingerprint = fingerprint;
                if (!fingerprint.equals(previous)) {
                    ModelHotReloadManager.reloadModelsAsync().exceptionally(failure -> {
                        LOGGER.warn("Automatic external model reload failed: {}", failure.getMessage());
                        return 0;
                    });
                }
            } catch (IOException | RuntimeException exception) {
                LOGGER.warn("Cannot scan external model resources: {}", exception.getMessage());
            }
        });
    }

    private static String fingerprint(Path root) throws IOException {
        StringBuilder state = new StringBuilder(root.toString());
        if (!Files.isDirectory(root)) return state.toString();
        try (var paths = Files.walk(root)) {
            for (Path path : paths.filter(Files::isRegularFile).sorted().toList()) {
                state.append('\n').append(root.relativize(path)).append(':').append(Files.size(path))
                        .append(':').append(Files.getLastModifiedTime(path).toMillis());
            }
        }
        return state.toString();
    }
}
