package cn.minerealms.assimploader.lookdev.nodeeditor.nodes.input;

import cn.minerealms.assimploader.lookdev.nodeeditor.Node;
import cn.minerealms.assimploader.lookdev.nodeeditor.NodeSocket;
import cn.minerealms.assimploader.lookdev.nodeeditor.SocketType;
import icyllis.modernui.graphics.Canvas;
import icyllis.modernui.graphics.Paint;

/**
 * Color Node
 *
 * Provides a constant color value.
 *
 * Outputs:
 * - Color (vec4): Full RGBA color
 * - RGB (vec3): RGB color without alpha
 *
 * Properties:
 * - Color picker (RGBA)
 *
 * Common uses:
 * - Base color tint
 * - Emissive color
 * - Constant color values
 */
public class ColorNode extends Node {

    private float r = 1.0f;
    private float g = 1.0f;
    private float b = 1.0f;
    private float a = 1.0f;

    public ColorNode() {
        super("Color");

        // Add output sockets
        addOutput(createSocket("Color", SocketType.VEC4, new float[]{r, g, b, a}));
        addOutput(createSocket("RGB", SocketType.VEC3, new float[]{r, g, b}));

        this.width = 150.0f;
    }

    @Override
    protected void drawBody(Canvas canvas, Paint paint) {
        float bodyY = y + TITLE_BAR_HEIGHT + 15;
        float colorBoxSize = 40;
        float colorBoxX = x + (width - colorBoxSize) / 2;

        // Draw color preview box
        int color = ((int)(a * 255) << 24) | ((int)(r * 255) << 16) | ((int)(g * 255) << 8) | (int)(b * 255);
        paint.setColor(color);
        paint.setStyle(Paint.FILL);
        canvas.drawRect(colorBoxX, bodyY, colorBoxX + colorBoxSize, bodyY + colorBoxSize, paint);

        // Draw border
        paint.setColor(0xFFFFFFFF);
        paint.setStyle(Paint.STROKE);
        paint.setStrokeWidth(1.5f);
        canvas.drawRect(colorBoxX, bodyY, colorBoxX + colorBoxSize, bodyY + colorBoxSize, paint);
    }

    private NodeSocket createSocket(String name, SocketType type, Object defaultValue) {
        return new EnhancedSocket(name, type, defaultValue);
    }

    public float getR() {
        return r;
    }

    public void setR(float r) {
        this.r = Math.max(0.0f, Math.min(1.0f, r));
        updateOutputs();
    }

    public float getG() {
        return g;
    }

    public void setG(float g) {
        this.g = Math.max(0.0f, Math.min(1.0f, g));
        updateOutputs();
    }

    public float getB() {
        return b;
    }

    public void setB(float b) {
        this.b = Math.max(0.0f, Math.min(1.0f, b));
        updateOutputs();
    }

    public float getA() {
        return a;
    }

    public void setA(float a) {
        this.a = Math.max(0.0f, Math.min(1.0f, a));
        updateOutputs();
    }

    public void setColor(float r, float g, float b, float a) {
        this.r = Math.max(0.0f, Math.min(1.0f, r));
        this.g = Math.max(0.0f, Math.min(1.0f, g));
        this.b = Math.max(0.0f, Math.min(1.0f, b));
        this.a = Math.max(0.0f, Math.min(1.0f, a));
        updateOutputs();
    }

    private void updateOutputs() {
        if (outputs.size() >= 2) {
            outputs.get(0).setValue(new float[]{r, g, b, a});
            outputs.get(1).setValue(new float[]{r, g, b});
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
