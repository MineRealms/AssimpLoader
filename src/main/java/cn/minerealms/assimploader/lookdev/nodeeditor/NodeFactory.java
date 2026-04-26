package cn.minerealms.assimploader.lookdev.nodeeditor;

import cn.minerealms.assimploader.lookdev.nodeeditor.nodes.input.*;
import cn.minerealms.assimploader.lookdev.nodeeditor.nodes.math.*;
import cn.minerealms.assimploader.lookdev.nodeeditor.nodes.utility.*;
import cn.minerealms.assimploader.lookdev.nodeeditor.nodes.output.*;

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

        // Create concrete node instances based on type
        switch (type) {
            // Input nodes
            case TEXTURE_SAMPLE:
                return new TextureSampleNode();
            case VALUE:
                return new ValueNode();
            case COLOR:
                return new ColorNode();
            case UV_COORDINATE:
                return new UVCoordinateNode();

            // Math nodes
            case MULTIPLY:
                return new MultiplyNode();
            case ADD:
                return new AddNode();
            case LERP:
                return new LerpNode();

            // Utility nodes
            case NORMAL_MAP:
                return new NormalMapNode();

            // Output node
            case MATERIAL_OUTPUT:
                return new MaterialOutputNode();

            default:
                return null;
        }
    }
}
