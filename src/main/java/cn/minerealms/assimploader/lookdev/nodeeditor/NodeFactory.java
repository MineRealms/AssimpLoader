package cn.minerealms.assimploader.lookdev.nodeeditor;

/**
 * Factory for creating node instances.
 *
 * Creates nodes based on NodeType enum, using metadata from NodeRegistry
 * to configure sockets and properties.
 *
 * Usage:
 *   Node node = NodeFactory.createNode(NodeType.TEXTURE_SAMPLE);
 *   node.setPosition(100, 100);
 */
public class NodeFactory {

    /**
     * Create a node of the specified type
     *
     * @param type The node type to create
     * @return A new node instance, or null if type is not registered
     */
    public static Node createNode(NodeType type) {
        if (!NodeRegistry.isRegistered(type)) {
            return null;
        }

        // For now, return a placeholder node
        // Phase 2 will implement concrete node classes
        return new PlaceholderNode(type);
    }

    /**
     * Placeholder node implementation for Phase 1
     * Will be replaced with concrete implementations in Phase 2
     */
    private static class PlaceholderNode extends Node {
        private final NodeType nodeType;

        public PlaceholderNode(NodeType type) {
            super(type.getDisplayName());
            this.nodeType = type;

            // Create sockets from metadata
            NodeRegistry.NodeMetadata metadata = NodeRegistry.getMetadata(type);

            // Add input sockets
            for (NodeRegistry.SocketDefinition def : metadata.getInputs()) {
                addInput(new EnhancedNodeSocket(def.getName(), def.getType()));
            }

            // Add output sockets
            for (NodeRegistry.SocketDefinition def : metadata.getOutputs()) {
                addOutput(new EnhancedNodeSocket(def.getName(), def.getType()));
            }
        }

        @Override
        protected void drawBody(icyllis.modernui.graphics.Canvas canvas, icyllis.modernui.graphics.Paint paint) {
            // Simple placeholder rendering
            paint.setColor(0xFF333333);
            paint.setStyle(icyllis.modernui.graphics.Paint.FILL);
            float bodyY = y + TITLE_BAR_HEIGHT;
            float bodyHeight = height - TITLE_BAR_HEIGHT;
            canvas.drawRect(
                    x + BORDER_WIDTH,
                    bodyY,
                    x + width - BORDER_WIDTH,
                    bodyY + bodyHeight,
                    paint
            );
        }

        public NodeType getNodeType() {
            return nodeType;
        }
    }

    /**
     * Enhanced socket that uses the new SocketType enum
     */
    private static class EnhancedNodeSocket extends NodeSocket {
        private final cn.minerealms.assimploader.lookdev.nodeeditor.SocketType enhancedType;

        public EnhancedNodeSocket(String name, cn.minerealms.assimploader.lookdev.nodeeditor.SocketType type) {
            // Map new SocketType to old NodeSocket.SocketType for compatibility
            super(name, mapToLegacyType(type));
            this.enhancedType = type;
        }

        /**
         * Get the enhanced socket type
         */
        public cn.minerealms.assimploader.lookdev.nodeeditor.SocketType getEnhancedType() {
            return enhancedType;
        }

        @Override
        public int getColor() {
            return enhancedType.getColor();
        }

        /**
         * Map new SocketType to legacy NodeSocket.SocketType
         * TODO: Remove this once NodeSocket is fully migrated
         */
        private static NodeSocket.SocketType mapToLegacyType(cn.minerealms.assimploader.lookdev.nodeeditor.SocketType type) {
            if (type == cn.minerealms.assimploader.lookdev.nodeeditor.SocketType.FLOAT) {
                return NodeSocket.SocketType.FLOAT;
            } else if (type == cn.minerealms.assimploader.lookdev.nodeeditor.SocketType.VEC3 ||
                       type == cn.minerealms.assimploader.lookdev.nodeeditor.SocketType.VEC4) {
                return NodeSocket.SocketType.COLOR;
            } else if (type == cn.minerealms.assimploader.lookdev.nodeeditor.SocketType.SAMPLER2D) {
                return NodeSocket.SocketType.TEXTURE;
            } else if (type == cn.minerealms.assimploader.lookdev.nodeeditor.SocketType.VEC2) {
                return NodeSocket.SocketType.VECTOR;
            } else {
                return NodeSocket.SocketType.ANY;
            }
        }
    }
}
