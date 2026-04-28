package cn.minerealms.assimploader.hotreload;

import cn.minerealms.assimploader.AssimpLoader;
import cn.minerealms.assimploader.animation.ClientAnimationManager;
import cn.minerealms.assimploader.core.AssimpModelLoader;
import cn.minerealms.assimploader.example.CircleBlockEntity;
import cn.minerealms.assimploader.model.AssimpScene;
import net.minecraft.client.Minecraft;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.HashMap;
import java.util.Map;
import java.util.stream.Stream;

/**
 * Manages hot-reloading of FBX models from external directory
 */
public class ModelHotReloadManager {

    private static final Path EXTERNAL_MODEL_DIR = Paths.get("H:/MinecraftMods/ImprovedMobs/models");
    private static final Map<String, AssimpScene> loadedModels = new HashMap<>();

    /**
     * Reload all FBX models from external directory
     * Scans recursively and preserves directory structure for model names
     * @return Number of models successfully reloaded
     */
    public static int reloadModels() {
        AssimpLoader.LOGGER.info("Starting hot-reload from: {}", EXTERNAL_MODEL_DIR);

        if (!Files.exists(EXTERNAL_MODEL_DIR)) {
            AssimpLoader.LOGGER.warn("External model directory does not exist: {}", EXTERNAL_MODEL_DIR);
            return 0;
        }

        int reloadedCount = 0;
        loadedModels.clear();

        try (Stream<Path> files = Files.walk(EXTERNAL_MODEL_DIR)) {
            for (Path file : files.filter(Files::isRegularFile).toList()) {
                String fileName = file.getFileName().toString().toLowerCase();

                // Only process FBX files
                if (!fileName.endsWith(".fbx")) {
                    continue;
                }

                try {
                    // Get relative path from EXTERNAL_MODEL_DIR to preserve directory structure
                    // Example: models/models/block/cnc.fbx -> models/block/cnc
                    Path relativePath = EXTERNAL_MODEL_DIR.relativize(file);
                    String relativePathStr = relativePath.toString().replace('\\', '/');
                    String modelName = relativePathStr.substring(0, relativePathStr.lastIndexOf('.'));

                    AssimpLoader.LOGGER.info("Hot-reloading model: {} from {}", modelName, file);

                    // Get simple model name for cache lookup
                    String simpleModelName = fileName.substring(0, fileName.lastIndexOf('.'));

                    // Check if this model exists in resource pack cache to get its original mode
                    AssimpScene existingScene = AssimpModelLoader.getCachedModel(simpleModelName);
                    cn.minerealms.assimploader.core.ModelImportMode mode = cn.minerealms.assimploader.core.ModelImportMode.STATIC;

                    if (existingScene != null) {
                        mode = existingScene.getImportMode();
                        AssimpLoader.LOGGER.info("Using existing mode {} for model {}", mode, simpleModelName);
                    } else {
                        AssimpLoader.LOGGER.warn("Model {} not found in cache, defaulting to STATIC mode", simpleModelName);
                    }

                    // Load the model with the correct mode
                    AssimpModelLoader loader = new AssimpModelLoader();
                    AssimpScene scene = loader.loadModel(file, mode);

                    if (scene != null) {
                        // Store with full path as key (e.g., "models/block/cnc")
                        loadedModels.put(modelName, scene);

                        // Also store with just filename for backward compatibility (e.g., "cnc")
                        loadedModels.put(simpleModelName, scene);

                        // CRITICAL: Update global cache so all systems use the new model
                        // This ensures hot-reload takes priority over resource pack and JAR resources
                        AssimpModelLoader.updateModelCache(simpleModelName, scene);
                        AssimpModelLoader.updateModelCache(modelName, scene); // Also update full path key

                        reloadedCount++;
                        AssimpLoader.LOGGER.info("Successfully reloaded model: {} (also as: {}) with mode {}",
                            modelName, simpleModelName, mode);
                    } else {
                        AssimpLoader.LOGGER.error("Failed to load model: {}", file);
                    }
                } catch (Exception e) {
                    AssimpLoader.LOGGER.error("Error loading model from {}", file, e);
                }
            }
        } catch (IOException e) {
            AssimpLoader.LOGGER.error("Error scanning external model directory", e);
        }

        // Trigger model manager refresh on client
        if (reloadedCount > 0) {
            refreshModelManager();
        }

        AssimpLoader.LOGGER.info("Hot-reload complete. Reloaded {} model(s)", reloadedCount);
        return reloadedCount;
    }

    /**
     * Get a hot-reloaded model by name
     */
    public static AssimpScene getModel(String modelName) {
        return loadedModels.get(modelName);
    }

    /**
     * Check if a model has been hot-reloaded
     */
    public static boolean hasModel(String modelName) {
        return loadedModels.containsKey(modelName);
    }

    /**
     * Refresh the Minecraft model manager to apply changes
     * Hot-reload only updates external model cache, does NOT trigger resource pack reload
     */
    private static void refreshModelManager() {
        // Clear ClientAnimationManager cache so items get new AnimationState
        ClientAnimationManager.clearCache();
        AssimpLoader.LOGGER.info("Cleared ClientAnimationManager cache for hot-reload");

        // Notify all CircleBlockEntity instances to reload their animations
        int notifiedCount = CircleBlockEntity.notifyAllReload();
        AssimpLoader.LOGGER.info("Notified {} CircleBlockEntity instances to reload", notifiedCount);

        AssimpLoader.LOGGER.info("Hot-reload complete. All renderers will use new models.");
    }
}
