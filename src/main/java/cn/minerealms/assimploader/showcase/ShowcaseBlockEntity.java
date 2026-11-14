package cn.minerealms.assimploader.showcase;

import cn.minerealms.assimploader.core.AssimpModelLoader;
import cn.minerealms.assimploader.config.AssimpLoaderConfig;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.AABB;

public class ShowcaseBlockEntity extends BlockEntity {
    public ShowcaseBlockEntity(BlockPos pos, BlockState state) {
        super(ShowcaseRegistry.BLOCK_ENTITY.get(), pos, state);
    }

    public ShowcaseModel getModel() {
        return ((ShowcaseBlock) getBlockState().getBlock()).getModel();
    }

    @Override
    public AABB getRenderBoundingBox() {
        return ShowcaseVisibility.renderBounds(getModelBounds(), worldPosition,
                AssimpLoaderConfig.getOrDefault(AssimpLoaderConfig.CLIENT.frustumCullingEnabled::get, true),
                AssimpLoaderConfig.getOrDefault(AssimpLoaderConfig.CLIENT.alwaysRenderLargeModels::get, false),
                AssimpLoaderConfig.getOrDefault(AssimpLoaderConfig.CLIENT.largeModelThreshold::get, 16.0));
    }

    public AABB getModelBounds() {
        return ShowcaseVisibility.modelBounds(AssimpModelLoader.getCachedModel(getModel().modelName()));
    }
}
