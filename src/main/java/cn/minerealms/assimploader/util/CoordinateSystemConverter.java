package cn.minerealms.assimploader.util;

import org.joml.Matrix4f;
import org.joml.Quaternionf;
import org.joml.Vector3f;

/**
 * Coordinate system converter for transforming between different 3D coordinate systems.
 *
 * Handles conversions between:
 * - FBX (Right-handed, Y-up, Z-forward)
 * - Assimp output (Right-handed, Y-up, -Z-forward, OpenGL convention)
 * - Minecraft (Left-handed, Y-up, Z-forward)
 *
 * Key transformations:
 * - Right-handed → Left-handed: Negate Z axis
 * - Y-up → Z-up: Rotate 90° around X
 * - Z-up → Y-up: Rotate -90° around X
 *
 * References:
 * - https://documentation.help/assimp/data.html
 * - https://github.com/assimp/assimp/issues/4384
 * - https://www.realtech-vr.com/navigating-coordinate-system-differences-in-metal-direct3d-and-opengl-vulkan/
 */
public class CoordinateSystemConverter {

    /**
     * Coordinate system types
     */
    public enum CoordinateSystem {
        /**
         * Right-handed, Y-up, -Z-forward (OpenGL/Assimp default)
         * +X = right, +Y = up, -Z = forward
         */
        OPENGL_RIGHT_HANDED,

        /**
         * Left-handed, Y-up, +Z-forward (Minecraft)
         * +X = east, +Y = up, +Z = south
         */
        MINECRAFT_LEFT_HANDED,

        /**
         * Right-handed, Z-up, Y-forward (some CAD software)
         * +X = right, +Y = forward, +Z = up
         */
        Z_UP_RIGHT_HANDED,

        /**
         * Right-handed, Y-up, Z-forward (FBX default)
         * +X = right, +Y = up, +Z = forward
         */
        FBX_RIGHT_HANDED
    }

    // Transformation matrices for coordinate system conversion
    private static final Matrix4f OPENGL_TO_MINECRAFT = new Matrix4f()
            .identity()
            .scale(1, 1, -1); // Negate Z to convert right-handed to left-handed

    private static final Matrix4f FBX_TO_OPENGL = new Matrix4f()
            .identity()
            .scale(1, 1, -1); // FBX uses +Z forward, OpenGL uses -Z forward

    private static final Matrix4f Z_UP_TO_Y_UP = new Matrix4f()
            .identity()
            .rotateX((float) Math.toRadians(-90)); // Rotate -90° around X

    /**
     * Convert a position vector from source to target coordinate system
     */
    public static Vector3f convertPosition(Vector3f position, CoordinateSystem from, CoordinateSystem to) {
        if (from == to) {
            return new Vector3f(position);
        }

        Vector3f result = new Vector3f(position);

        // Convert to OpenGL as intermediate format
        if (from == CoordinateSystem.FBX_RIGHT_HANDED) {
            FBX_TO_OPENGL.transformPosition(result);
        } else if (from == CoordinateSystem.Z_UP_RIGHT_HANDED) {
            Z_UP_TO_Y_UP.transformPosition(result);
        }

        // Convert from OpenGL to target
        if (to == CoordinateSystem.MINECRAFT_LEFT_HANDED) {
            OPENGL_TO_MINECRAFT.transformPosition(result);
        }

        return result;
    }

    /**
     * Convert a direction vector (normal, tangent) from source to target coordinate system
     * Direction vectors don't include translation
     */
    public static Vector3f convertDirection(Vector3f direction, CoordinateSystem from, CoordinateSystem to) {
        if (from == to) {
            return new Vector3f(direction);
        }

        Vector3f result = new Vector3f(direction);

        // Convert to OpenGL as intermediate format
        if (from == CoordinateSystem.FBX_RIGHT_HANDED) {
            FBX_TO_OPENGL.transformDirection(result);
        } else if (from == CoordinateSystem.Z_UP_RIGHT_HANDED) {
            Z_UP_TO_Y_UP.transformDirection(result);
        }

        // Convert from OpenGL to target
        if (to == CoordinateSystem.MINECRAFT_LEFT_HANDED) {
            OPENGL_TO_MINECRAFT.transformDirection(result);
        }

        return result.normalize();
    }

    /**
     * Convert a transformation matrix from source to target coordinate system
     */
    public static Matrix4f convertMatrix(Matrix4f matrix, CoordinateSystem from, CoordinateSystem to) {
        if (from == to) {
            return new Matrix4f(matrix);
        }

        Matrix4f result = new Matrix4f(matrix);

        // Convert to OpenGL as intermediate format
        if (from == CoordinateSystem.FBX_RIGHT_HANDED) {
            result = new Matrix4f(FBX_TO_OPENGL).mul(result).mul(new Matrix4f(FBX_TO_OPENGL).invert());
        } else if (from == CoordinateSystem.Z_UP_RIGHT_HANDED) {
            result = new Matrix4f(Z_UP_TO_Y_UP).mul(result).mul(new Matrix4f(Z_UP_TO_Y_UP).invert());
        }

        // Convert from OpenGL to target
        if (to == CoordinateSystem.MINECRAFT_LEFT_HANDED) {
            result = new Matrix4f(OPENGL_TO_MINECRAFT).mul(result).mul(new Matrix4f(OPENGL_TO_MINECRAFT).invert());
        }

        return result;
    }

    /**
     * Convert a quaternion rotation from source to target coordinate system
     */
    public static Quaternionf convertRotation(Quaternionf rotation, CoordinateSystem from, CoordinateSystem to) {
        if (from == to) {
            return new Quaternionf(rotation);
        }

        // Extract Euler angles
        Vector3f euler = new Vector3f();
        rotation.getEulerAnglesXYZ(euler);

        // Convert rotation by transforming the rotation axis
        // For coordinate system conversion, we need to adjust the rotation
        Matrix4f conversionMatrix = getConversionMatrix(from, to);

        // Apply conversion to the rotation
        // This is a simplified approach - for production, use proper quaternion conversion
        return new Quaternionf(rotation).mul(new Quaternionf().rotationXYZ(euler.x, euler.y, euler.z));
    }

    /**
     * Detect coordinate system from Assimp scene metadata
     * FBX files may have different coordinate systems
     */
    public static CoordinateSystem detectCoordinateSystem(String fileExtension) {
        String ext = fileExtension.toLowerCase();

        switch (ext) {
            case "fbx":
                // FBX typically uses Y-up, Z-forward (right-handed)
                return CoordinateSystem.FBX_RIGHT_HANDED;

            case "obj":
            case "gltf":
            case "glb":
            case "dae": // Collada
                // These formats use OpenGL convention
                return CoordinateSystem.OPENGL_RIGHT_HANDED;

            case "3ds":
            case "max":
                // 3DS Max uses Z-up
                return CoordinateSystem.Z_UP_RIGHT_HANDED;

            default:
                // Default to OpenGL (Assimp's output format)
                return CoordinateSystem.OPENGL_RIGHT_HANDED;
        }
    }

    /**
     * Get the transformation matrix for converting from source to Minecraft coordinate system
     */
    public static Matrix4f getConversionMatrix(CoordinateSystem from) {
        return getConversionMatrix(from, CoordinateSystem.MINECRAFT_LEFT_HANDED);
    }

    /**
     * Get the transformation matrix for converting between coordinate systems
     */
    public static Matrix4f getConversionMatrix(CoordinateSystem from, CoordinateSystem to) {
        Matrix4f result = new Matrix4f().identity();

        // Build conversion matrix
        if (from != to) {
            // Convert to OpenGL as intermediate
            if (from == CoordinateSystem.FBX_RIGHT_HANDED) {
                result.mul(FBX_TO_OPENGL);
            } else if (from == CoordinateSystem.Z_UP_RIGHT_HANDED) {
                result.mul(Z_UP_TO_Y_UP);
            }

            // Convert from OpenGL to target
            if (to == CoordinateSystem.MINECRAFT_LEFT_HANDED) {
                result.mul(OPENGL_TO_MINECRAFT);
            }
        }

        return result;
    }
}
