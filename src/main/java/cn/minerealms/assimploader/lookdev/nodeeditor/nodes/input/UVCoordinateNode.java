package cn.minerealms.assimploader.lookdev.nodeeditor.nodes.input;

import cn.minerealms.assimploader.lookdev.nodeeditor.Node;
import cn.minerealms.assimploader.lookdev.nodeeditor.NodeSocket;
import cn.minerealms.assimploader.lookdev.nodeeditor.SocketType;
import icyllis.modernui.graphics.Canvas;
import icyllis.modernui.graphics.Paint;

/**
 * UV Coordinate Node
 *
 * Provides UV texture coordinates with optional tiling and offset.
 *
 * Outputs:
 * - UV (vec2): Texture coordinates
 *
 * Properties:
 * - Tiling (vec2): Scale UV coordinates
 * - Offset (vec2): Shift UV coordinates
 *
 * Common uses:
 * - Input for texture sampling
 * - UV manipulation for tiling textures
 * - Animated UV scrolling
 */
public class UVCoordinateNode extends Node {

    private float tilingU = 1.0f;
    private float tilingV = 1.0f;
    private float offsetU = 0.0f;
    private float offsetV = 0.0f;

    public UVCoordinateNode() {
        super("UV Coordinate");

        // Add output socket
        addOutput(createSocket("UV", SocketType.VEC2, new float[]{0.0f, 0.0f}));

        this.width = 160.0f;
    }

    @Override
    protected void drawBody(Canvas canvas, Paint paint) {
        float bodyY = y + TITLE_BAR_HEIGHT + 10;
        float bodyX = x + 10;

        // Draw UV grid visualization
        float gridSize = 50;
        float gridX = bodyX + (width - gridSize - 20) / 2;
        float gridY = bodyY;

        // Background
        paint.setColor(0xFF1A1A1A);
        paint.setStyle(Paint.FILL);
        canvas.drawRect(gridX, gridY, gridX + gridSize, gridY + gridSize, paint);

        // Grid lines
        paint.setColor(0xFF444444);
        paint.setStyle(Paint.STROKE);
        paint.setStrokeWidth(1.0f);

        // Vertical lines
        for (int i = 0; i <= 4; i++) {
            float x = gridX + (gridSize / 4) * i;
            canvas.drawLine(x, gridY, x, gridY + gridSize, paint);
        }

        // Horizontal lines
        for (int i = 0; i <= 4; i++) {
            float y = gridY + (gridSize / 4) * i;
            canvas.drawLine(gridX, y, gridX + gridSize, y, paint);
        }

        // Border
        paint.setColor(0xFF00AAFF);
        paint.setStrokeWidth(1.5f);
        canvas.drawRect(gridX, gridY, gridX + gridSize, gridY + gridSize, paint);
    }

    private NodeSocket createSocket(String name, SocketType type, Object defaultValue) {
        return new EnhancedSocket(name, type, defaultValue);
    }

    public float getTilingU() {
        return tilingU;
    }

    public void setTilingU(float tilingU) {
        this.tilingU = tilingU;
    }

    public float getTilingV() {
        return tilingV;
    }

    public void setTilingV(float tilingV) {
        this.tilingV = tilingV;
    }

    public float getOffsetU() {
        return offsetU;
    }

    public void setOffsetU(float offsetU) {
        this.offsetU = offsetU;
    }

    public float getOffsetV() {
        return offsetV;
    }

    public void setOffsetV(float offsetV) {
        this.offsetV = offsetV;
    }

    private static class EnhancedSocket extends NodeSocket {
        private final cn.minerealms.assimploader.lookdev.nodeeditor.SocketType enhancedType;

        public EnhancedSocket(String name, cn.minerealms.assimploader.lookdev.nodeeditor.SocketType type, Object defaultValue) {
            super(name, mapToLegacyType(type), defaultValue);
            this.enhancedType = type;
        }

        @Override
        public int getColor() {
            return enhancedType.getColor();
        }

        private static NodeSocket.SocketType mapToLegacyType(cn.minerealms.assimploader.lookdev.nodeeditor.SocketType type) {
            if (type == cn.minerealms.assimploader.lookdev.nodeeditor.SocketType.FLOAT) return NodeSocket.SocketType.FLOAT;
            if (type == cn.minerealms.assimploader.lookdev.nodeeditor.SocketType.VEC2) return NodeSocket.SocketType.VECTOR;
            if (type == cn.minerealms.assimploader.lookdev.nodeeditor.SocketType.VEC3 ||
                type == cn.minerealms.assimploader.lookdev.nodeeditor.SocketType.VEC4) return NodeSocket.SocketType.COLOR;
            if (type == cn.minerealms.assimploader.lookdev.nodeeditor.SocketType.SAMPLER2D) return NodeSocket.SocketType.TEXTURE;
            return NodeSocket.SocketType.ANY;
        }
    }
}
