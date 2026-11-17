package cn.minerealms.assimpshowcase.showcase;

import net.minecraft.core.BlockPos;
import net.minecraft.world.level.block.BaseEntityBlock;
import net.minecraft.world.level.block.RenderShape;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.entity.BlockEntityTicker;
import net.minecraft.world.level.block.entity.BlockEntityType;
import cn.minerealms.assimpshowcase.rocket.client.RocketShowcaseClient;

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

    @Override
    public <T extends BlockEntity> BlockEntityTicker<T> getTicker(Level level, BlockState state, BlockEntityType<T> type) {
        return level.isClientSide && model == ShowcaseModel.ROCKET_ENGINE
                ? createTickerHelper(type, ShowcaseRegistry.BLOCK_ENTITY.get(), RocketShowcaseClient::tick) : null;
    }
}
