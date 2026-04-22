package cn.minerealms.assimploader.hotreload;

import cn.minerealms.assimploader.AssimpLoader;
import cn.minerealms.assimploader.core.AssimpModelLoader;
import cn.minerealms.assimploader.model.AssimpScene;
import net.minecraft.client.Minecraft;
import net.minecraft.client.resources.model.ModelManager;

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
                    String modelName = fileName.substring(0, fileName.lastIndexOf('.'));
                    AssimpLoader.LOGGER.info("Hot-reloading model: {} from {}", modelName, file);

                    // Load the model using AssimpModelLoader
                    AssimpScene scene = AssimpModelLoader.loadModelStatic(file.toString());

                    if (scene != null) {
                        loadedModels.put(modelName, scene);

                        // Update the model cache in AssimpModelLoader
                        AssimpModelLoader.updateModelCache(modelName, scene);

                        reloadedCount++;
                        AssimpLoader.LOGGER.info("Successfully reloaded model: {}", modelName);
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
     */
    private static void refreshModelManager() {
        Minecraft minecraft = Minecraft.getInstance();
        if (minecraft != null) {
            minecraft.execute(() -> {
                try {
                    AssimpLoader.LOGGER.info("Refreshing model manager...");
                    ModelManager modelManager = minecraft.getModelManager();

                    // Force model baker to rebuild models
                    // This will trigger re-baking of all models that use our geometry loader
                    minecraft.reloadResourcePacks();

                    AssimpLoader.LOGGER.info("Model manager refresh complete");
                } catch (Exception e) {
                    AssimpLoader.LOGGER.error("Error refreshing model manager", e);
                }
            });
        }
    }
}
