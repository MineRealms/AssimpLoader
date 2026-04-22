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
            AssimpLoader.LOGGER.info("=== BAKE CALLED ===");
            AssimpLoader.LOGGER.info("Model location: {}", modelLocation);
            AssimpLoader.LOGGER.info("Model path from JSON: {}", modelPath);
            AssimpLoader.LOGGER.info("Cached scene: {}", cachedScene);

            // Load the model if not cached
            if (cachedScene == null) {
                AssimpLoader.LOGGER.info("Scene not cached, loading...");
                try {
                    AssimpModelLoader loader = AssimpLoader.getInstance().getModelLoader();
                    AssimpLoader.LOGGER.info("ModelLoader instance: {}", loader);

                    if (loader != null) {
                        // Parse ResourceLocation from model path
                        ResourceLocation resourceLoc;
                        if (modelPath.contains(":")) {
                            resourceLoc = new ResourceLocation(modelPath);
                            AssimpLoader.LOGGER.info("Parsed ResourceLocation with namespace: {}", resourceLoc);
                        } else {
                            // Use the same namespace as the model JSON
                            resourceLoc = new ResourceLocation(modelLocation.getNamespace(), modelPath);
                            AssimpLoader.LOGGER.info("Created ResourceLocation from model JSON namespace: {}", resourceLoc);
                        }

                        AssimpLoader.LOGGER.info("Attempting to load Assimp model from ResourceLocation: {}", resourceLoc);

                        // Load from resource system (works with JAR and file system)
                        var resourceManager = net.minecraft.client.Minecraft.getInstance().getResourceManager();
                        AssimpLoader.LOGGER.info("Getting resource from ResourceManager...");
                        var resource = resourceManager.getResource(resourceLoc);

                        if (resource.isPresent()) {
                            AssimpLoader.LOGGER.info("Resource found, opening input stream...");
                            try (var inputStream = resource.get().open()) {
                                byte[] modelData = inputStream.readAllBytes();
                                AssimpLoader.LOGGER.info("Read {} bytes from resource", modelData.length);
                                String fileName = resourceLoc.getPath();

                                cachedScene = loader.loadModelFromMemory(fileName, modelData);
                                if (cachedScene != null) {
                                    AssimpLoader.LOGGER.info("Successfully loaded Assimp model: {}", modelPath);
                                } else {
                                    AssimpLoader.LOGGER.error("loadModelFromMemory returned null for: {}", modelPath);
                                }
                            }
                        } else {
                            AssimpLoader.LOGGER.error("Resource not found in ResourceManager: {}", resourceLoc);
                            throw new IOException("Resource not found: " + resourceLoc);
                        }
                    }
                } catch (IOException e) {
                    AssimpLoader.LOGGER.error("=== EXCEPTION DURING MODEL LOAD ===", e);
                    AssimpLoader.LOGGER.error("Exception type: {}", e.getClass().getName());
                    AssimpLoader.LOGGER.error("Exception message: {}", e.getMessage());
                    AssimpLoader.LOGGER.error("Stack trace:", e);
                    return null;
                } catch (Exception e) {
                    AssimpLoader.LOGGER.error("=== UNEXPECTED EXCEPTION ===", e);
                    AssimpLoader.LOGGER.error("Exception type: {}", e.getClass().getName());
                    AssimpLoader.LOGGER.error("Exception message: {}", e.getMessage());
                    AssimpLoader.LOGGER.error("Stack trace:", e);
                    return null;
                }
            }

            if (cachedScene == null) {
                AssimpLoader.LOGGER.error("=== SCENE IS NULL AFTER LOAD ===");
                AssimpLoader.LOGGER.error("Model path: {}", modelPath);
                AssimpLoader.LOGGER.error("Returning null BakedModel - will cause purple-black texture");
                return null;
            }

            AssimpLoader.LOGGER.info("Scene loaded successfully, converting to BakedModel...");
            AssimpLoader.LOGGER.info("Scene has {} meshes", cachedScene.getMeshes().size());

            // Get particle texture (use first material's diffuse texture or default)
            TextureAtlasSprite particleSprite = spriteGetter.apply(
                    new Material(
                            net.minecraft.client.renderer.texture.TextureAtlas.LOCATION_BLOCKS,
                            new ResourceLocation("minecraft", "block/stone")
                    )
            );

            AssimpLoader.LOGGER.info("Creating AssimpBakedModel...");
            AssimpBakedModel bakedModel = new AssimpBakedModel(cachedScene, particleSprite);
            AssimpLoader.LOGGER.info("=== BAKE COMPLETED SUCCESSFULLY ===");
            return bakedModel;
        }

        @Override
        public void resolveParents(Function<ResourceLocation, UnbakedModel> modelGetter, IGeometryBakingContext context) {
            // No parent resolution needed for Assimp models
        }
    }
}
