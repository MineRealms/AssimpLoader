package cn.minerealms.assimploader.lookdev.nodeeditor;

import icyllis.modernui.graphics.Canvas;
import icyllis.modernui.graphics.Paint;

/**
 * Represents a connection between two node sockets.
 *
 * Connections are drawn as bezier curves from output socket to input socket.
 */
public class NodeConnection {

    private NodeSocket outputSocket;
    private NodeSocket inputSocket;

    // Visual properties
    private int color;
    private float thickness = 3.0f;

    public NodeConnection(NodeSocket outputSocket, NodeSocket inputSocket) {
        if (outputSocket == null || inputSocket == null) {
            throw new IllegalArgumentException("Sockets cannot be null");
        }
        if (!outputSocket.isOutput() || inputSocket.isOutput()) {
            throw new IllegalArgumentException("Must connect output to input");
        }

        this.outputSocket = outputSocket;
        this.inputSocket = inputSocket;
        this.color = outputSocket.getColor();

        // Establish bidirectional connection
        outputSocket.connectTo(inputSocket);
    }

    /**
     * Draw the connection as a bezier curve
     */
    public void draw(Canvas canvas, Paint paint, float offsetX, float offsetY, float zoom) {
        // Get socket positions
        float[] startPos = getSocketPosition(outputSocket);
        float[] endPos = getSocketPosition(inputSocket);

        if (startPos == null || endPos == null) {
            return;
        }

        float startX = startPos[0];
        float startY = startPos[1];
        float endX = endPos[0];
        float endY = endPos[1];

        // Draw bezier curve
        drawBezierConnection(canvas, paint, startX, startY, endX, endY);
    }

    /**
     * Draw bezier curve connection
     */
    private void drawBezierConnection(Canvas canvas, Paint paint,
                                      float startX, float startY,
                                      float endX, float endY) {
        // Calculate control points for bezier curve
        float distance = Math.abs(endX - startX);
        float controlOffset = Math.min(distance * 0.5f, 150.0f);

        float cp1X = startX + controlOffset;
        float cp1Y = startY;
        float cp2X = endX - controlOffset;
        float cp2Y = endY;

        // Draw curve using line segments (ModernUI Canvas doesn't have drawPath)
        paint.setColor(color);
        paint.setStyle(Paint.STROKE);
        paint.setStrokeWidth(thickness);

        int segments = 20;
        float prevX = startX;
        float prevY = startY;

        for (int i = 1; i <= segments; i++) {
            float t = i / (float) segments;
            float t2 = t * t;
            float t3 = t2 * t;
            float mt = 1 - t;
            float mt2 = mt * mt;
            float mt3 = mt2 * mt;

            // Cubic bezier formula
            float x = mt3 * startX + 3 * mt2 * t * cp1X + 3 * mt * t2 * cp2X + t3 * endX;
            float y = mt3 * startY + 3 * mt2 * t * cp1Y + 3 * mt * t2 * cp2Y + t3 * endY;

            canvas.drawLine(prevX, prevY, x, y, paint);
            prevX = x;
            prevY = y;
        }

        // Draw small circles at endpoints
        paint.setStyle(Paint.FILL);
        canvas.drawCircle(startX, startY, 4.0f, paint);
        canvas.drawCircle(endX, endY, 4.0f, paint);
    }

    /**
     * Get world position of a socket
     */
    private float[] getSocketPosition(NodeSocket socket) {
        Node node = socket.getNode();
        if (node == null) {
            return null;
        }

        // Find socket index
        int index;
        if (socket.isOutput()) {
            index = node.getOutputs().indexOf(socket);
            if (index < 0) return null;

            // Output socket on right side
            float x = node.getX() + node.getWidth();
            float y = node.getY() + Node.TITLE_BAR_HEIGHT + (index + 1) * Node.SOCKET_SPACING;
            return new float[]{x, y};
        } else {
            index = node.getInputs().indexOf(socket);
            if (index < 0) return null;

            // Input socket on left side
            float x = node.getX();
            float y = node.getY() + Node.TITLE_BAR_HEIGHT + (index + 1) * Node.SOCKET_SPACING;
            return new float[]{x, y};
        }
    }

    /**
     * Check if this connection involves the given socket
     */
    public boolean hasSocket(NodeSocket socket) {
        return outputSocket == socket || inputSocket == socket;
    }

    /**
     * Check if this connection involves the given node
     */
    public boolean hasNode(Node node) {
        return outputSocket.getNode() == node || inputSocket.getNode() == node;
    }

    /**
     * Disconnect this connection
     */
    public void disconnect() {
        outputSocket.disconnect();
    }

    // Getters

    public NodeSocket getOutputSocket() {
        return outputSocket;
    }

    public NodeSocket getInputSocket() {
        return inputSocket;
    }

    public int getColor() {
        return color;
    }

    public void setColor(int color) {
        this.color = color;
    }

    public float getThickness() {
        return thickness;
    }

    public void setThickness(float thickness) {
        this.thickness = thickness;
    }
}
