package cn.minerealms.assimploader.lookdev.nodeeditor.ui;

import icyllis.modernui.graphics.Canvas;
import icyllis.modernui.graphics.Paint;
import icyllis.modernui.graphics.text.Font;
import icyllis.modernui.graphics.text.FontFamily;
import icyllis.modernui.graphics.text.FontPaint;

/**
 * Utility class for rendering text on ModernUI Canvas.
 * Provides simple text drawing methods with caching for performance.
 */
public class TextRenderer {

    private static Font defaultFont;

    /**
     * Initialize the text renderer with default font settings.
     * Should be called once during initialization.
     */
    public static void init() {
        if (defaultFont == null) {
            // Get default sans-serif font with normal style
            FontFamily fontFamily = FontFamily.SANS_SERIF;
            defaultFont = fontFamily.getClosestMatch(FontPaint.NORMAL);
        }
    }

    /**
     * Draw text at the specified position.
     *
     * @param canvas Canvas to draw on
     * @param text   Text to draw
     * @param x      X position
     * @param y      Y position
     * @param color  Text color (ARGB format)
     * @param size   Font size in pixels
     */
    public static void drawText(Canvas canvas, String text, float x, float y, int color, float size) {
        init();

        if (text == null || text.isEmpty()) {
            return;
        }

        Paint paint = new Paint();
        paint.setColor(color);

        // FontPaint is used for font metrics, not drawing
        FontPaint fontPaint = new FontPaint();
        fontPaint.setFontSize(size);

        // Use Canvas.drawSimpleText() which is available in ModernUI
        canvas.drawSimpleText(text, defaultFont, x, y, paint);
    }

    /**
     * Draw text at the specified position with default size (12px).
     *
     * @param canvas Canvas to draw on
     * @param text   Text to draw
     * @param x      X position
     * @param y      Y position
     * @param color  Text color (ARGB format)
     */
    public static void drawText(Canvas canvas, String text, float x, float y, int color) {
        drawText(canvas, text, x, y, color, 12.0f);
    }

    /**
     * Draw text at the specified position with custom size and Paint.
     *
     * @param canvas Canvas to draw on
     * @param text   Text to draw
     * @param x      X position
     * @param y      Y position
     * @param size   Font size in pixels
     * @param paint  Paint for color and style
     */
    public static void drawText(Canvas canvas, String text, float x, float y, float size, Paint paint) {
        init();

        if (text == null || text.isEmpty()) {
            return;
        }

        // Use Canvas.drawSimpleText() which is available in ModernUI
        canvas.drawSimpleText(text, defaultFont, x, y, paint);
    }

    /**
     * Measure the width of the given text.
     *
     * @param text Text to measure
     * @param size Font size in pixels
     * @return Width in pixels (approximate)
     */
    public static float measureText(String text, float size) {
        if (text == null || text.isEmpty()) {
            return 0;
        }

        // Approximate text width: 0.6 * fontSize per character
        // This is a rough estimate for sans-serif fonts
        return text.length() * size * 0.6f;
    }

    /**
     * Measure the width of the given text with default size (12px).
     *
     * @param text Text to measure
     * @return Width in pixels
     */
    public static float measureText(String text) {
        return measureText(text, 12.0f);
    }

    /**
     * Get font height for the given font size.
     *
     * @param fontSize Font size in pixels
     * @return Font height
     */
    public static float getFontHeight(float fontSize) {
        // Rough approximation
        return fontSize * 1.2f;
    }
}
