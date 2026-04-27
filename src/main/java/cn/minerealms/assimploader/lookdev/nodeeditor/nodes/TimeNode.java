package cn.minerealms.assimploader.lookdev.nodeeditor.nodes;

import cn.minerealms.assimploader.lookdev.nodeeditor.Node;
import cn.minerealms.assimploader.lookdev.nodeeditor.NodeSocket;
import cn.minerealms.assimploader.lookdev.nodeeditor.EnhancedSocketType;
import icyllis.modernui.graphics.Canvas;
import icyllis.modernui.graphics.Paint;

/**
 * Shader node that provides game time for animations.
 *
 * Outputs:
 * - Time: Current game time in seconds (float)
 * - Sin Time: sin(time) for oscillating animations
 * - Cos Time: cos(time) for oscillating animations
 *
 * Used for creating animated materials like:
 * - Flowing water
 * - Pulsing emissive effects
 * - Rotating textures
 */
public class TimeNode extends Node {

    public TimeNode() {
        super("Time");

        // Add output sockets
        addOutput(new NodeSocket("Time", NodeSocket.SocketType.FLOAT, 0.0f));
        addOutput(new NodeSocket("Sin Time", NodeSocket.SocketType.FLOAT, 0.0f));
        addOutput(new NodeSocket("Cos Time", NodeSocket.SocketType.FLOAT, 0.0f));

        this.width = 150.0f;
    }

    @Override
    protected void drawBody(Canvas canvas, Paint paint) {
        // Draw a simple clock icon
        float centerX = x + width / 2;
        float centerY = y + TITLE_BAR_HEIGHT + 30;
        float radius = 15;

        // Clock circle
        paint.setColor(0xFF4A90E2);
        paint.setStyle(Paint.STROKE);
        paint.setStrokeWidth(2.0f);
        canvas.drawCircle(centerX, centerY, radius, paint);

        // Clock hands (simple representation)
        paint.setStyle(Paint.FILL);
        canvas.drawLine(centerX, centerY, centerX + radius * 0.5f, centerY - radius * 0.5f, paint);
        canvas.drawLine(centerX, centerY, centerX + radius * 0.7f, centerY + radius * 0.3f, paint);
    }
}
