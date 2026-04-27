package cn.minerealms.assimploader.lookdev.nodeeditor.nodes;

import cn.minerealms.assimploader.lookdev.nodeeditor.Node;
import cn.minerealms.assimploader.lookdev.nodeeditor.NodeSocket;
import cn.minerealms.assimploader.lookdev.nodeeditor.EnhancedSocketType;
import icyllis.modernui.graphics.Canvas;
import icyllis.modernui.graphics.Paint;

/**
 * Shader node that provides light direction vector.
 *
 * Outputs the normalized direction from the fragment to the main light source.
 *
 * Used for:
 * - Custom lighting calculations
 * - Subsurface scattering
 * - Anisotropic reflections
 * - Light-dependent effects
 */
public class LightDirNode extends Node {

    public LightDirNode() {
        super("Light Direction");

        // Add output sockets
        addOutput(new NodeSocket("Direction", NodeSocket.SocketType.VECTOR, null));
        addOutput(new NodeSocket("X", NodeSocket.SocketType.FLOAT, 0.0f));
        addOutput(new NodeSocket("Y", NodeSocket.SocketType.FLOAT, 0.0f));
        addOutput(new NodeSocket("Z", NodeSocket.SocketType.FLOAT, 0.0f));

        this.width = 160.0f;
    }

    @Override
    protected void drawBody(Canvas canvas, Paint paint) {
        // Draw a sun icon with light rays
        float centerX = x + width / 2;
        float centerY = y + TITLE_BAR_HEIGHT + 30;
        float sunRadius = 10;

        // Sun circle
        paint.setColor(0xFFFFA500);
        paint.setStyle(Paint.FILL);
        canvas.drawCircle(centerX, centerY, sunRadius, paint);

        // Light rays
        paint.setStyle(Paint.STROKE);
        paint.setStrokeWidth(2.0f);
        int numRays = 8;
        for (int i = 0; i < numRays; i++) {
            double angle = (2 * Math.PI * i) / numRays;
            float startX = centerX + (float) (Math.cos(angle) * (sunRadius + 3));
            float startY = centerY + (float) (Math.sin(angle) * (sunRadius + 3));
            float endX = centerX + (float) (Math.cos(angle) * (sunRadius + 8));
            float endY = centerY + (float) (Math.sin(angle) * (sunRadius + 8));
            canvas.drawLine(startX, startY, endX, endY, paint);
        }
    }
}
