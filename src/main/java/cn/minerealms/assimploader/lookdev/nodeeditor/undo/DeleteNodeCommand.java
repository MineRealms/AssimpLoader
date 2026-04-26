package cn.minerealms.assimploader.lookdev.nodeeditor.undo;

import cn.minerealms.assimploader.lookdev.nodeeditor.Node;
import cn.minerealms.assimploader.lookdev.nodeeditor.NodeEditorCanvas;

/**
 * Delete Node Command
 *
 * Removes a node from the node editor canvas.
 *
 * Execute: Remove the node from the canvas
 * Undo: Add the node back to the canvas
 */
public class DeleteNodeCommand implements Command {

    private final NodeEditorCanvas canvas;
    private final Node node;
    private final String description;

    public DeleteNodeCommand(NodeEditorCanvas canvas, Node node) {
        this.canvas = canvas;
        this.node = node;
        this.description = "Delete " + node.getTitle();
    }

    @Override
    public void execute() {
        canvas.removeNode(node);
    }

    @Override
    public void undo() {
        canvas.addNode(node);
    }

    @Override
    public String getDescription() {
        return description;
    }
}
