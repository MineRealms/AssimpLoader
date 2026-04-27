package cn.minerealms.assimploader.lookdev.nodeeditor.nodes;

import cn.minerealms.assimploader.lookdev.nodeeditor.Node;
import cn.minerealms.assimploader.lookdev.nodeeditor.NodeSocket;
import cn.minerealms.assimploader.lookdev.nodeeditor.EnhancedSocketType;
import icyllis.modernui.graphics.Canvas;
import icyllis.modernui.graphics.Paint;

/**
 * Shader node that provides view direction vector.
 *
 * Outputs the normalized direction from the camera to the current fragment.
 *
 * Used for:
 * - Fresnel effects
 * - Rim lighting
 * - View-dependent reflections
 * - Camera-relative effects
 */
public class ViewDirNode extends Node {

    public ViewDirNode() {
        super("View Direction");

        // Add output sockets
        addOutput(new NodeSocket("Direction", NodeSocket.SocketType.VECTOR, null));
        addOutput(new NodeSocket("X", NodeSocket.SocketType.FLOAT, 0.0f));
        addOutput(new NodeSocket("Y", NodeSocket.SocketType.FLOAT, 0.0f));
        addOutput(new NodeSocket("Z", NodeSocket.SocketType.FLOAT, 0.0f));

        this.width = 160.0f;
    }

    @Override
    protected void drawBody(Canvas canvas, Paint paint) {
        // Draw an eye icon with direction arrow
        float centerX = x + width / 2;
        float centerY = y + TITLE_BAR_HEIGHT + 30;

        // Eye outline (using circle instead of oval)
        paint.setColor(0xFF9B59B6);
        paint.setStyle(Paint.STROKE);
        paint.setStrokeWidth(2.0f);

        // Eye shape (circle)
        canvas.drawCircle(centerX, centerY, 15, paint);

        // Pupil
        paint.setStyle(Paint.FILL);
        canvas.drawCircle(centerX, centerY, 5, paint);

        // Direction arrow
        paint.setStyle(Paint.STROKE);
        canvas.drawLine(centerX + 25, centerY, centerX + 40, centerY, paint);
        canvas.drawLine(centerX + 40, centerY, centerX + 35, centerY - 5, paint);
        canvas.drawLine(centerX + 40, centerY, centerX + 35, centerY + 5, paint);
    }
}
