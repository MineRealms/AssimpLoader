package cn.minerealms.assimploader.lookdev.nodeeditor;

/**
 * Socket for node input/output connections.
 *
 * Sockets have:
 * - Name (e.g., "Color", "Value", "Texture")
 * - Type (determines connection compatibility)
 * - Color (visual indicator)
 * - Value (current data)
 */
public class NodeSocket {

    public enum SocketType {
        FLOAT(0xFFFFAA00),      // Orange
        COLOR(0xFFFF00FF),      // Magenta
        TEXTURE(0xFF00FF00),    // Green
        VECTOR(0xFF0088FF),     // Blue
        ANY(0xFFAAAAAA);        // Gray

        private final int color;

        SocketType(int color) {
            this.color = color;
        }

        public int getColor() {
            return color;
        }
    }

    private String name;
    private SocketType type;
    private Object value;
    private Node node;
    private boolean isOutput;

    // Connection
    private NodeSocket connectedTo;

    public NodeSocket(String name, SocketType type) {
        this.name = name;
        this.type = type;
    }

    public NodeSocket(String name, SocketType type, Object defaultValue) {
        this.name = name;
        this.type = type;
        this.value = defaultValue;
    }

    /**
     * Check if this socket can connect to another
     *
     * Enhanced version that supports type conversion via TypeConverter
     */
    public boolean canConnectTo(NodeSocket other) {
        if (other == null) return false;
        if (this.isOutput == other.isOutput) return false; // Must be input-output pair
        if (this.node == other.node) return false; // Cannot connect to same node

        // Try to get enhanced socket types for better type checking
        EnhancedSocketType thisEnhancedType = getEnhancedSocketType();
        EnhancedSocketType otherEnhancedType = other.getEnhancedSocketType();

        // If both have enhanced types, use enhanced type checking
        if (thisEnhancedType != null && otherEnhancedType != null) {
            return thisEnhancedType.isCompatibleWith(otherEnhancedType);
        }

        // Fallback to legacy type compatibility
        if (this.type == SocketType.ANY || other.type == SocketType.ANY) {
            return true;
        }
        return this.type == other.type;
    }

    /**
     * Get enhanced socket type if this socket supports it
     * Override in subclasses that use the new EnhancedSocketType enum
     */
    protected EnhancedSocketType getEnhancedSocketType() {
        return null; // Default: no enhanced type
    }

    /**
     * Connect to another socket
     */
    public boolean connectTo(NodeSocket other) {
        if (!canConnectTo(other)) {
            return false;
        }

        // Disconnect existing connections
        disconnect();
        other.disconnect();

        // Create bidirectional connection
        this.connectedTo = other;
        other.connectedTo = this;

        return true;
    }

    /**
     * Disconnect from connected socket
     */
    public void disconnect() {
        if (connectedTo != null) {
            NodeSocket other = connectedTo;
            this.connectedTo = null;
            other.connectedTo = null;
        }
    }

    /**
     * Get value from this socket (or from connected socket if input)
     */
    public Object getValue() {
        if (!isOutput && connectedTo != null) {
            // Input socket: get value from connected output
            return connectedTo.getValue();
        }
        return value;
    }

    /**
     * Set value for this socket
     */
    public void setValue(Object value) {
        this.value = value;
    }

    // Getters and setters

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    public SocketType getType() {
        return type;
    }

    public void setType(SocketType type) {
        this.type = type;
    }

    public int getColor() {
        return type.getColor();
    }

    public Node getNode() {
        return node;
    }

    public void setNode(Node node) {
        this.node = node;
    }

    public boolean isOutput() {
        return isOutput;
    }

    public void setIsOutput(boolean isOutput) {
        this.isOutput = isOutput;
    }

    public NodeSocket getConnectedTo() {
        return connectedTo;
    }

    public boolean isConnected() {
        return connectedTo != null;
    }
}
