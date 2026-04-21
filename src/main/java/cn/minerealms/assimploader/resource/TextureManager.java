package cn.minerealms.assimploader.resource;

import com.mojang.blaze3d.platform.NativeImage;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.texture.DynamicTexture;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.packs.resources.Resource;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.io.InputStream;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Manages texture loading and caching for Assimp models.
 * Handles both embedded textures and external texture files.
 */
public class TextureManager {
    private static final Logger LOGGER = LoggerFactory.getLogger(TextureManager.class);
    private static final TextureManager INSTANCE = new TextureManager();

    private final Map<ResourceLocation, Integer> textureCache = new ConcurrentHashMap<>();
    private final Map<String, ResourceLocation> embeddedTextures = new ConcurrentHashMap<>();

    private TextureManager() {}

    public static TextureManager getInstance() {
        return INSTANCE;
    }

    /**
     * Loads a texture from a resource location.
     * Returns the OpenGL texture ID.
     */
    public int loadTexture(ResourceLocation location) {
        return textureCache.computeIfAbsent(location, this::loadTextureInternal);
    }

    private int loadTextureInternal(ResourceLocation location) {
        try {
            Optional<Resource> resource = Minecraft.getInstance()
                .getResourceManager()
                .getResource(location);

            if (resource.isEmpty()) {
                LOGGER.warn("Texture not found: {}", location);
                return -1;
            }

            try (InputStream stream = resource.get().open()) {
                NativeImage image = NativeImage.read(stream);
                DynamicTexture texture = new DynamicTexture(image);

                ResourceLocation textureLocation = Minecraft.getInstance()
                    .getTextureManager()
                    .register(location.getNamespace() + "_" + location.getPath(), texture);

                LOGGER.debug("Loaded texture: {}", location);
                return texture.getId();
            }
        } catch (IOException e) {
            LOGGER.error("Failed to load texture: {}", location, e);
            return -1;
        }
    }

    /**
     * Registers an embedded texture from Assimp model data.
     * Embedded textures are stored as byte arrays in the model file.
     */
    public void registerEmbeddedTexture(String name, byte[] data) {
        // TODO: Implement embedded texture loading
        // This would create a DynamicTexture from the byte array
        LOGGER.debug("Registered embedded texture: {}", name);
    }

    /**
     * Gets a texture by its embedded name.
     */
    public ResourceLocation getEmbeddedTexture(String name) {
        return embeddedTextures.get(name);
    }

    /**
     * Clears all cached textures.
     */
    public void clearCache() {
        textureCache.clear();
        embeddedTextures.clear();
        LOGGER.info("Texture cache cleared");
    }

    /**
     * Removes a specific texture from cache.
     */
    public void removeTexture(ResourceLocation location) {
        textureCache.remove(location);
        LOGGER.debug("Removed texture from cache: {}", location);
    }
}
