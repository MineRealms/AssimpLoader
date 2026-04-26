package cn.minerealms.assimploader.lookdev.nodeeditor;

import java.util.HashMap;
import java.util.Map;

/**
 * Registry for node type metadata.
 *
 * Stores information about each node type:
 * - Display name
 * - Category
 * - Input/output socket definitions
 * - Default properties
 *
 * This metadata is used by NodeFactory to create nodes and by the UI
 * to display the node palette.
 */
public class NodeRegistry {

    private static final Map<NodeType, NodeMetadata> registry = new HashMap<>();

    static {
        // Register all node types with their metadata
        registerInputNodes();
        registerMathNodes();
        registerUtilityNodes();
        registerOutputNode();
    }

    private static void registerInputNodes() {
        // Texture Sample Node
        registry.put(NodeType.TEXTURE_SAMPLE, new NodeMetadata.Builder(NodeType.TEXTURE_SAMPLE)
                .addInput("UV", SocketType.VEC2)
                .addOutput("Color", SocketType.VEC4)
                .addOutput("RGB", SocketType.VEC3)
                .addOutput("Alpha", SocketType.FLOAT)
                .build());

        // Value Node
        registry.put(NodeType.VALUE, new NodeMetadata.Builder(NodeType.VALUE)
                .addOutput("Value", SocketType.FLOAT)
                .build());

        // Color Node
        registry.put(NodeType.COLOR, new NodeMetadata.Builder(NodeType.COLOR)
                .addOutput("Color", SocketType.VEC4)
                .addOutput("RGB", SocketType.VEC3)
                .build());

        // UV Coordinate Node
        registry.put(NodeType.UV_COORDINATE, new NodeMetadata.Builder(NodeType.UV_COORDINATE)
                .addOutput("UV", SocketType.VEC2)
                .build());
    }

    private static void registerMathNodes() {
        // Multiply Node
        registry.put(NodeType.MULTIPLY, new NodeMetadata.Builder(NodeType.MULTIPLY)
                .addInput("A", SocketType.VEC3)
                .addInput("B", SocketType.VEC3)
                .addOutput("Result", SocketType.VEC3)
                .build());

        // Add Node
        registry.put(NodeType.ADD, new NodeMetadata.Builder(NodeType.ADD)
                .addInput("A", SocketType.VEC3)
                .addInput("B", SocketType.VEC3)
                .addOutput("Result", SocketType.VEC3)
                .build());

        // Lerp Node
        registry.put(NodeType.LERP, new NodeMetadata.Builder(NodeType.LERP)
                .addInput("A", SocketType.VEC3)
                .addInput("B", SocketType.VEC3)
                .addInput("T", SocketType.FLOAT)
                .addOutput("Result", SocketType.VEC3)
                .build());
    }

    private static void registerUtilityNodes() {
        // Normal Map Node
        registry.put(NodeType.NORMAL_MAP, new NodeMetadata.Builder(NodeType.NORMAL_MAP)
                .addInput("Normal Map", SocketType.SAMPLER2D)
                .addInput("UV", SocketType.VEC2)
                .addInput("Strength", SocketType.FLOAT)
                .addOutput("Normal", SocketType.VEC3)
                .build());
    }

    private static void registerOutputNode() {
        // Material Output Node
        registry.put(NodeType.MATERIAL_OUTPUT, new NodeMetadata.Builder(NodeType.MATERIAL_OUTPUT)
                .addInput("Base Color", SocketType.VEC3)
                .addInput("Metallic", SocketType.FLOAT)
                .addInput("Roughness", SocketType.FLOAT)
                .addInput("Normal", SocketType.VEC3)
                .addInput("Emissive", SocketType.VEC3)
                .addInput("Alpha", SocketType.FLOAT)
                .build());
    }

    /**
     * Get metadata for a node type
     */
    public static NodeMetadata getMetadata(NodeType type) {
        return registry.get(type);
    }

    /**
     * Check if a node type is registered
     */
    public static boolean isRegistered(NodeType type) {
        return registry.containsKey(type);
    }

    /**
     * Metadata for a node type
     */
    public static class NodeMetadata {
        private final NodeType type;
        private final SocketDefinition[] inputs;
        private final SocketDefinition[] outputs;

        private NodeMetadata(NodeType type, SocketDefinition[] inputs, SocketDefinition[] outputs) {
            this.type = type;
            this.inputs = inputs;
            this.outputs = outputs;
        }

        public NodeType getType() {
            return type;
        }

        public SocketDefinition[] getInputs() {
            return inputs;
        }

        public SocketDefinition[] getOutputs() {
            return outputs;
        }

        /**
         * Builder for NodeMetadata
         */
        public static class Builder {
            private final NodeType type;
            private final java.util.List<SocketDefinition> inputs = new java.util.ArrayList<>();
            private final java.util.List<SocketDefinition> outputs = new java.util.ArrayList<>();

            public Builder(NodeType type) {
                this.type = type;
            }

            public Builder addInput(String name, SocketType socketType) {
                inputs.add(new SocketDefinition(name, socketType));
                return this;
            }

            public Builder addOutput(String name, SocketType socketType) {
                outputs.add(new SocketDefinition(name, socketType));
                return this;
            }

            public NodeMetadata build() {
                return new NodeMetadata(
                        type,
                        inputs.toArray(new SocketDefinition[0]),
                        outputs.toArray(new SocketDefinition[0])
                );
            }
        }
    }

    /**
     * Definition of a socket (name + type)
     */
    public static class SocketDefinition {
        private final String name;
        private final SocketType type;

        public SocketDefinition(String name, SocketType type) {
            this.name = name;
            this.type = type;
        }

        public String getName() {
            return name;
        }

        public SocketType getType() {
            return type;
        }
    }
}
