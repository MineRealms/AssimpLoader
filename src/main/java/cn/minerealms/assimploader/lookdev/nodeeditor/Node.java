package cn.minerealms.assimploader.lookdev.nodeeditor;

import icyllis.modernui.graphics.Canvas;
import icyllis.modernui.graphics.Paint;
import icyllis.modernui.graphics.drawable.ColorDrawable;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/**
 * Base class for all nodes in the node editor.
 *
 * A node has:
 * - Position (x, y)
 * - Size (width, height)
 * - Title
 * - Input/output sockets
 * - Visual state (selected, hovered)
 */
public abstract class Node {

    // Unique identifier
    protected final String id;

    // Position and size
    protected float x;
    protected float y;
    protected float width = 200.0f;
    protected float height = 100.0f;

    // Visual properties
    protected String title;
    protected int backgroundColor = 0xFF2B2B2B;
    protected int titleBarColor = 0xFF1E1E1E;
    protected int borderColor = 0xFF444444;
    protected int selectedBorderColor = 0xFF00AAFF;

    // State
    protected boolean selected = false;
    protected boolean hovered = false;

    // Sockets
    protected List<NodeSocket> inputs = new ArrayList<>();
    protected List<NodeSocket> outputs = new ArrayList<>();

    // Constants
    protected static final float TITLE_BAR_HEIGHT = 30.0f;
    protected static final float SOCKET_RADIUS = 8.0f;
    protected static final float SOCKET_SPACING = 25.0f;
    protected static final float BORDER_WIDTH = 2.0f;

    public Node(String title) {
        this.id = UUID.randomUUID().toString();
        this.title = title;
    }

    /**
     * Draw the node on canvas
     */
    public void draw(Canvas canvas, Paint paint) {
        // Draw border
        paint.setColor(selected ? selectedBorderColor : borderColor);
        paint.setStyle(Paint.FILL);
        canvas.drawRect(x, y, x + width, y + height, paint);

        // Draw background
        paint.setColor(backgroundColor);
        canvas.drawRect(
            x + BORDER_WIDTH,
            y + BORDER_WIDTH,
            x + width - BORDER_WIDTH,
            y + height - BORDER_WIDTH,
            paint
        );

        // Draw title bar
        paint.setColor(titleBarColor);
        canvas.drawRect(
            x + BORDER_WIDTH,
            y + BORDER_WIDTH,
            x + width - BORDER_WIDTH,
            y + TITLE_BAR_HEIGHT,
            paint
        );

        // Draw title text (TODO: Add text rendering when available)
        // For now, title is shown in status bar when selected

        // Draw body content
        drawBody(canvas, paint);

        // Draw sockets
        drawSockets(canvas, paint);
    }

    /**
     * Draw node body content (override in subclasses)
     */
    protected abstract void drawBody(Canvas canvas, Paint paint);

    /**
     * Draw input/output sockets
     */
    protected void drawSockets(Canvas canvas, Paint paint) {
        // Draw input sockets (left side)
        for (int i = 0; i < inputs.size(); i++) {
            NodeSocket socket = inputs.get(i);
            float socketY = y + TITLE_BAR_HEIGHT + (i + 1) * SOCKET_SPACING;

            paint.setColor(socket.getColor());
            paint.setStyle(Paint.FILL);
            canvas.drawCircle(x, socketY, SOCKET_RADIUS, paint);

            // Draw border
            paint.setColor(0xFFFFFFFF);
            paint.setStyle(Paint.STROKE);
            paint.setStrokeWidth(1.5f);
            canvas.drawCircle(x, socketY, SOCKET_RADIUS, paint);
        }

        // Draw output sockets (right side)
        for (int i = 0; i < outputs.size(); i++) {
            NodeSocket socket = outputs.get(i);
            float socketY = y + TITLE_BAR_HEIGHT + (i + 1) * SOCKET_SPACING;

            paint.setColor(socket.getColor());
            paint.setStyle(Paint.FILL);
            canvas.drawCircle(x + width, socketY, SOCKET_RADIUS, paint);

            // Draw border
            paint.setColor(0xFFFFFFFF);
            paint.setStyle(Paint.STROKE);
            paint.setStrokeWidth(1.5f);
            canvas.drawCircle(x + width, socketY, SOCKET_RADIUS, paint);
        }
    }

    /**
     * Check if point is inside node bounds
     */
    public boolean contains(float px, float py) {
        return px >= x && px <= x + width && py >= y && py <= y + height;
    }

    /**
     * Check if point is inside title bar (for dragging)
     */
    public boolean containsTitleBar(float px, float py) {
        return px >= x && px <= x + width && py >= y && py <= y + TITLE_BAR_HEIGHT;
    }

    /**
     * Get input socket at point, or null
     */
    public NodeSocket getInputSocketAt(float px, float py) {
        for (int i = 0; i < inputs.size(); i++) {
            NodeSocket socket = inputs.get(i);
            float socketY = y + TITLE_BAR_HEIGHT + (i + 1) * SOCKET_SPACING;

            float dx = px - x;
            float dy = py - socketY;
            if (dx * dx + dy * dy <= SOCKET_RADIUS * SOCKET_RADIUS) {
                return socket;
            }
        }
        return null;
    }

    /**
     * Get output socket at point, or null
     */
    public NodeSocket getOutputSocketAt(float px, float py) {
        for (int i = 0; i < outputs.size(); i++) {
            NodeSocket socket = outputs.get(i);
            float socketY = y + TITLE_BAR_HEIGHT + (i + 1) * SOCKET_SPACING;

            float dx = px - (x + width);
            float dy = py - socketY;
            if (dx * dx + dy * dy <= SOCKET_RADIUS * SOCKET_RADIUS) {
                return socket;
            }
        }
        return null;
    }

    // Getters and setters

    public String getId() {
        return id;
    }

    public float getX() {
        return x;
    }

    public void setX(float x) {
        this.x = x;
    }

    public float getY() {
        return y;
    }

    public void setY(float y) {
        this.y = y;
    }

    public void setPosition(float x, float y) {
        this.x = x;
        this.y = y;
    }

    public float getWidth() {
        return width;
    }

    public void setWidth(float width) {
        this.width = width;
    }

    public float getHeight() {
        return height;
    }

    public void setHeight(float height) {
        this.height = height;
    }

    public String getTitle() {
        return title;
    }

    public void setTitle(String title) {
        this.title = title;
    }

    public boolean isSelected() {
        return selected;
    }

    public void setSelected(boolean selected) {
        this.selected = selected;
    }

    public boolean isHovered() {
        return hovered;
    }

    public void setHovered(boolean hovered) {
        this.hovered = hovered;
    }

    public List<NodeSocket> getInputs() {
        return inputs;
    }

    public List<NodeSocket> getOutputs() {
        return outputs;
    }

    /**
     * Add input socket
     */
    protected void addInput(NodeSocket socket) {
        inputs.add(socket);
        socket.setNode(this);
        socket.setIsOutput(false);
        updateHeight();
    }

    /**
     * Add output socket
     */
    protected void addOutput(NodeSocket socket) {
        outputs.add(socket);
        socket.setNode(this);
        socket.setIsOutput(true);
        updateHeight();
    }

    /**
     * Update node height based on socket count
     */
    protected void updateHeight() {
        int maxSockets = Math.max(inputs.size(), outputs.size());
        height = TITLE_BAR_HEIGHT + (maxSockets + 1) * SOCKET_SPACING;
    }

    /**
     * Evaluate this node and compute output values.
     *
     * This method is called during graph execution. Subclasses should:
     * 1. Read input socket values (already computed by upstream nodes)
     * 2. Perform their computation
     * 3. Write results to output sockets
     * 4. Cache results in the context
     *
     * @param context Evaluation context for caching and state tracking
     */
    public void evaluate(NodeEvaluationContext context) {
        // Check if already evaluated (cached)
        if (context.hasNodeResult(this)) {
            return;
        }

        // Check for cycles
        if (context.isEvaluating(this)) {
            throw new RuntimeException("Cycle detected at node: " + title);
        }

        context.beginEvaluating(this);

        try {
            // Evaluate all upstream nodes first
            for (NodeSocket input : inputs) {
                if (input.isConnected()) {
                    NodeSocket connectedSocket = input.getConnectedTo();
                    if (connectedSocket != null && connectedSocket.getNode() != null) {
                        connectedSocket.getNode().evaluate(context);

                        // Propagate value from connected socket to this input
                        Object value = context.getSocketValue(connectedSocket);
                        if (value != null) {
                            input.setValue(value);
                            context.setSocketValue(input, value);
                        }
                    }
                }
            }

            // Perform node-specific computation
            computeOutputs(context);

            // Mark as evaluated
            context.setNodeResult(this, true);
        } finally {
            context.endEvaluating(this);
        }
    }

    /**
     * Compute output values based on input values.
     * Override in subclasses to implement node-specific logic.
     *
     * Default implementation does nothing (for nodes with static values).
     *
     * @param context Evaluation context for caching
     */
    protected void computeOutputs(NodeEvaluationContext context) {
        // Default: no computation needed
        // Input nodes have static values
        // Output nodes just collect inputs
        // Override in nodes that perform computation (math, texture sampling, etc.)
    }
}
