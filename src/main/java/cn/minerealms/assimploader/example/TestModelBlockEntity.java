package cn.minerealms.assimploader.example;

import net.minecraft.core.BlockPos;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;

/**
 * BlockEntity for TestModelBlock to enable custom rendering
 */
public class TestModelBlockEntity extends BlockEntity {

    public TestModelBlockEntity(BlockPos pos, BlockState state) {
        super(ExampleRegistry.TEST_MODEL_BLOCK_ENTITY.get(), pos, state);
    }
}
