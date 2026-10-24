package cn.minerealms.assimploader.config;

import net.minecraftforge.common.ForgeConfigSpec;
import net.minecraftforge.fml.ModLoadingContext;
import net.minecraftforge.fml.config.ModConfig;
import org.apache.commons.lang3.tuple.Pair;

import java.util.Arrays;
import java.util.List;

/**
 * Configuration system for AssimpLoader.
 *
 * Provides comprehensive configuration options for:
 * - Debug logging and profiling
 * - Animation system (bone limits, IK solvers)
 * - Rendering pipeline (PBR, GPU skinning, shaders)
 * - Performance optimization
 * - Oculus/Iris compatibility
 * - Hot-reload system
 * - Gaussian Splatting
 * - Development tools
 */
public class AssimpLoaderConfig {

    public static final ClientConfig CLIENT;
    public static final ForgeConfigSpec CLIENT_SPEC;

    public static final CommonConfig COMMON;
    public static final ForgeConfigSpec COMMON_SPEC;

    static {
        final Pair<ClientConfig, ForgeConfigSpec> clientPair = new ForgeConfigSpec.Builder().configure(ClientConfig::new);
        CLIENT = clientPair.getLeft();
        CLIENT_SPEC = clientPair.getRight();

        final Pair<CommonConfig, ForgeConfigSpec> commonPair = new ForgeConfigSpec.Builder().configure(CommonConfig::new);
        COMMON = commonPair.getLeft();
        COMMON_SPEC = commonPair.getRight();
    }

    /**
     * Reads a config value, falling back when the config has not been loaded yet.
     *
     * <p>Forge throws from {@code ConfigValue#get()} before the file is loaded, which makes
     * any code path that touches config unusable from unit tests.
     */
    public static int getIntOrDefault(ForgeConfigSpec.IntValue value, int fallback) {
        return getOrDefault(value::get, fallback);
    }

    /**
     * Generic variant of {@link #getIntOrDefault} for non-int config values.
     */
    public static <T> T getOrDefault(java.util.function.Supplier<T> getter, T fallback) {
        try {
            return getter.get();
        } catch (IllegalStateException configNotLoaded) {
            return fallback;
        }
    }

    /**
     * Register configs with Forge
     */
    public static void register() {
        ModLoadingContext.get().registerConfig(ModConfig.Type.CLIENT, CLIENT_SPEC);
        ModLoadingContext.get().registerConfig(ModConfig.Type.COMMON, COMMON_SPEC);
    }

    /**
     * Client-side configuration
     */
    public static class ClientConfig {
        // Debug
        public final ForgeConfigSpec.BooleanValue debugEnabled;
        public final ForgeConfigSpec.BooleanValue profilingEnabled;
        public final ForgeConfigSpec.ConfigValue<String> debugLogPath;
        public final ForgeConfigSpec.IntValue debugRateLimitMs;
        public final ForgeConfigSpec.ConfigValue<List<? extends String>> debugCategories;

        // Rendering
        public final ForgeConfigSpec.BooleanValue pbrEnabled;
        public final ForgeConfigSpec.BooleanValue gpuSkinningEnabled;
        public final ForgeConfigSpec.ConfigValue<String> externalModelDirectory;
        public final ForgeConfigSpec.BooleanValue frustumCullingEnabled;
        public final ForgeConfigSpec.EnumValue<ShaderMode> shaderMode;

        // Oculus/Iris
        public final ForgeConfigSpec.BooleanValue oculusEnabled;
        public final ForgeConfigSpec.BooleanValue oculusShaderInjectionEnabled;
        public final ForgeConfigSpec.BooleanValue oculusPbrSupportEnabled;
        public final ForgeConfigSpec.IntValue oculusInjectionPriority;
        public final ForgeConfigSpec.BooleanValue oculusSplatShaderEnabled;

        // Performance
        public final ForgeConfigSpec.EnumValue<OptimizationLevel> optimizationLevel;
        public final ForgeConfigSpec.BooleanValue asyncLoadingEnabled;
        public final ForgeConfigSpec.IntValue modelCacheSize;
        public final ForgeConfigSpec.IntValue textureCacheSizeMB;
        public final ForgeConfigSpec.BooleanValue tboEnabled;
        public final ForgeConfigSpec.IntValue maxTBOBones;

        // Hot-Reload
        public final ForgeConfigSpec.BooleanValue hotReloadEnabled;
        public final ForgeConfigSpec.ConfigValue<String> hotReloadResourcePath;
        public final ForgeConfigSpec.BooleanValue hotReloadAutoEnabled;
        public final ForgeConfigSpec.IntValue hotReloadWatcherPollInterval;
        public final ForgeConfigSpec.ConfigValue<List<? extends String>> hotReloadSupportedExtensions;

        // Gaussian Splatting
        public final ForgeConfigSpec.BooleanValue splatEnabled;
        public final ForgeConfigSpec.BooleanValue splatLodEnabled;
        public final ForgeConfigSpec.DoubleValue splatLodDistanceHigh;
        public final ForgeConfigSpec.DoubleValue splatLodDistanceMedium;
        public final ForgeConfigSpec.DoubleValue splatLodDistanceLow;
        public final ForgeConfigSpec.DoubleValue splatLodDistanceCull;
        public final ForgeConfigSpec.IntValue splatLodFactorHigh;
        public final ForgeConfigSpec.IntValue splatLodFactorMedium;
        public final ForgeConfigSpec.IntValue splatLodFactorLow;
        public final ForgeConfigSpec.BooleanValue splatCompressedPLYEnabled;
        public final ForgeConfigSpec.IntValue splatCacheSize;

        // Development
        public final ForgeConfigSpec.BooleanValue devAutoTestEnabled;
        public final ForgeConfigSpec.ConfigValue<List<? extends String>> devAutoTestCommands;
        public final ForgeConfigSpec.IntValue devAutoTestDelay;
        public final ForgeConfigSpec.BooleanValue devExampleBlocksEnabled;
        public final ForgeConfigSpec.BooleanValue devLookdevEnabled;

        public ClientConfig(ForgeConfigSpec.Builder builder) {
            // Debug Configuration
            builder.comment("Debug Configuration").push("debug");

            debugEnabled = builder
                .comment("Enable debug logging to separate log file")
                .define("enabled", false);

            profilingEnabled = builder
                .comment("Enable performance profiling")
                .define("profiling", false);

            debugLogPath = builder
                .comment("Debug log file path (relative to .minecraft)")
                .define("logPath", "logs/assimploader-debug.log");

            debugRateLimitMs = builder
                .comment("Rate limit for debug messages (milliseconds)")
                .defineInRange("rateLimitMs", 3000, 100, 60000);

            debugCategories = builder
                .comment("Debug categories to enable (CORE, RENDER, ANIMATION, CACHE, IK, SPLAT, LOOKDEV)")
                .defineList("categories", Arrays.asList("CORE", "RENDER", "ANIMATION"),
                    obj -> obj instanceof String);

            builder.pop();

            // Rendering Configuration
            builder.comment("Rendering Configuration").push("rendering");

            pbrEnabled = builder
                .comment("Enable PBR (Physically Based Rendering) pipeline")
                .define("pbrEnabled", true);

            gpuSkinningEnabled = builder
                .comment("Enable GPU skinning for skeletal animations")
                .define("gpuSkinningEnabled", true);

            externalModelDirectory = builder
                .comment("Optional folder (absolute, or relative to the game directory) that is searched",
                        "before the resource pack for Assimp models. Leave empty to disable.")
                .define("externalModelDirectory", "");

            frustumCullingEnabled = builder
                .comment("Enable frustum culling optimization")
                .define("frustumCullingEnabled", true);

            shaderMode = builder
                .comment("Custom shader mode: VANILLA, OCULUS, DUAL")
                .defineEnum("shaderMode", ShaderMode.DUAL);

            builder.pop();

            // Oculus/Iris Configuration
            builder.comment("Oculus/Iris Compatibility Configuration").push("oculus");

            oculusEnabled = builder
                .comment("Enable Oculus/Iris shader compatibility")
                .define("enabled", true);

            oculusShaderInjectionEnabled = builder
                .comment("Enable shader injection for custom materials")
                .define("shaderInjectionEnabled", true);

            oculusPbrSupportEnabled = builder
                .comment("Enable PBR material support in Oculus")
                .define("pbrSupportEnabled", true);

            oculusInjectionPriority = builder
                .comment("Shader injection priority (higher = inject later)")
                .defineInRange("injectionPriority", 100, 0, 1000);

            oculusSplatShaderEnabled = builder
                .comment("Enable Gaussian Splatting shader injection")
                .define("splatShaderEnabled", true);

            builder.pop();

            // Performance Configuration
            builder.comment("Performance Configuration").push("performance");

            optimizationLevel = builder
                .comment("GPU optimization level: AUTO, HIGH, MEDIUM, LOW, FALLBACK")
                .defineEnum("optimizationLevel", OptimizationLevel.AUTO);

            asyncLoadingEnabled = builder
                .comment("Enable async model loading")
                .define("asyncLoadingEnabled", true);

            modelCacheSize = builder
                .comment("Model cache size (number of cached models)")
                .defineInRange("modelCacheSize", 100, 10, 1000);

            textureCacheSizeMB = builder
                .comment("Texture cache size (MB)")
                .defineInRange("textureCacheSizeMB", 512, 64, 2048);

            tboEnabled = builder
                .comment("Enable TBO (Texture Buffer Object) for bone matrices")
                .define("tboEnabled", true);

            maxTBOBones = builder
                .comment("Maximum TBO size (number of bones)")
                .defineInRange("maxTBOBones", 1024, 64, 16384);

            builder.pop();

            // Hot-Reload Configuration
            builder.comment("Hot-Reload Configuration").push("hotreload");

            hotReloadEnabled = builder
                .comment("Enable hot-reload system")
                .define("enabled", true);

            hotReloadResourcePath = builder
                .comment("Hot-reload directory (relative to .minecraft)")
                .define("resourcePath", "AssimpLoader/Resource");

            hotReloadAutoEnabled = builder
                .comment("Auto-reload on file change (requires file watcher)")
                .define("autoReloadEnabled", false);

            hotReloadWatcherPollInterval = builder
                .comment("File watcher poll interval (milliseconds)")
                .defineInRange("watcherPollInterval", 1000, 100, 10000);

            hotReloadSupportedExtensions = builder
                .comment("Supported file extensions for hot-reload")
                .defineList("supportedExtensions",
                    Arrays.asList("fbx", "gltf", "glb", "dae", "obj", "blend"),
                    obj -> obj instanceof String);

            builder.pop();

            // Gaussian Splatting Configuration
            builder.comment("Gaussian Splatting Configuration").push("splat");

            splatEnabled = builder
                .comment("Enable Gaussian Splatting rendering")
                .define("enabled", true);

            splatLodEnabled = builder
                .comment("Enable LOD (Level of Detail) system")
                .define("lodEnabled", true);

            splatLodDistanceHigh = builder
                .comment("LOD distance threshold for high detail (blocks)")
                .defineInRange("lodDistanceHigh", 16.0, 1.0, 256.0);

            splatLodDistanceMedium = builder
                .comment("LOD distance threshold for medium detail (blocks)")
                .defineInRange("lodDistanceMedium", 32.0, 1.0, 256.0);

            splatLodDistanceLow = builder
                .comment("LOD distance threshold for low detail (blocks)")
                .defineInRange("lodDistanceLow", 64.0, 1.0, 256.0);

            splatLodDistanceCull = builder
                .comment("LOD distance threshold for culling (blocks)")
                .defineInRange("lodDistanceCull", 128.0, 1.0, 512.0);

            splatLodFactorHigh = builder
                .comment("LOD sampling factor for high detail (every Nth splat)")
                .defineInRange("lodFactorHigh", 1, 1, 10);

            splatLodFactorMedium = builder
                .comment("LOD sampling factor for medium detail (every Nth splat)")
                .defineInRange("lodFactorMedium", 2, 1, 10);

            splatLodFactorLow = builder
                .comment("LOD sampling factor for low detail (every Nth splat)")
                .defineInRange("lodFactorLow", 4, 1, 10);

            splatCompressedPLYEnabled = builder
                .comment("Enable compressed PLY support")
                .define("compressedPLYEnabled", true);

            splatCacheSize = builder
                .comment("Splat cache size (number of loaded splat models)")
                .defineInRange("cacheSize", 20, 5, 100);

            builder.pop();

            // Development Configuration
            builder.comment("Development/Testing Configuration").push("dev");

            devAutoTestEnabled = builder
                .comment("Enable auto-test system (executes commands on world join)")
                .define("autoTestEnabled", false);

            devAutoTestCommands = builder
                .comment("Auto-test commands to execute")
                .defineList("autoTestCommands", Arrays.asList("/lookdev"),
                    obj -> obj instanceof String);

            devAutoTestDelay = builder
                .comment("Delay before executing auto-test (ticks)")
                .defineInRange("autoTestDelay", 100, 0, 1200);

            devExampleBlocksEnabled = builder
                .comment("Enable example blocks (CNC machine, etc.)")
                .define("exampleBlocksEnabled", true);

            devLookdevEnabled = builder
                .comment("Enable LookDev node editor")
                .define("lookdevEnabled", true);

            builder.pop();
        }
    }

    /**
     * Common (server + client) configuration
     */
    public static class CommonConfig {
        // Animation
        public final ForgeConfigSpec.IntValue animMaxBonesPerVertex;
        public final ForgeConfigSpec.BooleanValue animIkEnabled;
        public final ForgeConfigSpec.EnumValue<IKSolverType> animIkSolverType;
        public final ForgeConfigSpec.IntValue animIkMaxIterationsFABRIK;
        public final ForgeConfigSpec.IntValue animIkMaxIterationsCCD;
        public final ForgeConfigSpec.DoubleValue animIkDistanceThreshold;
        public final ForgeConfigSpec.BooleanValue animBlendingEnabled;
        public final ForgeConfigSpec.DoubleValue animBlendTime;

        public CommonConfig(ForgeConfigSpec.Builder builder) {
            // Animation Configuration
            builder.comment("Animation Configuration").push("animation");

            animMaxBonesPerVertex = builder
                .comment("Maximum bones per vertex (affects memory and performance)")
                .defineInRange("maxBonesPerVertex", 4, 1, 8);

            animIkEnabled = builder
                .comment("Enable IK (Inverse Kinematics) system")
                .define("ikEnabled", true);

            animIkSolverType = builder
                .comment("IK solver type: FABRIK, CCD, TWO_BONE")
                .defineEnum("ikSolverType", IKSolverType.FABRIK);

            animIkMaxIterationsFABRIK = builder
                .comment("Maximum IK iterations for FABRIK solver")
                .defineInRange("ikMaxIterationsFABRIK", 20, 1, 50);

            animIkMaxIterationsCCD = builder
                .comment("Maximum IK iterations for CCD solver")
                .defineInRange("ikMaxIterationsCCD", 10, 1, 30);

            animIkDistanceThreshold = builder
                .comment("IK solve distance threshold (meters)")
                .defineInRange("ikDistanceThreshold", 0.01, 0.001, 1.0);

            animBlendingEnabled = builder
                .comment("Enable animation blending")
                .define("blendingEnabled", true);

            animBlendTime = builder
                .comment("Animation blend time (seconds)")
                .defineInRange("blendTime", 0.3, 0.0, 2.0);

            builder.pop();
        }
    }

    /**
     * Shader mode enum
     */
    public enum ShaderMode {
        VANILLA,  // Use Minecraft's vanilla shader system
        OCULUS,   // Use Oculus/Iris shader injection
        DUAL      // Support both modes dynamically
    }

    /**
     * Optimization level enum
     */
    public enum OptimizationLevel {
        AUTO,     // Auto-detect based on GPU capabilities
        HIGH,     // High-end GPU optimizations
        MEDIUM,   // Mid-range GPU optimizations
        LOW,      // Low-end GPU optimizations
        FALLBACK  // Minimal features for compatibility
    }

    /**
     * IK solver type enum
     */
    public enum IKSolverType {
        FABRIK,    // Forward And Backward Reaching Inverse Kinematics
        CCD,       // Cyclic Coordinate Descent
        TWO_BONE   // Two-bone IK (for limbs)
    }
}
