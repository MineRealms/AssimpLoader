package cn.minerealms.assimploader.lookdev.nodeeditor.ui;

import cn.minerealms.assimploader.debug.DebugManager;
import cn.minerealms.assimploader.debug.DebugManager.DebugCategory;
import cn.minerealms.assimploader.lookdev.nodeeditor.Node;
import cn.minerealms.assimploader.lookdev.nodeeditor.NodeSocket;
import icyllis.modernui.graphics.Canvas;
import icyllis.modernui.graphics.Paint;
import icyllis.modernui.view.View;
import org.joml.Vector3f;
import org.joml.Vector4f;

/**
 * Node Preview Panel
 *
 * Displays real-time preview of node outputs.
 *
 * Features:
 * - Preview color outputs as colored squares
 * - Preview float values as grayscale
 * - Preview vectors as RGB colors
 * - Preview textures (when available)
 * - Zoom and pan controls
 * - Auto-update on node changes
 *
 * Usage:
 *   NodePreviewPanel preview = new NodePreviewPanel(context);
 *   preview.setPreviewNode(node);
 *   preview.setPreviewSocket(socket);
 */
public class NodePreviewPanel extends View {

    private Node previewNode;
    private NodeSocket previewSocket;
    private boolean autoUpdate = true;
    private float zoom = 1.0f;
    private float panX = 0;
    private float panY = 0;

    // Preview size
    private static final int PREVIEW_SIZE = 200;
    private static final int PADDING = 10;

    public NodePreviewPanel(icyllis.modernui.core.Context context) {
        super(context);
        setBackground(new icyllis.modernui.graphics.drawable.ColorDrawable(0xFF1E1E1E));
        DebugManager.debug(DebugCategory.LOOKDEV, "[NodePreviewPanel] Initialized");
    }

    /**
     * Set the node to preview
     */
    public void setPreviewNode(Node node) {
        this.previewNode = node;
        this.previewSocket = null;
        if (autoUpdate) {
            invalidate();
        }
        DebugManager.debug(DebugCategory.LOOKDEV,
            "[NodePreviewPanel] Preview node set: {}", node != null ? node.getTitle() : "null");
    }

    /**
     * Set specific socket to preview
     */
    public void setPreviewSocket(NodeSocket socket) {
        this.previewSocket = socket;
        if (socket != null) {
            this.previewNode = socket.getNode();
        }
        if (autoUpdate) {
            invalidate();
        }
        DebugManager.debug(DebugCategory.LOOKDEV,
            "[NodePreviewPanel] Preview socket set: {}", socket != null ? socket.getName() : "null");
    }

    /**
     * Set auto-update mode
     */
    public void setAutoUpdate(boolean autoUpdate) {
        this.autoUpdate = autoUpdate;
    }

    /**
     * Set zoom level
     */
    public void setZoom(float zoom) {
        this.zoom = Math.max(0.1f, Math.min(5.0f, zoom));
        invalidate();
    }

    /**
     * Set pan offset
     */
    public void setPan(float x, float y) {
        this.panX = x;
        this.panY = y;
        invalidate();
    }

    /**
     * Reset view
     */
    public void resetView() {
        this.zoom = 1.0f;
        this.panX = 0;
        this.panY = 0;
        invalidate();
    }

    @Override
    protected void onDraw(Canvas canvas) {
        super.onDraw(canvas);

        Paint paint = Paint.obtain();

        // Draw background
        paint.setColor(0xFF2B2B2B);
        paint.setStyle(Paint.FILL);
        canvas.drawRect(0, 0, getWidth(), getHeight(), paint);

        // Draw title bar
        paint.setColor(0xFF1E1E1E);
        canvas.drawRect(0, 0, getWidth(), 30, paint);

        // Draw title text
        paint.setColor(0xFFCCCCCC);
        paint.setStyle(Paint.FILL);
        cn.minerealms.assimploader.lookdev.nodeeditor.ui.TextRenderer.drawText(
            canvas,
            "Preview",
            10,
            20,
            11.0f,
            paint
        );

        // Draw preview area
        if (previewNode != null) {
            drawPreview(canvas, paint);
        } else {
            drawNoPreview(canvas, paint);
        }

        paint.recycle();
    }

    /**
     * Draw preview content
     */
    private void drawPreview(Canvas canvas, Paint paint) {
        float centerX = getWidth() / 2.0f;
        float centerY = getHeight() / 2.0f + 15; // Offset for title bar

        // Apply zoom and pan
        float previewSize = PREVIEW_SIZE * zoom;
        float previewX = centerX + panX - previewSize / 2;
        float previewY = centerY + panY - previewSize / 2;

        // Draw preview border
        paint.setColor(0xFF444444);
        paint.setStyle(Paint.STROKE);
        paint.setStrokeWidth(2.0f);
        canvas.drawRect(previewX, previewY, previewX + previewSize, previewY + previewSize, paint);

        // Get preview value
        Object value = null;
        if (previewSocket != null) {
            value = previewSocket.getValue();
        } else if (previewNode != null && !previewNode.getOutputs().isEmpty()) {
            // Preview first output socket
            value = previewNode.getOutputs().get(0).getValue();
        }

        // Draw preview based on value type
        if (value != null) {
            drawValue(canvas, paint, value, previewX, previewY, previewSize);
        } else {
            drawNoValue(canvas, paint, previewX, previewY, previewSize);
        }

        // Draw zoom indicator
        paint.setColor(0xFF888888);
        paint.setStyle(Paint.FILL);
        String zoomText = String.format("%.0f%%", zoom * 100);
        float textWidth = cn.minerealms.assimploader.lookdev.nodeeditor.ui.TextRenderer.measureText(zoomText, 9.0f);
        cn.minerealms.assimploader.lookdev.nodeeditor.ui.TextRenderer.drawText(
            canvas,
            zoomText,
            getWidth() - textWidth - 10,
            getHeight() - 10,
            9.0f,
            paint
        );
    }

    /**
     * Draw value preview
     */
    private void drawValue(Canvas canvas, Paint paint, Object value, float x, float y, float size) {
        paint.setStyle(Paint.FILL);

        if (value instanceof Vector4f) {
            // Color preview
            Vector4f color = (Vector4f) value;
            int colorInt = toColorInt(color);
            paint.setColor(colorInt);
            canvas.drawRect(x, y, x + size, y + size, paint);

        } else if (value instanceof Vector3f) {
            // Vector as RGB
            Vector3f vec = (Vector3f) value;
            int colorInt = toColorInt(vec);
            paint.setColor(colorInt);
            canvas.drawRect(x, y, x + size, y + size, paint);

        } else if (value instanceof Float) {
            // Float as grayscale
            float f = (Float) value;
            int gray = (int) (Math.max(0, Math.min(1, f)) * 255);
            int colorInt = 0xFF000000 | (gray << 16) | (gray << 8) | gray;
            paint.setColor(colorInt);
            canvas.drawRect(x, y, x + size, y + size, paint);

        } else if (value instanceof Integer) {
            // Texture ID or integer value
            int intValue = (Integer) value;
            // Draw checkerboard pattern for texture placeholder
            drawCheckerboard(canvas, paint, x, y, size);

            // Draw texture ID text
            paint.setColor(0xFFFFFFFF);
            paint.setStyle(Paint.FILL);
            String texIdText = "Texture #" + intValue;
            float textWidth = cn.minerealms.assimploader.lookdev.nodeeditor.ui.TextRenderer.measureText(texIdText, 10.0f);
            cn.minerealms.assimploader.lookdev.nodeeditor.ui.TextRenderer.drawText(
                canvas,
                texIdText,
                x + (size - textWidth) / 2,
                y + size / 2 + 4,
                10.0f,
                paint
            );

        } else {
            // Unknown type
            drawNoValue(canvas, paint, x, y, size);
        }
    }

    /**
     * Draw "no value" indicator
     */
    private void drawNoValue(Canvas canvas, Paint paint, float x, float y, float size) {
        // Draw diagonal lines
        paint.setColor(0xFF444444);
        paint.setStyle(Paint.STROKE);
        paint.setStrokeWidth(2.0f);
        canvas.drawLine(x, y, x + size, y + size, paint);
        canvas.drawLine(x + size, y, x, y + size, paint);
    }

    /**
     * Draw "no preview" message
     */
    private void drawNoPreview(Canvas canvas, Paint paint) {
        float centerX = getWidth() / 2.0f;
        float centerY = getHeight() / 2.0f;

        paint.setColor(0xFF666666);
        paint.setStyle(Paint.FILL);

        // Draw "No node selected" text
        String message = "No node selected";
        float textWidth = cn.minerealms.assimploader.lookdev.nodeeditor.ui.TextRenderer.measureText(message, 11.0f);
        cn.minerealms.assimploader.lookdev.nodeeditor.ui.TextRenderer.drawText(
            canvas,
            message,
            centerX - textWidth / 2,
            centerY + 4,
            11.0f,
            paint
        );

        // Draw placeholder icon (circle with X)
        paint.setStyle(Paint.STROKE);
        paint.setStrokeWidth(2.0f);
        canvas.drawCircle(centerX, centerY, 30, paint);
        canvas.drawLine(centerX - 15, centerY - 15, centerX + 15, centerY + 15, paint);
        canvas.drawLine(centerX + 15, centerY - 15, centerX - 15, centerY + 15, paint);
    }

    /**
     * Draw checkerboard pattern
     */
    private void drawCheckerboard(Canvas canvas, Paint paint, float x, float y, float size) {
        int squares = 8;
        float squareSize = size / squares;

        for (int i = 0; i < squares; i++) {
            for (int j = 0; j < squares; j++) {
                if ((i + j) % 2 == 0) {
                    paint.setColor(0xFF666666);
                } else {
                    paint.setColor(0xFF444444);
                }
                paint.setStyle(Paint.FILL);
                canvas.drawRect(
                    x + i * squareSize,
                    y + j * squareSize,
                    x + (i + 1) * squareSize,
                    y + (j + 1) * squareSize,
                    paint
                );
            }
        }
    }

    /**
     * Convert Vector4f to color int
     */
    private int toColorInt(Vector4f color) {
        int r = (int) (Math.max(0, Math.min(1, color.x)) * 255);
        int g = (int) (Math.max(0, Math.min(1, color.y)) * 255);
        int b = (int) (Math.max(0, Math.min(1, color.z)) * 255);
        int a = (int) (Math.max(0, Math.min(1, color.w)) * 255);
        return (a << 24) | (r << 16) | (g << 8) | b;
    }

    /**
     * Convert Vector3f to color int (RGB, full alpha)
     */
    private int toColorInt(Vector3f vec) {
        int r = (int) (Math.max(0, Math.min(1, vec.x)) * 255);
        int g = (int) (Math.max(0, Math.min(1, vec.y)) * 255);
        int b = (int) (Math.max(0, Math.min(1, vec.z)) * 255);
        return 0xFF000000 | (r << 16) | (g << 8) | b;
    }

    /**
     * Refresh preview
     */
    public void refresh() {
        invalidate();
    }
}
