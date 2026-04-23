package cn.minerealms.assimploader.render;

import cn.minerealms.assimploader.AssimpLoader;
import cn.minerealms.assimploader.model.AssimpMaterial;
import cn.minerealms.assimploader.model.AssimpMesh;
import cn.minerealms.assimploader.model.AssimpScene;
import com.mojang.blaze3d.vertex.PoseStack;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.block.model.BakedQuad;
import net.minecraft.client.renderer.block.model.ItemOverrides;
import net.minecraft.client.renderer.block.model.ItemTransforms;
import net.minecraft.client.renderer.texture.TextureAtlasSprite;
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
import java.util.List;

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

    public AssimpBakedModel(AssimpScene scene, TextureAtlasSprite particleIcon) {
        this.scene = scene;
        this.particleIcon = particleIcon;
        this.quads = new ArrayList<>();
        boolean transparent = false;

        // Convert all meshes to quads
        AssimpLoader.LOGGER.info("Building BakedModel: {} meshes, {} materials",
                scene.getMeshes().size(), scene.getMaterials().size());

        for (int i = 0; i < scene.getMeshes().size(); i++) {
            AssimpMesh mesh = scene.getMeshes().get(i);

            // Get material for this mesh
            AssimpMaterial material = null;
            TextureAtlasSprite meshSprite = particleIcon;  // Default to white

            if (mesh.getMaterialIndex() >= 0 && mesh.getMaterialIndex() < scene.getMaterials().size()) {
                material = scene.getMaterials().get(mesh.getMaterialIndex());
                AssimpLoader.LOGGER.info("Mesh {} using material index {}", i, mesh.getMaterialIndex());

                // Check if material is transparent
                if (material.isTransparent()) {
                    transparent = true;
                    AssimpLoader.LOGGER.info("Material {} is transparent (alpha={})",
                        mesh.getMaterialIndex(), material.getDiffuseColor()[3]);
                }

                // Generate solid color texture for this material
                ResourceLocation colorTextureLoc = MaterialTextureGenerator.getOrCreateColorTexture(material, i);

                // Get sprite for the generated texture
                // Note: This won't work because generated textures aren't in the atlas
                // So we'll use white_concrete and pass material color to converter
                meshSprite = particleIcon;  // Use white sprite
            } else {
                AssimpLoader.LOGGER.warn("Mesh {} has invalid material index: {}", i, mesh.getMaterialIndex());
            }

            // Convert mesh to quads - pass material for color info
            List<BakedQuad> meshQuads = MinecraftMeshConverter.convertToQuads(mesh, meshSprite, material);
            AssimpLoader.LOGGER.info("Mesh {} generated {} quads", i, meshQuads.size());
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
        return false;
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
