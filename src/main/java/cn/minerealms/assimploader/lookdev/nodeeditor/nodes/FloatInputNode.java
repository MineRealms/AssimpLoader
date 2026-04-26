package cn.minerealms.assimploader.lookdev.nodeeditor.nodes;

import cn.minerealms.assimploader.lookdev.nodeeditor.Node;
import cn.minerealms.assimploader.lookdev.nodeeditor.NodeSocket;
import cn.minerealms.assimploader.lookdev.nodeeditor.EnhancedSocketType;
import icyllis.modernui.graphics.Canvas;
import icyllis.modernui.graphics.Paint;

/**
 * Input node that provides a float value.
 *
 * Used for parameters like:
 * - Roughness
 * - Metallic
 * - Scale factors
 */
public class FloatInputNode extends Node {

    private float value = 0.5f;

    public FloatInputNode() {
        super("Float Input");

        // Add output socket
        NodeSocket output = new NodeSocket("Value", NodeSocket.SocketType.FLOAT, value);
        addOutput(output);

        // Smaller size for input nodes
        this.width = 150.0f;
    }

    @Override
    protected void drawBody(Canvas canvas, Paint paint) {
        // Draw value indicator (simple bar)
        float barY = y + TITLE_BAR_HEIGHT + 15;
        float barHeight = 10;
        float barWidth = width - 20;
        float barX = x + 10;

        // Background
        paint.setColor(0xFF1A1A1A);
        paint.setStyle(Paint.FILL);
        canvas.drawRect(barX, barY, barX + barWidth, barY + barHeight, paint);

        // Value bar
        paint.setColor(0xFFFFAA00);
        canvas.drawRect(barX, barY, barX + barWidth * value, barY + barHeight, paint);
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
}
