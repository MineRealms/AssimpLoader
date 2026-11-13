package cn.minerealms.assimploader.util;

import org.joml.Matrix4f;
import org.joml.Quaternionf;
import org.joml.Vector3f;
import org.lwjgl.assimp.AIMatrix4x4;

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
    // OpenGL: Right-handed, Y-up, -Z-forward
    // Minecraft: Left-handed, Y-up, +Z-forward
    // FBX: Right-handed, Y-up, +Z-forward

    // OpenGL to Minecraft: flip Z (right-hand to left-hand)
    private static final Matrix4f OPENGL_TO_MINECRAFT = new Matrix4f()
            .identity()
            .scale(1, 1, -1); // Negate Z to convert right-handed to left-handed

    private static final Matrix4f FBX_TO_MINECRAFT = new Matrix4f().scaling(-1, 1, 1);

    private static final Matrix4f Z_UP_TO_Y_UP = new Matrix4f()
            .identity()
            .rotateX((float) Math.toRadians(-90)); // Rotate -90° around X

    private static final Matrix4f[][] CONVERSION_MATRICES = buildConversionMatrices();

    private static Matrix4f[][] buildConversionMatrices() {
        CoordinateSystem[] systems = CoordinateSystem.values();
        Matrix4f[][] matrices = new Matrix4f[systems.length][systems.length];
        for (CoordinateSystem from : systems) {
            for (CoordinateSystem to : systems) {
                matrices[from.ordinal()][to.ordinal()] = toMinecraft(to).invert().mul(toMinecraft(from));
            }
        }
        return matrices;
    }

    /**
     * Convert a position vector from source to target coordinate system
     */
    public static Vector3f convertPosition(Vector3f position, CoordinateSystem from, CoordinateSystem to) {
        return CONVERSION_MATRICES[from.ordinal()][to.ordinal()].transformPosition(new Vector3f(position));
    }

    /**
     * Convert a direction vector (normal, tangent) from source to target coordinate system
     * Direction vectors don't include translation
     */
    public static Vector3f convertDirection(Vector3f direction, CoordinateSystem from, CoordinateSystem to) {
        return CONVERSION_MATRICES[from.ordinal()][to.ordinal()].transformDirection(new Vector3f(direction));
    }

    /**
     * Convert a transformation matrix from source to target coordinate system
     */
    public static Matrix4f convertMatrix(Matrix4f matrix, CoordinateSystem from, CoordinateSystem to) {
        if (from == to) {
            return new Matrix4f(matrix);
        }

        Matrix4f basis = getConversionMatrix(from, to);
        return new Matrix4f(basis).mul(matrix).mul(new Matrix4f(basis).invert());
    }

    /** Copy Assimp's row-major fields into JOML's column-major constructor, then change basis. */
    public static Matrix4f convertMatrix(AIMatrix4x4 matrix, CoordinateSystem from, CoordinateSystem to) {
        Matrix4f copied = new Matrix4f(
                matrix.a1(), matrix.b1(), matrix.c1(), matrix.d1(),
                matrix.a2(), matrix.b2(), matrix.c2(), matrix.d2(),
                matrix.a3(), matrix.b3(), matrix.c3(), matrix.d3(),
                matrix.a4(), matrix.b4(), matrix.c4(), matrix.d4());
        return convertMatrix(copied, from, to);
    }

    /**
     * Convert a quaternion rotation from source to target coordinate system
     */
    public static Quaternionf convertRotation(Quaternionf rotation, CoordinateSystem from, CoordinateSystem to) {
        if (from == to) {
            return new Quaternionf(rotation);
        }

        Matrix4f converted = convertMatrix(new Matrix4f().rotation(rotation), from, to);
        return converted.getNormalizedRotation(new Quaternionf()).normalize();
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
        return new Matrix4f(CONVERSION_MATRICES[from.ordinal()][to.ordinal()]);
    }

    private static Matrix4f toMinecraft(CoordinateSystem system) {
        return switch (system) {
            case MINECRAFT_LEFT_HANDED -> new Matrix4f();
            case OPENGL_RIGHT_HANDED -> new Matrix4f(OPENGL_TO_MINECRAFT);
            case FBX_RIGHT_HANDED -> new Matrix4f(FBX_TO_MINECRAFT);
            case Z_UP_RIGHT_HANDED -> new Matrix4f(OPENGL_TO_MINECRAFT).mul(Z_UP_TO_Y_UP);
        };
    }
}
