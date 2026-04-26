package cn.minerealms.assimploader.lookdev.nodeeditor;

/**
 * Type converter for automatic socket type conversions.
 *
 * Handles conversions between compatible socket types:
 * - FLOAT → VEC2, VEC3, VEC4 (broadcast/splat)
 * - VEC3 → VEC4 (add alpha = 1.0)
 * - VEC4 → VEC3 (drop alpha)
 *
 * Used during connection validation and shader code generation.
 */
public class TypeConverter {

    /**
     * Check if a value can be converted from source type to target type
     */
    public static boolean canConvert(EnhancedSocketType source, EnhancedSocketType target) {
        return source.canConvertTo(target);
    }

    /**
     * Convert a value from source type to target type
     *
     * @param value The value to convert
     * @param source The source type
     * @param target The target type
     * @return The converted value, or null if conversion is not possible
     */
    public static Object convert(Object value, EnhancedSocketType source, EnhancedSocketType target) {
        if (value == null) {
            return null;
        }

        // No conversion needed
        if (source == target) {
            return value;
        }

        // FLOAT → VEC2, VEC3, VEC4 (broadcast)
        if (source == EnhancedSocketType.FLOAT) {
            float f = ((Number) value).floatValue();

            if (target == EnhancedSocketType.VEC2) {
                return new float[]{f, f};
            } else if (target == EnhancedSocketType.VEC3) {
                return new float[]{f, f, f};
            } else if (target == EnhancedSocketType.VEC4) {
                return new float[]{f, f, f, f};
            }
        }

        // VEC3 → VEC4 (add alpha = 1.0)
        if (source == EnhancedSocketType.VEC3 && target == EnhancedSocketType.VEC4) {
            float[] vec3 = (float[]) value;
            return new float[]{vec3[0], vec3[1], vec3[2], 1.0f};
        }

        // VEC4 → VEC3 (drop alpha)
        if (source == EnhancedSocketType.VEC4 && target == EnhancedSocketType.VEC3) {
            float[] vec4 = (float[]) value;
            return new float[]{vec4[0], vec4[1], vec4[2]};
        }

        // Conversion not supported
        return null;
    }

    /**
     * Generate GLSL code for type conversion
     *
     * @param varName The variable name to convert
     * @param source The source type
     * @param target The target type
     * @return GLSL code string, or the original varName if no conversion needed
     */
    public static String generateGLSLConversion(String varName, EnhancedSocketType source, EnhancedSocketType target) {
        if (source == target) {
            return varName;
        }

        // FLOAT → VEC2, VEC3, VEC4
        if (source == EnhancedSocketType.FLOAT) {
            if (target == EnhancedSocketType.VEC2) {
                return "vec2(" + varName + ")";
            } else if (target == EnhancedSocketType.VEC3) {
                return "vec3(" + varName + ")";
            } else if (target == EnhancedSocketType.VEC4) {
                return "vec4(" + varName + ")";
            }
        }

        // VEC3 → VEC4
        if (source == EnhancedSocketType.VEC3 && target == EnhancedSocketType.VEC4) {
            return "vec4(" + varName + ", 1.0)";
        }

        // VEC4 → VEC3
        if (source == EnhancedSocketType.VEC4 && target == EnhancedSocketType.VEC3) {
            return varName + ".rgb";
        }

        // No conversion available
        return varName;
    }

    /**
     * Get default value for a socket type
     */
    public static Object getDefaultValue(EnhancedSocketType type) {
        switch (type) {
            case FLOAT:
                return 0.0f;
            case VEC2:
                return new float[]{0.0f, 0.0f};
            case VEC3:
                return new float[]{0.0f, 0.0f, 0.0f};
            case VEC4:
                return new float[]{0.0f, 0.0f, 0.0f, 1.0f};
            case SAMPLER2D:
                return null; // Textures don't have default values
            default:
                return null;
        }
    }
}
