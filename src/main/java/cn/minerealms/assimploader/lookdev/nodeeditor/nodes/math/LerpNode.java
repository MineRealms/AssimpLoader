package cn.minerealms.assimploader.lookdev.nodeeditor.nodes.math;

import cn.minerealms.assimploader.lookdev.nodeeditor.Node;
import cn.minerealms.assimploader.lookdev.nodeeditor.NodeSocket;
import cn.minerealms.assimploader.lookdev.nodeeditor.SocketType;
import icyllis.modernui.graphics.Canvas;
import icyllis.modernui.graphics.Paint;

/**
 * Lerp Node (Linear Interpolation)
 *
 * Linearly interpolates between two values based on a factor.
 * Result = A * (1 - T) + B * T
 *
 * Inputs:
 * - A (float/vec3/vec4): Start value
 * - B (float/vec3/vec4): End value
 * - T (float): Interpolation factor (0.0 = A, 1.0 = B)
 *
 * Outputs:
 * - Result (same type as A/B): Interpolated value
 *
 * Common uses:
 * - Blending between two colors
 * - Smooth transitions
 * - Masking operations
 */
public class LerpNode extends Node {

    public LerpNode() {
        super("Lerp");

        // Add input sockets
        addInput(createSocket("A", SocketType.VEC3));
        addInput(createSocket("B", SocketType.VEC3));
        addInput(createSocket("T", SocketType.FLOAT));

        // Add output socket
        addOutput(createSocket("Result", SocketType.VEC3));

        this.width = 150.0f;
    }

    @Override
    protected void drawBody(Canvas canvas, Paint paint) {
        float bodyY = y + TITLE_BAR_HEIGHT + 15;
        float bodyX = x + 15;
        float bodyWidth = width - 30;

        // Draw gradient visualization (A -> B)
        paint.setStyle(Paint.FILL);

        // Simple gradient approximation with multiple rectangles
        int steps = 10;
        for (int i = 0; i < steps; i++) {
            float t = (float) i / steps;
            int gray = (int) (255 * t);
            int color = 0xFF000000 | (gray << 16) | (gray << 8) | gray;

            paint.setColor(color);
            float rectX = bodyX + (bodyWidth / steps) * i;
            float rectWidth = bodyWidth / steps + 1; // +1 to avoid gaps
            canvas.drawRect(rectX, bodyY, rectX + rectWidth, bodyY + 20, paint);
        }

        // Draw border
        paint.setColor(0xFF444444);
        paint.setStyle(Paint.STROKE);
        paint.setStrokeWidth(1.0f);
        canvas.drawRect(bodyX, bodyY, bodyX + bodyWidth, bodyY + 20, paint);
    }

    private NodeSocket createSocket(String name, SocketType type) {
        return new EnhancedSocket(name, type);
    }

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
