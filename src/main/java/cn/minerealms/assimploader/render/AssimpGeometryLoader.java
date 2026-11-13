package cn.minerealms.assimploader.render;

import cn.minerealms.assimploader.config.AssimpLoaderConfig;
import cn.minerealms.assimploader.core.AssimpModelLoader;
import cn.minerealms.assimploader.core.ModelImportMode;
import cn.minerealms.assimploader.debug.DebugManager;
import cn.minerealms.assimploader.debug.DebugManager.DebugCategory;
import cn.minerealms.assimploader.model.AssimpScene;
import cn.minerealms.assimploader.util.AssimpLoaderPaths;
import com.google.gson.JsonDeserializationContext;
import com.google.gson.JsonObject;
import com.google.gson.JsonParseException;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.block.model.ItemOverrides;
import net.minecraft.client.renderer.texture.TextureAtlas;
import net.minecraft.client.renderer.texture.TextureAtlasSprite;
import net.minecraft.client.resources.model.*;
import net.minecraft.resources.ResourceLocation;
import net.minecraftforge.client.model.geometry.IGeometryBakingContext;
import net.minecraftforge.client.model.geometry.IGeometryLoader;
import net.minecraftforge.client.model.geometry.IUnbakedGeometry;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.InvalidPathException;
import java.nio.file.Path;
import java.util.function.Function;

/** Forge geometry entry point. Imports are shared by all JSON models referencing the same identity and mode. */
public class AssimpGeometryLoader implements IGeometryLoader<AssimpGeometryLoader.AssimpGeometry> {
    public static final AssimpGeometryLoader INSTANCE = new AssimpGeometryLoader();

    @Override
    public AssimpGeometry read(JsonObject json, JsonDeserializationContext context) {
        if (!json.has("model")) throw new JsonParseException("Assimp geometry requires a model resource path");
        String mode = json.has("mode") ? json.get("mode").getAsString() : "static";
        if (!mode.equalsIgnoreCase("static") && !mode.equalsIgnoreCase("dynamic")) {
            throw new JsonParseException("Unknown Assimp import mode: " + mode);
        }
        return new AssimpGeometry(json.get("model").getAsString(), mode);
    }

    public static class AssimpGeometry implements IUnbakedGeometry<AssimpGeometry> {
        private final String modelPath;
        private final ModelImportMode mode;

        public AssimpGeometry(String modelPath, String mode) {
            this.modelPath = modelPath;
            this.mode = "dynamic".equalsIgnoreCase(mode) ? ModelImportMode.DYNAMIC : ModelImportMode.STATIC;
        }

        @Override
        public BakedModel bake(IGeometryBakingContext context, ModelBaker baker,
                               Function<Material, TextureAtlasSprite> spriteGetter, ModelState state,
                               ItemOverrides overrides, ResourceLocation modelLocation) {
            ResourceLocation resource = modelPath.contains(":") ? new ResourceLocation(modelPath)
                    : new ResourceLocation(modelLocation.getNamespace(), modelPath);
            AssimpScene scene = null;
            Path external = resolveExternalPath(resource);
            if (external != null) {
                try {
                    scene = AssimpModelLoader.loadCachedFile(AssimpModelLoader.canonicalIdentity(resource), external, mode, true);
                } catch (IOException | RuntimeException exception) {
                    DebugManager.error(DebugCategory.RENDER, "Cannot import external model {}; falling back to resources", external, exception);
                }
            }
            if (scene == null) {
                try {
                    scene = AssimpModelLoader.loadCachedResource(Minecraft.getInstance().getResourceManager(), resource, mode);
                } catch (IOException | RuntimeException exception) {
                    DebugManager.error(DebugCategory.RENDER, "Cannot import model resource {}", resource, exception);
                }
            }
            if (scene == null) return baker.bake(new ResourceLocation("minecraft", "block/stone"), state);
            TextureAtlasSprite white = spriteGetter.apply(new Material(TextureAtlas.LOCATION_BLOCKS,
                    new ResourceLocation("minecraft", "block/white_concrete")));
            return new AssimpBakedModel(scene, white, modelLocation, context.getTransforms());
        }

        private Path resolveExternalPath(ResourceLocation resource) {
            String configured = AssimpLoaderConfig.CLIENT.externalModelDirectory.get();
            if (configured == null || configured.isBlank()) return null;
            try {
                Path root = Path.of(configured.trim());
                if (!root.isAbsolute()) root = AssimpLoaderPaths.gameDirectory().resolve(root);
                Path namespaced = resolveExternalPath(root, resource.getNamespace() + "/" + resource.getPath());
                if (namespaced != null) return namespaced;
                // Legacy paths apply only to this mod; other namespaces cannot silently alias the same file.
                return resource.getNamespace().equals("assimploader") ? resolveExternalPath(root, resource.getPath()) : null;
            } catch (InvalidPathException | IOException exception) {
                DebugManager.warn(DebugCategory.RENDER, "Cannot resolve external model directory '{}': {}", configured, exception.getMessage());
                return null;
            }
        }

        static Path resolveExternalPath(Path root, String resourcePath) throws IOException {
            Path normalizedRoot = root.toAbsolutePath().normalize();
            Path candidate = normalizedRoot.resolve(resourcePath).normalize();
            if (!candidate.startsWith(normalizedRoot) || !Files.isRegularFile(candidate)) return null;
            Path realRoot = normalizedRoot.toRealPath();
            Path realCandidate = candidate.toRealPath();
            return realCandidate.startsWith(realRoot) ? realCandidate : null;
        }

        @Override
        public void resolveParents(Function<ResourceLocation, UnbakedModel> modelGetter, IGeometryBakingContext context) {}
    }
}
