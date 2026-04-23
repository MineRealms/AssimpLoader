package cn.minerealms.assimploader.render;

import cn.minerealms.assimploader.AssimpLoader;
import cn.minerealms.assimploader.core.AssimpModelLoader;
import cn.minerealms.assimploader.model.AssimpScene;
import com.google.gson.JsonDeserializationContext;
import com.google.gson.JsonObject;
import com.mojang.datafixers.util.Pair;
import net.minecraft.client.renderer.block.model.ItemOverrides;
import net.minecraft.client.renderer.texture.TextureAtlasSprite;
import net.minecraft.client.resources.model.*;
import net.minecraft.resources.ResourceLocation;
import net.minecraftforge.client.model.geometry.IGeometryBakingContext;
import net.minecraftforge.client.model.geometry.IGeometryLoader;
import net.minecraftforge.client.model.geometry.IUnbakedGeometry;

import java.io.IOException;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Collection;
import java.util.Set;
import java.util.function.Function;

/**
 * Custom geometry loader for Assimp models.
 *
 * Registers with Forge's model loading system to handle custom model formats.
 *
 * Usage in model JSON:
 * {
 *   "loader": "assimploader:assimp",
 *   "model": "models/custom/mymodel.fbx"
 * }
 */
public class AssimpGeometryLoader implements IGeometryLoader<AssimpGeometryLoader.AssimpGeometry> {

    public static final AssimpGeometryLoader INSTANCE = new AssimpGeometryLoader();

    @Override
    public AssimpGeometry read(JsonObject jsonObject, JsonDeserializationContext context) {
        String modelPath = jsonObject.get("model").getAsString();
        return new AssimpGeometry(modelPath);
    }

    /**
     * Unbaked geometry that holds the model path
     */
    public static class AssimpGeometry implements IUnbakedGeometry<AssimpGeometry> {

        private final String modelPath;
        private AssimpScene cachedScene;

        public AssimpGeometry(String modelPath) {
            this.modelPath = modelPath;
        }

        @Override
        public BakedModel bake(IGeometryBakingContext context, ModelBaker baker, Function<Material, TextureAtlasSprite> spriteGetter, ModelState modelState, ItemOverrides overrides, ResourceLocation modelLocation) {
            String modelName = extractModelName(modelPath);

            // Extract simple model name (just filename without extension) for backward compatibility
            String simpleModelName = modelPath;
            if (simpleModelName.contains("/")) {
                simpleModelName = simpleModelName.substring(simpleModelName.lastIndexOf('/') + 1);
            }
            if (simpleModelName.endsWith(".fbx")) {
                simpleModelName = simpleModelName.substring(0, simpleModelName.length() - 4);
            }

            // Parse ResourceLocation from model path
            ResourceLocation resourceLoc;
            if (modelPath.contains(":")) {
                resourceLoc = new ResourceLocation(modelPath);
            } else {
                // Use the same namespace as the model JSON
                resourceLoc = new ResourceLocation(modelLocation.getNamespace(), modelPath);
            }

            // PRIORITY 1: Try external models directory first
            // Convert resource path to external path: assimploader:models/block/cnc.fbx -> H:/MinecraftMods/ImprovedMobs/models/models/block/cnc.fbx
            String externalModelsPath = "H:/MinecraftMods/ImprovedMobs/models/" +
                                       resourceLoc.getPath();

            java.nio.file.Path externalPath = java.nio.file.Paths.get(externalModelsPath);

            if (java.nio.file.Files.exists(externalPath)) {
                AssimpLoader.LOGGER.info("Loading model from external models directory: {}", externalModelsPath);
                try {
                    AssimpScene scene = AssimpModelLoader.loadModelStatic(externalModelsPath);
                    if (scene != null) {
                        cachedScene = scene;
                        AssimpModelLoader.updateModelCache(modelName, scene);
                        AssimpModelLoader.updateModelCache(simpleModelName, scene); // Also cache with simple name
                        AssimpLoader.LOGGER.info("Successfully loaded external model: {} (also as: {})", modelName, simpleModelName);
                    }
                } catch (Exception e) {
                    AssimpLoader.LOGGER.error("Failed to load external model from {}: {}", externalModelsPath, e.getMessage());
                }
            }

            // PRIORITY 2: Try resource pack (internal)
            if (cachedScene == null) {
                try {
                    AssimpModelLoader loader = AssimpLoader.getInstance().getModelLoader();
                    if (loader != null) {
                        AssimpLoader.LOGGER.info("Loading model from resource pack: {}", resourceLoc);

                        var resourceManager = net.minecraft.client.Minecraft.getInstance().getResourceManager();
                        var resource = resourceManager.getResource(resourceLoc);

                        if (resource.isPresent()) {
                            try (var inputStream = resource.get().open()) {
                                byte[] modelData = inputStream.readAllBytes();
                                String fileName = resourceLoc.getPath();

                                cachedScene = loader.loadModelFromMemory(fileName, modelData);

                                if (cachedScene != null) {
                                    AssimpModelLoader.updateModelCache(modelName, cachedScene);
                                    AssimpModelLoader.updateModelCache(simpleModelName, cachedScene); // Also cache with simple name
                                    AssimpLoader.LOGGER.info("Successfully loaded resource pack model: {} (also as: {})", modelName, simpleModelName);
                                }
                            }
                        } else {
                            AssimpLoader.LOGGER.error("Resource not found: {}", resourceLoc);
                        }
                    }
                } catch (IOException e) {
                    AssimpLoader.LOGGER.error("Failed to load resource pack model: {}", modelPath, e);
                }
            }

            // PRIORITY 3: Create placeholder if both failed
            if (cachedScene == null) {
                AssimpLoader.LOGGER.error("FAILED TO LOAD MODEL: {} - Returning fallback model", modelName);
                AssimpLoader.LOGGER.error("  Tried external: {}", externalModelsPath);
                AssimpLoader.LOGGER.error("  Tried resource: {}", resourceLoc);

                // Return Minecraft's missing model (purple/black checkerboard)
                // We can't create a placeholder AssimpScene because those classes now wrap native Assimp objects
                return baker.bake(new ResourceLocation("minecraft", "block/stone"), modelState);
            }

            // Use white concrete texture so vertex colors display correctly
            // Vertex color × white texture = vertex color
            TextureAtlasSprite whiteSprite = spriteGetter.apply(
                    new Material(
                            net.minecraft.client.renderer.texture.TextureAtlas.LOCATION_BLOCKS,
                            new ResourceLocation("minecraft", "block/white_concrete")
                    )
            );

            return new AssimpBakedModel(cachedScene, whiteSprite, modelLocation);
        }

        /**
         * Extract model name from path
         * Returns the full path without extension (e.g., "models/block/cnc.fbx" -> "models/block/cnc")
         * This ensures unique cache keys and matches the resource location structure
         */
        private String extractModelName(String path) {
            // Remove .fbx extension
            int dotIndex = path.lastIndexOf('.');
            return dotIndex > 0 ? path.substring(0, dotIndex) : path;
        }

        /**
         * Create a placeholder scene when model loading fails
         * NOTE: This method is deprecated and should not be used.
         * AssimpScene/Mesh/Material classes now wrap native Assimp objects and cannot be constructed from raw data.
         * Instead, return null and let the bake() method handle the fallback.
         */
        private AssimpScene createPlaceholderScene() {
            AssimpLoader.LOGGER.warn("createPlaceholderScene() called - this is deprecated, returning null");
            return null;
        }

        @Override
        public void resolveParents(Function<ResourceLocation, UnbakedModel> modelGetter, IGeometryBakingContext context) {
            // No parent resolution needed for Assimp models
        }
    }
}
