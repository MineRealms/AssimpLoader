package cn.minerealms.assimploader;

import cn.minerealms.assimploader.core.AssimpModelLoader;
import cn.minerealms.assimploader.cache.ModelCacheManager;
import cn.minerealms.assimploader.render.AssimpGeometryLoader;
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

    // Debug toggle - set to true to enable detailed debug logging
    public static final boolean DEBUG_ENABLED = Boolean.getBoolean("assimploader.debug");

    private static AssimpLoader instance;
    private AssimpModelLoader modelLoader;

    public AssimpLoader() {
        instance = this;
        LOGGER.info("Initializing AssimpLoader - Advanced 3D Model Loading System");

        // Initialize debug logging if enabled
        if (DEBUG_ENABLED) {
            DebugLogger.setEnabled(true);
            DebugLogger.info("AssimpLoader debug mode enabled via -Dassimploader.debug=true");
        }

        IEventBus modEventBus = FMLJavaModLoadingContext.get().getModEventBus();

        // Register setup events
        modEventBus.addListener(this::commonSetup);

        if (FMLEnvironment.dist == Dist.CLIENT) {
            modEventBus.addListener(this::clientSetup);
            modEventBus.addListener(this::registerModelLoaders);
        }

        // Register examples
        cn.minerealms.assimploader.example.ExampleRegistry.register(modEventBus);

        // Register to Forge event bus
        MinecraftForge.EVENT_BUS.register(this);

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
            // Initialize model loader
            modelLoader = new AssimpModelLoader();
            LOGGER.info("Assimp model loader initialized");
            DebugLogger.debug("AssimpModelLoader instance created");

            // Log supported formats
            LOGGER.info("Supported formats: FBX, GLTF, GLB, DAE, OBJ, BLEND, 3DS, and 40+ more");
            DebugLogger.debug("Supported formats registered: FBX, GLTF, GLB, DAE, OBJ, BLEND, 3DS, etc.");
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
}
