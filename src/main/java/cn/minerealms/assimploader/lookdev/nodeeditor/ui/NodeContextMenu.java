package cn.minerealms.assimploader.lookdev.nodeeditor.ui;

import cn.minerealms.assimploader.debug.DebugManager;
import cn.minerealms.assimploader.debug.DebugManager.DebugCategory;
import cn.minerealms.assimploader.lookdev.nodeeditor.Node;

import icyllis.modernui.core.Context;
import icyllis.modernui.view.View;
import icyllis.modernui.widget.LinearLayout;
import icyllis.modernui.widget.TextView;
import icyllis.modernui.graphics.drawable.Drawable;
import icyllis.modernui.graphics.drawable.ShapeDrawable;

/**
 * Node Context Menu
 *
 * Right-click menu for nodes with common operations:
 * - Delete: Remove the node from the graph
 * - Duplicate: Create a copy of the node
 * - Rename: Change the node's title (future enhancement)
 *
 * Usage:
 *   NodeContextMenu menu = new NodeContextMenu(context, node);
 *   menu.setOnActionListener(action -> { ... });
 *   menu.show(x, y);
 */
public class NodeContextMenu extends LinearLayout {

    public enum Action {
        DELETE,
        DUPLICATE,
        RENAME
    }

    private final Node targetNode;
    private OnActionListener actionListener;

    public NodeContextMenu(Context context, Node node) {
        super(context);
        this.targetNode = node;

        setOrientation(VERTICAL);
        setBackground(createMenuBackground());
        setPadding(5, 5, 5, 5);

        DebugManager.debug(DebugCategory.LOOKDEV,
            "[NodeContextMenu] Creating context menu for node: {}", node.getTitle());

        initializeMenuItems();
    }

    private void initializeMenuItems() {
        // Delete option
        addMenuItem("Delete", 0xFFFF4444, () -> {
            DebugManager.debug(DebugCategory.LOOKDEV,
                "[NodeContextMenu] Delete action triggered for node: {}", targetNode.getTitle());
            if (actionListener != null) {
                actionListener.onAction(Action.DELETE, targetNode);
            }
        });

        // Duplicate option
        addMenuItem("Duplicate", 0xFF44AAFF, () -> {
            DebugManager.debug(DebugCategory.LOOKDEV,
                "[NodeContextMenu] Duplicate action triggered for node: {}", targetNode.getTitle());
            if (actionListener != null) {
                actionListener.onAction(Action.DUPLICATE, targetNode);
            }
        });

        // Rename option (placeholder for future)
        addMenuItem("Rename", 0xFFFFAA44, () -> {
            DebugManager.debug(DebugCategory.LOOKDEV,
                "[NodeContextMenu] Rename action triggered (not yet implemented)");
            if (actionListener != null) {
                actionListener.onAction(Action.RENAME, targetNode);
            }
        });
    }

    private void addMenuItem(String label, int accentColor, Runnable action) {
        TextView menuItem = new TextView(getContext());
        menuItem.setText(label);
        menuItem.setTextColor(0xFFFFFFFF);
        menuItem.setTextSize(14);
        menuItem.setPadding(10, 8, 10, 8);
        menuItem.setBackground(createMenuItemBackground());

        // Hover effect
        menuItem.setOnHoverListener((v, event) -> {
            // Hover effects handled by ModernUI
            return false;
        });

        // Click action
        menuItem.setOnClickListener(v -> {
            action.run();
            // Hide menu after action
            setVisibility(GONE);
        });

        LayoutParams params = new LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.WRAP_CONTENT);
        params.setMargins(0, 2, 0, 2);
        addView(menuItem, params);
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

    /**
     * Show the context menu at the specified position
     */
    public void show(float x, float y) {
        setX(x);
        setY(y);
        setVisibility(VISIBLE);

        DebugManager.debug(DebugCategory.LOOKDEV,
            "[NodeContextMenu] Showing menu at ({}, {})", x, y);
    }

    /**
     * Hide the context menu
     */
    public void hide() {
        setVisibility(GONE);
        DebugManager.debug(DebugCategory.LOOKDEV, "[NodeContextMenu] Menu hidden");
    }

    /**
     * Set the action listener for menu actions
     */
    public void setOnActionListener(OnActionListener listener) {
        this.actionListener = listener;
    }

    /**
     * Listener interface for menu actions
     */
    @FunctionalInterface
    public interface OnActionListener {
        void onAction(Action action, Node node);
    }
}
