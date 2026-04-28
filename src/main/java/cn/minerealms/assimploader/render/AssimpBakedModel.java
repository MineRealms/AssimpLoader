package cn.minerealms.assimploader.render;

import cn.minerealms.assimploader.debug.DebugManager;
import cn.minerealms.assimploader.debug.DebugManager.DebugCategory;
import cn.minerealms.assimploader.model.AssimpMaterial;
import cn.minerealms.assimploader.model.AssimpMesh;
import cn.minerealms.assimploader.model.AssimpScene;
import cn.minerealms.assimploader.model.AssimpTexture;
import com.mojang.blaze3d.vertex.PoseStack;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.block.model.BakedQuad;
import net.minecraft.client.renderer.block.model.ItemOverrides;
import net.minecraft.client.renderer.block.model.ItemTransforms;
import net.minecraft.client.renderer.texture.DynamicTexture;
import net.minecraft.client.renderer.texture.TextureAtlasSprite;
import net.minecraft.client.renderer.texture.TextureManager;
import net.minecraft.client.resources.model.BakedModel;
import net.minecraft.core.Direction;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraftforge.client.ChunkRenderTypeSet;
import net.minecraftforge.client.model.data.ModelData;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Custom BakedModel implementation for Assimp-loaded models.
 *
 * This model converts Assimp mesh data into Minecraft's BakedQuad format
 * for rendering in the game world.
 *
 * Phase 3 implementation - Minecraft rendering pipeline adapter
 */
public class AssimpBakedModel implements BakedModel {

    private final AssimpScene scene;
    private final List<BakedQuad> quads;
    private final TextureAtlasSprite particleIcon;
    private final boolean hasTransparency;
    private final Map<Integer, ResourceLocation> embeddedTextureCache;
    private final ResourceLocation modelLocation;

    public AssimpBakedModel(AssimpScene scene, TextureAtlasSprite particleIcon, ResourceLocation modelLocation) {
        this.scene = scene;
        this.particleIcon = particleIcon;
        this.modelLocation = modelLocation;
        this.quads = new ArrayList<>();
        this.embeddedTextureCache = new HashMap<>();
        boolean transparent = false;

        // Log import mode
        DebugManager.info(DebugCategory.RENDER, "Building BakedModel for '{}': mode={}, {} meshes, {} materials",
                modelLocation, scene.getImportMode(), scene.getMeshes().size(), scene.getMaterials().size());

        // Load embedded textures first
        if (scene.hasEmbeddedTextures()) {
            loadEmbeddedTextures();
        }

        // DYNAMIC mode: Return empty quads to force BEWLR rendering for items
        if (scene.getImportMode() == cn.minerealms.assimploader.core.ModelImportMode.DYNAMIC) {
            DebugManager.info(DebugCategory.RENDER,
                "DYNAMIC mode model '{}' - returning empty BakedQuads to force BEWLR rendering",
                modelLocation);
            this.hasTransparency = false;
            return; // Skip quad generation
        }

        // STATIC mode: Convert all meshes to quads
        DebugManager.info(DebugCategory.RENDER, "Building BakedModel: {} meshes, {} materials",
                scene.getMeshes().size(), scene.getMaterials().size());

        for (int i = 0; i < scene.getMeshes().size(); i++) {
            AssimpMesh mesh = scene.getMeshes().get(i);

            // Get material for this mesh
            AssimpMaterial material = null;
            TextureAtlasSprite meshSprite = particleIcon;  // Default to white

            if (mesh.getMaterialIndex() >= 0 && mesh.getMaterialIndex() < scene.getMaterials().size()) {
                material = scene.getMaterials().get(mesh.getMaterialIndex());
                DebugManager.info(DebugCategory.RENDER, "Mesh {} using material index {}", i, mesh.getMaterialIndex());

                // Check if material is transparent
                if (material.isTransparent()) {
                    transparent = true;
                    DebugManager.info(DebugCategory.RENDER, "Material {} is transparent (alpha={})",
                        mesh.getMaterialIndex(), material.getDiffuseColor()[3]);
                }

                // Check for embedded texture
                String texPath = material.getDiffuseTexture();
                if (texPath != null && material.isEmbeddedTexture(texPath)) {
                    int texIndex = material.getEmbeddedTextureIndex(texPath);
                    ResourceLocation embeddedTexLoc = embeddedTextureCache.get(texIndex);
                    if (embeddedTexLoc != null) {
                        DebugManager.info(DebugCategory.RENDER, "Using embedded texture {} for mesh {}", embeddedTexLoc, i);
                        // Note: Embedded textures are registered as dynamic textures
                        // BakedModel uses white_concrete sprite, actual texture binding happens in BlockEntityRenderer
                    }
                }

                // Use white sprite with material color
                meshSprite = particleIcon;
            } else {
                DebugManager.warn(DebugCategory.RENDER, "Mesh {} has invalid material index: {}", i, mesh.getMaterialIndex());
            }

            // Convert mesh to quads - pass material for color info
            List<BakedQuad> meshQuads = MinecraftMeshConverter.convertToQuads(mesh, meshSprite, material);
            DebugManager.info(DebugCategory.RENDER, "Mesh {} generated {} quads", i, meshQuads.size());
            quads.addAll(meshQuads);
        }

        this.hasTransparency = transparent;
    }

    @Override
    public @NotNull List<BakedQuad> getQuads(@Nullable BlockState state, @Nullable Direction side, @NotNull RandomSource rand) {
        // Return all quads for null side (general quads)
        // Direction-specific quads not used for custom models
        if (side == null) {
            return quads;
        }
        return List.of();
    }

    @Override
    public @NotNull List<BakedQuad> getQuads(@Nullable BlockState state, @Nullable Direction side, @NotNull RandomSource rand, @NotNull ModelData extraData, @Nullable net.minecraft.client.renderer.RenderType renderType) {
        return getQuads(state, side, rand);
    }

    @Override
    public boolean useAmbientOcclusion() {
        // Disable AO for smooth models - AO causes stepped lighting on curved surfaces
        // This gives better results for complex 3D models like donuts, spheres, etc.
        return false;
    }

    @Override
    public boolean isGui3d() {
        return true;
    }

    @Override
    public boolean usesBlockLight() {
        // Enable block light but without AO for smoother appearance
        return true;
    }

    @Override
    public boolean isCustomRenderer() {
        // Return true to use BlockEntityWithoutLevelRenderer for items
        // This allows items to render with embedded textures just like blocks in world
        return true;
    }

    @Override
    public @NotNull TextureAtlasSprite getParticleIcon() {
        return particleIcon;
    }

    @Override
    public @NotNull ItemTransforms getTransforms() {
        return ItemTransforms.NO_TRANSFORMS;
    }

    @Override
    public @NotNull ItemOverrides getOverrides() {
        return ItemOverrides.EMPTY;
    }

    public AssimpScene getScene() {
        return scene;
    }

    /**
     * Get the first embedded texture location (for Mixin texture binding)
     */
    public ResourceLocation getEmbeddedTextureLocation() {
        if (!embeddedTextureCache.isEmpty()) {
            return embeddedTextureCache.values().iterator().next();
        }
        return null;
    }

    /**
     * Load embedded textures from the scene and register them with Minecraft's TextureManager
     */
    private void loadEmbeddedTextures() {
        TextureManager textureManager = Minecraft.getInstance().getTextureManager();
        List<AssimpTexture> textures = scene.getTextures();

        DebugManager.info(DebugCategory.RENDER, "Loading {} embedded texture(s)", textures.size());

        // Normalize model name for texture cache key (use full path, replace / with _)
        String modelName = modelLocation.getPath()
            .replace(".json", "")
            .replace("/", "_")
            .replace(".fbx", "");

        for (int i = 0; i < textures.size(); i++) {
            AssimpTexture texture = textures.get(i);
            DynamicTexture dynamicTexture = texture.createDynamicTexture();

            if (dynamicTexture != null) {
                ResourceLocation textureLoc = new ResourceLocation("assimploader",
                    "embedded/" + modelName + "_" + i);
                textureManager.register(textureLoc, dynamicTexture);
                embeddedTextureCache.put(i, textureLoc);
                DebugManager.info(DebugCategory.RENDER, "Registered embedded texture {}: {}", i, textureLoc);
            }
        }
    }

    /**
     * Forge extension: Specify which render types this model should use
     * This is critical for transparency support
     */
    @Override
    public ChunkRenderTypeSet getRenderTypes(@NotNull BlockState state, @NotNull RandomSource rand, @NotNull ModelData data) {
        if (hasTransparency) {
            // Use translucent render type for transparent materials
            return ChunkRenderTypeSet.of(RenderType.translucent());
        } else {
            // Use solid render type for opaque materials
            return ChunkRenderTypeSet.of(RenderType.solid());
        }
    }
}
