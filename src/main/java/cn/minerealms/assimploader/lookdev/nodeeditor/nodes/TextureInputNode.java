package cn.minerealms.assimploader.lookdev.nodeeditor.nodes;

import cn.minerealms.assimploader.lookdev.nodeeditor.Node;
import cn.minerealms.assimploader.lookdev.nodeeditor.NodeSocket;
import cn.minerealms.assimploader.lookdev.nodeeditor.EnhancedSocketType;
import icyllis.modernui.graphics.Canvas;
import icyllis.modernui.graphics.Paint;
import net.minecraft.resources.ResourceLocation;

/**
 * Input node that provides a texture.
 *
 * Used for:
 * - Base color texture
 * - Normal map
 * - Metallic/roughness map
 * - Emissive texture
 */
public class TextureInputNode extends Node {

    private ResourceLocation texture;

    public TextureInputNode() {
        super("Texture Input");

        // Add output socket
        NodeSocket output = new NodeSocket("Texture", NodeSocket.SocketType.TEXTURE, null);
        addOutput(output);

        // Smaller size for input nodes
        this.width = 150.0f;
    }

    @Override
    protected void drawBody(Canvas canvas, Paint paint) {
        // Draw texture preview box
        float boxSize = 50;
        float boxX = x + (width - boxSize) / 2;
        float boxY = y + TITLE_BAR_HEIGHT + 10;

        if (texture != null) {
            // TODO: Draw actual texture when texture rendering is available
            // For now, draw a placeholder with checkered pattern
            drawCheckerboard(canvas, paint, boxX, boxY, boxSize);
        } else {
            // No texture - draw empty box
            paint.setColor(0xFF1A1A1A);
            paint.setStyle(Paint.FILL);
            canvas.drawRect(boxX, boxY, boxX + boxSize, boxY + boxSize, paint);
        }

        // Draw border
        paint.setColor(0xFFFFFFFF);
        paint.setStyle(Paint.STROKE);
        paint.setStrokeWidth(1.5f);
        canvas.drawRect(boxX, boxY, boxX + boxSize, boxY + boxSize, paint);
    }

    /**
     * Draw checkerboard pattern as texture placeholder
     */
    private void drawCheckerboard(Canvas canvas, Paint paint, float x, float y, float size) {
        int checks = 8;
        float checkSize = size / checks;

        for (int i = 0; i < checks; i++) {
            for (int j = 0; j < checks; j++) {
                boolean isLight = (i + j) % 2 == 0;
                paint.setColor(isLight ? 0xFF666666 : 0xFF333333);
                paint.setStyle(Paint.FILL);
                canvas.drawRect(
                    x + i * checkSize,
                    y + j * checkSize,
                    x + (i + 1) * checkSize,
                    y + (j + 1) * checkSize,
                    paint
                );
            }
        }
    }

    public ResourceLocation getTexture() {
        return texture;
    }

    public void setTexture(ResourceLocation texture) {
        this.texture = texture;

        // Update output socket value
        if (!outputs.isEmpty()) {
            outputs.get(0).setValue(texture);
        }
    }
}
