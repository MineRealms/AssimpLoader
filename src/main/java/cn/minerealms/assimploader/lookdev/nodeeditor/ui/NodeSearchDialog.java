package cn.minerealms.assimploader.lookdev.nodeeditor.ui;

import cn.minerealms.assimploader.lookdev.nodeeditor.NodeType;
import icyllis.modernui.fragment.Fragment;
import icyllis.modernui.graphics.Canvas;
import icyllis.modernui.graphics.Paint;
import icyllis.modernui.view.KeyEvent;

import java.util.*;

/**
 * Node search dialog for quick node creation.
 *
 * Features:
 * - Fuzzy search by node name
 * - Category filtering
 * - Keyboard navigation
 * - Quick creation with Enter
 *
 * Activated by Tab key in node editor.
 */
public class NodeSearchDialog {

    private boolean visible = false;
    private String searchQuery = "";
    private List<SearchResult> results = new ArrayList<>();
    private int selectedIndex = 0;

    // Position
    private float x = 0;
    private float y = 0;
    private float width = 300;
    private float height = 400;

    // Callback
    private NodeSelectionCallback callback;

    public NodeSearchDialog() {
        updateResults();
    }

    /**
     * Show dialog at position
     */
    public void show(float x, float y, NodeSelectionCallback callback) {
        this.x = x;
        this.y = y;
        this.callback = callback;
        this.visible = true;
        this.searchQuery = "";
        this.selectedIndex = 0;
        updateResults();
    }

    /**
     * Hide dialog
     */
    public void hide() {
        this.visible = false;
        this.searchQuery = "";
        this.selectedIndex = 0;
    }

    /**
     * Handle keyboard input
     */
    public boolean onKeyPressed(int keyCode, int scanCode, int modifiers) {
        if (!visible) return false;

        switch (keyCode) {
            case KeyEvent.KEY_ESCAPE:
                hide();
                return true;

            case KeyEvent.KEY_ENTER:
                if (!results.isEmpty() && selectedIndex >= 0 && selectedIndex < results.size()) {
                    SearchResult result = results.get(selectedIndex);
                    if (callback != null) {
                        callback.onNodeSelected(result.type);
                    }
                    hide();
                }
                return true;

            case KeyEvent.KEY_UP:
                selectedIndex = Math.max(0, selectedIndex - 1);
                return true;

            case KeyEvent.KEY_DOWN:
                selectedIndex = Math.min(results.size() - 1, selectedIndex + 1);
                return true;

            case KeyEvent.KEY_BACKSPACE:
                if (!searchQuery.isEmpty()) {
                    searchQuery = searchQuery.substring(0, searchQuery.length() - 1);
                    updateResults();
                }
                return true;

            default:
                return false;
        }
    }

    /**
     * Handle character input
     */
    public boolean onCharTyped(char character) {
        if (!visible) return false;

        if (Character.isLetterOrDigit(character) || character == ' ' || character == '_') {
            searchQuery += character;
            updateResults();
            selectedIndex = 0;
            return true;
        }

        return false;
    }

    /**
     * Update search results based on query
     */
    private void updateResults() {
        results.clear();

        String query = searchQuery.toLowerCase();

        for (NodeType type : NodeType.values()) {
            String name = type.getDisplayName().toLowerCase();
            String category = type.getCategory().getDisplayName().toLowerCase();

            // Fuzzy match
            if (query.isEmpty() || name.contains(query) || category.contains(query)) {
                int score = calculateScore(name, category, query);
                results.add(new SearchResult(type, score));
            }
        }

        // Sort by score
        results.sort(Comparator.comparingInt(r -> -r.score));

        // Limit results
        if (results.size() > 10) {
            results = results.subList(0, 10);
        }
    }

    /**
     * Calculate search score
     */
    private int calculateScore(String name, String category, String query) {
        if (query.isEmpty()) return 0;

        int score = 0;

        // Exact match
        if (name.equals(query)) score += 100;

        // Starts with
        if (name.startsWith(query)) score += 50;

        // Contains
        if (name.contains(query)) score += 25;

        // Category match
        if (category.contains(query)) score += 10;

        return score;
    }

    /**
     * Draw search dialog
     */
    public void draw(Canvas canvas, Paint paint) {
        if (!visible) return;

        // Draw background
        paint.setColor(0xE0202020);
        paint.setStyle(Paint.FILL);
        canvas.drawRoundRect(x, y, x + width, y + height, 8, 8, paint);

        // Draw border
        paint.setColor(0xFF00AAFF);
        paint.setStyle(Paint.STROKE);
        paint.setStrokeWidth(2.0f);
        canvas.drawRoundRect(x, y, x + width, y + height, 8, 8, paint);

        // Draw search box
        float searchBoxY = y + 10;
        paint.setColor(0xFF1A1A1A);
        paint.setStyle(Paint.FILL);
        canvas.drawRect(x + 10, searchBoxY, x + width - 10, searchBoxY + 30, paint);

        // Draw search query text (placeholder - actual text rendering would go here)
        paint.setColor(0xFFCCCCCC);
        // TODO: Draw actual text when ModernUI text rendering is available
        // For now, just show a visual indicator

        // Draw results
        float resultY = searchBoxY + 40;
        float resultHeight = 35;

        for (int i = 0; i < results.size(); i++) {
            SearchResult result = results.get(i);

            // Highlight selected
            if (i == selectedIndex) {
                paint.setColor(0xFF00AAFF);
                paint.setStyle(Paint.FILL);
                canvas.drawRect(x + 10, resultY, x + width - 10, resultY + resultHeight, paint);
            }

            // Draw result background
            paint.setColor(i == selectedIndex ? 0xFF003366 : 0xFF2A2A2A);
            paint.setStyle(Paint.FILL);
            canvas.drawRect(x + 15, resultY + 2, x + width - 15, resultY + resultHeight - 2, paint);

            // Draw category indicator
            paint.setColor(getCategoryColor(result.type.getCategory().getDisplayName()));
            canvas.drawRect(x + 15, resultY + 2, x + 20, resultY + resultHeight - 2, paint);

            // TODO: Draw node name and category text

            resultY += resultHeight;
        }

        // Draw hint text at bottom
        float hintY = y + height - 25;
        paint.setColor(0xFF666666);
        // TODO: Draw "↑↓ Navigate | Enter Select | Esc Cancel"
    }

    /**
     * Get color for category
     */
    private int getCategoryColor(String category) {
        return switch (category.toLowerCase()) {
            case "input" -> 0xFF00AA00;
            case "math" -> 0xFFAA0000;
            case "utility" -> 0xFF0000AA;
            case "output" -> 0xFFAA00AA;
            default -> 0xFF888888;
        };
    }

    /**
     * Check if dialog is visible
     */
    public boolean isVisible() {
        return visible;
    }

    /**
     * Search result
     */
    private static class SearchResult {
        NodeType type;
        int score;

        SearchResult(NodeType type, int score) {
            this.type = type;
            this.score = score;
        }
    }

    /**
     * Callback for node selection
     */
    public interface NodeSelectionCallback {
        void onNodeSelected(NodeType type);
    }
}
