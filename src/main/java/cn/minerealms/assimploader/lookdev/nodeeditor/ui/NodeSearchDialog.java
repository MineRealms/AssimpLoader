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
 * - Recent nodes tracking
 * - Tag-based search
 *
 * Activated by Tab key in node editor.
 */
public class NodeSearchDialog {

    private boolean visible = false;
    private String searchQuery = "";
    private List<SearchResult> results = new ArrayList<>();
    private int selectedIndex = 0;
    private NodeType.NodeCategory filterCategory = null;

    // Position
    private float x = 0;
    private float y = 0;
    private float width = 300;
    private float height = 400;

    // Callback
    private NodeSelectionCallback callback;

    // Recent nodes (most recent first)
    private final LinkedList<NodeType> recentNodes = new LinkedList<>();
    private static final int MAX_RECENT_NODES = 5;

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
        this.filterCategory = null;
        updateResults();
    }

    /**
     * Hide dialog
     */
    public void hide() {
        this.visible = false;
        this.searchQuery = "";
        this.selectedIndex = 0;
        this.filterCategory = null;
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
                        addToRecent(result.type);
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

            case KeyEvent.KEY_TAB:
                // Cycle through category filters
                cycleCategory();
                updateResults();
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
     * Cycle through category filters
     */
    private void cycleCategory() {
        if (filterCategory == null) {
            filterCategory = NodeType.NodeCategory.INPUT;
        } else {
            NodeType.NodeCategory[] categories = NodeType.NodeCategory.values();
            int currentIndex = -1;
            for (int i = 0; i < categories.length; i++) {
                if (categories[i] == filterCategory) {
                    currentIndex = i;
                    break;
                }
            }
            if (currentIndex >= 0 && currentIndex < categories.length - 1) {
                filterCategory = categories[currentIndex + 1];
            } else {
                filterCategory = null; // Reset to no filter
            }
        }
    }

    /**
     * Add node to recent list
     */
    private void addToRecent(NodeType type) {
        // Remove if already exists
        recentNodes.remove(type);
        // Add to front
        recentNodes.addFirst(type);
        // Limit size
        while (recentNodes.size() > MAX_RECENT_NODES) {
            recentNodes.removeLast();
        }
    }

    /**
     * Update search results based on query
     */
    private void updateResults() {
        results.clear();

        String query = searchQuery.toLowerCase();

        // If query is empty, show recent nodes first
        if (query.isEmpty() && filterCategory == null && !recentNodes.isEmpty()) {
            for (NodeType type : recentNodes) {
                results.add(new SearchResult(type, 1000)); // High score for recent
            }
        }

        for (NodeType type : NodeType.values()) {
            // Skip if already in recent nodes section
            if (query.isEmpty() && filterCategory == null && recentNodes.contains(type)) {
                continue;
            }

            // Apply category filter
            if (filterCategory != null && type.getCategory() != filterCategory) {
                continue;
            }

            String name = type.getDisplayName().toLowerCase();
            String category = type.getCategory().getDisplayName().toLowerCase();

            // Fuzzy match
            if (query.isEmpty() || fuzzyMatch(name, query) || name.contains(query) || category.contains(query)) {
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
     * Fuzzy match algorithm
     * Returns true if all characters in query appear in name in order
     */
    private boolean fuzzyMatch(String name, String query) {
        if (query.isEmpty()) return true;

        int nameIndex = 0;
        int queryIndex = 0;

        while (nameIndex < name.length() && queryIndex < query.length()) {
            if (name.charAt(nameIndex) == query.charAt(queryIndex)) {
                queryIndex++;
            }
            nameIndex++;
        }

        return queryIndex == query.length();
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

        // Fuzzy match bonus
        if (fuzzyMatch(name, query)) score += 15;

        // Category match
        if (category.contains(query)) score += 10;

        // Shorter names get bonus (more specific)
        score += Math.max(0, 20 - name.length());

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

        // Draw category filter indicator
        if (filterCategory != null) {
            paint.setColor(getCategoryColor(filterCategory.getDisplayName()));
            canvas.drawRect(x + 10, searchBoxY, x + 15, searchBoxY + 30, paint);
        }

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

            // Draw recent indicator (star)
            if (recentNodes.contains(result.type)) {
                paint.setColor(0xFFFFAA00);
                canvas.drawCircle(x + width - 25, resultY + resultHeight / 2, 4, paint);
            }

            // TODO: Draw node name and category text

            resultY += resultHeight;
        }

        // Draw hint text at bottom
        float hintY = y + height - 25;
        paint.setColor(0xFF666666);
        // TODO: Draw "↑↓ Navigate | Tab Filter | Enter Select | Esc Cancel"
    }

    /**
     * Get color for category
     */
    private int getCategoryColor(String category) {
        return switch (category.toLowerCase()) {
            case "input" -> 0xFF00AA00;
            case "math" -> 0xFFAA0000;
            case "vector" -> 0xFF0088FF;
            case "texture" -> 0xFF00AA88;
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
     * Get recent nodes list (for debugging/testing)
     */
    public List<NodeType> getRecentNodes() {
        return new ArrayList<>(recentNodes);
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
