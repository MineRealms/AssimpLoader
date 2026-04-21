package cn.minerealms.assimploader.model;

import cn.minerealms.assimploader.AssimpLoader;
import cn.minerealms.assimploader.util.CoordinateSystemConverter;
import org.lwjgl.PointerBuffer;
import org.lwjgl.assimp.*;

import javax.annotation.Nullable;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

/**
 * Wrapper for Assimp AIScene containing all model data.
 *
 * This class holds the native Assimp scene and provides convenient access
 * to meshes, materials, animations, and the scene graph.
 *
 * IMPORTANT: Must call release() when done to free native memory!
 */
public class AssimpScene {

    private final AIScene aiScene;
    private final Path sourcePath;
    private final List<AssimpMesh> meshes;
    private final List<AssimpMaterial> materials;
    private final List<AssimpAnimation> animations;
    private final AssimpNode rootNode;
    private final CoordinateSystemConverter.CoordinateSystem sourceCoordinateSystem;

    private boolean released = false;

    public AssimpScene(AIScene aiScene, @Nullable Path sourcePath, CoordinateSystemConverter.CoordinateSystem coordinateSystem) {
        this.aiScene = aiScene;
        this.sourcePath = sourcePath;
        this.sourceCoordinateSystem = coordinateSystem;

        // Parse meshes
        this.meshes = new ArrayList<>();
        int meshCount = aiScene.mNumMeshes();
        if (meshCount > 0) {
            PointerBuffer meshesBuffer = aiScene.mMeshes();
            for (int i = 0; i < meshCount; i++) {
                AIMesh aiMesh = AIMesh.create(meshesBuffer.get(i));
                meshes.add(new AssimpMesh(aiMesh, i, coordinateSystem));
            }
        }

        // Parse materials
        this.materials = new ArrayList<>();
        int materialCount = aiScene.mNumMaterials();
        if (materialCount > 0) {
            PointerBuffer materialsBuffer = aiScene.mMaterials();
            for (int i = 0; i < materialCount; i++) {
                AIMaterial aiMaterial = AIMaterial.create(materialsBuffer.get(i));
                materials.add(new AssimpMaterial(aiMaterial, i));
            }
        }

        // Parse animations
        this.animations = new ArrayList<>();
        int animCount = aiScene.mNumAnimations();
        if (animCount > 0) {
            PointerBuffer animsBuffer = aiScene.mAnimations();
            for (int i = 0; i < animCount; i++) {
                AIAnimation aiAnim = AIAnimation.create(animsBuffer.get(i));
                animations.add(new AssimpAnimation(aiAnim, i));
            }
        }

        // Parse scene graph
        AINode aiRootNode = aiScene.mRootNode();
        this.rootNode = aiRootNode != null ? new AssimpNode(aiRootNode) : null;

        AssimpLoader.LOGGER.debug("AssimpScene created: {} meshes, {} materials, {} animations, coordinate system: {}",
                meshes.size(), materials.size(), animations.size(), coordinateSystem);
    }

    public List<AssimpMesh> getMeshes() {
        return meshes;
    }

    public List<AssimpMaterial> getMaterials() {
        return materials;
    }

    public List<AssimpAnimation> getAnimations() {
        return animations;
    }

    @Nullable
    public AssimpNode getRootNode() {
        return rootNode;
    }

    @Nullable
    public Path getSourcePath() {
        return sourcePath;
    }

    public CoordinateSystemConverter.CoordinateSystem getSourceCoordinateSystem() {
        return sourceCoordinateSystem;
    }

    public AIScene getNativeScene() {
        return aiScene;
    }

    public boolean hasAnimations() {
        return !animations.isEmpty();
    }

    public int getMeshCount() {
        return meshes.size();
    }

    public int getMaterialCount() {
        return materials.size();
    }

    public int getAnimationCount() {
        return animations.size();
    }

    /**
     * Release native Assimp memory.
     * MUST be called when done with this scene!
     */
    public void release() {
        if (!released) {
            Assimp.aiReleaseImport(aiScene);
            released = true;
            AssimpLoader.LOGGER.debug("AssimpScene released");
        }
    }

    @Override
    protected void finalize() throws Throwable {
        if (!released) {
            AssimpLoader.LOGGER.warn("AssimpScene was not properly released! Memory leak detected.");
            release();
        }
        super.finalize();
    }
}
