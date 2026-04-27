package cn.minerealms.assimploader.rendering;

/**
 * Vertex format constants for skinned meshes with bone animation support.
 *
 * Layout (interleaved):
 * - Position: vec3 (12 bytes)
 * - Normal: vec3 (12 bytes)
 * - TexCoord: vec2 (8 bytes)
 * - BoneIndices: ivec4 (16 bytes)
 * - BoneWeights: vec4 (16 bytes)
 *
 * Total stride: 64 bytes per vertex
 */
public class SkinnedVertexFormat {

    /**
     * Get the stride (bytes per vertex)
     */
    public static int getStride() {
        return 3 * Float.BYTES + 3 * Float.BYTES + 2 * Float.BYTES + 4 * Integer.BYTES + 4 * Float.BYTES;
    }

    /**
     * Get offset for position attribute (location 0)
     */
    public static int getPositionOffset() {
        return 0;
    }

    /**
     * Get offset for normal attribute (location 1)
     */
    public static int getNormalOffset() {
        return 3 * Float.BYTES;
    }

    /**
     * Get offset for texCoord attribute (location 2)
     */
    public static int getTexCoordOffset() {
        return 6 * Float.BYTES;
    }

    /**
     * Get offset for boneIndices attribute (location 3)
     */
    public static int getBoneIndicesOffset() {
        return 8 * Float.BYTES;
    }

    /**
     * Get offset for boneWeights attribute (location 4)
     */
    public static int getBoneWeightsOffset() {
        return 8 * Float.BYTES + 4 * Integer.BYTES;
    }
}
