package cn.minerealms.assimploader.showcase;

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
        return new AABB(worldPosition).inflate(3.0);
    }
}
