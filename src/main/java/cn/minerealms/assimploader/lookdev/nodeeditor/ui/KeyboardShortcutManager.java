package cn.minerealms.assimploader.lookdev.nodeeditor.ui;

import icyllis.modernui.view.KeyEvent;

import java.util.*;

/**
 * Keyboard shortcut manager for node editor.
 *
 * Supported shortcuts:
 * - Tab: Open node search
 * - Ctrl+S: Save material
 * - Ctrl+O: Open material
 * - Ctrl+Z: Undo
 * - Ctrl+Y / Ctrl+Shift+Z: Redo
 * - Delete: Delete selected nodes
 * - Ctrl+D: Duplicate selected nodes
 * - Ctrl+A: Select all nodes
 * - F: Frame selected nodes
 * - Home: Reset view
 * - Ctrl+G: Generate shader
 */
public class KeyboardShortcutManager {

    private final Map<ShortcutKey, ShortcutAction> shortcuts = new HashMap<>();

    public KeyboardShortcutManager() {
        registerDefaultShortcuts();
    }

    /**
     * Register default shortcuts
     */
    private void registerDefaultShortcuts() {
        // Node search
        register(KeyEvent.KEY_TAB, 0, ShortcutAction.OPEN_NODE_SEARCH);

        // File operations
        register(KeyEvent.KEY_S, KeyEvent.META_CTRL_ON, ShortcutAction.SAVE_MATERIAL);
        register(KeyEvent.KEY_O, KeyEvent.META_CTRL_ON, ShortcutAction.OPEN_MATERIAL);

        // Edit operations
        register(KeyEvent.KEY_Z, KeyEvent.META_CTRL_ON, ShortcutAction.UNDO);
        register(KeyEvent.KEY_Y, KeyEvent.META_CTRL_ON, ShortcutAction.REDO);
        register(KeyEvent.KEY_Z, KeyEvent.META_CTRL_ON | KeyEvent.META_SHIFT_ON, ShortcutAction.REDO);

        // Node operations
        register(KeyEvent.KEY_DELETE, 0, ShortcutAction.DELETE_SELECTED);
        register(KeyEvent.KEY_D, KeyEvent.META_CTRL_ON, ShortcutAction.DUPLICATE_SELECTED);
        register(KeyEvent.KEY_A, KeyEvent.META_CTRL_ON, ShortcutAction.SELECT_ALL);

        // View operations
        register(KeyEvent.KEY_F, 0, ShortcutAction.FRAME_SELECTED);
        register(KeyEvent.KEY_HOME, 0, ShortcutAction.RESET_VIEW);

        // Shader operations
        register(KeyEvent.KEY_G, KeyEvent.META_CTRL_ON, ShortcutAction.GENERATE_SHADER);
    }

    /**
     * Register a shortcut
     */
    public void register(int keyCode, int modifiers, ShortcutAction action) {
        shortcuts.put(new ShortcutKey(keyCode, modifiers), action);
    }

    /**
     * Handle key press
     */
    public ShortcutAction handleKeyPress(int keyCode, int modifiers) {
        ShortcutKey key = new ShortcutKey(keyCode, modifiers);
        return shortcuts.get(key);
    }

    /**
     * Get shortcut description
     */
    public String getShortcutDescription(ShortcutAction action) {
        for (Map.Entry<ShortcutKey, ShortcutAction> entry : shortcuts.entrySet()) {
            if (entry.getValue() == action) {
                return entry.getKey().toString();
            }
        }
        return "";
    }

    /**
     * Get all shortcuts
     */
    public Map<ShortcutAction, String> getAllShortcuts() {
        Map<ShortcutAction, String> result = new HashMap<>();
        for (Map.Entry<ShortcutKey, ShortcutAction> entry : shortcuts.entrySet()) {
            result.put(entry.getValue(), entry.getKey().toString());
        }
        return result;
    }

    /**
     * Shortcut key combination
     */
    private static class ShortcutKey {
        final int keyCode;
        final int modifiers;

        ShortcutKey(int keyCode, int modifiers) {
            this.keyCode = keyCode;
            this.modifiers = modifiers;
        }

        @Override
        public boolean equals(Object o) {
            if (this == o) return true;
            if (o == null || getClass() != o.getClass()) return false;
            ShortcutKey that = (ShortcutKey) o;
            return keyCode == that.keyCode && modifiers == that.modifiers;
        }

        @Override
        public int hashCode() {
            return Objects.hash(keyCode, modifiers);
        }

        @Override
        public String toString() {
            StringBuilder sb = new StringBuilder();

            if ((modifiers & KeyEvent.META_CTRL_ON) != 0) {
                sb.append("Ctrl+");
            }
            if ((modifiers & KeyEvent.META_SHIFT_ON) != 0) {
                sb.append("Shift+");
            }
            if ((modifiers & KeyEvent.META_ALT_ON) != 0) {
                sb.append("Alt+");
            }

            sb.append(getKeyName(keyCode));

            return sb.toString();
        }

        private String getKeyName(int keyCode) {
            return switch (keyCode) {
                case KeyEvent.KEY_TAB -> "Tab";
                case KeyEvent.KEY_DELETE -> "Delete";
                case KeyEvent.KEY_HOME -> "Home";
                case KeyEvent.KEY_F -> "F";
                case KeyEvent.KEY_S -> "S";
                case KeyEvent.KEY_O -> "O";
                case KeyEvent.KEY_Z -> "Z";
                case KeyEvent.KEY_Y -> "Y";
                case KeyEvent.KEY_D -> "D";
                case KeyEvent.KEY_A -> "A";
                case KeyEvent.KEY_G -> "G";
                default -> "Key" + keyCode;
            };
        }
    }

    /**
     * Shortcut actions
     */
    public enum ShortcutAction {
        OPEN_NODE_SEARCH,
        SAVE_MATERIAL,
        OPEN_MATERIAL,
        UNDO,
        REDO,
        DELETE_SELECTED,
        DUPLICATE_SELECTED,
        SELECT_ALL,
        FRAME_SELECTED,
        RESET_VIEW,
        GENERATE_SHADER
    }
}
