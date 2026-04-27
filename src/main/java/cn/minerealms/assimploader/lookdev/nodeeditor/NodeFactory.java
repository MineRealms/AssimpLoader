package cn.minerealms.assimploader.lookdev.nodeeditor;

import cn.minerealms.assimploader.debug.DebugManager;
import cn.minerealms.assimploader.debug.DebugManager.DebugCategory;
import cn.minerealms.assimploader.lookdev.nodeeditor.nodes.input.*;
import cn.minerealms.assimploader.lookdev.nodeeditor.nodes.math.*;
import cn.minerealms.assimploader.lookdev.nodeeditor.nodes.vector.*;
import cn.minerealms.assimploader.lookdev.nodeeditor.nodes.texture.*;
import cn.minerealms.assimploader.lookdev.nodeeditor.nodes.utility.*;
import cn.minerealms.assimploader.lookdev.nodeeditor.nodes.*;

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
        DebugManager.debug(DebugCategory.LOOKDEV, "[NodeFactory] Creating node of type: {}", type);

        if (!NodeRegistry.isRegistered(type)) {
            DebugManager.warn(DebugCategory.LOOKDEV,
                "[NodeFactory] Cannot create unregistered node type: {}", type);
            return null;
        }

        try {
            Node node = null;

            // Create concrete node instances based on type
            switch (type) {
                // Input nodes
                case TEXTURE_SAMPLE:
                    node = new TextureSampleNode();
                    break;
                case VALUE:
                    node = new ValueNode();
                    break;
                case COLOR:
                    node = new ColorNode();
                    break;
                case UV_COORDINATE:
                    node = new UVCoordinateNode();
                    break;

                // Math nodes
                case MULTIPLY:
                    node = new MultiplyNode();
                    break;
                case ADD:
                    node = new AddNode();
                    break;
                case SUBTRACT:
                    node = new SubtractNode();
                    break;
                case DIVIDE:
                    node = new DivideNode();
                    break;
                case LERP:
                    node = new LerpNode();
                    break;
                case CLAMP:
                    node = new ClampNode();
                    break;
                case MATH_FUNCTION:
                    node = new MathFunctionNode();
                    break;

                // Vector nodes
                case SPLIT:
                    node = new SplitNode();
                    break;
                case COMBINE:
                    node = new CombineNode();
                    break;
                case VECTOR_MATH:
                    node = new VectorMathNode();
                    break;

                // Texture nodes
                case TEXTURE_COORDINATE:
                    node = new TextureCoordinateNode();
                    break;
                case MAPPING:
                    node = new MappingNode();
                    break;

                // Utility nodes
                case NORMAL_MAP:
                    node = new NormalMapNode();
                    break;

                // Shader nodes
                case TIME:
                    node = new TimeNode();
                    break;
                case NOISE:
                    node = new NoiseNode();
                    break;
                case WORLD_POS:
                    node = new WorldPosNode();
                    break;
                case VIEW_DIR:
                    node = new ViewDirNode();
                    break;
                case LIGHT_DIR:
                    node = new LightDirNode();
                    break;

                // Output node
                case MATERIAL_OUTPUT:
                    node = new OutputNode();
                    break;

                default:
                    DebugManager.warn(DebugCategory.LOOKDEV,
                        "[NodeFactory] No implementation for node type: {}", type);
                    return null;
            }

            if (node != null) {
                DebugManager.debug(DebugCategory.LOOKDEV,
                    "[NodeFactory] Successfully created node: {} (id: {})", type, node.getId());
            }

            return node;
        } catch (Exception e) {
            DebugManager.error(DebugCategory.LOOKDEV,
                "[NodeFactory] Failed to create node: " + type, e);
            return null;
        }
    }
}
