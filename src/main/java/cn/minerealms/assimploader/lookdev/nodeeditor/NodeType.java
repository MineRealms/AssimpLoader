package cn.minerealms.assimploader.lookdev.nodeeditor;

/**
 * Enumeration of all node types in the node editor.
 *
 * Each node type represents a specific operation or data source:
 * - Input nodes: Provide data (textures, values, colors, UV coordinates)
 * - Math nodes: Perform operations (multiply, add, lerp)
 * - Utility nodes: Special operations (normal mapping)
 * - Output node: Final material output (required, only one per graph)
 */
public enum NodeType {
    // Input Nodes
    TEXTURE_SAMPLE("Texture Sample", NodeCategory.INPUT),
    VALUE("Value", NodeCategory.INPUT),
    COLOR("Color", NodeCategory.INPUT),
    UV_COORDINATE("UV Coordinate", NodeCategory.INPUT),

    // Math Nodes
    MULTIPLY("Multiply", NodeCategory.MATH),
    ADD("Add", NodeCategory.MATH),
    SUBTRACT("Subtract", NodeCategory.MATH),
    DIVIDE("Divide", NodeCategory.MATH),
    LERP("Lerp", NodeCategory.MATH),
    CLAMP("Clamp", NodeCategory.MATH),
    MATH_FUNCTION("Math Function", NodeCategory.MATH),

    // Vector Nodes
    SPLIT("Split", NodeCategory.VECTOR),
    COMBINE("Combine", NodeCategory.VECTOR),
    VECTOR_MATH("Vector Math", NodeCategory.VECTOR),

    // Texture Nodes
    TEXTURE_COORDINATE("Texture Coordinate", NodeCategory.TEXTURE),
    MAPPING("Mapping", NodeCategory.TEXTURE),

    // Utility Nodes
    NORMAL_MAP("Normal Map", NodeCategory.UTILITY),

    // Output Node
    MATERIAL_OUTPUT("Material Output", NodeCategory.OUTPUT);

    private final String displayName;
    private final NodeCategory category;

    NodeType(String displayName, NodeCategory category) {
        this.displayName = displayName;
        this.category = category;
    }

    /**
     * Get human-readable display name
     */
    public String getDisplayName() {
        return displayName;
    }

    /**
     * Get node category for UI organization
     */
    public NodeCategory getCategory() {
        return category;
    }

    /**
     * Node categories for organizing the node palette
     */
    public enum NodeCategory {
        INPUT("Input"),
        MATH("Math"),
        VECTOR("Vector"),
        TEXTURE("Texture"),
        UTILITY("Utility"),
        OUTPUT("Output");

        private final String displayName;

        NodeCategory(String displayName) {
            this.displayName = displayName;
        }

        public String getDisplayName() {
            return displayName;
        }
    }
}
