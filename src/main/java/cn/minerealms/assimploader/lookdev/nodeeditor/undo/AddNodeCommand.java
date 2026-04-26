package cn.minerealms.assimploader.lookdev.nodeeditor.undo;

import cn.minerealms.assimploader.lookdev.nodeeditor.Node;
import cn.minerealms.assimploader.lookdev.nodeeditor.NodeEditorCanvas;

/**
 * Add Node Command
 *
 * Adds a node to the node editor canvas.
 *
 * Execute: Add the node to the canvas
 * Undo: Remove the node from the canvas
 */
public class AddNodeCommand implements Command {

    private final NodeEditorCanvas canvas;
    private final Node node;
    private final String description;

    public AddNodeCommand(NodeEditorCanvas canvas, Node node) {
        this.canvas = canvas;
        this.node = node;
        this.description = "Add " + node.getTitle();
    }

    @Override
    public void execute() {
        canvas.addNode(node);
    }

    @Override
    public void undo() {
        canvas.removeNode(node);
    }

    @Override
    public String getDescription() {
        return description;
    }
}
