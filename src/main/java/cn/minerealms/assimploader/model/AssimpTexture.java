package cn.minerealms.assimploader.model;

import cn.minerealms.assimploader.AssimpLoader;
import com.mojang.blaze3d.platform.NativeImage;
import net.minecraft.client.renderer.texture.DynamicTexture;
import org.lwjgl.assimp.AITexel;
import org.lwjgl.assimp.AITexture;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.nio.ByteBuffer;

/**
 * Wrapper for Assimp AITexture containing embedded texture data.
 *
 * Supports both compressed formats (PNG, JPG, BMP) and raw ARGB8888 data.
 */
public class AssimpTexture {

    private final AITexture aiTexture;
    private final int index;
    private final String formatHint;
    private final boolean isCompressed;

    public AssimpTexture(AITexture aiTexture, int index) {
        this.aiTexture = aiTexture;
        this.index = index;
        this.formatHint = aiTexture.achFormatHintString();
        this.isCompressed = aiTexture.mHeight() == 0;

        AssimpLoader.LOGGER.info("Embedded texture {}: format={}, compressed={}, width={}, height={}",
            index, formatHint, isCompressed, aiTexture.mWidth(), aiTexture.mHeight());
    }

    public int getIndex() {
        return index;
    }

    public String getFormatHint() {
        return formatHint;
    }

    public boolean isCompressed() {
        return isCompressed;
    }

    /**
     * Load the embedded texture as a Minecraft NativeImage
     */
    public NativeImage loadAsNativeImage() throws IOException {
        if (isCompressed) {
            return loadCompressedTexture();
        } else {
            return loadRawTexture();
        }
    }

    /**
     * Load compressed texture (PNG, JPG, BMP, etc.)
     */
    private NativeImage loadCompressedTexture() throws IOException {
        int dataSize = aiTexture.mWidth(); // For compressed, mWidth stores data size
        ByteBuffer dataBuffer = aiTexture.pcDataCompressed();

        // Copy to byte array for NativeImage
        byte[] imageData = new byte[dataSize];
        dataBuffer.get(imageData);

        AssimpLoader.LOGGER.info("Loading compressed texture: {} bytes, format hint: {}", dataSize, formatHint);

        try (ByteArrayInputStream inputStream = new ByteArrayInputStream(imageData)) {
            return NativeImage.read(inputStream);
        }
    }

    /**
     * Load raw ARGB8888 texture data
     */
    private NativeImage loadRawTexture() throws IOException {
        int width = aiTexture.mWidth();
        int height = aiTexture.mHeight();

        AssimpLoader.LOGGER.info("Loading raw texture: {}x{}", width, height);

        NativeImage image = new NativeImage(width, height, false);

        AITexel.Buffer texelBuffer = aiTexture.pcData();

        for (int y = 0; y < height; y++) {
            for (int x = 0; x < width; x++) {
                AITexel texel = texelBuffer.get(y * width + x);

                // AITexel stores ARGB
                int a = texel.a() & 0xFF;
                int r = texel.r() & 0xFF;
                int g = texel.g() & 0xFF;
                int b = texel.b() & 0xFF;

                // NativeImage expects ABGR format
                int abgr = (a << 24) | (b << 16) | (g << 8) | r;
                image.setPixelRGBA(x, y, abgr);
            }
        }

        return image;
    }

    /**
     * Create a Minecraft DynamicTexture from this embedded texture
     */
    public DynamicTexture createDynamicTexture() {
        try {
            NativeImage image = loadAsNativeImage();
            return new DynamicTexture(image);
        } catch (IOException e) {
            AssimpLoader.LOGGER.error("Failed to load embedded texture {}: {}", index, e.getMessage());
            return null;
        }
    }
}
