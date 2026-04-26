package cn.minerealms.assimploader.lookdev.nodeeditor;

/**
 * Enhanced socket implementation that uses the new EnhancedSocketType enum.
 *
 * This class bridges the old NodeSocket.SocketType and the new EnhancedSocketType enum,
 * providing proper type checking and conversion support.
 *
 * All concrete node implementations should use this class for their sockets.
 */
public class EnhancedNodeSocket extends NodeSocket {
    private final EnhancedSocketType enhancedType;

    public EnhancedNodeSocket(String name, EnhancedSocketType type) {
        super(name, mapToLegacyType(type));
        this.enhancedType = type;
    }

    public EnhancedNodeSocket(String name, EnhancedSocketType type, Object defaultValue) {
        super(name, mapToLegacyType(type), defaultValue);
        this.enhancedType = type;
    }

    @Override
    protected EnhancedSocketType getEnhancedSocketType() {
        return enhancedType;
    }

    @Override
    public int getColor() {
        return enhancedType.getColor();
    }

    /**
     * Map new EnhancedSocketType to legacy NodeSocket.SocketType for compatibility
     */
    private static NodeSocket.SocketType mapToLegacyType(EnhancedSocketType type) {
        if (type == EnhancedSocketType.FLOAT) {
            return NodeSocket.SocketType.FLOAT;
        } else if (type == EnhancedSocketType.VEC2) {
            return NodeSocket.SocketType.VECTOR;
        } else if (type == EnhancedSocketType.VEC3 || type == EnhancedSocketType.VEC4) {
            return NodeSocket.SocketType.COLOR;
        } else if (type == EnhancedSocketType.SAMPLER2D) {
            return NodeSocket.SocketType.TEXTURE;
        } else {
            return NodeSocket.SocketType.ANY;
        }
    }
}
