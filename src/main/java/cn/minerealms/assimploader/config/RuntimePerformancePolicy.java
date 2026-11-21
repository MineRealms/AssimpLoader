package cn.minerealms.assimploader.config;

import cn.minerealms.assimploader.AssimpLoader;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.event.config.ModConfigEvent;

import java.util.Objects;

/** Immutable workload budgets. Config events publish once; render-time reads allocate nothing. */
@Mod.EventBusSubscriber(modid = AssimpLoader.MODID, bus = Mod.EventBusSubscriber.Bus.MOD, value = Dist.CLIENT)
public record RuntimePerformancePolicy(
        Mode mode, int nearUpdateTicks, int farUpdateTicks, double farDistance,
        int hiddenUpdateTicks, int visibilityGraceTicks,
        boolean ikEnabled, boolean nativeIkEnabled, boolean springBonesEnabled, boolean particlesEnabled, boolean trailsEnabled,
        int maxParticleEmissions, int maxTrailSamples,
        boolean gpuMorphEnabled, int maxMorphTargets, int maxMorphMiB) {

    public enum Mode { QUALITY, BALANCED, PERFORMANCE, CUSTOM }

    private static volatile RuntimePerformancePolicy current = forMode(Mode.BALANCED);

    public RuntimePerformancePolicy {
        Objects.requireNonNull(mode, "Runtime performance mode");
        range(nearUpdateTicks, 1, 20, "nearUpdateTicks");
        range(farUpdateTicks, nearUpdateTicks, 20, "farUpdateTicks");
        if (!Double.isFinite(farDistance) || farDistance < 1 || farDistance > 4096) {
            throw new IllegalArgumentException("farDistance must be finite and in [1,4096]");
        }
        range(hiddenUpdateTicks, nearUpdateTicks, 20, "hiddenUpdateTicks");
        range(visibilityGraceTicks, 0, 1200, "visibilityGraceTicks");
        range(maxParticleEmissions, 0, 32, "maxParticleEmissions");
        range(maxTrailSamples, 2, 4096, "maxTrailSamples");
        range(maxMorphTargets, 1, 32, "maxMorphTargets");
        range(maxMorphMiB, 1, 128, "maxMorphMiB");
    }

    /** Backward-compatible constructor: native IK is opt-in and defaults to disabled. */
    public RuntimePerformancePolicy(Mode mode, int nearUpdateTicks, int farUpdateTicks, double farDistance,
            int hiddenUpdateTicks, int visibilityGraceTicks,
            boolean ikEnabled, boolean springBonesEnabled, boolean particlesEnabled, boolean trailsEnabled,
            int maxParticleEmissions, int maxTrailSamples,
            boolean gpuMorphEnabled, int maxMorphTargets, int maxMorphMiB) {
        this(mode, nearUpdateTicks, farUpdateTicks, farDistance, hiddenUpdateTicks, visibilityGraceTicks,
                ikEnabled, false, springBonesEnabled, particlesEnabled, trailsEnabled,
                maxParticleEmissions, maxTrailSamples, gpuMorphEnabled, maxMorphTargets, maxMorphMiB);
    }

    public RuntimePerformancePolicy withNativeIk(boolean enabled) {
        return new RuntimePerformancePolicy(mode, nearUpdateTicks, farUpdateTicks, farDistance,
                hiddenUpdateTicks, visibilityGraceTicks, ikEnabled, enabled, springBonesEnabled,
                particlesEnabled, trailsEnabled, maxParticleEmissions, maxTrailSamples,
                gpuMorphEnabled, maxMorphTargets, maxMorphMiB);
    }

    private static void range(int value, int minimum, int maximum, String name) {
        if (value < minimum || value > maximum) {
            throw new IllegalArgumentException(name + " must be in [" + minimum + "," + maximum + "]");
        }
    }

    public static RuntimePerformancePolicy current() { return current; }

    public static RuntimePerformancePolicy forMode(Mode mode) {
        return switch (Objects.requireNonNull(mode)) {
            case QUALITY -> new RuntimePerformancePolicy(mode, 1, 1, 64, 1, 40,
                    true, false, true, true, true, 32, 4096, true, 32, 128);
            case BALANCED, CUSTOM -> new RuntimePerformancePolicy(mode, 1, 2, 64, 10, 40,
                    true, false, true, true, true, 16, 128, true, 32, 128);
            case PERFORMANCE -> new RuntimePerformancePolicy(mode, 1, 4, 32, 20, 20,
                    true, false, true, true, true, 8, 32, true, 32, 128);
        };
    }

    /** Byte and target limits complement driver capabilities and the renderer's hard safety limits. */
    public boolean permitsGpuMorph(int targets, long bytes) {
        return gpuMorphEnabled && targets > 0 && targets <= maxMorphTargets && bytes > 0
                && bytes <= (long) maxMorphMiB * 1024 * 1024;
    }

    public static void refreshFromConfig() {
        var config = AssimpLoaderConfig.CLIENT.runtime;
        Mode mode = AssimpLoaderConfig.getOrDefault(config.mode::get, Mode.BALANCED);
        boolean nativeIk = AssimpLoaderConfig.getOrDefault(config.nativeIkEnabled::get, false);
        if (mode != Mode.CUSTOM) {
            current = forMode(mode).withNativeIk(nativeIk);
            return;
        }
        int near = AssimpLoaderConfig.getIntOrDefault(config.nearUpdateTicks, 1);
        current = new RuntimePerformancePolicy(mode, near,
                Math.max(near, AssimpLoaderConfig.getIntOrDefault(config.farUpdateTicks, 2)),
                AssimpLoaderConfig.getOrDefault(config.farDistance::get, 64.0),
                Math.max(near, AssimpLoaderConfig.getIntOrDefault(config.hiddenUpdateTicks, 10)),
                AssimpLoaderConfig.getIntOrDefault(config.visibilityGraceTicks, 40),
                AssimpLoaderConfig.getOrDefault(config.ikEnabled::get, true),
                nativeIk,
                AssimpLoaderConfig.getOrDefault(config.springBonesEnabled::get, true),
                AssimpLoaderConfig.getOrDefault(config.particlesEnabled::get, true),
                AssimpLoaderConfig.getOrDefault(config.trailsEnabled::get, true),
                AssimpLoaderConfig.getIntOrDefault(config.maxParticleEmissions, 16),
                AssimpLoaderConfig.getIntOrDefault(config.maxTrailSamples, 128),
                AssimpLoaderConfig.getOrDefault(config.gpuMorphEnabled::get, true),
                AssimpLoaderConfig.getIntOrDefault(config.maxMorphTargets, 32),
                AssimpLoaderConfig.getIntOrDefault(config.maxMorphMiB, 128));
    }

    @SubscribeEvent
    public static void onConfigLoading(ModConfigEvent.Loading event) { changed(event); }

    @SubscribeEvent
    public static void onConfigReloading(ModConfigEvent.Reloading event) { changed(event); }

    private static void changed(ModConfigEvent event) {
        if (event.getConfig().getSpec() == AssimpLoaderConfig.CLIENT_SPEC) refreshFromConfig();
    }
}
