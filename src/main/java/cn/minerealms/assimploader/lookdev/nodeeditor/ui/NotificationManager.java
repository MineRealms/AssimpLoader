package cn.minerealms.assimploader.lookdev.nodeeditor.ui;

import icyllis.modernui.graphics.Canvas;
import icyllis.modernui.graphics.Paint;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;

/**
 * Toast notification system for user feedback.
 *
 * Features:
 * - Success, warning, error, info messages
 * - Auto-dismiss after timeout
 * - Stack multiple notifications
 * - Smooth fade in/out animations
 */
public class NotificationManager {

    private final List<Notification> notifications = new ArrayList<>();
    private static final int MAX_NOTIFICATIONS = 5;
    private static final long DEFAULT_DURATION = 3000; // 3 seconds

    /**
     * Show success notification
     */
    public void success(String message) {
        addNotification(message, NotificationType.SUCCESS, DEFAULT_DURATION);
    }

    /**
     * Show warning notification
     */
    public void warning(String message) {
        addNotification(message, NotificationType.WARNING, DEFAULT_DURATION);
    }

    /**
     * Show error notification
     */
    public void error(String message) {
        addNotification(message, NotificationType.ERROR, DEFAULT_DURATION * 2); // Errors stay longer
    }

    /**
     * Show info notification
     */
    public void info(String message) {
        addNotification(message, NotificationType.INFO, DEFAULT_DURATION);
    }

    /**
     * Add notification
     */
    private void addNotification(String message, NotificationType type, long duration) {
        // Remove oldest if at max
        if (notifications.size() >= MAX_NOTIFICATIONS) {
            notifications.remove(0);
        }

        Notification notification = new Notification(message, type, duration);
        notifications.add(notification);
    }

    /**
     * Update notifications (call every frame)
     */
    public void update() {
        long currentTime = System.currentTimeMillis();

        // Remove expired notifications
        Iterator<Notification> iterator = notifications.iterator();
        while (iterator.hasNext()) {
            Notification notification = iterator.next();
            if (currentTime > notification.expiryTime) {
                iterator.remove();
            }
        }
    }

    /**
     * Draw notifications
     */
    public void draw(Canvas canvas, Paint paint, int screenWidth, int screenHeight) {
        float x = screenWidth - 320;
        float y = 20;
        float width = 300;
        float height = 60;
        float spacing = 10;

        for (Notification notification : notifications) {
            drawNotification(canvas, paint, notification, x, y, width, height);
            y += height + spacing;
        }
    }

    /**
     * Draw single notification
     */
    private void drawNotification(Canvas canvas, Paint paint, Notification notification,
                                   float x, float y, float width, float height) {
        long currentTime = System.currentTimeMillis();
        long elapsed = currentTime - notification.createTime;
        long remaining = notification.expiryTime - currentTime;

        // Calculate alpha for fade in/out
        float alpha = 1.0f;
        if (elapsed < 300) {
            // Fade in
            alpha = elapsed / 300.0f;
        } else if (remaining < 500) {
            // Fade out
            alpha = remaining / 500.0f;
        }

        int alphaInt = (int) (alpha * 255);

        // Draw background
        int bgColor = getBackgroundColor(notification.type, alphaInt);
        paint.setColor(bgColor);
        paint.setStyle(Paint.FILL);
        canvas.drawRoundRect(x, y, x + width, y + height, 8, 8, paint);

        // Draw border
        int borderColor = getBorderColor(notification.type, alphaInt);
        paint.setColor(borderColor);
        paint.setStyle(Paint.STROKE);
        paint.setStrokeWidth(2.0f);
        canvas.drawRoundRect(x, y, x + width, y + height, 8, 8, paint);

        // Draw icon area
        float iconSize = 40;
        float iconX = x + 10;
        float iconY = y + (height - iconSize) / 2;

        paint.setColor(getIconColor(notification.type, alphaInt));
        paint.setStyle(Paint.FILL);
        canvas.drawCircle(iconX + iconSize / 2, iconY + iconSize / 2, iconSize / 2, paint);

        // Draw icon symbol (simplified)
        paint.setColor(withAlpha(0xFFFFFFFF, alphaInt));
        switch (notification.type) {
            case SUCCESS -> {
                // Checkmark (simplified)
                canvas.drawCircle(iconX + iconSize / 2, iconY + iconSize / 2, iconSize / 4, paint);
            }
            case WARNING -> {
                // Exclamation mark (simplified)
                canvas.drawRect(iconX + iconSize / 2 - 2, iconY + 10,
                               iconX + iconSize / 2 + 2, iconY + 25, paint);
                canvas.drawCircle(iconX + iconSize / 2, iconY + 32, 3, paint);
            }
            case ERROR -> {
                // X mark (simplified)
                paint.setStrokeWidth(3.0f);
                paint.setStyle(Paint.STROKE);
                canvas.drawLine(iconX + 10, iconY + 10, iconX + 30, iconY + 30, paint);
                canvas.drawLine(iconX + 30, iconY + 10, iconX + 10, iconY + 30, paint);
            }
            case INFO -> {
                // i mark (simplified)
                canvas.drawCircle(iconX + iconSize / 2, iconY + 12, 3, paint);
                canvas.drawRect(iconX + iconSize / 2 - 2, iconY + 18,
                               iconX + iconSize / 2 + 2, iconY + 32, paint);
            }
        }

        // Draw message text (placeholder)
        // TODO: Draw actual text when ModernUI text rendering is available
        float textX = iconX + iconSize + 10;
        float textY = y + height / 2;
        paint.setColor(withAlpha(0xFFFFFFFF, alphaInt));
        // For now, just draw a rectangle to indicate text area
        canvas.drawRect(textX, textY - 8, textX + 150, textY + 8, paint);
    }

    /**
     * Get background color for notification type
     */
    private int getBackgroundColor(NotificationType type, int alpha) {
        return switch (type) {
            case SUCCESS -> withAlpha(0xFF1A4D1A, alpha);
            case WARNING -> withAlpha(0xFF4D4D1A, alpha);
            case ERROR -> withAlpha(0xFF4D1A1A, alpha);
            case INFO -> withAlpha(0xFF1A1A4D, alpha);
        };
    }

    /**
     * Get border color for notification type
     */
    private int getBorderColor(NotificationType type, int alpha) {
        return switch (type) {
            case SUCCESS -> withAlpha(0xFF00AA00, alpha);
            case WARNING -> withAlpha(0xFFAAAA00, alpha);
            case ERROR -> withAlpha(0xFFAA0000, alpha);
            case INFO -> withAlpha(0xFF0000AA, alpha);
        };
    }

    /**
     * Get icon color for notification type
     */
    private int getIconColor(NotificationType type, int alpha) {
        return switch (type) {
            case SUCCESS -> withAlpha(0xFF00FF00, alpha);
            case WARNING -> withAlpha(0xFFFFFF00, alpha);
            case ERROR -> withAlpha(0xFFFF0000, alpha);
            case INFO -> withAlpha(0xFF0088FF, alpha);
        };
    }

    /**
     * Apply alpha to color
     */
    private int withAlpha(int color, int alpha) {
        return (alpha << 24) | (color & 0x00FFFFFF);
    }

    /**
     * Clear all notifications
     */
    public void clear() {
        notifications.clear();
    }

    /**
     * Notification class
     */
    private static class Notification {
        final String message;
        final NotificationType type;
        final long createTime;
        final long expiryTime;

        Notification(String message, NotificationType type, long duration) {
            this.message = message;
            this.type = type;
            this.createTime = System.currentTimeMillis();
            this.expiryTime = createTime + duration;
        }
    }

    /**
     * Notification types
     */
    public enum NotificationType {
        SUCCESS,
        WARNING,
        ERROR,
        INFO
    }
}
