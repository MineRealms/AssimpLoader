package cn.minerealms.assimploader.lookdev.nodeeditor;

import cn.minerealms.assimploader.debug.DebugManager;
import cn.minerealms.assimploader.debug.DebugManager.DebugCategory;
import icyllis.modernui.annotation.NonNull;
import icyllis.modernui.graphics.Canvas;
import icyllis.modernui.graphics.Paint;
import icyllis.modernui.view.MotionEvent;
import icyllis.modernui.view.View;
import net.minecraft.client.Minecraft;

import java.util.ArrayList;
import java.util.List;

/**
 * Custom canvas view for node editor.
 *
 * Features:
 * - Pan: Click and drag to move viewport
 * - Zoom: Mouse wheel to zoom in/out
 * - Grid background
 * - Node rendering and interaction
 */
public class NodeEditorCanvas extends View {

    // View transform
    private float offsetX = 0.0f;
    private float offsetY = 0.0f;
    private float zoom = 1.0f;

    // Pan state
    private boolean isPanning = false;
    private float lastMouseX = 0.0f;
    private float lastMouseY = 0.0f;

    // Nodes
    private List<Node> nodes = new ArrayList<>();
    private Node selectedNode = null;
    private boolean isDraggingNode = false;

    // Connections
    private List<NodeConnection> connections = new ArrayList<>();
    private boolean isDraggingConnection = false;
    private NodeSocket dragStartSocket = null;
    private float dragEndX = 0.0f;
    private float dragEndY = 0.0f;

    // Grid settings
    private static final float GRID_SIZE = 20.0f;
    private static final int GRID_COLOR = 0xFF333333;
    private static final int GRID_MAJOR_COLOR = 0xFF444444;
    private static final int BACKGROUND_COLOR = 0xFF1E1E1E;

    // Zoom limits
    private static final float MIN_ZOOM = 0.1f;
    private static final float MAX_ZOOM = 3.0f;

    private Paint paint;

    // Node selection listener
    private OnNodeSelectionListener nodeSelectionListener;

    public NodeEditorCanvas(icyllis.modernui.core.Context context) {
        super(context);
        init();
    }

    private void init() {
        paint = new Paint();
        setFocusable(true);
        setFocusableInTouchMode(true);

        DebugManager.debug(DebugCategory.LOOKDEV, "[NodeEditorCanvas] Initialized");
    }

    @Override
    protected void onDraw(@NonNull Canvas canvas) {
        super.onDraw(canvas);

        int width = getWidth();
        int height = getHeight();

        // Clear background
        paint.setColor(BACKGROUND_COLOR);
        paint.setStyle(Paint.FILL);
        canvas.drawRect(0, 0, width, height, paint);

        // Save canvas state
        canvas.save();

        // Apply transform
        canvas.translate(offsetX, offsetY);
        canvas.scale(zoom, zoom);

        // Draw grid
        drawGrid(canvas, width, height);

        // Draw connections first (behind nodes)
        for (NodeConnection connection : connections) {
            connection.draw(canvas, paint, offsetX, offsetY, zoom);
        }

        // Draw dragging connection (if any)
        if (isDraggingConnection && dragStartSocket != null) {
            drawDraggingConnection(canvas, paint);
        }

        // Draw nodes on top
        for (Node node : nodes) {
            node.draw(canvas, paint);
        }

        // Restore canvas state
        canvas.restore();

        // Draw UI overlay (zoom level, etc.)
        drawOverlay(canvas, width, height);
    }

    /**
     * Draw grid background
     */
    private void drawGrid(Canvas canvas, int width, int height) {
        Paint paint = Paint.obtain();
        paint.setStyle(Paint.STROKE);
        paint.setStrokeWidth(1.0f);

        // Calculate visible grid range
        float startX = -offsetX / zoom;
        float startY = -offsetY / zoom;
        float endX = (width - offsetX) / zoom;
        float endY = (height - offsetY) / zoom;

        // Snap to grid
        startX = (float) (Math.floor(startX / GRID_SIZE) * GRID_SIZE);
        startY = (float) (Math.floor(startY / GRID_SIZE) * GRID_SIZE);

        // Draw vertical lines
        for (float x = startX; x < endX; x += GRID_SIZE) {
            boolean isMajor = (Math.round(x / GRID_SIZE) % 5 == 0);
            paint.setColor(isMajor ? GRID_MAJOR_COLOR : GRID_COLOR);
            canvas.drawLine(x, startY, x, endY, paint);
        }

        // Draw horizontal lines
        for (float y = startY; y < endY; y += GRID_SIZE) {
            boolean isMajor = (Math.round(y / GRID_SIZE) % 5 == 0);
            paint.setColor(isMajor ? GRID_MAJOR_COLOR : GRID_COLOR);
            canvas.drawLine(startX, y, endX, y, paint);
        }

        paint.recycle();
    }

    /**
     * Draw connection being dragged
     */
    private void drawDraggingConnection(Canvas canvas, Paint paint) {
        if (dragStartSocket == null) return;

        // Get start position
        float[] startPos = getSocketWorldPosition(dragStartSocket);
        if (startPos == null) return;

        float startX = startPos[0];
        float startY = startPos[1];
        float endX = dragEndX;
        float endY = dragEndY;

        // Draw bezier curve using line segments
        drawBezierCurve(canvas, paint, startX, startY, endX, endY,
                       dragStartSocket.getColor(), 3.0f, 180);
    }

    /**
     * Draw bezier curve using line segments
     */
    private void drawBezierCurve(Canvas canvas, Paint paint,
                                 float startX, float startY,
                                 float endX, float endY,
                                 int color, float thickness, int alpha) {
        // Calculate control points
        float distance = Math.abs(endX - startX);
        float controlOffset = Math.min(distance * 0.5f, 150.0f);

        float cp1X = startX + controlOffset;
        float cp1Y = startY;
        float cp2X = endX - controlOffset;
        float cp2Y = endY;

        // Draw curve using line segments
        paint.setColor(color);
        paint.setStyle(Paint.STROKE);
        paint.setStrokeWidth(thickness);
        paint.setAlpha(alpha);

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

        paint.setAlpha(255);
    }

    /**
     * Get world position of a socket
     */
    private float[] getSocketWorldPosition(NodeSocket socket) {
        Node node = socket.getNode();
        if (node == null) return null;

        int index;
        if (socket.isOutput()) {
            index = node.getOutputs().indexOf(socket);
            if (index < 0) return null;

            float x = node.getX() + node.getWidth();
            float y = node.getY() + Node.TITLE_BAR_HEIGHT + (index + 1) * Node.SOCKET_SPACING;
            return new float[]{x, y};
        } else {
            index = node.getInputs().indexOf(socket);
            if (index < 0) return null;

            float x = node.getX();
            float y = node.getY() + Node.TITLE_BAR_HEIGHT + (index + 1) * Node.SOCKET_SPACING;
            return new float[]{x, y};
        }
    }

    /**
     * Draw overlay UI (zoom level, etc.)
     */
    private void drawOverlay(Canvas canvas, int width, int height) {
        // TODO: Add text rendering when ModernUI text API is available
        // For now, zoom info is shown in status bar
    }

    @Override
    public boolean onTouchEvent(@NonNull MotionEvent event) {
        float x = event.getX();
        float y = event.getY();

        // Transform to canvas coordinates
        float canvasX = (x - offsetX) / zoom;
        float canvasY = (y - offsetY) / zoom;

        switch (event.getAction()) {
            case MotionEvent.ACTION_DOWN:
                // Check if clicking on a socket (for connection dragging)
                NodeSocket clickedSocket = getSocketAt(canvasX, canvasY);
                if (clickedSocket != null) {
                    DebugManager.debug(DebugCategory.LOOKDEV,
                        "[NodeEditorCanvas] Started dragging connection from socket: {}",
                        clickedSocket.getName());
                    // Start dragging connection
                    isDraggingConnection = true;
                    dragStartSocket = clickedSocket;
                    dragEndX = canvasX;
                    dragEndY = canvasY;
                    return true;
                }

                // Check if clicking on a node
                Node clickedNode = getNodeAt(canvasX, canvasY);
                if (clickedNode != null) {
                    DebugManager.debug(DebugCategory.LOOKDEV,
                        "[NodeEditorCanvas] Selected node at ({}, {})",
                        clickedNode.getX(), clickedNode.getY());
                    // Select node
                    selectNode(clickedNode);

                    // Check if clicking on title bar (for dragging)
                    if (clickedNode.containsTitleBar(canvasX, canvasY)) {
                        isDraggingNode = true;
                        lastMouseX = x;
                        lastMouseY = y;
                        return true;
                    }
                } else {
                    // Deselect all nodes
                    selectNode(null);

                    // Start panning
                    isPanning = true;
                    lastMouseX = x;
                    lastMouseY = y;
                }
                return true;

            case MotionEvent.ACTION_MOVE:
                if (isDraggingConnection) {
                    // Update drag end position
                    dragEndX = canvasX;
                    dragEndY = canvasY;
                    invalidate();
                    return true;
                } else if (isDraggingNode && selectedNode != null) {
                    // Drag node
                    float dx = (x - lastMouseX) / zoom;
                    float dy = (y - lastMouseY) / zoom;

                    selectedNode.setPosition(
                        selectedNode.getX() + dx,
                        selectedNode.getY() + dy
                    );

                    lastMouseX = x;
                    lastMouseY = y;

                    invalidate();
                    return true;
                } else if (isPanning) {
                    // Pan viewport
                    float dx = x - lastMouseX;
                    float dy = y - lastMouseY;

                    offsetX += dx;
                    offsetY += dy;

                    lastMouseX = x;
                    lastMouseY = y;

                    invalidate();
                    return true;
                }
                break;

            case MotionEvent.ACTION_UP:
            case MotionEvent.ACTION_CANCEL:
                if (isDraggingConnection) {
                    // Try to complete connection
                    NodeSocket targetSocket = getSocketAt(canvasX, canvasY);
                    if (targetSocket != null && dragStartSocket != null) {
                        DebugManager.debug(DebugCategory.LOOKDEV,
                            "[NodeEditorCanvas] Attempting connection: {} -> {}",
                            dragStartSocket.getName(), targetSocket.getName());
                        tryCreateConnection(dragStartSocket, targetSocket);
                    }

                    // Reset drag state
                    isDraggingConnection = false;
                    dragStartSocket = null;
                    invalidate();
                    return true;
                }

                isPanning = false;
                isDraggingNode = false;
                return true;
        }

        return super.onTouchEvent(event);
    }

    @Override
    public boolean onGenericMotionEvent(@NonNull MotionEvent event) {
        // Handle mouse wheel for zoom
        if (event.getAction() == MotionEvent.ACTION_SCROLL) {
            float scrollY = event.getAxisValue(MotionEvent.AXIS_VSCROLL);

            if (scrollY != 0) {
                float mouseX = event.getX();
                float mouseY = event.getY();

                // Zoom towards mouse position
                float oldZoom = zoom;
                zoom *= (scrollY > 0) ? 1.1f : 0.9f;
                zoom = Math.max(MIN_ZOOM, Math.min(MAX_ZOOM, zoom));

                // Adjust offset to zoom towards mouse
                float zoomFactor = zoom / oldZoom;
                offsetX = mouseX - (mouseX - offsetX) * zoomFactor;
                offsetY = mouseY - (mouseY - offsetY) * zoomFactor;

                invalidate();
                return true;
            }
        }

        return super.onGenericMotionEvent(event);
    }

    /**
     * Reset view to default position and zoom
     */
    public void resetView() {
        offsetX = 0.0f;
        offsetY = 0.0f;
        zoom = 1.0f;
        invalidate();
    }

    /**
     * Get current zoom level
     */
    public float getZoom() {
        return zoom;
    }

    /**
     * Get current X offset
     */
    public float getOffsetX() {
        return offsetX;
    }

    /**
     * Get current Y offset
     */
    public float getOffsetY() {
        return offsetY;
    }

    /**
     * Add a node to the canvas
     */
    public void addNode(Node node) {
        nodes.add(node);
        invalidate();
    }

    /**
     * Remove a node from the canvas
     */
    public void removeNode(Node node) {
        nodes.remove(node);
        if (selectedNode == node) {
            selectedNode = null;
        }
        // Remove all connections involving this node
        removeConnectionsForNode(node);
        invalidate();
    }

    /**
     * Get node at position, or null
     */
    private Node getNodeAt(float x, float y) {
        // Check in reverse order (top to bottom)
        for (int i = nodes.size() - 1; i >= 0; i--) {
            Node node = nodes.get(i);
            if (node.contains(x, y)) {
                return node;
            }
        }
        return null;
    }

    /**
     * Select a node
     */
    private void selectNode(Node node) {
        if (selectedNode != null) {
            selectedNode.setSelected(false);
        }
        selectedNode = node;
        if (selectedNode != null) {
            selectedNode.setSelected(true);
        }

        // Notify listener
        if (nodeSelectionListener != null) {
            nodeSelectionListener.onNodeSelected(selectedNode);
        }

        invalidate();
    }

    /**
     * Set node selection listener
     */
    public void setOnNodeSelectionListener(OnNodeSelectionListener listener) {
        this.nodeSelectionListener = listener;
    }

    /**
     * Node selection listener interface
     */
    @FunctionalInterface
    public interface OnNodeSelectionListener {
        void onNodeSelected(Node node);
    }

    /**
     * Get all nodes
     */
    public List<Node> getNodes() {
        return nodes;
    }

    /**
     * Get selected node
     */
    public Node getSelectedNode() {
        return selectedNode;
    }

    /**
     * Clear all nodes
     */
    public void clearNodes() {
        nodes.clear();
        selectedNode = null;
        connections.clear();
        invalidate();
    }

    /**
     * Get socket at position, or null
     */
    private NodeSocket getSocketAt(float x, float y) {
        // Check all nodes for socket hit
        for (Node node : nodes) {
            // Check output sockets
            NodeSocket outputSocket = node.getOutputSocketAt(x, y);
            if (outputSocket != null) {
                return outputSocket;
            }

            // Check input sockets
            NodeSocket inputSocket = node.getInputSocketAt(x, y);
            if (inputSocket != null) {
                return inputSocket;
            }
        }
        return null;
    }

    /**
     * Try to create a connection between two sockets
     */
    private void tryCreateConnection(NodeSocket socket1, NodeSocket socket2) {
        if (socket1 == null || socket2 == null) {
            return;
        }

        // Ensure we have output -> input order
        NodeSocket outputSocket = socket1.isOutput() ? socket1 : socket2;
        NodeSocket inputSocket = socket1.isOutput() ? socket2 : socket1;

        // Check if connection is valid
        if (!outputSocket.canConnectTo(inputSocket)) {
            DebugManager.warn(DebugCategory.LOOKDEV,
                "[NodeEditorCanvas] Cannot connect: incompatible socket types");
            return;
        }

        // Remove existing connection to input socket (inputs can only have one connection)
        removeConnectionsTo(inputSocket);

        // Create new connection
        try {
            NodeConnection connection = new NodeConnection(outputSocket, inputSocket);
            connections.add(connection);
            DebugManager.info(DebugCategory.LOOKDEV,
                "[NodeEditorCanvas] Created connection: " +
                outputSocket.getNode().getTitle() + " -> " +
                inputSocket.getNode().getTitle());

            // TODO: BACKEND INTEGRATION (Phase 10)
            // When connection is created, trigger material update:
            // 1. Execute node graph
            // 2. Generate new material
            // 3. Apply to preview (if auto-update enabled)
            // 4. Consider debouncing to avoid too many updates
        } catch (Exception e) {
            DebugManager.error(DebugCategory.LOOKDEV,
                "[NodeEditorCanvas] Failed to create connection: " + e.getMessage());
        }
    }

    /**
     * Remove all connections to a socket
     */
    private void removeConnectionsTo(NodeSocket socket) {
        connections.removeIf(conn -> conn.hasSocket(socket));
    }

    /**
     * Remove all connections involving a node
     */
    private void removeConnectionsForNode(Node node) {
        connections.removeIf(conn -> conn.hasNode(node));
    }

    /**
     * Add a connection
     */
    public void addConnection(NodeConnection connection) {
        connections.add(connection);
        invalidate();
    }

    /**
     * Remove a connection
     */
    public void removeConnection(NodeConnection connection) {
        connections.remove(connection);
        connection.disconnect();
        invalidate();
    }

    /**
     * Get all connections
     */
    public List<NodeConnection> getConnections() {
        return connections;
    }

    /**
     * Clear all connections
     */
    public void clearConnections() {
        for (NodeConnection conn : connections) {
            conn.disconnect();
        }
        connections.clear();
        invalidate();
    }
}
