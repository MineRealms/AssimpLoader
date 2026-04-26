package cn.minerealms.assimploader.lookdev.nodeeditor.nodes.math;

import cn.minerealms.assimploader.lookdev.nodeeditor.Node;
import cn.minerealms.assimploader.lookdev.nodeeditor.NodeSocket;
import cn.minerealms.assimploader.lookdev.nodeeditor.SocketType;
import icyllis.modernui.graphics.Canvas;
import icyllis.modernui.graphics.Paint;

/**
 * Multiply Node
 *
 * Multiplies two values component-wise.
 *
 * Inputs:
 * - A (float/vec3/vec4): First value
 * - B (float/vec3/vec4): Second value
 *
 * Outputs:
 * - Result (same type as inputs): A * B
 *
 * Common uses:
 * - Color tinting (multiply texture by color)
 * - Scaling values
 * - Masking operations
 */
public class MultiplyNode extends Node {

    public MultiplyNode() {
        super("Multiply");

        // Add input sockets (default to VEC3 for color operations)
        addInput(createSocket("A", SocketType.VEC3));
        addInput(createSocket("B", SocketType.VEC3));

        // Add output socket
        addOutput(createSocket("Result", SocketType.VEC3));

        this.width = 150.0f;
    }

    @Override
    protected void drawBody(Canvas canvas, Paint paint) {
        float bodyY = y + TITLE_BAR_HEIGHT + 20;
        float centerX = x + width / 2;

        // Draw multiply symbol (×)
        paint.setColor(0xFFFFFFFF);
        paint.setStyle(Paint.STROKE);
        paint.setStrokeWidth(2.5f);

        float symbolSize = 15;
        canvas.drawLine(
                centerX - symbolSize / 2, bodyY - symbolSize / 2,
                centerX + symbolSize / 2, bodyY + symbolSize / 2,
                paint
        );
        canvas.drawLine(
                centerX - symbolSize / 2, bodyY + symbolSize / 2,
                centerX + symbolSize / 2, bodyY - symbolSize / 2,
                paint
        );
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
