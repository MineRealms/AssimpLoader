package cn.minerealms.assimploader.lookdev.nodeeditor.undo;

/**
 * Command Interface
 *
 * Represents a reversible action in the node editor.
 * Implements the Command pattern for undo/redo functionality.
 *
 * Each command encapsulates:
 * - execute(): Perform the action
 * - undo(): Reverse the action
 * - getDescription(): Human-readable description
 *
 * Common commands:
 * - AddNodeCommand: Add a node to the graph
 * - DeleteNodeCommand: Remove a node from the graph
 * - MoveNodeCommand: Change node position
 * - ConnectSocketsCommand: Create a connection
 * - DisconnectSocketsCommand: Remove a connection
 * - ChangePropertyCommand: Modify node property
 */
public interface Command {

    /**
     * Execute the command (perform the action)
     */
    void execute();

    /**
     * Undo the command (reverse the action)
     */
    void undo();

    /**
     * Get a human-readable description of this command
     * Used for debugging and UI display
     */
    String getDescription();
}
