package cn.minerealms.assimploader.cache;

import cn.minerealms.assimploader.core.AssimpModelLoader;
import cn.minerealms.assimploader.core.ModelImportMode;
import cn.minerealms.assimploader.model.AssimpScene;
import cn.minerealms.assimploader.resource.ResourcePackIntegration;
import net.minecraft.resources.ResourceLocation;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.fml.loading.FMLEnvironment;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.InvalidPathException;
import java.nio.file.Path;

/** Compatibility facade over the canonical model repository; this class owns no competing cache. */
public final class ModelCacheManager {
    private static final Logger LOGGER = LoggerFactory.getLogger(ModelCacheManager.class);
    private static final ModelCacheManager INSTANCE = new ModelCacheManager();

    private ModelCacheManager() {}

    public static ModelCacheManager getInstance() { return INSTANCE; }

    public AssimpScene loadModel(String resourcePath) {
        AssimpScene existing = get(resourcePath);
        if (existing != null) return existing;
        try {
            try {
                Path path = Path.of(resourcePath);
                if (Files.isRegularFile(path)) return AssimpModelLoader.loadCachedFile(path, ModelImportMode.STATIC);
            } catch (InvalidPathException ignored) {
                // Namespaced resource locations are not Windows filesystem paths.
            }
            if (FMLEnvironment.dist == Dist.CLIENT) {
                ResourceLocation resource = resourcePath.contains(":") ? new ResourceLocation(resourcePath)
                        : new ResourceLocation("assimploader", resourcePath);
                return ResourcePackIntegration.getInstance().loadModel(resource, ModelImportMode.STATIC);
            }
        } catch (IOException | RuntimeException exception) {
            LOGGER.error("Cannot load model {}", resourcePath, exception);
        }
        return null;
    }

    public AssimpScene get(String resourcePath) { return AssimpModelLoader.getCachedModel(resourcePath); }
    public void put(String resourcePath, AssimpScene scene) { AssimpModelLoader.updateModelCache(resourcePath, scene); }
    public boolean contains(String resourcePath) { return get(resourcePath) != null; }
    public void remove(String resourcePath) { AssimpModelLoader.removeCachedModel(resourcePath); }
    public void clearCache() { AssimpModelLoader.clearCache(); }
    public int size() { return AssimpModelLoader.getLoadedModels().size(); }
}
