package cn.minerealms.iic.turrets.common.block;

import cn.minerealms.iic.turrets.common.block_entity.FlameThrowerTurretBlockEntity;
import mekanism.common.block.prefab.BlockTile;
import mekanism.common.content.blocktype.BlockTypeTile;
import mekanism.common.resource.BlockResourceInfo;
import mekanism.common.tile.base.WrenchResult;
import mekanism.common.util.WorldUtils;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Mirror;
import net.minecraft.world.level.block.RenderShape;
import net.minecraft.world.level.block.Rotation;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.level.block.state.properties.DirectionProperty;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.shapes.BooleanOp;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.Shapes;
import net.minecraft.world.phys.shapes.VoxelShape;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.stream.Stream;

/**
 * 火焰喷射器炮塔方块
 *
 * 特性：
 * - 只能放置在能量底座的上面或下面（通过TurretPlacementHandler限制）
 * - 使用GeckoLib动画系统
 * - 支持扳手交互
 */
public class FlameThrowerTurretBlock extends BlockTile.BlockTileModel<FlameThrowerTurretBlockEntity, BlockTypeTile<FlameThrowerTurretBlockEntity>> {

    public static final DirectionProperty FACING = BlockStateProperties.FACING;
    private static VoxelShape SHAPE_UP;
    private static VoxelShape SHAPE_DOWN;

    public FlameThrowerTurretBlock(BlockTypeTile<FlameThrowerTurretBlockEntity> type) {
        super(type, properties -> properties.mapColor(BlockResourceInfo.STEEL.getMapColor()));
        this.registerDefaultState(this.stateDefinition.any().setValue(FACING, Direction.UP));
    }

    @Override
    protected void createBlockStateDefinition(@NotNull StateDefinition.Builder<Block, BlockState> builder) {
        super.createBlockStateDefinition(builder);
        builder.add(FACING);
    }

    public BlockState rotate(BlockState pState, Rotation pRot) {
        return pState.setValue(FACING, pRot.rotate(pState.getValue(FACING)));
    }

    public BlockState mirror(BlockState pState, Mirror pMirror) {
        return pState.rotate(pMirror.getRotation(pState.getValue(FACING)));
    }

    @Override
    public @NotNull InteractionResult use(@NotNull BlockState state, @NotNull Level world, @NotNull BlockPos pos,
                                         @NotNull Player player, @NotNull InteractionHand hand, @NotNull BlockHitResult hit) {
        FlameThrowerTurretBlockEntity tile = WorldUtils.getTileEntity(FlameThrowerTurretBlockEntity.class, world, pos, true);
        if (tile == null) {
            return InteractionResult.PASS;
        } else if (world.isClientSide) {
            return genericClientActivated(player, hand);
        } else if (tile.tryWrench(state, player, hand, hit) != WrenchResult.PASS) {
            return InteractionResult.SUCCESS;
        }
        return tile.openGui(player);
    }

    @Override
    public boolean hasAnalogOutputSignal(@NotNull BlockState blockState) {
        return true;
    }

    @Override
    public int getAnalogOutputSignal(@NotNull BlockState blockState, @NotNull Level world, @NotNull BlockPos pos) {
        FlameThrowerTurretBlockEntity tile = WorldUtils.getTileEntity(FlameThrowerTurretBlockEntity.class, world, pos, true);
        return tile != null && tile.hasTarget() ? 15 : 0;
    }

    @Override
    public @Nullable BlockState getStateForPlacement(@NotNull BlockPlaceContext context) {
        Direction clickedFace = context.getClickedFace();
        // 只允许放置在上面或下面
        if (clickedFace == Direction.UP || clickedFace == Direction.DOWN) {
            return this.defaultBlockState().setValue(FACING, clickedFace.getOpposite());
        }
        return null;
    }

    @Override
    public RenderShape getRenderShape(BlockState pState) {
        return RenderShape.ENTITYBLOCK_ANIMATED;
    }

    @Override
    public VoxelShape getCollisionShape(BlockState state, BlockGetter pLevel, BlockPos pPos, CollisionContext pContext) {
        return switch (state.getValue(FACING)) {
            case UP -> {
                if (SHAPE_UP == null) {
                    SHAPE_UP = Stream.of(
                            Block.box(2, 15, 2, 14, 16, 14),
                            Block.box(5, 14, 5, 11, 15, 11),
                            Block.box(12, 14, 3, 13, 15, 4),
                            Block.box(3, 14, 3, 4, 15, 4),
                            Block.box(3, 14, 12, 4, 15, 13),
                            Block.box(12, 14, 12, 13, 15, 13)
                    ).reduce((v1, v2) -> Shapes.join(v1, v2, BooleanOp.OR)).get();
                }
                yield SHAPE_UP;
            }
            case DOWN -> {
                if (SHAPE_DOWN == null) {
                    SHAPE_DOWN = Stream.of(
                            Block.box(2, 0, 2, 14, 1, 14),
                            Block.box(5, 1, 5, 11, 2, 11),
                            Block.box(12, 1, 3, 13, 2, 4),
                            Block.box(12, 1, 12, 13, 2, 13),
                            Block.box(3, 1, 12, 4, 2, 13),
                            Block.box(3, 1, 3, 4, 2, 4)
                    ).reduce((v1, v2) -> Shapes.join(v1, v2, BooleanOp.OR)).get();
                }
                yield SHAPE_DOWN;
            }
            default -> Shapes.block();
        };
    }

    @Override
    public @NotNull VoxelShape getShape(@NotNull BlockState state, @NotNull BlockGetter world, @NotNull BlockPos pos, @NotNull CollisionContext context) {
        return Shapes.block();
    }

    @Override
    public VoxelShape getOcclusionShape(BlockState pState, BlockGetter pLevel, BlockPos pPos) {
        return Shapes.create(AABB.ofSize(pPos.getCenter(), 2, 2, 2));
    }
}
