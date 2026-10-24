package cn.minerealms.assimploader.hotreload;

import cn.minerealms.assimploader.AssimpLoader;
import cn.minerealms.assimploader.animation.ClientAnimationManager;
import cn.minerealms.assimploader.core.AssimpModelLoader;
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
 * Manages hot-reloading of models from .minecraft/AssimpLoader/Resource directory.
 *
 * Directory structure:
 * - .minecraft/AssimpLoader/Resource/models/    - Model files (FBX, GLTF, etc.)
 * - .minecraft/AssimpLoader/Resource/textures/  - Texture files
 * - .minecraft/AssimpLoader/Resource/animations/ - Animation files
 */
public class ModelHotReloadManager {

    // Use relative path from game directory (.minecraft)
    private static final Path EXTERNAL_MODEL_DIR = getGameDirectory().resolve("AssimpLoader/Resource/models");
    private static final Map<String, AssimpScene> loadedModels = new HashMap<>();

    /**
     * Get the game directory (.minecraft)
     */
    private static Path getGameDirectory() {
        return Minecraft.getInstance().gameDirectory.toPath();
    }

    /**
     * Reload all models from AssimpLoader/Resource/models directory
     * Scans recursively and preserves directory structure for model names
     * @return Number of models successfully reloaded
     */
    public static int reloadModels() {
        AssimpLoader.LOGGER.info("Starting hot-reload from: {}", EXTERNAL_MODEL_DIR.toAbsolutePath());

        if (!Files.exists(EXTERNAL_MODEL_DIR)) {
            AssimpLoader.LOGGER.warn("Hot-reload directory does not exist: {}", EXTERNAL_MODEL_DIR.toAbsolutePath());
            AssimpLoader.LOGGER.info("Create directory at: {}", EXTERNAL_MODEL_DIR.toAbsolutePath());
            try {
                Files.createDirectories(EXTERNAL_MODEL_DIR);
                AssimpLoader.LOGGER.info("Created hot-reload directory");
            } catch (IOException e) {
                AssimpLoader.LOGGER.error("Failed to create hot-reload directory", e);
            }
            return 0;
        }

        int reloadedCount = 0;
        loadedModels.clear();

        try (Stream<Path> files = Files.walk(EXTERNAL_MODEL_DIR)) {
            for (Path file : files.filter(Files::isRegularFile).toList()) {
                String fileName = file.getFileName().toString().toLowerCase();

                // Support multiple model formats
                String extension = null;
                if (fileName.endsWith(".fbx")) {
                    extension = ".fbx";
                } else if (fileName.endsWith(".gltf")) {
                    extension = ".gltf";
                } else if (fileName.endsWith(".glb")) {
                    extension = ".glb";
                } else if (fileName.endsWith(".dae")) {
                    extension = ".dae";
                } else if (fileName.endsWith(".obj")) {
                    extension = ".obj";
                } else if (fileName.endsWith(".blend")) {
                    extension = ".blend";
                }

                if (extension == null) {
                    continue; // Skip unsupported files
                }

                try {
                    // Get relative path from EXTERNAL_MODEL_DIR to preserve directory structure
                    // Example: AssimpLoader/Resource/models/block/cnc.fbx -> block/cnc
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

                    // Decode on the model-loader pool when async loading is enabled
                    AssimpScene scene = cn.minerealms.assimploader.core.AsyncModelLoader.loadBlocking(file, mode);

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

        AssimpLoader.LOGGER.info("Hot-reload complete. All renderers will use new models.");
    }
}
