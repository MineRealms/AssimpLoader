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
            // Load the model if not cached
            if (cachedScene == null) {
                try {
                    AssimpModelLoader loader = AssimpLoader.getInstance().getModelLoader();
                    if (loader != null) {
                        // Try to load from resource pack or file system
                        Path path = Paths.get("resourcepacks", modelPath);
                        cachedScene = loader.loadModel(path);
                        AssimpLoader.LOGGER.info("Loaded Assimp model: {}", modelPath);
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

            // Get particle texture (use first material's diffuse texture or default)
            TextureAtlasSprite particleSprite = spriteGetter.apply(
                    new Material(
                            net.minecraft.client.renderer.texture.TextureAtlas.LOCATION_BLOCKS,
                            new ResourceLocation("minecraft", "block/stone")
                    )
            );

            return new AssimpBakedModel(cachedScene, particleSprite);
        }

        @Override
        public void resolveParents(Function<ResourceLocation, UnbakedModel> modelGetter, IGeometryBakingContext context) {
            // No parent resolution needed for Assimp models
        }
    }
}
