package cn.minerealms.assimploader.lookdev.nodeeditor.undo;

import cn.minerealms.assimploader.lookdev.nodeeditor.Node;

/**
 * Move Node Command
 *
 * Changes the position of a node.
 *
 * Execute: Move node to new position
 * Undo: Move node back to old position
 */
public class MoveNodeCommand implements Command {

    private final Node node;
    private final float oldX, oldY;
    private final float newX, newY;
    private final String description;

    public MoveNodeCommand(Node node, float oldX, float oldY, float newX, float newY) {
        this.node = node;
        this.oldX = oldX;
        this.oldY = oldY;
        this.newX = newX;
        this.newY = newY;
        this.description = "Move " + node.getTitle();
    }

    @Override
    public void execute() {
        node.setPosition(newX, newY);
    }

    @Override
    public void undo() {
        node.setPosition(oldX, oldY);
    }

    @Override
    public String getDescription() {
        return description;
    }
}
