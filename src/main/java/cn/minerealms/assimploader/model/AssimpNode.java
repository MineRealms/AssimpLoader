package cn.minerealms.assimploader.model;

import org.joml.Matrix4f;
import org.lwjgl.assimp.AIMatrix4x4;
import org.lwjgl.assimp.AINode;

import java.util.ArrayList;
import java.util.List;

/**
 * Wrapper for Assimp AINode representing scene graph hierarchy.
 *
 * Each node has:
 * - Transformation matrix
 * - List of mesh indices
 * - Parent-child relationships
 */
public class AssimpNode {

    private final AINode aiNode;
    private final String name;
    private final Matrix4f transformation;
    private final int[] meshIndices;
    private final List<AssimpNode> children;

    public AssimpNode(AINode aiNode) {
        this.aiNode = aiNode;
        this.name = aiNode.mName().dataString();

        // Convert transformation matrix
        AIMatrix4x4 aiMatrix = aiNode.mTransformation();
        this.transformation = new Matrix4f(
                aiMatrix.a1(), aiMatrix.a2(), aiMatrix.a3(), aiMatrix.a4(),
                aiMatrix.b1(), aiMatrix.b2(), aiMatrix.b3(), aiMatrix.b4(),
                aiMatrix.c1(), aiMatrix.c2(), aiMatrix.c3(), aiMatrix.c4(),
                aiMatrix.d1(), aiMatrix.d2(), aiMatrix.d3(), aiMatrix.d4()
        );

        // Get mesh indices
        int numMeshes = aiNode.mNumMeshes();
        this.meshIndices = new int[numMeshes];
        if (numMeshes > 0) {
            aiNode.mMeshes().get(meshIndices);
        }

        // Parse children recursively
        this.children = new ArrayList<>();
        int numChildren = aiNode.mNumChildren();
        if (numChildren > 0) {
            for (int i = 0; i < numChildren; i++) {
                AINode childNode = AINode.create(aiNode.mChildren().get(i));
                children.add(new AssimpNode(childNode));
            }
        }
    }

    public String getName() {
        return name;
    }

    public Matrix4f getTransformation() {
        return transformation;
    }

    public int[] getMeshIndices() {
        return meshIndices;
    }

    public List<AssimpNode> getChildren() {
        return children;
    }

    public AINode getNativeNode() {
        return aiNode;
    }
}
