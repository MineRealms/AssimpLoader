package cn.minerealms.assimploader.lookdev.nodeeditor;

import cn.minerealms.assimploader.debug.DebugManager;
import cn.minerealms.assimploader.debug.DebugManager.DebugCategory;

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
        DebugManager.debug(DebugCategory.LOOKDEV, "[NodeRegistry] Initializing node registry");
        registerInputNodes();
        registerMathNodes();
        registerVectorNodes();
        registerTextureNodes();
        registerUtilityNodes();
        registerOutputNode();
        DebugManager.debug(DebugCategory.LOOKDEV, "[NodeRegistry] Registered {} node types", registry.size());
    }

    private static void registerInputNodes() {
        // Texture Sample Node
        registry.put(NodeType.TEXTURE_SAMPLE, new NodeMetadata.Builder(NodeType.TEXTURE_SAMPLE)
                .addInput("UV", EnhancedSocketType.VEC2)
                .addOutput("Color", EnhancedSocketType.VEC4)
                .addOutput("RGB", EnhancedSocketType.VEC3)
                .addOutput("Alpha", EnhancedSocketType.FLOAT)
                .build());

        // Value Node
        registry.put(NodeType.VALUE, new NodeMetadata.Builder(NodeType.VALUE)
                .addOutput("Value", EnhancedSocketType.FLOAT)
                .build());

        // Color Node
        registry.put(NodeType.COLOR, new NodeMetadata.Builder(NodeType.COLOR)
                .addOutput("Color", EnhancedSocketType.VEC4)
                .addOutput("RGB", EnhancedSocketType.VEC3)
                .build());

        // UV Coordinate Node
        registry.put(NodeType.UV_COORDINATE, new NodeMetadata.Builder(NodeType.UV_COORDINATE)
                .addOutput("UV", EnhancedSocketType.VEC2)
                .build());
    }

    private static void registerMathNodes() {
        // Multiply Node
        registry.put(NodeType.MULTIPLY, new NodeMetadata.Builder(NodeType.MULTIPLY)
                .addInput("A", EnhancedSocketType.VEC3)
                .addInput("B", EnhancedSocketType.VEC3)
                .addOutput("Result", EnhancedSocketType.VEC3)
                .build());

        // Add Node
        registry.put(NodeType.ADD, new NodeMetadata.Builder(NodeType.ADD)
                .addInput("A", EnhancedSocketType.VEC3)
                .addInput("B", EnhancedSocketType.VEC3)
                .addOutput("Result", EnhancedSocketType.VEC3)
                .build());

        // Subtract Node
        registry.put(NodeType.SUBTRACT, new NodeMetadata.Builder(NodeType.SUBTRACT)
                .addInput("A", EnhancedSocketType.FLOAT)
                .addInput("B", EnhancedSocketType.FLOAT)
                .addOutput("Result", EnhancedSocketType.FLOAT)
                .build());

        // Divide Node
        registry.put(NodeType.DIVIDE, new NodeMetadata.Builder(NodeType.DIVIDE)
                .addInput("A", EnhancedSocketType.FLOAT)
                .addInput("B", EnhancedSocketType.FLOAT)
                .addOutput("Result", EnhancedSocketType.FLOAT)
                .build());

        // Lerp Node
        registry.put(NodeType.LERP, new NodeMetadata.Builder(NodeType.LERP)
                .addInput("A", EnhancedSocketType.VEC3)
                .addInput("B", EnhancedSocketType.VEC3)
                .addInput("T", EnhancedSocketType.FLOAT)
                .addOutput("Result", EnhancedSocketType.VEC3)
                .build());

        // Clamp Node
        registry.put(NodeType.CLAMP, new NodeMetadata.Builder(NodeType.CLAMP)
                .addInput("Value", EnhancedSocketType.FLOAT)
                .addInput("Min", EnhancedSocketType.FLOAT)
                .addInput("Max", EnhancedSocketType.FLOAT)
                .addOutput("Result", EnhancedSocketType.FLOAT)
                .build());

        // Math Function Node
        registry.put(NodeType.MATH_FUNCTION, new NodeMetadata.Builder(NodeType.MATH_FUNCTION)
                .addInput("Value", EnhancedSocketType.FLOAT)
                .addInput("Exponent", EnhancedSocketType.FLOAT)
                .addOutput("Result", EnhancedSocketType.FLOAT)
                .build());
    }

    private static void registerVectorNodes() {
        // Split Node
        registry.put(NodeType.SPLIT, new NodeMetadata.Builder(NodeType.SPLIT)
                .addInput("Vector", EnhancedSocketType.VEC3)
                .addOutput("X", EnhancedSocketType.FLOAT)
                .addOutput("Y", EnhancedSocketType.FLOAT)
                .addOutput("Z", EnhancedSocketType.FLOAT)
                .build());

        // Combine Node
        registry.put(NodeType.COMBINE, new NodeMetadata.Builder(NodeType.COMBINE)
                .addInput("X", EnhancedSocketType.FLOAT)
                .addInput("Y", EnhancedSocketType.FLOAT)
                .addInput("Z", EnhancedSocketType.FLOAT)
                .addOutput("Vector", EnhancedSocketType.VEC3)
                .build());

        // Vector Math Node
        registry.put(NodeType.VECTOR_MATH, new NodeMetadata.Builder(NodeType.VECTOR_MATH)
                .addInput("A", EnhancedSocketType.VEC3)
                .addInput("B", EnhancedSocketType.VEC3)
                .addOutput("Result", EnhancedSocketType.VEC3)
                .build());
    }

    private static void registerTextureNodes() {
        // Texture Coordinate Node
        registry.put(NodeType.TEXTURE_COORDINATE, new NodeMetadata.Builder(NodeType.TEXTURE_COORDINATE)
                .addInput("Offset", EnhancedSocketType.VEC3)
                .addInput("Scale", EnhancedSocketType.VEC3)
                .addInput("Rotation", EnhancedSocketType.FLOAT)
                .addOutput("UV", EnhancedSocketType.VEC3)
                .build());

        // Mapping Node
        registry.put(NodeType.MAPPING, new NodeMetadata.Builder(NodeType.MAPPING)
                .addInput("Vector", EnhancedSocketType.VEC3)
                .addInput("Location", EnhancedSocketType.VEC3)
                .addInput("Rotation", EnhancedSocketType.VEC3)
                .addInput("Scale", EnhancedSocketType.VEC3)
                .addOutput("Vector", EnhancedSocketType.VEC3)
                .build());
    }

    private static void registerUtilityNodes() {
        // Normal Map Node
        registry.put(NodeType.NORMAL_MAP, new NodeMetadata.Builder(NodeType.NORMAL_MAP)
                .addInput("Normal Map", EnhancedSocketType.SAMPLER2D)
                .addInput("UV", EnhancedSocketType.VEC2)
                .addInput("Strength", EnhancedSocketType.FLOAT)
                .addOutput("Normal", EnhancedSocketType.VEC3)
                .build());
    }

    private static void registerOutputNode() {
        // Material Output Node
        registry.put(NodeType.MATERIAL_OUTPUT, new NodeMetadata.Builder(NodeType.MATERIAL_OUTPUT)
                .addInput("Base Color", EnhancedSocketType.VEC3)
                .addInput("Metallic", EnhancedSocketType.FLOAT)
                .addInput("Roughness", EnhancedSocketType.FLOAT)
                .addInput("Normal", EnhancedSocketType.VEC3)
                .addInput("Emissive", EnhancedSocketType.VEC3)
                .addInput("Alpha", EnhancedSocketType.FLOAT)
                .build());
    }

    /**
     * Get metadata for a node type
     */
    public static NodeMetadata getMetadata(NodeType type) {
        NodeMetadata metadata = registry.get(type);
        if (metadata == null) {
            DebugManager.warn(DebugCategory.LOOKDEV,
                "[NodeRegistry] No metadata found for node type: {}", type);
        }
        return metadata;
    }

    /**
     * Check if a node type is registered
     */
    public static boolean isRegistered(NodeType type) {
        boolean registered = registry.containsKey(type);
        if (!registered) {
            DebugManager.debug(DebugCategory.LOOKDEV,
                "[NodeRegistry] Node type not registered: {}", type);
        }
        return registered;
    }

    /**
     * Get all registered node types
     */
    public static java.util.List<NodeType> getAllNodeTypes() {
        return new java.util.ArrayList<>(registry.keySet());
    }

    /**
     * Get all registered node types as strings
     */
    public static java.util.List<String> getAllNodeTypeNames() {
        java.util.List<String> names = new java.util.ArrayList<>();
        for (NodeType type : registry.keySet()) {
            names.add(type.name());
        }
        return names;
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

            public Builder addInput(String name, EnhancedSocketType socketType) {
                inputs.add(new SocketDefinition(name, socketType));
                return this;
            }

            public Builder addOutput(String name, EnhancedSocketType socketType) {
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
        private final EnhancedSocketType type;

        public SocketDefinition(String name, EnhancedSocketType type) {
            this.name = name;
            this.type = type;
        }

        public String getName() {
            return name;
        }

        public EnhancedSocketType getType() {
            return type;
        }
    }
}
