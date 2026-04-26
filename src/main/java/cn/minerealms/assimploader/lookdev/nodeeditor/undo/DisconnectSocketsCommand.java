package cn.minerealms.assimploader.lookdev.nodeeditor.undo;

import cn.minerealms.assimploader.lookdev.nodeeditor.NodeSocket;

/**
 * Disconnect Sockets Command
 *
 * Removes a connection between two node sockets.
 *
 * Execute: Remove the connection
 * Undo: Restore the connection
 */
public class DisconnectSocketsCommand implements Command {

    private final NodeSocket socket1;
    private final NodeSocket socket2;
    private final String description;

    public DisconnectSocketsCommand(NodeSocket socket1, NodeSocket socket2) {
        this.socket1 = socket1;
        this.socket2 = socket2;
        this.description = "Disconnect " + socket1.getNode().getTitle() +
                          " from " + socket2.getNode().getTitle();
    }

    @Override
    public void execute() {
        socket1.disconnect();
    }

    @Override
    public void undo() {
        socket1.connectTo(socket2);
    }

    @Override
    public String getDescription() {
        return description;
    }
}
