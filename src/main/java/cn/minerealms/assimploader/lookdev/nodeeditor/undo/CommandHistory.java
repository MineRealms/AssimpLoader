package cn.minerealms.assimploader.lookdev.nodeeditor.undo;

import cn.minerealms.assimploader.debug.DebugManager;
import cn.minerealms.assimploader.debug.DebugManager.DebugCategory;

import java.util.Stack;

/**
 * Command History
 *
 * Manages undo/redo functionality for the node editor.
 * Maintains two stacks:
 * - undoStack: Commands that can be undone
 * - redoStack: Commands that can be redone
 *
 * Usage:
 *   CommandHistory history = new CommandHistory();
 *   history.execute(new AddNodeCommand(...));
 *   history.undo();
 *   history.redo();
 *
 * Features:
 * - Configurable history limit (default: 50)
 * - Automatic redo stack clearing on new command
 * - Debug logging for all operations
 */
public class CommandHistory {

    private static final int DEFAULT_HISTORY_LIMIT = 50;

    private final Stack<Command> undoStack;
    private final Stack<Command> redoStack;
    private final int historyLimit;

    public CommandHistory() {
        this(DEFAULT_HISTORY_LIMIT);
    }

    public CommandHistory(int historyLimit) {
        this.undoStack = new Stack<>();
        this.redoStack = new Stack<>();
        this.historyLimit = historyLimit;

        DebugManager.debug(DebugCategory.LOOKDEV,
            "[CommandHistory] Initialized with history limit: {}", historyLimit);
    }

    /**
     * Execute a command and add it to the undo stack
     */
    public void execute(Command command) {
        DebugManager.debug(DebugCategory.LOOKDEV,
            "[CommandHistory] Executing command: {}", command.getDescription());

        // Execute the command
        command.execute();

        // Add to undo stack
        undoStack.push(command);

        // Clear redo stack (new action invalidates redo history)
        if (!redoStack.isEmpty()) {
            DebugManager.debug(DebugCategory.LOOKDEV,
                "[CommandHistory] Clearing redo stack ({} commands)", redoStack.size());
            redoStack.clear();
        }

        // Enforce history limit
        if (undoStack.size() > historyLimit) {
            undoStack.remove(0);
            DebugManager.debug(DebugCategory.LOOKDEV,
                "[CommandHistory] History limit reached, removed oldest command");
        }

        DebugManager.debug(DebugCategory.LOOKDEV,
            "[CommandHistory] Undo stack size: {}, Redo stack size: {}",
            undoStack.size(), redoStack.size());
    }

    /**
     * Undo the last command
     * @return true if undo was successful, false if nothing to undo
     */
    public boolean undo() {
        if (undoStack.isEmpty()) {
            DebugManager.debug(DebugCategory.LOOKDEV,
                "[CommandHistory] Cannot undo: undo stack is empty");
            return false;
        }

        Command command = undoStack.pop();
        DebugManager.debug(DebugCategory.LOOKDEV,
            "[CommandHistory] Undoing command: {}", command.getDescription());

        command.undo();
        redoStack.push(command);

        DebugManager.debug(DebugCategory.LOOKDEV,
            "[CommandHistory] Undo stack size: {}, Redo stack size: {}",
            undoStack.size(), redoStack.size());

        return true;
    }

    /**
     * Redo the last undone command
     * @return true if redo was successful, false if nothing to redo
     */
    public boolean redo() {
        if (redoStack.isEmpty()) {
            DebugManager.debug(DebugCategory.LOOKDEV,
                "[CommandHistory] Cannot redo: redo stack is empty");
            return false;
        }

        Command command = redoStack.pop();
        DebugManager.debug(DebugCategory.LOOKDEV,
            "[CommandHistory] Redoing command: {}", command.getDescription());

        command.execute();
        undoStack.push(command);

        DebugManager.debug(DebugCategory.LOOKDEV,
            "[CommandHistory] Undo stack size: {}, Redo stack size: {}",
            undoStack.size(), redoStack.size());

        return true;
    }

    /**
     * Check if undo is available
     */
    public boolean canUndo() {
        return !undoStack.isEmpty();
    }

    /**
     * Check if redo is available
     */
    public boolean canRedo() {
        return !redoStack.isEmpty();
    }

    /**
     * Get the description of the next command to undo
     */
    public String getUndoDescription() {
        if (undoStack.isEmpty()) {
            return null;
        }
        return undoStack.peek().getDescription();
    }

    /**
     * Get the description of the next command to redo
     */
    public String getRedoDescription() {
        if (redoStack.isEmpty()) {
            return null;
        }
        return redoStack.peek().getDescription();
    }

    /**
     * Clear all history
     */
    public void clear() {
        DebugManager.debug(DebugCategory.LOOKDEV,
            "[CommandHistory] Clearing all history (undo: {}, redo: {})",
            undoStack.size(), redoStack.size());

        undoStack.clear();
        redoStack.clear();
    }

    /**
     * Get the number of commands in the undo stack
     */
    public int getUndoStackSize() {
        return undoStack.size();
    }

    /**
     * Get the number of commands in the redo stack
     */
    public int getRedoStackSize() {
        return redoStack.size();
    }
}
