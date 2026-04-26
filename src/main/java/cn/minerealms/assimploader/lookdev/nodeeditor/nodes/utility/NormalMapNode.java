package cn.minerealms.assimploader.lookdev.nodeeditor.nodes.utility;

import cn.minerealms.assimploader.lookdev.nodeeditor.Node;
import cn.minerealms.assimploader.lookdev.nodeeditor.NodeSocket;
import cn.minerealms.assimploader.lookdev.nodeeditor.SocketType;
import icyllis.modernui.graphics.Canvas;
import icyllis.modernui.graphics.Paint;

/**
 * Normal Map Node
 *
 * Samples a normal map texture and converts it to tangent-space normals.
 *
 * Inputs:
 * - Normal Map (sampler2D): Normal map texture
 * - UV (vec2): Texture coordinates
 * - Strength (float): Normal map intensity (0.0 = flat, 1.0 = full strength)
 *
 * Outputs:
 * - Normal (vec3): Tangent-space normal vector
 *
 * Common uses:
 * - Adding surface detail without geometry
 * - PBR material normal input
 */
public class NormalMapNode extends Node {

    private float strength = 1.0f;

    public NormalMapNode() {
        super("Normal Map");

        // Add input sockets
        addInput(createSocket("Normal Map", SocketType.SAMPLER2D));
        addInput(createSocket("UV", SocketType.VEC2));
        addInput(createSocket("Strength", SocketType.FLOAT));

        // Add output socket
        addOutput(createSocket("Normal", SocketType.VEC3));

        this.width = 170.0f;
    }

    @Override
    protected void drawBody(Canvas canvas, Paint paint) {
        float bodyY = y + TITLE_BAR_HEIGHT + 10;
        float bodyX = x + 10;
        float bodyWidth = width - 20;

        // Draw normal map indicator (blue-ish box)
        paint.setColor(0xFF8080FF);
        paint.setStyle(Paint.FILL);
        canvas.drawRect(bodyX, bodyY, bodyX + bodyWidth, bodyY + 30, paint);

        // Draw strength indicator
        float barY = bodyY + 35;
        float barHeight = 8;

        // Background
        paint.setColor(0xFF1A1A1A);
        canvas.drawRect(bodyX, barY, bodyX + bodyWidth, barY + barHeight, paint);

        // Strength bar
        paint.setColor(0xFF00AAFF);
        canvas.drawRect(bodyX, barY, bodyX + bodyWidth * strength, barY + barHeight, paint);

        // Border
        paint.setColor(0xFF444444);
        paint.setStyle(Paint.STROKE);
        paint.setStrokeWidth(1.0f);
        canvas.drawRect(bodyX, barY, bodyX + bodyWidth, barY + barHeight, paint);
    }

    private NodeSocket createSocket(String name, SocketType type) {
        return new EnhancedSocket(name, type);
    }

    public float getStrength() {
        return strength;
    }

    public void setStrength(float strength) {
        this.strength = Math.max(0.0f, Math.min(1.0f, strength));
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
