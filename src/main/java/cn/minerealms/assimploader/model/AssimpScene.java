package cn.minerealms.assimploader.model;

import cn.minerealms.assimploader.core.ModelImportMode;
import cn.minerealms.assimploader.debug.DebugManager;
import cn.minerealms.assimploader.debug.DebugManager.DebugCategory;
import cn.minerealms.assimploader.util.CoordinateSystemConverter;
import org.lwjgl.PointerBuffer;
import org.lwjgl.assimp.AIAnimation;
import org.lwjgl.assimp.AIMaterial;
import org.lwjgl.assimp.AIMesh;
import org.lwjgl.assimp.AINode;
import org.lwjgl.assimp.AIScene;
import org.lwjgl.assimp.AITexture;
import org.lwjgl.assimp.Assimp;

import javax.annotation.Nullable;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Fully materialised, Java-side copy of an Assimp scene.
 *
 * <p>The native {@code aiScene} is consumed in the constructor and released immediately
 * afterwards. Nothing in this class (or in {@link AssimpMesh}, {@link AssimpMaterial},
 * {@link AssimpAnimation}, {@link AssimpTexture}, {@link AssimpNode}) points into native
 * memory, which is what makes it safe to keep models cached for the lifetime of the game.
 */
public class AssimpScene {

    private final Path sourcePath;
    private final List<AssimpMesh> meshes;
    private final List<AssimpMaterial> materials;
    private final List<AssimpAnimation> animations;
    private final List<AssimpTexture> textures;
    private final AssimpNode rootNode;
    private final CoordinateSystemConverter.CoordinateSystem sourceCoordinateSystem;
    private final ModelImportMode importMode;
    private final Map<Integer, String> meshIndexToNodeName;
    private final boolean skinned;

    /**
     * @param aiScene native scene; ownership is transferred and it is released before this
     *                constructor returns
     */
    public AssimpScene(AIScene aiScene, @Nullable Path sourcePath,
                       CoordinateSystemConverter.CoordinateSystem coordinateSystem,
                       ModelImportMode importMode) {
        this.sourcePath = sourcePath;
        this.sourceCoordinateSystem = coordinateSystem;
        this.importMode = importMode;

        List<AssimpMesh> meshList = new ArrayList<>();
        List<AssimpMaterial> materialList = new ArrayList<>();
        List<AssimpAnimation> animationList = new ArrayList<>();
        List<AssimpTexture> textureList = new ArrayList<>();
        AssimpNode root = null;

        try {
            int meshCount = aiScene.mNumMeshes();
            if (meshCount > 0) {
                PointerBuffer buffer = aiScene.mMeshes();
                for (int i = 0; i < meshCount; i++) {
                    meshList.add(new AssimpMesh(AIMesh.create(buffer.get(i)), i, coordinateSystem));
                }
            }

            int materialCount = aiScene.mNumMaterials();
            if (materialCount > 0) {
                PointerBuffer buffer = aiScene.mMaterials();
                for (int i = 0; i < materialCount; i++) {
                    materialList.add(new AssimpMaterial(AIMaterial.create(buffer.get(i)), i));
                }
            }

            int animationCount = aiScene.mNumAnimations();
            if (animationCount > 0) {
                PointerBuffer buffer = aiScene.mAnimations();
                for (int i = 0; i < animationCount; i++) {
                    animationList.add(new AssimpAnimation(AIAnimation.create(buffer.get(i)), i, coordinateSystem));
                }
            }

            int textureCount = aiScene.mNumTextures();
            if (textureCount > 0) {
                PointerBuffer buffer = aiScene.mTextures();
                for (int i = 0; i < textureCount; i++) {
                    textureList.add(new AssimpTexture(AITexture.create(buffer.get(i)), i));
                }
            }

            AINode aiRoot = aiScene.mRootNode();
            if (aiRoot != null) {
                root = new AssimpNode(aiRoot);
            }
        } finally {
            // Everything above has been copied into Java objects - the native scene is dead weight.
            Assimp.aiReleaseImport(aiScene);
        }

        this.meshes = Collections.unmodifiableList(meshList);
        this.materials = Collections.unmodifiableList(materialList);
        this.animations = Collections.unmodifiableList(animationList);
        this.textures = Collections.unmodifiableList(textureList);
        this.rootNode = root;

        this.meshIndexToNodeName = new HashMap<>();
        if (root != null) {
            buildMeshToNodeMapping(root);
        }

        boolean anySkinned = false;
        for (AssimpMesh mesh : meshes) {
            if (mesh.hasBones()) {
                anySkinned = true;
                break;
            }
        }
        this.skinned = anySkinned;

        DebugManager.debug(DebugCategory.CORE,
                "AssimpScene ready: {} meshes, {} materials, {} animations, {} textures, skinned={}",
                meshes.size(), materials.size(), animations.size(), textures.size(), skinned);
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

    public boolean hasAnimations() {
        return !animations.isEmpty();
    }

    /** True when at least one mesh carries bone weights, i.e. GPU skinning can be used. */
    public boolean hasSkeleton() {
        return skinned;
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

    public ModelImportMode getImportMode() {
        return importMode;
    }

    public boolean isDynamic() {
        return importMode == ModelImportMode.DYNAMIC;
    }

    @Nullable
    public AssimpNode findNodeByName(String name) {
        return rootNode == null ? null : rootNode.find(name);
    }

    private void buildMeshToNodeMapping(AssimpNode node) {
        for (int meshIndex : node.getMeshIndices()) {
            meshIndexToNodeName.put(meshIndex, node.getName());
        }
        for (AssimpNode child : node.getChildren()) {
            buildMeshToNodeMapping(child);
        }
    }

    @Nullable
    public String getNodeNameForMesh(AssimpMesh mesh) {
        return meshIndexToNodeName.get(mesh.getIndex());
    }

    /** First animation clip, or {@code null}. */
    @Nullable
    public AssimpAnimation getDefaultAnimation() {
        return animations.isEmpty() ? null : animations.get(0);
    }
}
