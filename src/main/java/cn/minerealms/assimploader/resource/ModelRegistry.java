package cn.minerealms.assimploader.resource;

import cn.minerealms.assimploader.core.AssimpModelLoader;
import cn.minerealms.assimploader.core.ModelImportMode;
import cn.minerealms.assimploader.model.AssimpScene;
import net.minecraft.resources.ResourceLocation;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import cn.minerealms.assimploader.util.ResourceLocations;
/** Compatibility API over the canonical loader. Variant labels do not create duplicate scene ownership. */
public final class ModelRegistry {
    private static final Logger LOGGER = LoggerFactory.getLogger(ModelRegistry.class);
    private static final ModelRegistry INSTANCE = new ModelRegistry();

    private ModelRegistry() {}
    public static ModelRegistry getInstance() { return INSTANCE; }

    public void registerModel(ModelResourceLocation location, AssimpScene scene) {
        AssimpModelLoader.registerModel(resource(location), scene);
    }

    public AssimpScene getModel(ModelResourceLocation location) {
        ResourceLocation resource = resource(location);
        ModelImportMode mode = location.isAnimated() ? ModelImportMode.DYNAMIC : ModelImportMode.STATIC;
        AssimpScene cached = AssimpModelLoader.getCachedModel(AssimpModelLoader.canonicalIdentity(resource), mode);
        if (cached != null) return cached;
        try {
            return ResourcePackIntegration.getInstance().loadModel(resource, mode);
        } catch (IOException exception) {
            LOGGER.error("Cannot load model {}", location, exception);
            return null;
        }
    }

    public AssimpScene getModel(ResourceLocation location) { return getModel(new ModelResourceLocation(location)); }

    public boolean hasModel(ModelResourceLocation location) {
        return AssimpModelLoader.getCachedModel(AssimpModelLoader.canonicalIdentity(resource(location)),
                location.isAnimated() ? ModelImportMode.DYNAMIC : ModelImportMode.STATIC) != null;
    }

    public void unregisterModel(ModelResourceLocation location) {
        String key = AssimpModelLoader.canonicalIdentity(resource(location)) + (location.isAnimated() ? "#dynamic" : "#static");
        AssimpModelLoader.removeCachedModel(key);
    }

    public void clearAll() { AssimpModelLoader.clearCache(); }
    public int getModelCount() { return AssimpModelLoader.getLoadedModels().size(); }

    private static ResourceLocation resource(ModelResourceLocation location) {
        ResourceLocation resource = location.getLocation();
        String path = resource.getPath();
        return ResourceLocations.of(resource.getNamespace(), path.startsWith("models/") ? path : "models/" + path);
    }
}
