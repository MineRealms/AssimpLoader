package cn.minerealms.assimploader.resource;

import cn.minerealms.assimploader.core.AssimpModelLoader;
import cn.minerealms.assimploader.core.ModelImportMode;
import cn.minerealms.assimploader.model.AssimpScene;
import net.minecraft.client.Minecraft;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.packs.resources.Resource;
import net.minecraft.server.packs.resources.ResourceManager;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.Optional;
import cn.minerealms.assimploader.util.ResourceLocations;
/**
 * Integrates Assimp model loading with Minecraft's resource pack system.
 * Allows models to be loaded from resource packs and mod resources.
 */
public class ResourcePackIntegration {
    private static final Logger LOGGER = LoggerFactory.getLogger(ResourcePackIntegration.class);
    private static final ResourcePackIntegration INSTANCE = new ResourcePackIntegration();

    private ResourcePackIntegration() {}

    public static ResourcePackIntegration getInstance() {
        return INSTANCE;
    }

    public AssimpScene loadModel(ResourceLocation location, ModelImportMode mode) throws IOException {
        ResourceManager manager = Minecraft.getInstance().getResourceManager();
        ResourceLocation resolved = resolveModelLocation(location.getNamespace(), location.getPath());
        if (manager.getResource(resolved).isPresent()) return AssimpModelLoader.loadCachedResource(manager, resolved, mode);
        for (String extension : new String[]{"fbx", "glb", "gltf", "dae", "obj", "blend", "3ds"}) {
            ResourceLocation candidate = ResourceLocations.of(resolved.getNamespace(), resolved.getPath() + "." + extension);
            if (manager.getResource(candidate).isPresent()) return AssimpModelLoader.loadCachedResource(manager, candidate, mode);
        }
        throw new IOException("Model resource not found: " + resolved);
    }

    /**
     * Loads a model file from Minecraft's resource system.
     * Returns an InputStream for the model data.
     */
    public Optional<InputStream> loadModelStream(ResourceLocation location) {
        ResourceManager resourceManager = Minecraft.getInstance().getResourceManager();

        try {
            Optional<Resource> resource = resourceManager.getResource(location);
            if (resource.isPresent()) {
                return Optional.of(resource.get().open());
            }
        } catch (IOException e) {
            LOGGER.error("Failed to load model resource: {}", location, e);
        }

        return Optional.empty();
    }

    /**
     * Exports just the main file for diagnostics. Model loading uses the resource IO adapter instead.
     */
    @Deprecated
    public Optional<Path> extractModelToTemp(ResourceLocation location) {
        Optional<InputStream> stream = loadModelStream(location);
        if (stream.isEmpty()) {
            return Optional.empty();
        }

        Path tempFile = null;
        try (InputStream in = stream.get()) {
            // Create temp file with appropriate extension
            String fileName = location.getPath();
            int dot = fileName.lastIndexOf('.');
            String extension = dot >= 0 ? fileName.substring(dot) : ".model";
            tempFile = Files.createTempFile("assimp_model_", extension);

            // Copy resource to temp file
            Files.copy(in, tempFile, StandardCopyOption.REPLACE_EXISTING);

            // Mark for deletion on exit
            tempFile.toFile().deleteOnExit();

            LOGGER.debug("Extracted model to temp file: {} -> {}", location, tempFile);
            return Optional.of(tempFile);

        } catch (IOException e) {
            if (tempFile != null) {
                try {
                    Files.deleteIfExists(tempFile);
                } catch (IOException cleanupFailure) {
                    e.addSuppressed(cleanupFailure);
                }
            }
            LOGGER.error("Failed to extract model to temp file: {}", location, e);
            return Optional.empty();
        }
    }

    /**
     * Checks if a model resource exists.
     */
    public boolean modelExists(ResourceLocation location) {
        ResourceManager resourceManager = Minecraft.getInstance().getResourceManager();
        return resourceManager.getResource(location).isPresent();
    }

    /**
     * Resolves a model path to a ResourceLocation.
     * Example: "entity/zombie.gltf" -> "assimploader:models/entity/zombie.gltf"
     */
    public ResourceLocation resolveModelLocation(String namespace, String path) {
        if (!path.startsWith("models/")) {
            path = "models/" + path;
        }
        return ResourceLocations.of(namespace, path);
    }
}
