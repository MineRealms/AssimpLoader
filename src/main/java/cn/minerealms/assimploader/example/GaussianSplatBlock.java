package cn.minerealms.assimploader.example;

import net.minecraft.core.BlockPos;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.BaseEntityBlock;
import net.minecraft.world.level.block.RenderShape;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.BlockEntityTicker;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockState;
import org.jetbrains.annotations.Nullable;

/**
 * Block that renders a Gaussian Splat model.
 *
 * Similar to CircleBlock but for 3DGS models.
 */
public class GaussianSplatBlock extends BaseEntityBlock {

    public GaussianSplatBlock(Properties properties) {
        super(properties);
    }

    @Nullable
    @Override
    public BlockEntity newBlockEntity(BlockPos pos, BlockState state) {
        return new GaussianSplatBlockEntity(pos, state);
    }

    @Override
    public RenderShape getRenderShape(BlockState state) {
        // Use MODEL for fallback rendering when no splat data available
        // ENTITYBLOCK_ANIMATED for custom rendering when splat data exists
        return RenderShape.MODEL;
    }

    @Nullable
    @Override
    public <T extends BlockEntity> BlockEntityTicker<T> getTicker(Level level, BlockState state, BlockEntityType<T> type) {
        return level.isClientSide ? createTickerHelper(type, ExampleRegistry.GAUSSIAN_SPLAT_BLOCK_ENTITY.get(),
            (level1, pos, state1, blockEntity) -> blockEntity.tick()) : null;
    }
}
