package cn.minerealms.assimploader.lookdev.nodeeditor;

import cn.minerealms.assimploader.debug.DebugManager;
import cn.minerealms.assimploader.debug.DebugManager.DebugCategory;
import icyllis.modernui.annotation.NonNull;
import icyllis.modernui.graphics.Canvas;
import icyllis.modernui.graphics.Paint;
import icyllis.modernui.view.MotionEvent;
import icyllis.modernui.view.View;
import net.minecraft.client.Minecraft;

/**
 * Custom canvas view for node editor.
 *
 * Features:
 * - Pan: Click and drag to move viewport
 * - Zoom: Mouse wheel to zoom in/out
 * - Grid background
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

    // Grid settings
    private static final float GRID_SIZE = 20.0f;
    private static final int GRID_COLOR = 0xFF333333;
    private static final int GRID_MAJOR_COLOR = 0xFF444444;
    private static final int BACKGROUND_COLOR = 0xFF1E1E1E;

    // Zoom limits
    private static final float MIN_ZOOM = 0.1f;
    private static final float MAX_ZOOM = 3.0f;

    private Paint paint;

    public NodeEditorCanvas(icyllis.modernui.core.Context context) {
        super(context);
        init();
    }

    private void init() {
        paint = new Paint();
        setFocusable(true);
        setFocusableInTouchMode(true);

        DebugManager.info(DebugCategory.LOOKDEV, "[NodeEditorCanvas] Initialized");
    }

    @Override
    protected void onDraw(@NonNull Canvas canvas) {
        super.onDraw(canvas);

        int width = getWidth();
        int height = getHeight();

        // Clear background
        canvas.drawColor(BACKGROUND_COLOR);

        // Save canvas state
        canvas.save();

        // Apply transform
        canvas.translate(offsetX, offsetY);
        canvas.scale(zoom, zoom);

        // Draw grid
        drawGrid(canvas, width, height);

        // TODO: Draw nodes and connections here

        // Restore canvas state
        canvas.restore();

        // Draw UI overlay (zoom level, etc.)
        drawOverlay(canvas, width, height);
    }

    /**
     * Draw grid background
     */
    private void drawGrid(Canvas canvas, int width, int height) {
        paint.setColor(GRID_COLOR);
        paint.setStrokeWidth(1.0f);

        // Calculate visible grid range
        float startX = (-offsetX / zoom) - GRID_SIZE;
        float startY = (-offsetY / zoom) - GRID_SIZE;
        float endX = (width - offsetX) / zoom + GRID_SIZE;
        float endY = (height - offsetY) / zoom + GRID_SIZE;

        // Snap to grid
        startX = (float) (Math.floor(startX / GRID_SIZE) * GRID_SIZE);
        startY = (float) (Math.floor(startY / GRID_SIZE) * GRID_SIZE);

        // Draw vertical lines
        for (float x = startX; x < endX; x += GRID_SIZE) {
            boolean isMajor = (Math.abs(x) % (GRID_SIZE * 5)) < 0.1f;
            paint.setColor(isMajor ? GRID_MAJOR_COLOR : GRID_COLOR);
            canvas.drawLine(x, startY, x, endY, paint);
        }

        // Draw horizontal lines
        for (float y = startY; y < endY; y += GRID_SIZE) {
            boolean isMajor = (Math.abs(y) % (GRID_SIZE * 5)) < 0.1f;
            paint.setColor(isMajor ? GRID_MAJOR_COLOR : GRID_COLOR);
            canvas.drawLine(startX, y, endX, y, paint);
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

        switch (event.getAction()) {
            case MotionEvent.ACTION_DOWN:
                // Start panning
                isPanning = true;
                lastMouseX = x;
                lastMouseY = y;
                return true;

            case MotionEvent.ACTION_MOVE:
                if (isPanning) {
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
                isPanning = false;
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
}
