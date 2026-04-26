package cn.minerealms.assimploader.lookdev.nodeeditor.undo;

import cn.minerealms.assimploader.lookdev.nodeeditor.NodeSocket;

/**
 * Connect Sockets Command
 *
 * Creates a connection between two node sockets.
 *
 * Execute: Create the connection
 * Undo: Remove the connection
 */
public class ConnectSocketsCommand implements Command {

    private final NodeSocket outputSocket;
    private final NodeSocket inputSocket;
    private final String description;

    public ConnectSocketsCommand(NodeSocket outputSocket, NodeSocket inputSocket) {
        this.outputSocket = outputSocket;
        this.inputSocket = inputSocket;
        this.description = "Connect " + outputSocket.getNode().getTitle() +
                          " to " + inputSocket.getNode().getTitle();
    }

    @Override
    public void execute() {
        outputSocket.connectTo(inputSocket);
    }

    @Override
    public void undo() {
        outputSocket.disconnect();
    }

    @Override
    public String getDescription() {
        return description;
    }
}
