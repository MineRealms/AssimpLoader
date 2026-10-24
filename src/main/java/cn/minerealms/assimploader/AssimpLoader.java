package cn.minerealms.assimploader;

import cn.minerealms.assimploader.compat.photon.PhotonCompat;
import cn.minerealms.assimploader.config.AssimpLoaderConfig;
import cn.minerealms.assimploader.core.AssimpModelLoader;
import cn.minerealms.assimploader.cache.ModelCacheManager;
import cn.minerealms.assimploader.render.AssimpGeometryLoader;
import cn.minerealms.assimploader.render.GLCapabilityDetector;
import cn.minerealms.assimploader.util.DebugLogger;
import net.minecraft.resources.ResourceLocation;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.client.event.ModelEvent;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.eventbus.api.IEventBus;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.event.lifecycle.FMLClientSetupEvent;
import net.minecraftforge.fml.event.lifecycle.FMLCommonSetupEvent;
import net.minecraftforge.fml.javafmlmod.FMLJavaModLoadingContext;
import net.minecraftforge.fml.loading.FMLEnvironment;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * AssimpLoader - Advanced 3D Model Loading System for Minecraft
 *
 * This mod provides a bridge between Assimp (Open Asset Import Library) and Minecraft's
 * rendering pipeline, enabling support for modern 3D model formats including:
 * - FBX, GLTF/GLB, Collada (DAE), OBJ, Blender, 3DS, and 40+ other formats
 * - Skeletal animations with bone hierarchies
 * - PBR materials and textures
 * - Efficient model caching and async loading
 *
 * Architecture:
 * - Phase 1: Project setup and LWJGL Assimp integration ✓
 * - Phase 2: Assimp to Minecraft data conversion layer
 * - Phase 3: Custom BakedModel and rendering pipeline
 * - Phase 4: Skeletal animation system
 * - Phase 5: Resource loading and caching
 * - Phase 6: Testing and examples
 */
@Mod(AssimpLoader.MODID)
public class AssimpLoader {

    public static final String MODID = "assimploader";
    public static final Logger LOGGER = LoggerFactory.getLogger("AssimpLoader");

    private static AssimpLoader instance;
    private AssimpModelLoader modelLoader;

    public AssimpLoader() {
        instance = this;

        LOGGER.info("Initializing AssimpLoader - Advanced 3D Model Loading System");

        // Register configuration FIRST
        AssimpLoaderConfig.register();
        LOGGER.info("Configuration system registered");

        // Initialize LWJGL Assimp bootstrap FIRST
        try {
            AssimpBootstrap.init();
            LOGGER.info("LWJGL Assimp bootstrap initialized");
        } catch (Exception e) {
            LOGGER.error("Failed to initialize LWJGL Assimp bootstrap", e);
            // Don't throw - let it fail later if actually needed
        }

        // Check LWJGL Assimp availability (warning only, not fatal)
        if (!AssimpBootstrap.isAvailable()) {
            LOGGER.warn("===========================================");
            LOGGER.warn("LWJGL Assimp class not found in classpath");
            LOGGER.warn("===========================================");
            LOGGER.warn("This may be normal in development environment.");
            LOGGER.warn("If models fail to load, check LWJGL Assimp dependency.");
            LOGGER.warn("===========================================");
        } else {
            String assimpVersion = AssimpBootstrap.getVersion();
            LOGGER.info("LWJGL Assimp initialized: version {}", assimpVersion);
            DebugLogger.info("Assimp version: {}", assimpVersion);
        }

        IEventBus modEventBus = FMLJavaModLoadingContext.get().getModEventBus();

        // Register setup events
        modEventBus.addListener(this::commonSetup);

        if (FMLEnvironment.dist == Dist.CLIENT) {
            modEventBus.addListener(this::clientSetup);
            modEventBus.addListener(this::registerModelLoaders);
            modEventBus.addListener(cn.minerealms.assimploader.lookdev.LookDevKeybind::registerKeyMappings);
        }

        // Register examples
        cn.minerealms.assimploader.example.ExampleRegistry.register(modEventBus);

        // Register auto-test manager (client-side only) - controlled by config
        if (FMLEnvironment.dist == Dist.CLIENT) {
            MinecraftForge.EVENT_BUS.register(cn.minerealms.assimploader.debug.AutoTestManager.class);
        }

        // Initialize Photon integration (conditional)
        PhotonCompat.init();

        LOGGER.info("AssimpLoader mod instance created");
    }

    private void commonSetup(final FMLCommonSetupEvent event) {
        LOGGER.info("AssimpLoader common setup");
        DebugLogger.debug("Common setup phase starting");

        event.enqueueWork(() -> {
            // Cache manager is now a singleton
            LOGGER.info("Model cache manager ready");
            DebugLogger.debug("Model cache manager initialized");
        });
    }

    private void clientSetup(final FMLClientSetupEvent event) {
        LOGGER.info("AssimpLoader client setup");
        DebugLogger.debug("Client setup phase starting");

        event.enqueueWork(() -> {
            // Initialize debug logging from config
            boolean debugEnabled = AssimpLoaderConfig.CLIENT.debugEnabled.get();
            DebugLogger.setEnabled(debugEnabled);

            if (debugEnabled) {
                LOGGER.info("Debug logging enabled via config");
                DebugLogger.info("=== AssimpLoader Debug Mode ===");
            }

            // Initialize DebugManager and PerformanceProfiler
            cn.minerealms.assimploader.debug.DebugManager.enable();

            // Enable profiling if configured
            if (AssimpLoaderConfig.CLIENT.profilingEnabled.get()) {
                cn.minerealms.assimploader.debug.DebugManager.enableProfiling();
                LOGGER.info("Performance profiling enabled via config");
            }

            // Detect GPU capabilities for optimization
            try {
                GLCapabilityDetector.detect();
                GLCapabilityDetector.OptimizationLevel optLevel = GLCapabilityDetector.getRecommendedOptimizationLevel();
                LOGGER.info("GPU optimization level: {} - {}", optLevel, optLevel.getDescription());
            } catch (Exception e) {
                LOGGER.warn("Failed to detect GPU capabilities, using fallback mode", e);
            }

            // Initialize model loader
            modelLoader = new AssimpModelLoader();
            LOGGER.info("Assimp model loader initialized");
            DebugLogger.debug("AssimpModelLoader instance created");

            // Log supported formats
            LOGGER.info("Supported formats: FBX, GLTF, GLB, DAE, OBJ, BLEND, 3DS, and 40+ more");
            DebugLogger.debug("Supported formats registered: FBX, GLTF, GLB, DAE, OBJ, BLEND, 3DS, etc.");

            // Check Oculus compatibility
            cn.minerealms.assimploader.compat.OculusCompat.isOculusLoaded();
        });
    }

    private void registerModelLoaders(final ModelEvent.RegisterGeometryLoaders event) {
        LOGGER.info("Registering Assimp geometry loader");
        DebugLogger.debug("Registering geometry loader for event: {}", event.getClass().getSimpleName());

        event.register("assimp", AssimpGeometryLoader.INSTANCE);

        LOGGER.info("Assimp geometry loader registered successfully");
        DebugLogger.debug("Geometry loader 'assimp' registered with Forge");
    }

    public static AssimpLoader getInstance() {
        return instance;
    }

    public AssimpModelLoader getModelLoader() {
        return modelLoader;
    }

    public ModelCacheManager getCacheManager() {
        return ModelCacheManager.getInstance();
    }

    /**
     * Enable or disable debug logging at runtime
     */
    public static void setDebugEnabled(boolean enabled) {
        DebugLogger.setEnabled(enabled);
        LOGGER.info("AssimpLoader debug logging {}", enabled ? "enabled" : "disabled");
    }

    /**
     * Check if debug logging is enabled
     */
    public static boolean isDebugEnabled() {
        return DebugLogger.isEnabled();
    }

    /**
     * Get configuration instance
     */
    public static AssimpLoaderConfig.ClientConfig getClientConfig() {
        return AssimpLoaderConfig.CLIENT;
    }

    /**
     * Get common configuration instance
     */
    public static AssimpLoaderConfig.CommonConfig getCommonConfig() {
        return AssimpLoaderConfig.COMMON;
    }
}
