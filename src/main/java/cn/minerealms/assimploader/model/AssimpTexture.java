package cn.minerealms.assimploader.model;

import cn.minerealms.assimploader.debug.DebugManager;
import cn.minerealms.assimploader.debug.DebugManager.DebugCategory;
import com.mojang.blaze3d.platform.NativeImage;
import net.minecraft.client.renderer.texture.DynamicTexture;
import org.lwjgl.assimp.AITexel;
import org.lwjgl.assimp.AITexture;

import javax.annotation.Nullable;
import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.nio.ByteBuffer;

/**
 * Immutable snapshot of an embedded texture.
 *
 * <p>Assimp stores embedded textures inside the {@code aiScene}; the bytes are copied out
 * here so the texture can still be decoded after the scene has been released.
 */
public class AssimpTexture {

    private final int index;
    private final String formatHint;
    private final boolean compressed;
    /** Compressed (PNG/JPG/...) payload, or {@code null} for raw textures. */
    private final byte[] encodedBytes;
    /** Raw ABGR8888 pixels, or {@code null} for compressed textures. */
    private final int[] rawAbgr;
    private final int width;
    private final int height;

    public AssimpTexture(AITexture aiTexture, int index) {
        this.index = index;
        this.formatHint = aiTexture.achFormatHintString();
        this.compressed = aiTexture.mHeight() == 0;

        if (compressed) {
            // For compressed textures Assimp stores the encoded byte count in mWidth.
            int size = aiTexture.mWidth();
            ByteBuffer source = aiTexture.pcDataCompressed();
            byte[] bytes = new byte[Math.max(size, 0)];
            if (source != null && size > 0) {
                source.duplicate().get(bytes);
            }
            this.encodedBytes = bytes;
            this.rawAbgr = null;
            this.width = -1;
            this.height = -1;
        } else {
            int w = aiTexture.mWidth();
            int h = aiTexture.mHeight();
            int[] pixels = new int[w * h];
            AITexel.Buffer texels = aiTexture.pcData();
            if (texels != null) {
                for (int i = 0; i < pixels.length; i++) {
                    AITexel texel = texels.get(i);
                    // AITexel is ARGB, NativeImage expects ABGR
                    pixels[i] = ((texel.a() & 0xFF) << 24)
                            | ((texel.b() & 0xFF) << 16)
                            | ((texel.g() & 0xFF) << 8)
                            | (texel.r() & 0xFF);
                }
            }
            this.rawAbgr = pixels;
            this.encodedBytes = null;
            this.width = w;
            this.height = h;
        }
    }

    public int getIndex() {
        return index;
    }

    public String getFormatHint() {
        return formatHint;
    }

    public boolean isCompressed() {
        return compressed;
    }

    public int getWidth() {
        return width;
    }

    public int getHeight() {
        return height;
    }

    /**
     * Decode this texture into a new {@link NativeImage}. The caller owns the image.
     */
    public NativeImage loadAsNativeImage() throws IOException {
        if (compressed) {
            if (encodedBytes == null || encodedBytes.length == 0) {
                throw new IOException("Embedded texture " + index + " has no payload");
            }
            try (ByteArrayInputStream in = new ByteArrayInputStream(encodedBytes)) {
                return NativeImage.read(in);
            }
        }

        NativeImage image = new NativeImage(width, height, false);
        for (int y = 0; y < height; y++) {
            for (int x = 0; x < width; x++) {
                image.setPixelRGBA(x, y, rawAbgr[y * width + x]);
            }
        }
        return image;
    }

    /**
     * Convenience wrapper that returns {@code null} instead of throwing.
     */
    @Nullable
    public DynamicTexture createDynamicTexture() {
        try {
            return new DynamicTexture(loadAsNativeImage());
        } catch (IOException | RuntimeException e) {
            DebugManager.error(DebugCategory.CORE, "Failed to decode embedded texture {}", index, e);
            return null;
        }
    }
}
