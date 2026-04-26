package cn.minerealms.assimploader.lookdev.nodeeditor;

/**
 * Enhanced socket type system for node editor.
 *
 * Defines all data types that can flow through node connections:
 * - FLOAT: Single floating-point value (0.0 - 1.0)
 * - VEC2: 2D vector (UV coordinates, etc.)
 * - VEC3: 3D vector (RGB color, normals, etc.)
 * - VEC4: 4D vector (RGBA color with alpha)
 * - SAMPLER2D: Texture sampler reference
 *
 * Type compatibility and automatic conversions are handled by TypeConverter.
 */
public enum EnhancedSocketType {
    FLOAT(0xFFFFAA00, "float", 1),      // Orange - single value
    VEC2(0xFF00AAFF, "vec2", 2),        // Cyan - 2D vector
    VEC3(0xFFFF00FF, "vec3", 3),        // Magenta - 3D vector (RGB)
    VEC4(0xFFFF0088, "vec4", 4),        // Pink - 4D vector (RGBA)
    SAMPLER2D(0xFF00FF00, "sampler2D", 0); // Green - texture

    private final int color;
    private final String glslType;
    private final int componentCount;

    EnhancedSocketType(int color, String glslType, int componentCount) {
        this.color = color;
        this.glslType = glslType;
        this.componentCount = componentCount;
    }

    /**
     * Get visual color for this socket type
     */
    public int getColor() {
        return color;
    }

    /**
     * Get GLSL type name for shader generation
     */
    public String getGlslType() {
        return glslType;
    }

    /**
     * Get number of components (1 for float, 3 for vec3, etc.)
     */
    public int getComponentCount() {
        return componentCount;
    }

    /**
     * Check if this type can be automatically converted to another type
     */
    public boolean canConvertTo(EnhancedSocketType target) {
        if (this == target) return true;

        // FLOAT can expand to VEC2, VEC3, VEC4 (broadcast)
        if (this == FLOAT && (target == VEC2 || target == VEC3 || target == VEC4)) {
            return true;
        }

        // VEC3 can convert to VEC4 (add alpha=1.0)
        if (this == VEC3 && target == VEC4) {
            return true;
        }

        // VEC4 can convert to VEC3 (drop alpha)
        if (this == VEC4 && target == VEC3) {
            return true;
        }

        return false;
    }

    /**
     * Check if two types are compatible for connection
     */
    public boolean isCompatibleWith(EnhancedSocketType other) {
        return this.canConvertTo(other) || other.canConvertTo(this);
    }
}
