package cn.minerealms.assimploader.lookdev.nodeeditor.nodes.input;

import cn.minerealms.assimploader.lookdev.nodeeditor.Node;
import cn.minerealms.assimploader.lookdev.nodeeditor.NodeSocket;
import cn.minerealms.assimploader.lookdev.nodeeditor.SocketType;
import icyllis.modernui.graphics.Canvas;
import icyllis.modernui.graphics.Paint;

/**
 * Value Node
 *
 * Provides a single float value (0.0 - 1.0).
 *
 * Outputs:
 * - Value (float): The numeric value
 *
 * Properties:
 * - Value slider (0.0 - 1.0)
 *
 * Common uses:
 * - Roughness parameter
 * - Metallic parameter
 * - Scale factors
 * - Blend weights
 */
public class ValueNode extends Node {

    private float value = 0.5f;

    public ValueNode() {
        super("Value");

        // Add output socket
        addOutput(createSocket("Value", SocketType.FLOAT, value));

        this.width = 150.0f;
    }

    @Override
    protected void drawBody(Canvas canvas, Paint paint) {
        float bodyY = y + TITLE_BAR_HEIGHT + 15;
        float barHeight = 10;
        float barWidth = width - 20;
        float barX = x + 10;

        // Draw background bar
        paint.setColor(0xFF1A1A1A);
        paint.setStyle(Paint.FILL);
        canvas.drawRect(barX, bodyY, barX + barWidth, bodyY + barHeight, paint);

        // Draw value bar
        paint.setColor(0xFFFFAA00);
        canvas.drawRect(barX, bodyY, barX + barWidth * value, bodyY + barHeight, paint);

        // Draw border
        paint.setColor(0xFF444444);
        paint.setStyle(Paint.STROKE);
        paint.setStrokeWidth(1.0f);
        canvas.drawRect(barX, bodyY, barX + barWidth, bodyY + barHeight, paint);
    }

    private NodeSocket createSocket(String name, SocketType type, Object defaultValue) {
        return new EnhancedSocket(name, type, defaultValue);
    }

    public float getValue() {
        return value;
    }

    public void setValue(float value) {
        this.value = Math.max(0.0f, Math.min(1.0f, value));

        // Update output socket value
        if (!outputs.isEmpty()) {
            outputs.get(0).setValue(this.value);
        }
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
