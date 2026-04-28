package cn.minerealms.assimploader.lookdev.nodeeditor.ui;

import icyllis.modernui.core.Context;
import icyllis.modernui.graphics.Canvas;
import icyllis.modernui.graphics.Paint;
import icyllis.modernui.graphics.drawable.ColorDrawable;
import icyllis.modernui.view.MotionEvent;
import icyllis.modernui.view.View;
import icyllis.modernui.view.ViewGroup;

/**
 * Resizable Divider - allows user to resize panels by dragging.
 *
 * Features:
 * - Vertical divider bar (for horizontal panel layout)
 * - Mouse hover highlight
 * - Drag to resize
 * - Minimum/maximum width constraints
 * - Cursor change on hover
 *
 * Usage:
 *   ResizableDivider divider = new ResizableDivider(context);
 *   divider.setOnResizeListener((newWidth) -> {
 *       // Update panel width
 *   });
 */
public class ResizableDivider extends View {

    private static final int DIVIDER_WIDTH = 4;
    private static final int HOVER_WIDTH = 8; // Wider hit area for easier grabbing
    private static final int COLOR_NORMAL = 0xFF2B2B2B;
    private static final int COLOR_HOVER = 0xFF00AAFF;
    private static final int COLOR_DRAGGING = 0xFF0088DD;

    private boolean isHovered = false;
    private boolean isDragging = false;
    private float dragStartX = 0;
    private float dragStartWidth = 0;

    private ResizeListener listener;
    private int minWidth = 150;
    private int maxWidth = 500;
    private int currentWidth = 250;

    public interface ResizeListener {
        void onResize(int newWidth);
    }

    public ResizableDivider(Context context) {
        super(context);
        setBackground(new ColorDrawable(COLOR_NORMAL));
        setClickable(true);
        setFocusable(true);
    }

    public void setOnResizeListener(ResizeListener listener) {
        this.listener = listener;
    }

    public void setMinWidth(int minWidth) {
        this.minWidth = minWidth;
    }

    public void setMaxWidth(int maxWidth) {
        this.maxWidth = maxWidth;
    }

    public void setCurrentWidth(int currentWidth) {
        this.currentWidth = currentWidth;
    }

    @Override
    protected void onDraw(Canvas canvas) {
        super.onDraw(canvas);

        Paint paint = Paint.obtain();

        // Draw divider bar
        int color = isDragging ? COLOR_DRAGGING : (isHovered ? COLOR_HOVER : COLOR_NORMAL);
        paint.setColor(color);
        paint.setStyle(Paint.FILL);
        canvas.drawRect(0, 0, getWidth(), getHeight(), paint);

        // Draw grip indicator (3 dots in center)
        if (isHovered || isDragging) {
            paint.setColor(0xFFFFFFFF);
            float centerY = getHeight() / 2.0f;
            float centerX = getWidth() / 2.0f;
            float dotRadius = 2;
            float dotSpacing = 8;

            canvas.drawCircle(centerX, centerY - dotSpacing, dotRadius, paint);
            canvas.drawCircle(centerX, centerY, dotRadius, paint);
            canvas.drawCircle(centerX, centerY + dotSpacing, dotRadius, paint);
        }

        paint.recycle();
    }

    @Override
    public boolean onHoverEvent(MotionEvent event) {
        switch (event.getAction()) {
            case MotionEvent.ACTION_HOVER_ENTER:
                isHovered = true;
                invalidate();
                // TODO: Change cursor to resize cursor when ModernUI supports it
                // ModernUI does not currently expose setPointerIcon() or PointerIcon API
                // Once available, use: setPointerIcon(PointerIcon.getSystemIcon(context, PointerIcon.TYPE_HORIZONTAL_DOUBLE_ARROW))
                return true;

            case MotionEvent.ACTION_HOVER_EXIT:
                if (!isDragging) {
                    isHovered = false;
                    invalidate();
                }
                return true;
        }
        return super.onHoverEvent(event);
    }

    @Override
    public boolean onTouchEvent(MotionEvent event) {
        switch (event.getAction()) {
            case MotionEvent.ACTION_DOWN:
                isDragging = true;
                dragStartX = event.getX();
                dragStartWidth = currentWidth;
                invalidate();
                return true;

            case MotionEvent.ACTION_MOVE:
                if (isDragging) {
                    float deltaX = event.getX() - dragStartX;
                    int newWidth = (int) (dragStartWidth - deltaX); // Subtract because divider is on right side

                    // Clamp to min/max
                    newWidth = Math.max(minWidth, Math.min(maxWidth, newWidth));

                    if (newWidth != currentWidth) {
                        currentWidth = newWidth;
                        if (listener != null) {
                            listener.onResize(newWidth);
                        }
                    }
                    return true;
                }
                break;

            case MotionEvent.ACTION_UP:
            case MotionEvent.ACTION_CANCEL:
                isDragging = false;
                invalidate();
                return true;
        }
        return super.onTouchEvent(event);
    }

    @Override
    protected void onMeasure(int widthMeasureSpec, int heightMeasureSpec) {
        // Fixed width, match parent height
        // Extract size from heightMeasureSpec (size is in the upper 30 bits)
        int height = heightMeasureSpec & ~0x3;
        setMeasuredDimension(DIVIDER_WIDTH, height);
    }
}
