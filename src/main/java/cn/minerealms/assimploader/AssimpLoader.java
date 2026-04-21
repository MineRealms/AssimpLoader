package cn.minerealms.assimploader;

import cn.minerealms.assimploader.core.AssimpModelLoader;
import cn.minerealms.assimploader.cache.ModelCacheManager;
import cn.minerealms.assimploader.render.AssimpGeometryLoader;
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
    private ModelCacheManager cacheManager;

    public AssimpLoader() {
        instance = this;
        LOGGER.info("Initializing AssimpLoader - Advanced 3D Model Loading System");

        IEventBus modEventBus = FMLJavaModLoadingContext.get().getModEventBus();

        // Register setup events
        modEventBus.addListener(this::commonSetup);

        if (FMLEnvironment.dist == Dist.CLIENT) {
            modEventBus.addListener(this::clientSetup);
            modEventBus.addListener(this::registerModelLoaders);
        }

        // Register to Forge event bus
        MinecraftForge.EVENT_BUS.register(this);

        LOGGER.info("AssimpLoader mod instance created");
    }

    private void commonSetup(final FMLCommonSetupEvent event) {
        LOGGER.info("AssimpLoader common setup");

        event.enqueueWork(() -> {
            // Initialize cache manager
            cacheManager = new ModelCacheManager();
            LOGGER.info("Model cache manager initialized");
        });
    }

    private void clientSetup(final FMLClientSetupEvent event) {
        LOGGER.info("AssimpLoader client setup");

        event.enqueueWork(() -> {
            // Initialize model loader
            modelLoader = new AssimpModelLoader();
            LOGGER.info("Assimp model loader initialized");

            // Log supported formats
            LOGGER.info("Supported formats: FBX, GLTF, GLB, DAE, OBJ, BLEND, 3DS, and 40+ more");
        });
    }

    private void registerModelLoaders(final ModelEvent.RegisterGeometryLoaders event) {
        LOGGER.info("Registering Assimp geometry loader");
        event.register("assimp", AssimpGeometryLoader.INSTANCE);
        LOGGER.info("Assimp geometry loader registered successfully");
    }

    public static AssimpLoader getInstance() {
        return instance;
    }

    public AssimpModelLoader getModelLoader() {
        return modelLoader;
    }

    public ModelCacheManager getCacheManager() {
        return cacheManager;
    }
}
