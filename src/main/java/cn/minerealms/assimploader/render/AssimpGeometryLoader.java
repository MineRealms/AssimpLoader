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
            // Check for hot-reloaded model first (always check, even if we have a cached scene)
            String modelName = extractModelName(modelPath);
            AssimpScene hotReloadedScene = AssimpModelLoader.getCachedModel(modelName);

            if (hotReloadedScene != null) {
                AssimpLoader.LOGGER.info("Using hot-reloaded model: {}", modelName);
                // Always use the hot-reloaded version if available
                cachedScene = hotReloadedScene;
            } else if (cachedScene == null) {
                // Only load from resources if we don't have a cached scene and no hot-reload available
                try {
                    AssimpModelLoader loader = AssimpLoader.getInstance().getModelLoader();
                    if (loader != null) {
                        // Parse ResourceLocation from model path
                        ResourceLocation resourceLoc;
                        if (modelPath.contains(":")) {
                            resourceLoc = new ResourceLocation(modelPath);
                        } else {
                            // Use the same namespace as the model JSON
                            resourceLoc = new ResourceLocation(modelLocation.getNamespace(), modelPath);
                        }

                        AssimpLoader.LOGGER.info("Attempting to load Assimp model from ResourceLocation: {}", resourceLoc);

                        // Load from resource system (works with JAR and file system)
                        var resourceManager = net.minecraft.client.Minecraft.getInstance().getResourceManager();
                        var resource = resourceManager.getResource(resourceLoc);

                        if (resource.isPresent()) {
                            try (var inputStream = resource.get().open()) {
                                byte[] modelData = inputStream.readAllBytes();
                                String fileName = resourceLoc.getPath();

                                cachedScene = loader.loadModelFromMemory(fileName, modelData);

                                // IMPORTANT: Cache the scene so BlockEntityRenderer can access it
                                if (cachedScene != null) {
                                    AssimpModelLoader.updateModelCache(modelName, cachedScene);
                                    AssimpLoader.LOGGER.info("Loaded and cached Assimp model: {} -> {}", modelPath, modelName);
                                }
                            }
                        } else {
                            throw new IOException("Resource not found: " + resourceLoc);
                        }
                    }
                } catch (IOException e) {
                    AssimpLoader.LOGGER.error("Failed to load Assimp model: {}", modelPath, e);
                    return null;
                }
            }

            if (cachedScene == null) {
                AssimpLoader.LOGGER.error("Failed to bake model, scene is null: {}", modelPath);
                return null;
            }

            // Use white concrete texture so vertex colors display correctly
            // Vertex color × white texture = vertex color
            TextureAtlasSprite whiteSprite = spriteGetter.apply(
                    new Material(
                            net.minecraft.client.renderer.texture.TextureAtlas.LOCATION_BLOCKS,
                            new ResourceLocation("minecraft", "block/white_concrete")
                    )
            );

            return new AssimpBakedModel(cachedScene, whiteSprite);
        }

        /**
         * Extract model name from path (e.g., "models/block/cnc.fbx" -> "cnc")
         */
        private String extractModelName(String path) {
            String fileName = path.substring(path.lastIndexOf('/') + 1);
            int dotIndex = fileName.lastIndexOf('.');
            return dotIndex > 0 ? fileName.substring(0, dotIndex) : fileName;
        }

        @Override
        public void resolveParents(Function<ResourceLocation, UnbakedModel> modelGetter, IGeometryBakingContext context) {
            // No parent resolution needed for Assimp models
        }
    }
}
