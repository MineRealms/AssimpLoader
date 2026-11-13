package cn.minerealms.assimploader.model;

import org.joml.Matrix4f;
import org.lwjgl.assimp.AINode;
import cn.minerealms.assimploader.util.CoordinateSystemConverter;

import java.util.ArrayList;
import java.util.List;

/**
 * Immutable snapshot of one node of the Assimp scene graph.
 *
 * <p>Copied out of native memory at construction time, so it stays valid after the
 * {@code aiScene} has been released.
 */
public class AssimpNode {

    private final String name;
    private final Matrix4f transformation;
    private final int[] meshIndices;
    private final List<AssimpNode> children;

    public AssimpNode(AINode aiNode) {
        this(aiNode, CoordinateSystemConverter.CoordinateSystem.OPENGL_RIGHT_HANDED);
    }

    public AssimpNode(AINode aiNode, CoordinateSystemConverter.CoordinateSystem coordinateSystem) {
        this.name = aiNode.mName().dataString();

        this.transformation = CoordinateSystemConverter.convertMatrix(aiNode.mTransformation(),
                coordinateSystem, CoordinateSystemConverter.CoordinateSystem.MINECRAFT_LEFT_HANDED);

        int numMeshes = aiNode.mNumMeshes();
        this.meshIndices = new int[numMeshes];
        if (numMeshes > 0) {
            aiNode.mMeshes().get(meshIndices);
        }

        this.children = new ArrayList<>(aiNode.mNumChildren());
        for (int i = 0; i < aiNode.mNumChildren(); i++) {
            children.add(new AssimpNode(AINode.create(aiNode.mChildren().get(i)), coordinateSystem));
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

    /** Depth-first lookup by node name. */
    public AssimpNode find(String nodeName) {
        if (name.equals(nodeName)) {
            return this;
        }
        for (AssimpNode child : children) {
            AssimpNode found = child.find(nodeName);
            if (found != null) {
                return found;
            }
        }
        return null;
    }
}
