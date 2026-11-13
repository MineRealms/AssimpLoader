package cn.minerealms.assimploader.showcase;

import net.minecraft.core.BlockPos;
import net.minecraft.world.level.block.BaseEntityBlock;
import net.minecraft.world.level.block.RenderShape;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;

public class ShowcaseBlock extends BaseEntityBlock {
    private final ShowcaseModel model;

    public ShowcaseBlock(ShowcaseModel model, Properties properties) {
        super(properties);
        this.model = model;
    }

    public ShowcaseModel getModel() { return model; }

    @Override
    public RenderShape getRenderShape(BlockState state) {
        return RenderShape.ENTITYBLOCK_ANIMATED;
    }

    @Override
    public BlockEntity newBlockEntity(BlockPos pos, BlockState state) {
        return new ShowcaseBlockEntity(pos, state);
    }
}
