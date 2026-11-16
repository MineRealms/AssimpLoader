package cn.minerealms.assimploader.config;

import cn.minerealms.assimploader.AssimpLoader;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.event.config.ModConfigEvent;

import java.util.Objects;

/** Config events publish immutable client visual budgets; draws only read the cached snapshot. */
@Mod.EventBusSubscriber(modid = AssimpLoader.MODID, bus = Mod.EventBusSubscriber.Bus.MOD, value = Dist.CLIENT)
public record GpuBatchPolicy(Mode mode, int maxInstances, int geometryMiB, int frameMiB,
                             boolean hiZEnabled, int hiZMaxResolution, double hiZBias) {
    public enum Mode { OFF, INSTANCED, MDI }

    private static volatile GpuBatchPolicy current = defaults();

    public GpuBatchPolicy {
        Objects.requireNonNull(mode, "Batch mode");
        range(maxInstances, 1, 16384, "maxInstances");
        range(geometryMiB, 1, 256, "geometryMiB");
        range(frameMiB, 1, 128, "frameMiB");
        range(hiZMaxResolution, 64, 1024, "hiZMaxResolution");
        if (!Double.isFinite(hiZBias) || hiZBias < 0 || hiZBias > .05)
            throw new IllegalArgumentException("hiZBias must be finite and in [0,0.05]");
    }

    private static void range(int value, int minimum, int maximum, String field) {
        if (value < minimum || value > maximum)
            throw new IllegalArgumentException(field + " must be in [" + minimum + "," + maximum + "]");
    }

    public static GpuBatchPolicy defaults() {
        return new GpuBatchPolicy(Mode.OFF, 4096, 128, 32, false, 1024, .0005);
    }

    public static GpuBatchPolicy current() { return current; }
    public boolean enabled() { return mode != Mode.OFF; }
    public boolean usesHiZ() { return mode == Mode.MDI && hiZEnabled; }
    public long geometryBytes() { return (long) geometryMiB * 1024 * 1024; }
    public long frameBytes() { return (long) frameMiB * 1024 * 1024; }

    public static void refreshFromConfig() {
        var config = AssimpLoaderConfig.CLIENT.batch;
        current = new GpuBatchPolicy(
                AssimpLoaderConfig.getOrDefault(config.mode::get, Mode.OFF),
                AssimpLoaderConfig.getIntOrDefault(config.maxInstances, 4096),
                AssimpLoaderConfig.getIntOrDefault(config.geometryMiB, 128),
                AssimpLoaderConfig.getIntOrDefault(config.frameMiB, 32),
                AssimpLoaderConfig.getOrDefault(config.hiZEnabled::get, false),
                AssimpLoaderConfig.getIntOrDefault(config.hiZMaxResolution, 1024),
                AssimpLoaderConfig.getOrDefault(config.hiZBias::get, .0005));
    }

    @SubscribeEvent public static void onConfigLoading(ModConfigEvent.Loading event) { changed(event); }
    @SubscribeEvent public static void onConfigReloading(ModConfigEvent.Reloading event) { changed(event); }

    private static void changed(ModConfigEvent event) {
        if (event.getConfig().getSpec() == AssimpLoaderConfig.CLIENT_SPEC) refreshFromConfig();
    }
}
