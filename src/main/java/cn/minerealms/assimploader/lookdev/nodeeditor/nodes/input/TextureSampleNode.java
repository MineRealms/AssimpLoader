package cn.minerealms.assimploader.lookdev.nodeeditor.nodes.input;

import cn.minerealms.assimploader.lookdev.nodeeditor.Node;
import cn.minerealms.assimploader.lookdev.nodeeditor.NodeSocket;
import cn.minerealms.assimploader.lookdev.nodeeditor.SocketType;
import icyllis.modernui.graphics.Canvas;
import icyllis.modernui.graphics.Paint;

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

    public TextureSampleNode() {
        super("Texture Sample");

        // Add input socket
        addInput(createSocket("UV", SocketType.VEC2));

        // Add output sockets
        addOutput(createSocket("Color", SocketType.VEC4));
        addOutput(createSocket("RGB", SocketType.VEC3));
        addOutput(createSocket("Alpha", SocketType.FLOAT));

        this.width = 180.0f;
    }

    @Override
    protected void drawBody(Canvas canvas, Paint paint) {
        float bodyY = y + TITLE_BAR_HEIGHT + BORDER_WIDTH;
        float bodyX = x + BORDER_WIDTH;
        float bodyWidth = width - BORDER_WIDTH * 2;

        // Draw texture preview area (placeholder)
        paint.setColor(0xFF1A1A1A);
        paint.setStyle(Paint.FILL);
        canvas.drawRect(bodyX + 10, bodyY + 10, bodyX + bodyWidth - 10, bodyY + 50, paint);

        // Draw texture indicator
        if (texturePath.isEmpty()) {
            paint.setColor(0xFF666666);
            canvas.drawRect(bodyX + 15, bodyY + 15, bodyX + bodyWidth - 15, bodyY + 45, paint);
        } else {
            // TODO: Draw actual texture preview (Phase 10)
            paint.setColor(0xFF00FF00);
            canvas.drawRect(bodyX + 15, bodyY + 15, bodyX + bodyWidth - 15, bodyY + 45, paint);
        }
    }

    /**
     * Create a socket with enhanced type
     */
    private NodeSocket createSocket(String name, SocketType type) {
        return new EnhancedSocket(name, type);
    }

    public String getTexturePath() {
        return texturePath;
    }

    public void setTexturePath(String texturePath) {
        this.texturePath = texturePath;
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
        NEAREST,
        LINEAR
    }

    public enum WrapMode {
        REPEAT,
        CLAMP
    }

    /**
     * Enhanced socket with new type system
     */
    private static class EnhancedSocket extends NodeSocket {
        private final cn.minerealms.assimploader.lookdev.nodeeditor.SocketType enhancedType;

        public EnhancedSocket(String name, cn.minerealms.assimploader.lookdev.nodeeditor.SocketType type) {
            super(name, mapToLegacyType(type));
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
