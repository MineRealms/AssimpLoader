package cn.minerealms.assimploader.lookdev.nodeeditor.ui;

import cn.minerealms.assimploader.debug.DebugManager;
import cn.minerealms.assimploader.debug.DebugManager.DebugCategory;
import cn.minerealms.assimploader.lookdev.nodeeditor.NodeType;

import icyllis.modernui.core.Context;
import icyllis.modernui.view.View;
import icyllis.modernui.widget.LinearLayout;
import icyllis.modernui.widget.TextView;
import icyllis.modernui.graphics.drawable.Drawable;
import icyllis.modernui.graphics.drawable.ShapeDrawable;

/**
 * Canvas Context Menu
 *
 * Right-click menu for the canvas with quick node creation:
 * - Organized by category (Input, Math, Vector, Texture, Utility, Output)
 * - Quick access to commonly used nodes
 * - Spawns node at click position
 *
 * Usage:
 *   CanvasContextMenu menu = new CanvasContextMenu(context);
 *   menu.setOnNodeTypeSelectedListener(type -> { ... });
 *   menu.show(x, y);
 */
public class CanvasContextMenu extends LinearLayout {

    private OnNodeTypeSelectedListener nodeTypeListener;
    private float spawnX, spawnY;

    public CanvasContextMenu(Context context) {
        super(context);

        setOrientation(VERTICAL);
        setBackground(createMenuBackground());
        setPadding(5, 5, 5, 5);

        DebugManager.debug(DebugCategory.LOOKDEV, "[CanvasContextMenu] Creating canvas context menu");

        initializeMenuItems();
    }

    private void initializeMenuItems() {
        // Add category headers and node types
        addCategoryHeader("Input");
        addNodeTypeItem(NodeType.TEXTURE_SAMPLE);
        addNodeTypeItem(NodeType.VALUE);
        addNodeTypeItem(NodeType.COLOR);
        addNodeTypeItem(NodeType.UV_COORDINATE);

        addSeparator();

        addCategoryHeader("Math");
        addNodeTypeItem(NodeType.ADD);
        addNodeTypeItem(NodeType.MULTIPLY);
        addNodeTypeItem(NodeType.SUBTRACT);
        addNodeTypeItem(NodeType.DIVIDE);
        addNodeTypeItem(NodeType.LERP);
        addNodeTypeItem(NodeType.CLAMP);
        addNodeTypeItem(NodeType.MATH_FUNCTION);

        addSeparator();

        addCategoryHeader("Vector");
        addNodeTypeItem(NodeType.SPLIT);
        addNodeTypeItem(NodeType.COMBINE);
        addNodeTypeItem(NodeType.VECTOR_MATH);

        addSeparator();

        addCategoryHeader("Texture");
        addNodeTypeItem(NodeType.TEXTURE_COORDINATE);
        addNodeTypeItem(NodeType.MAPPING);

        addSeparator();

        addCategoryHeader("Utility");
        addNodeTypeItem(NodeType.NORMAL_MAP);

        addSeparator();

        addCategoryHeader("Output");
        addNodeTypeItem(NodeType.MATERIAL_OUTPUT);
    }

    private void addCategoryHeader(String category) {
        TextView header = new TextView(getContext());
        header.setText(category);
        header.setTextColor(0xFFFFAA00);
        header.setTextSize(12);
        header.setPadding(10, 8, 10, 4);

        LayoutParams params = new LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.WRAP_CONTENT);
        params.setMargins(0, 4, 0, 0);
        addView(header, params);
    }

    private void addNodeTypeItem(NodeType nodeType) {
        TextView menuItem = new TextView(getContext());
        menuItem.setText("  " + nodeType.getDisplayName());
        menuItem.setTextColor(0xFFCCCCCC);
        menuItem.setTextSize(13);
        menuItem.setPadding(10, 6, 10, 6);
        menuItem.setBackground(createMenuItemBackground());

        // Hover effect
        menuItem.setOnHoverListener((v, event) -> {
            // Hover effects handled by ModernUI
            return false;
        });

        // Click action
        menuItem.setOnClickListener(v -> {
            DebugManager.debug(DebugCategory.LOOKDEV,
                "[CanvasContextMenu] Node type selected: {}", nodeType);
            if (nodeTypeListener != null) {
                nodeTypeListener.onNodeTypeSelected(nodeType, spawnX, spawnY);
            }
            hide();
        });

        LayoutParams params = new LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.WRAP_CONTENT);
        params.setMargins(0, 1, 0, 1);
        addView(menuItem, params);
    }

    private void addSeparator() {
        View separator = new View(getContext());
        separator.setBackground(createSeparatorDrawable());

        LayoutParams params = new LayoutParams(LayoutParams.MATCH_PARENT, 1);
        params.setMargins(5, 4, 5, 4);
        addView(separator, params);
    }

    private Drawable createMenuBackground() {
        ShapeDrawable drawable = new ShapeDrawable();
        drawable.setColor(0xFF2A2A2A);
        return drawable;
    }

    private Drawable createMenuItemBackground() {
        ShapeDrawable drawable = new ShapeDrawable();
        drawable.setColor(0xFF1A1A1A);
        return drawable;
    }

    private Drawable createSeparatorDrawable() {
        ShapeDrawable drawable = new ShapeDrawable();
        drawable.setColor(0xFF444444);
        return drawable;
    }

    /**
     * Show the context menu at the specified position
     */
    public void show(float x, float y) {
        this.spawnX = x;
        this.spawnY = y;
        setX(x);
        setY(y);
        setVisibility(VISIBLE);

        DebugManager.debug(DebugCategory.LOOKDEV,
            "[CanvasContextMenu] Showing menu at ({}, {})", x, y);
    }

    /**
     * Hide the context menu
     */
    public void hide() {
        setVisibility(GONE);
        DebugManager.debug(DebugCategory.LOOKDEV, "[CanvasContextMenu] Menu hidden");
    }

    /**
     * Set the listener for node type selection
     */
    public void setOnNodeTypeSelectedListener(OnNodeTypeSelectedListener listener) {
        this.nodeTypeListener = listener;
    }

    /**
     * Listener interface for node type selection
     */
    @FunctionalInterface
    public interface OnNodeTypeSelectedListener {
        void onNodeTypeSelected(NodeType nodeType, float x, float y);
    }
}
