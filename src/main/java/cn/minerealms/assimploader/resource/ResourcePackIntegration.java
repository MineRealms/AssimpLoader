package cn.minerealms.assimploader.resource;

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
     * Extracts a model file to a temporary location for Assimp to load.
     * Assimp requires file paths, not streams, so we need to extract resources.
     */
    public Optional<Path> extractModelToTemp(ResourceLocation location) {
        Optional<InputStream> stream = loadModelStream(location);
        if (stream.isEmpty()) {
            return Optional.empty();
        }

        try {
            // Create temp file with appropriate extension
            String fileName = location.getPath();
            String extension = fileName.substring(fileName.lastIndexOf('.'));
            Path tempFile = Files.createTempFile("assimp_model_", extension);

            // Copy resource to temp file
            try (InputStream in = stream.get()) {
                Files.copy(in, tempFile, StandardCopyOption.REPLACE_EXISTING);
            }

            // Mark for deletion on exit
            tempFile.toFile().deleteOnExit();

            LOGGER.debug("Extracted model to temp file: {} -> {}", location, tempFile);
            return Optional.of(tempFile);

        } catch (IOException e) {
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
        return new ResourceLocation(namespace, path);
    }
}
