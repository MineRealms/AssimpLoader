package cn.minerealms.assimploader.core;

import org.lwjgl.assimp.AIScene;

import static org.lwjgl.assimp.Assimp.*;

/**
 * Import mode for 3D models.
 *
 * STATIC: Bakes all node transformations into vertices using aiProcess_PreTransformVertices.
 *         Best for static models that don't need runtime animation.
 *         Higher performance, lower memory usage.
 *
 * DYNAMIC: Preserves scene graph structure for runtime manipulation.
 *          Required for models with animations or parts that need individual control.
 *          Slightly slower rendering, but enables runtime transformations.
 */
public enum ModelImportMode {
    /**
     * Static mode - bake all transformations into vertices.
     * Use for models that don't need runtime animation.
     */
    STATIC,

    /**
     * Dynamic mode - preserve scene graph for runtime control.
     * Use for animated models or models with controllable parts.
     */
    DYNAMIC;

    /**
     * Get the Assimp import flags for this mode.
     */
    public int getImportFlags() {
        return switch (this) {
            case STATIC -> aiProcess_Triangulate |
                          aiProcess_GenSmoothNormals |
                          aiProcess_FlipUVs |
                          aiProcess_PreTransformVertices;  // Bake transformations
            case DYNAMIC -> aiProcess_Triangulate |
                           aiProcess_GenSmoothNormals |
                           aiProcess_FlipUVs;  // Preserve scene graph
        };
    }

    /**
     * Automatically detect the best import mode based on scene characteristics.
     *
     * Heuristics:
     * - If scene has animations -> DYNAMIC
     * - If scene has complex node hierarchy (>3 levels) -> DYNAMIC
     * - If scene has many nodes with transformations -> DYNAMIC
     * - Otherwise -> STATIC
     *
     * Note: This is a best-guess. Explicit mode specification is recommended.
     */
    public static ModelImportMode detectFromScene(AIScene aiScene) {
        // If scene has animations, definitely use DYNAMIC
        if (aiScene.mNumAnimations() > 0) {
            return DYNAMIC;
        }

        // If scene has complex hierarchy, likely needs DYNAMIC
        // (Simple models usually have flat structure)
        int nodeDepth = calculateNodeDepth(aiScene.mRootNode(), 0);
        if (nodeDepth > 3) {
            return DYNAMIC;
        }

        // Count nodes with non-identity transformations
        int transformedNodes = countTransformedNodes(aiScene.mRootNode());
        if (transformedNodes > 5) {
            return DYNAMIC;
        }

        // Default to STATIC for simple models
        return STATIC;
    }

    private static int calculateNodeDepth(org.lwjgl.assimp.AINode node, int currentDepth) {
        if (node == null) {
            return currentDepth;
        }

        int maxChildDepth = currentDepth;
        org.lwjgl.PointerBuffer children = node.mChildren();
        if (children != null) {
            for (int i = 0; i < node.mNumChildren(); i++) {
                org.lwjgl.assimp.AINode child = org.lwjgl.assimp.AINode.create(children.get(i));
                int childDepth = calculateNodeDepth(child, currentDepth + 1);
                maxChildDepth = Math.max(maxChildDepth, childDepth);
            }
        }

        return maxChildDepth;
    }

    private static int countTransformedNodes(org.lwjgl.assimp.AINode node) {
        if (node == null) {
            return 0;
        }

        int count = 0;

        // Check if this node has non-identity transformation
        org.lwjgl.assimp.AIMatrix4x4 transform = node.mTransformation();
        if (!isIdentityMatrix(transform)) {
            count++;
        }

        // Recursively count children
        org.lwjgl.PointerBuffer children = node.mChildren();
        if (children != null) {
            for (int i = 0; i < node.mNumChildren(); i++) {
                org.lwjgl.assimp.AINode child = org.lwjgl.assimp.AINode.create(children.get(i));
                count += countTransformedNodes(child);
            }
        }

        return count;
    }

    private static boolean isIdentityMatrix(org.lwjgl.assimp.AIMatrix4x4 matrix) {
        float epsilon = 0.0001f;
        return Math.abs(matrix.a1() - 1.0f) < epsilon &&
               Math.abs(matrix.b2() - 1.0f) < epsilon &&
               Math.abs(matrix.c3() - 1.0f) < epsilon &&
               Math.abs(matrix.d4() - 1.0f) < epsilon &&
               Math.abs(matrix.a2()) < epsilon &&
               Math.abs(matrix.a3()) < epsilon &&
               Math.abs(matrix.a4()) < epsilon &&
               Math.abs(matrix.b1()) < epsilon &&
               Math.abs(matrix.b3()) < epsilon &&
               Math.abs(matrix.b4()) < epsilon &&
               Math.abs(matrix.c1()) < epsilon &&
               Math.abs(matrix.c2()) < epsilon &&
               Math.abs(matrix.c4()) < epsilon &&
               Math.abs(matrix.d1()) < epsilon &&
               Math.abs(matrix.d2()) < epsilon &&
               Math.abs(matrix.d3()) < epsilon;
    }
}
