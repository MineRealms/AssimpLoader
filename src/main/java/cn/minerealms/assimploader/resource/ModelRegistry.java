package cn.minerealms.assimploader.resource;

import cn.minerealms.assimploader.cache.ModelCacheManager;
import cn.minerealms.assimploader.model.AssimpScene;
import net.minecraft.resources.ResourceLocation;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Central registry for all loaded Assimp models.
 * Provides model lookup and lifecycle management.
 */
public class ModelRegistry {
    private static final Logger LOGGER = LoggerFactory.getLogger(ModelRegistry.class);
    private static final ModelRegistry INSTANCE = new ModelRegistry();

    private final Map<ModelResourceLocation, AssimpScene> models = new ConcurrentHashMap<>();
    private final ModelCacheManager cacheManager = ModelCacheManager.getInstance();

    private ModelRegistry() {}

    public static ModelRegistry getInstance() {
        return INSTANCE;
    }

    /**
     * Registers a model with the registry.
     */
    public void registerModel(ModelResourceLocation location, AssimpScene scene) {
        models.put(location, scene);
        LOGGER.info("Registered model: {}", location);
    }

    /**
     * Gets a model from the registry, loading it if necessary.
     */
    public AssimpScene getModel(ModelResourceLocation location) {
        return models.computeIfAbsent(location, this::loadModel);
    }

    /**
     * Gets a model by simple ResourceLocation (uses default variant).
     */
    public AssimpScene getModel(ResourceLocation location) {
        return getModel(new ModelResourceLocation(location));
    }

    private AssimpScene loadModel(ModelResourceLocation location) {
        try {
            String path = resolveModelPath(location);
            AssimpScene scene = cacheManager.loadModel(path);

            if (scene == null) {
                LOGGER.error("Failed to load model: {}", location);
                return null;
            }

            LOGGER.info("Loaded model: {} from {}", location, path);
            return scene;
        } catch (Exception e) {
            LOGGER.error("Error loading model: {}", location, e);
            return null;
        }
    }

    /**
     * Resolves a ModelResourceLocation to an actual file path.
     * Searches in resource packs and mod resources.
     */
    private String resolveModelPath(ModelResourceLocation location) {
        // For now, assume models are in assets/<namespace>/models/
        // TODO: Integrate with Minecraft's resource system
        ResourceLocation rl = location.getLocation();
        return "assets/" + rl.getNamespace() + "/models/" + rl.getPath();
    }

    /**
     * Checks if a model is registered.
     */
    public boolean hasModel(ModelResourceLocation location) {
        return models.containsKey(location);
    }

    /**
     * Unregisters a model and cleans up its resources.
     */
    public void unregisterModel(ModelResourceLocation location) {
        AssimpScene scene = models.remove(location);
        if (scene != null) {
            scene.release();
            LOGGER.info("Unregistered model: {}", location);
        }
    }

    /**
     * Clears all registered models.
     */
    public void clearAll() {
        models.values().forEach(AssimpScene::release);
        models.clear();
        cacheManager.clearCache();
        LOGGER.info("Cleared all models from registry");
    }

    /**
     * Gets the number of registered models.
     */
    public int getModelCount() {
        return models.size();
    }
}
