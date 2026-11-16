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
        public final ForgeConfigSpec.EnumValue<ClientVisualPolicy.Mode> visualMode;
        public final ForgeConfigSpec.BooleanValue localGroundPlacement;
        public final ForgeConfigSpec.BooleanValue localEffects;
        public final ForgeConfigSpec.ConfigValue<String> externalModelDirectory;
        public final ForgeConfigSpec.BooleanValue frustumCullingEnabled;
        public final ForgeConfigSpec.BooleanValue alwaysRenderLargeModels;
        public final ForgeConfigSpec.DoubleValue largeModelThreshold;
        public final ForgeConfigSpec.IntValue modelViewDistance;
        public final ForgeConfigSpec.EnumValue<ShaderMode> shaderMode;
        public final ForgeConfigSpec.BooleanValue rocketExhaustEnabled;
        public final ForgeConfigSpec.BooleanValue rocketShaderBridgeEnabled;

        // Oculus/Iris
        public final ForgeConfigSpec.BooleanValue oculusEnabled;
        public final ForgeConfigSpec.BooleanValue oculusShaderInjectionEnabled;
        public final ForgeConfigSpec.BooleanValue oculusPbrSupportEnabled;
        public final ForgeConfigSpec.IntValue oculusInjectionPriority;
        public final ForgeConfigSpec.BooleanValue oculusSplatShaderEnabled;
        public final ForgeConfigSpec.BooleanValue oculusPoseCacheEnabled;
        public final ForgeConfigSpec.IntValue oculusPoseCacheMiB;
        public final ForgeConfigSpec.IntValue oculusPoseCacheInstances;

        // Performance
        public final ForgeConfigSpec.EnumValue<OptimizationLevel> optimizationLevel;
        public final ForgeConfigSpec.BooleanValue asyncLoadingEnabled;
        public final ForgeConfigSpec.IntValue modelCacheSize;
        public final ForgeConfigSpec.IntValue textureCacheSizeMB;
        public final ForgeConfigSpec.BooleanValue tboEnabled;
        public final ForgeConfigSpec.IntValue maxTBOBones;
        public final ForgeConfigSpec.BooleanValue computeSkinningEnabled;
        public final ForgeConfigSpec.IntValue computeSkinningMiB;
        public final ForgeConfigSpec.IntValue computeSkinningOwners;
        public final RuntimeConfig runtime;
        public final BatchConfig batch;
        public final CharacterConfig character;

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
            character = new CharacterConfig(builder);
            builder.comment("Client visual inputs; never changes server collision or hit authority").push("visual");
            visualMode=builder.defineEnum("mode",ClientVisualPolicy.Mode.HOST_CONTROLLED);
            localGroundPlacement=builder.comment("CLIENT_LOCAL: align poses using loaded client block collision shapes")
                    .define("groundPlacement",true);
            localEffects=builder.comment("CLIENT_LOCAL: enable configured attachment particles and trails")
                    .define("effects",false);
            builder.pop();
            builder.comment("Rocket exhaust rendering").push("rocketExhaust");
            rocketExhaustEnabled = builder.comment("Render the data-driven GPU volumetric rocket demo")
                    .define("enabled", true);
            rocketShaderBridgeEnabled = builder.comment("Oculus 1.8.0 only: upload exhaust uniforms to explicitly compatible shader packs")
                    .define("shaderPackBridge", true);
            builder.pop();

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

            alwaysRenderLargeModels = builder
                .comment("Bypass frustum and distance culling for showcase models at least largeModelThreshold blocks across.",
                        "The anchor chunk must still be loaded. Rendering distant large models increases GPU work.")
                .define("alwaysRenderLargeModels", false);

            largeModelThreshold = builder
                .comment("Largest model dimension in blocks that qualifies for alwaysRenderLargeModels")
                .defineInRange("largeModelThreshold", 16.0, 1.0, 4096.0);

            modelViewDistance = builder
                .comment("Showcase model view distance in blocks, measured from the closest point of its bounds")
                .defineInRange("modelViewDistance", 128, 16, 4096);

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
                .comment("Reserved legacy option; node-material shader injection is inactive")
                .define("shaderInjectionEnabled", true);

            oculusPbrSupportEnabled = builder
                .comment("Enable PBR material support in Oculus")
                .define("pbrSupportEnabled", true);

            oculusInjectionPriority = builder
                .comment("Reserved legacy option; no shader injection is installed")
                .defineInRange("injectionPriority", 100, 0, 1000);

            oculusSplatShaderEnabled = builder
                .comment("Reserved legacy option; Gaussian shader-pack injection is inactive")
                .define("splatShaderEnabled", true);

            oculusPoseCacheEnabled = builder.comment("Reuse opaque animated ENTITY buffers across Oculus main/shadow passes")
                .define("poseCacheEnabled", true);
            oculusPoseCacheMiB = builder.comment("Total resident animated vertex buffer budget in MiB; oversized poses use normal submission")
                .defineInRange("poseCacheMiB", 128, 1, 1024);
            oculusPoseCacheInstances = builder.comment("Maximum animated instances with resident Oculus buffers")
                .defineInRange("poseCacheInstances", 128, 1, 1024);

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
                .comment("Maximum unpinned/on-demand file models; JSON models stay pinned until resource reload")
                .defineInRange("modelCacheSize", 100, 10, 1000);

            textureCacheSizeMB = builder
                .comment("Reserved texture-budget setting; shared material textures currently live until resource invalidation")
                .defineInRange("textureCacheSizeMB", 512, 64, 2048);

            tboEnabled = builder
                .comment("Enable TBO (Texture Buffer Object) for bone matrices")
                .define("tboEnabled", true);

            maxTBOBones = builder
                .comment("Maximum TBO size (number of bones)")
                .defineInRange("maxTBOBones", 1024, 64, 16384);

            computeSkinningEnabled = builder.comment("Opt-in OpenGL 4.3 skinning; vanilla custom-shader path only")
                    .define("computeSkinningEnabled", false);
            computeSkinningMiB = builder.comment("Total Compute skinning buffer budget in MiB")
                    .defineInRange("computeSkinningMiB", 128, 1, 1024);
            computeSkinningOwners = builder.comment("Maximum cached Compute animation owners")
                    .defineInRange("computeSkinningOwners", 128, 1, 1024);

            runtime = new RuntimeConfig(builder);
            batch = new BatchConfig(builder);
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
                .comment("Reserved format option; compressed PLY is rejected, scalar PLY is supported")
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
                .comment("Reserved legacy option; example registry IDs remain registered for save compatibility")
                .define("exampleBlocksEnabled", true);

            devLookdevEnabled = builder
                .comment("Enable LookDev loaded-model preview; node editing is inactive")
                .define("lookdevEnabled", true);

            builder.pop();
        }
    }

    /** Optional local-player display. Server simulation never reads these client settings. */
    public static final class CharacterConfig {
        public final ForgeConfigSpec.BooleanValue enabled;
        public final ForgeConfigSpec.ConfigValue<String> profile;
        public final ForgeConfigSpec.BooleanValue groundPlacement;
        public final ForgeConfigSpec.BooleanValue effects;
        public final ForgeConfigSpec.BooleanValue physicsEnabled, physicsWorldCollision, ragdollOnDeath;
        public final ForgeConfigSpec.IntValue physicsSampleTicks, physicsRadius;

        public CharacterConfig(ForgeConfigSpec.Builder builder) {
            builder.comment("Local third-person character display; server movement and hitboxes stay authoritative")
                    .push("character");
            enabled = builder.comment("Replace only the local player's third-person world renderer")
                    .define("enabled", false);
            profile = builder.comment("Namespaced character profile JSON resource; resource packs may override it")
                    .define("profile", "assimploader:models/character/block_player/profile.json");
            groundPlacement = builder.comment("Sample loaded client block shapes for configured visual foot placement")
                    .define("groundPlacement", true);
            effects = builder.comment("Enable effects declared by the character instance definition")
                    .define("effects", false);
            physicsEnabled=builder.comment("Enable the profile's isolated client Bullet world; reload the character after changing")
                    .define("physicsEnabled",true);
            physicsWorldCollision=builder.comment("Use bounded collision proxies from already loaded client blocks")
                    .define("physicsWorldCollision",true);
            ragdollOnDeath=builder.define("ragdollOnDeath",true);
            physicsSampleTicks=builder.defineInRange("physicsSampleTicks",4,1,20);
            physicsRadius=builder.defineInRange("physicsRadius",2,1,4);
            builder.pop();
        }
    }

    /** Opt-in opaque model submission. Collision and server simulation never read these client settings. */
    public static final class BatchConfig {
        public final ForgeConfigSpec.EnumValue<GpuBatchPolicy.Mode> mode;
        public final ForgeConfigSpec.IntValue maxInstances;
        public final ForgeConfigSpec.IntValue geometryMiB;
        public final ForgeConfigSpec.IntValue frameMiB;
        public final ForgeConfigSpec.BooleanValue hiZEnabled;
        public final ForgeConfigSpec.IntValue hiZMaxResolution;
        public final ForgeConfigSpec.DoubleValue hiZBias;

        private BatchConfig(ForgeConfigSpec.Builder builder) {
            builder.comment("Optional opaque model batching; unsupported render paths keep their existing renderer")
                    .push("batch");
            mode = builder.comment("OFF, INSTANCED or MDI; requires compatible OpenGL and no active shader pack")
                    .defineEnum("mode", GpuBatchPolicy.Mode.OFF);
            maxInstances = builder.comment("Maximum queued mesh instances per batch; mixed render paths may split a frame")
                    .defineInRange("maxInstances", 4096, 1, 16384);
            geometryMiB = builder.comment("Maximum shared geometry storage in MiB")
                    .defineInRange("geometryMiB", 128, 1, 256);
            frameMiB = builder.comment("Maximum transient instance, palette and command storage in MiB")
                    .defineInRange("frameMiB", 32, 1, 128);
            hiZEnabled = builder.comment("MDI only: conservative occlusion using this frame's opaque client depth")
                    .define("hiZEnabled", false);
            hiZMaxResolution = builder.comment("Maximum dimension of the Hi-Z depth pyramid")
                    .defineInRange("hiZMaxResolution", 1024, 64, 1024);
            hiZBias = builder.comment("Additional depth tolerance; larger values cull less aggressively")
                    .defineInRange("hiZBias", .0005, 0.0, .05);
            builder.pop();
        }
    }

    /** Runtime work budgets; GPU backend selection remains in the existing performance keys. */
    public static final class RuntimeConfig {
        public final ForgeConfigSpec.EnumValue<RuntimePerformancePolicy.Mode> mode;
        public final ForgeConfigSpec.IntValue nearUpdateTicks;
        public final ForgeConfigSpec.IntValue farUpdateTicks;
        public final ForgeConfigSpec.DoubleValue farDistance;
        public final ForgeConfigSpec.IntValue hiddenUpdateTicks;
        public final ForgeConfigSpec.IntValue visibilityGraceTicks;
        public final ForgeConfigSpec.BooleanValue ikEnabled;
        public final ForgeConfigSpec.BooleanValue springBonesEnabled;
        public final ForgeConfigSpec.BooleanValue particlesEnabled;
        public final ForgeConfigSpec.BooleanValue trailsEnabled;
        public final ForgeConfigSpec.IntValue maxParticleEmissions;
        public final ForgeConfigSpec.IntValue maxTrailSamples;
        public final ForgeConfigSpec.BooleanValue gpuMorphEnabled;
        public final ForgeConfigSpec.IntValue maxMorphTargets;
        public final ForgeConfigSpec.IntValue maxMorphMiB;

        RuntimeConfig(ForgeConfigSpec.Builder builder) {
            builder.comment("Runtime workload policy, separate from optimizationLevel GPU backend selection.",
                    "QUALITY, BALANCED and PERFORMANCE use fixed presets; CUSTOM reads the values below.",
                    "One tick is 0.05 simulation seconds. Far/hidden intervals cannot be faster than near.")
                    .push("runtime");
            mode = builder.defineEnum("mode", RuntimePerformancePolicy.Mode.BALANCED);
            nearUpdateTicks = builder.comment("CUSTOM: visible nearby instance update interval in ticks")
                    .defineInRange("nearUpdateTicks", 1, 1, 20);
            farUpdateTicks = builder.comment("CUSTOM: distant instance update interval in ticks")
                    .defineInRange("farUpdateTicks", 2, 1, 20);
            farDistance = builder.comment("CUSTOM: distance in blocks at which far update cadence applies")
                    .defineInRange("farDistance", 64.0, 1.0, 4096.0);
            hiddenUpdateTicks = builder.comment("CUSTOM: update interval after the last rendered instance exceeds its grace period")
                    .defineInRange("hiddenUpdateTicks", 10, 1, 20);
            visibilityGraceTicks = builder.comment("CUSTOM: ticks after last render before hidden cadence applies")
                    .defineInRange("visibilityGraceTicks", 40, 0, 1200);
            ikEnabled = builder.comment("CUSTOM: allow instance IK modifiers")
                    .define("ikEnabled", true);
            springBonesEnabled = builder.comment("CUSTOM: allow instance Spring Bone simulation")
                    .define("springBonesEnabled", true);
            particlesEnabled = builder.comment("CUSTOM: allow attachment particle emitters")
                    .define("particlesEnabled", true);
            trailsEnabled = builder.comment("CUSTOM: allow attachment trail sampling and rendering")
                    .define("trailsEnabled", true);
            maxParticleEmissions = builder.comment("CUSTOM: maximum emissions across all emitters per instance update, 0 suppresses emission")
                    .defineInRange("maxParticleEmissions", 16, 0, 32);
            maxTrailSamples = builder.comment("CUSTOM: maximum retained samples per attachment trail")
                    .defineInRange("maxTrailSamples", 128, 2, 4096);
            gpuMorphEnabled = builder.comment("CUSTOM: allow the existing skinned vertex-shader Morph path")
                    .define("gpuMorphEnabled", true);
            maxMorphTargets = builder.comment("CUSTOM: maximum GPU Morph targets per mesh; excess uses CPU fallback")
                    .defineInRange("maxMorphTargets", 32, 1, 32);
            maxMorphMiB = builder.comment("CUSTOM: maximum immutable GPU Morph delta storage per mesh in MiB; excess uses CPU fallback")
                    .defineInRange("maxMorphMiB", 128, 1, 128);
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
