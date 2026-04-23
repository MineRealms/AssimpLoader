package cn.minerealms.assimploader.example;

import net.minecraft.core.BlockPos;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;

/**
 * BlockEntity for CCBBlock to enable custom rendering
 */
public class CCBBlockEntity extends BlockEntity {

    public CCBBlockEntity(BlockPos pos, BlockState state) {
        super(ExampleRegistry.CCB_BLOCK_ENTITY.get(), pos, state);
    }
}
