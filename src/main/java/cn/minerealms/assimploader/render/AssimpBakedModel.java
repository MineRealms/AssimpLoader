package cn.minerealms.assimploader.render;

import cn.minerealms.assimploader.model.AssimpMesh;
import cn.minerealms.assimploader.model.AssimpScene;
import com.mojang.blaze3d.vertex.PoseStack;
import net.minecraft.client.renderer.block.model.BakedQuad;
import net.minecraft.client.renderer.block.model.ItemOverrides;
import net.minecraft.client.renderer.block.model.ItemTransforms;
import net.minecraft.client.renderer.texture.TextureAtlasSprite;
import net.minecraft.client.resources.model.BakedModel;
import net.minecraft.core.Direction;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.block.state.BlockState;
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

    public AssimpBakedModel(AssimpScene scene, TextureAtlasSprite particleIcon) {
        this.scene = scene;
        this.particleIcon = particleIcon;
        this.quads = new ArrayList<>();

        // Convert all meshes to quads
        for (AssimpMesh mesh : scene.getMeshes()) {
            List<BakedQuad> meshQuads = MinecraftMeshConverter.convertToQuads(mesh, particleIcon);
            quads.addAll(meshQuads);
        }
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
        return true;
    }

    @Override
    public boolean isGui3d() {
        return true;
    }

    @Override
    public boolean usesBlockLight() {
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
}
