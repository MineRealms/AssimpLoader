package cn.minerealms.assimploader.model;

import cn.minerealms.assimploader.AssimpLoader;
import cn.minerealms.assimploader.core.ModelImportMode;
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
    private final List<AssimpTexture> textures;
    private final AssimpNode rootNode;
    private final CoordinateSystemConverter.CoordinateSystem sourceCoordinateSystem;
    private final ModelImportMode importMode;

    private boolean released = false;

    public AssimpScene(AIScene aiScene, @Nullable Path sourcePath, CoordinateSystemConverter.CoordinateSystem coordinateSystem) {
        this(aiScene, sourcePath, coordinateSystem, ModelImportMode.STATIC);
    }

    public AssimpScene(AIScene aiScene, @Nullable Path sourcePath, CoordinateSystemConverter.CoordinateSystem coordinateSystem, ModelImportMode importMode) {
        this.aiScene = aiScene;
        this.sourcePath = sourcePath;
        this.sourceCoordinateSystem = coordinateSystem;
        this.importMode = importMode;

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

        // Parse embedded textures
        this.textures = new ArrayList<>();
        int textureCount = aiScene.mNumTextures();
        if (textureCount > 0) {
            PointerBuffer texturesBuffer = aiScene.mTextures();
            for (int i = 0; i < textureCount; i++) {
                AITexture aiTexture = AITexture.create(texturesBuffer.get(i));
                textures.add(new AssimpTexture(aiTexture, i));
            }
            AssimpLoader.LOGGER.info("Found {} embedded texture(s)", textureCount);
        }

        // Parse scene graph
        AINode aiRootNode = aiScene.mRootNode();
        this.rootNode = aiRootNode != null ? new AssimpNode(aiRootNode) : null;

        AssimpLoader.LOGGER.debug("AssimpScene created: {} meshes, {} materials, {} animations, {} textures, coordinate system: {}",
                meshes.size(), materials.size(), animations.size(), textures.size(), coordinateSystem);
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

    public List<AssimpTexture> getTextures() {
        return textures;
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

    public int getTextureCount() {
        return textures.size();
    }

    public boolean hasEmbeddedTextures() {
        return !textures.isEmpty();
    }

    /**
     * Get the import mode used for this scene.
     */
    public ModelImportMode getImportMode() {
        return importMode;
    }

    /**
     * Check if this scene was loaded in dynamic mode (preserves scene graph).
     */
    public boolean isDynamic() {
        return importMode == ModelImportMode.DYNAMIC;
    }

    /**
     * Find a node by name in the scene graph.
     * Only works in DYNAMIC mode where scene graph is preserved.
     *
     * @param name The name of the node to find
     * @return The node if found, null otherwise
     */
    @Nullable
    public AssimpNode findNodeByName(String name) {
        if (rootNode == null) {
            return null;
        }
        return findNodeByNameRecursive(rootNode, name);
    }

    @Nullable
    private AssimpNode findNodeByNameRecursive(AssimpNode node, String name) {
        if (node.getName().equals(name)) {
            return node;
        }
        for (AssimpNode child : node.getChildren()) {
            AssimpNode found = findNodeByNameRecursive(child, name);
            if (found != null) {
                return found;
            }
        }
        return null;
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
