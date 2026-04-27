package cn.minerealms.assimploader.lookdev.nodeeditor.nodes;

import cn.minerealms.assimploader.lookdev.nodeeditor.Node;
import cn.minerealms.assimploader.lookdev.nodeeditor.NodeSocket;
import cn.minerealms.assimploader.lookdev.nodeeditor.EnhancedSocketType;
import icyllis.modernui.graphics.Canvas;
import icyllis.modernui.graphics.Paint;

/**
 * Shader node that provides world-space position.
 *
 * Outputs the current fragment's position in world coordinates.
 *
 * Used for:
 * - World-space effects (triplanar mapping)
 * - Position-based coloring
 * - Distance-based effects
 * - Procedural texturing based on world location
 */
public class WorldPosNode extends Node {

    public WorldPosNode() {
        super("World Position");

        // Add output sockets
        addOutput(new NodeSocket("Position", NodeSocket.SocketType.VECTOR, null));
        addOutput(new NodeSocket("X", NodeSocket.SocketType.FLOAT, 0.0f));
        addOutput(new NodeSocket("Y", NodeSocket.SocketType.FLOAT, 0.0f));
        addOutput(new NodeSocket("Z", NodeSocket.SocketType.FLOAT, 0.0f));

        this.width = 160.0f;
    }

    @Override
    protected void drawBody(Canvas canvas, Paint paint) {
        // Draw a simple coordinate system icon
        float centerX = x + width / 2;
        float centerY = y + TITLE_BAR_HEIGHT + 30;
        float axisLength = 20;

        paint.setStrokeWidth(2.0f);
        paint.setStyle(Paint.STROKE);

        // X axis (red)
        paint.setColor(0xFFFF0000);
        canvas.drawLine(centerX, centerY, centerX + axisLength, centerY, paint);

        // Y axis (green)
        paint.setColor(0xFF00FF00);
        canvas.drawLine(centerX, centerY, centerX, centerY - axisLength, paint);

        // Z axis (blue)
        paint.setColor(0xFF0000FF);
        canvas.drawLine(centerX, centerY, centerX - axisLength * 0.5f, centerY + axisLength * 0.5f, paint);

        // Draw center point
        paint.setStyle(Paint.FILL);
        paint.setColor(0xFFFFFFFF);
        canvas.drawCircle(centerX, centerY, 3, paint);
    }
}
