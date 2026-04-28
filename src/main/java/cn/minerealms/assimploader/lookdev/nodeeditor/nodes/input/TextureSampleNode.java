package cn.minerealms.assimploader.lookdev.nodeeditor.nodes.input;

import cn.minerealms.assimploader.lookdev.nodeeditor.Node;
import cn.minerealms.assimploader.lookdev.nodeeditor.EnhancedNodeSocket;
import cn.minerealms.assimploader.lookdev.nodeeditor.EnhancedSocketType;

import icyllis.modernui.graphics.Canvas;
import icyllis.modernui.graphics.Paint;
import icyllis.modernui.graphics.Image;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.texture.AbstractTexture;
import net.minecraft.client.renderer.texture.TextureManager;
import net.minecraft.resources.ResourceLocation;

/**
 * Texture Sample Node
 *
 * Samples a texture at given UV coordinates and outputs color data.
 *
 * Inputs:
 * - UV (vec2): Texture coordinates
 *
 * Outputs:
 * - Color (vec4): Full RGBA color
 * - RGB (vec3): RGB color without alpha
 * - Alpha (float): Alpha channel only
 *
 * Properties:
 * - Texture path
 * - Filtering mode (nearest/linear)
 * - Wrap mode (repeat/clamp)
 */
public class TextureSampleNode extends Node {

    private String texturePath = "";
    private FilterMode filterMode = FilterMode.LINEAR;
    private WrapMode wrapMode = WrapMode.REPEAT;
    private Image cachedTextureImage = null;
    private String cachedTexturePath = null;

    public TextureSampleNode() {
        super("Texture Sample");

        // Add input socket
        addInput(new EnhancedNodeSocket("UV", EnhancedSocketType.VEC2));

        // Add output sockets
        addOutput(new EnhancedNodeSocket("Color", EnhancedSocketType.VEC4));
        addOutput(new EnhancedNodeSocket("RGB", EnhancedSocketType.VEC3));
        addOutput(new EnhancedNodeSocket("Alpha", EnhancedSocketType.FLOAT));

        this.width = 180.0f;
    }

    @Override
    protected void drawBody(Canvas canvas, Paint paint) {
        float bodyY = y + TITLE_BAR_HEIGHT + BORDER_WIDTH;
        float bodyX = x + BORDER_WIDTH;
        float bodyWidth = width - BORDER_WIDTH * 2;
        float previewHeight = 50;

        // Draw texture preview area background
        paint.setColor(0xFF1A1A1A);
        paint.setStyle(Paint.FILL);
        canvas.drawRect(bodyX + 10, bodyY + 10, bodyX + bodyWidth - 10, bodyY + 10 + previewHeight, paint);

        if (texturePath.isEmpty()) {
            // No texture - draw placeholder
            paint.setColor(0xFF666666);
            canvas.drawRect(bodyX + 15, bodyY + 15, bodyX + bodyWidth - 15, bodyY + 15 + previewHeight - 10, paint);
        } else {
            // Try to load and draw texture
            Image textureImage = getOrLoadTextureImage();
            if (textureImage != null) {
                // Draw the texture preview
                canvas.save();
                canvas.drawImage(textureImage, null,
                    bodyX + 15, bodyY + 15,
                    bodyX + bodyWidth - 15, bodyY + 15 + previewHeight - 10,
                    paint);
                canvas.restore();
            } else {
                // Failed to load - draw error indicator
                paint.setColor(0xFFFF0000);
                canvas.drawRect(bodyX + 15, bodyY + 15, bodyX + bodyWidth - 15, bodyY + 15 + previewHeight - 10, paint);
            }
        }

        // Draw border
        paint.setColor(0xFF666666);
        paint.setStyle(Paint.STROKE);
        paint.setStrokeWidth(1.0f);
        canvas.drawRect(bodyX + 10, bodyY + 10, bodyX + bodyWidth - 10, bodyY + 10 + previewHeight, paint);
    }

    /**
     * Get or load the texture image for preview
     */
    private Image getOrLoadTextureImage() {
        // Check cache
        if (cachedTextureImage != null && texturePath.equals(cachedTexturePath)) {
            return cachedTextureImage;
        }

        // Clear old cache
        if (cachedTextureImage != null) {
            cachedTextureImage = null;
        }

        // Try to load new texture
        if (!texturePath.isEmpty()) {
            try {
                ResourceLocation resourceLocation = new ResourceLocation(texturePath);
                TextureManager textureManager = Minecraft.getInstance().getTextureManager();
                AbstractTexture texture = textureManager.getTexture(resourceLocation);

                if (texture != null) {
                    // TODO: Convert OpenGL texture to ModernUI Image
                    // This requires reading back the texture data from GPU
                    // For now, we'll keep the placeholder
                    cachedTexturePath = texturePath;
                    // cachedTextureImage = convertToImage(texture);
                }
            } catch (Exception e) {
                // Failed to load texture
                cachedTextureImage = null;
                cachedTexturePath = null;
            }
        }

        return cachedTextureImage;
    }

    public String getTexturePath() {
        return texturePath;
    }

    public void setTexturePath(String texturePath) {
        this.texturePath = texturePath;
        // Invalidate cache when path changes
        cachedTextureImage = null;
        cachedTexturePath = null;
    }

    public FilterMode getFilterMode() {
        return filterMode;
    }

    public void setFilterMode(FilterMode filterMode) {
        this.filterMode = filterMode;
    }

    public WrapMode getWrapMode() {
        return wrapMode;
    }

    public void setWrapMode(WrapMode wrapMode) {
        this.wrapMode = wrapMode;
    }

    public enum FilterMode {
        NEAREST("Nearest"),
        LINEAR("Linear"),
        MIPMAP("Mipmap");

        private final String displayName;

        FilterMode(String displayName) {
            this.displayName = displayName;
        }

        public String getDisplayName() {
            return displayName;
        }

        public int toGLConstant() {
            switch (this) {
                case NEAREST: return 9728; // GL_NEAREST
                case LINEAR: return 9729; // GL_LINEAR
                case MIPMAP: return 9987; // GL_LINEAR_MIPMAP_LINEAR
                default: return 9729;
            }
        }
    }

    public enum WrapMode {
        REPEAT("Repeat"),
        CLAMP("Clamp"),
        MIRROR("Mirror");

        private final String displayName;

        WrapMode(String displayName) {
            this.displayName = displayName;
        }

        public String getDisplayName() {
            return displayName;
        }

        public int toGLConstant() {
            switch (this) {
                case REPEAT: return 10497; // GL_REPEAT
                case CLAMP: return 33071; // GL_CLAMP_TO_EDGE
                case MIRROR: return 33648; // GL_MIRRORED_REPEAT
                default: return 10497;
            }
        }
    }
}
