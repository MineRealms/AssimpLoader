package cn.minerealms.iic.test;

import net.minecraft.core.BlockPos;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.SoundType;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.Shapes;
import net.minecraft.world.phys.shapes.VoxelShape;

/**
 * CNC Machine Block - Test block for AssimpLoader FBX model loading
 *
 * This block uses AssimpLoader to load a 3x3x3 FBX model with materials.
 * Model path: assets/integratedindustrialcraft/models/block/cnc.fbx
 */
public class CNCBlock extends Block {

    // 3x3x3 bounding box (48 pixels = 3 blocks)
    private static final VoxelShape SHAPE = Shapes.box(0.0, 0.0, 0.0, 3.0, 3.0, 3.0);

    public CNCBlock() {
        super(BlockBehaviour.Properties.of()
                .strength(5.0F, 6.0F)
                .sound(SoundType.METAL)
                .requiresCorrectToolForDrops()
                .noOcclusion() // Allow custom model rendering
        );
    }

    @Override
    public VoxelShape getShape(BlockState state, BlockGetter level, BlockPos pos, CollisionContext context) {
        return SHAPE;
    }

    @Override
    public VoxelShape getCollisionShape(BlockState state, BlockGetter level, BlockPos pos, CollisionContext context) {
        return SHAPE;
    }
}
