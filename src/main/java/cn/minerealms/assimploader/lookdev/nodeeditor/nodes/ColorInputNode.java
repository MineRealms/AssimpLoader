package cn.minerealms.assimploader.lookdev.nodeeditor.nodes;

import cn.minerealms.assimploader.lookdev.nodeeditor.Node;
import cn.minerealms.assimploader.lookdev.nodeeditor.NodeSocket;
import cn.minerealms.assimploader.lookdev.nodeeditor.EnhancedSocketType;
import icyllis.modernui.graphics.Canvas;
import icyllis.modernui.graphics.Paint;

/**
 * Input node that provides a color value.
 *
 * Used for:
 * - Base color
 * - Emissive color
 * - Tint colors
 */
public class ColorInputNode extends Node {

    private float r = 1.0f;
    private float g = 1.0f;
    private float b = 1.0f;
    private float a = 1.0f;

    public ColorInputNode() {
        super("Color Input");

        // Add output socket
        NodeSocket output = new NodeSocket("Color", NodeSocket.SocketType.COLOR, getColorInt());
        addOutput(output);

        // Smaller size for input nodes
        this.width = 150.0f;
    }

    @Override
    protected void drawBody(Canvas canvas, Paint paint) {
        // Draw color preview box
        float boxSize = 40;
        float boxX = x + (width - boxSize) / 2;
        float boxY = y + TITLE_BAR_HEIGHT + 10;

        // Draw color
        paint.setColor(getColorInt());
        paint.setStyle(Paint.FILL);
        canvas.drawRect(boxX, boxY, boxX + boxSize, boxY + boxSize, paint);

        // Draw border
        paint.setColor(0xFFFFFFFF);
        paint.setStyle(Paint.STROKE);
        paint.setStrokeWidth(1.5f);
        canvas.drawRect(boxX, boxY, boxX + boxSize, boxY + boxSize, paint);
    }

    /**
     * Get color as ARGB integer
     */
    public int getColorInt() {
        int ai = (int) (a * 255);
        int ri = (int) (r * 255);
        int gi = (int) (g * 255);
        int bi = (int) (b * 255);
        return (ai << 24) | (ri << 16) | (gi << 8) | bi;
    }

    /**
     * Set color from ARGB integer
     */
    public void setColor(int color) {
        this.a = ((color >> 24) & 0xFF) / 255.0f;
        this.r = ((color >> 16) & 0xFF) / 255.0f;
        this.g = ((color >> 8) & 0xFF) / 255.0f;
        this.b = (color & 0xFF) / 255.0f;

        // Update output socket value
        if (!outputs.isEmpty()) {
            outputs.get(0).setValue(getColorInt());
        }
    }

    /**
     * Set color from RGB components
     */
    public void setColor(float r, float g, float b) {
        this.r = Math.max(0.0f, Math.min(1.0f, r));
        this.g = Math.max(0.0f, Math.min(1.0f, g));
        this.b = Math.max(0.0f, Math.min(1.0f, b));

        // Update output socket value
        if (!outputs.isEmpty()) {
            outputs.get(0).setValue(getColorInt());
        }
    }

    public float getR() {
        return r;
    }

    public float getG() {
        return g;
    }

    public float getB() {
        return b;
    }

    public float getA() {
        return a;
    }
}
