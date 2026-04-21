package cn.minerealms.iic.turrets.common.registry;
import cn.minerealms.iic.IntegratedIndustrialCraft;
import io.github.flemmli97.improvedmobs.ImprovedMobs;

import cn.minerealms.iic.turrets.common.block_entity.ElectricFenceBlockEntity;
import cn.minerealms.iic.turrets.common.block_entity.EnergyPedestalBlockEntity;
import cn.minerealms.iic.turrets.common.block_entity.FlameThrowerTurretBlockEntity;
import cn.minerealms.iic.turrets.common.block_entity.LaserTurretBlockEntity;
import mekanism.common.registration.impl.TileEntityTypeDeferredRegister;
import mekanism.common.registration.impl.TileEntityTypeRegistryObject;
import mekanism.common.registries.MekanismBlocks;
import mekanism.common.tile.TileEntityFluidTank;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraftforge.registries.DeferredRegister;
import net.minecraftforge.registries.ForgeRegistries;
import net.minecraftforge.registries.RegistryObject;

public class BlockEntityTypeRegistry {

    public static final TileEntityTypeDeferredRegister BLOCK_ENTITY_TYPES = new TileEntityTypeDeferredRegister(IntegratedIndustrialCraft.MODID);

    // 标准 Forge 注册（用于非 Mekanism BlockEntity）
    public static final DeferredRegister<BlockEntityType<?>> STANDARD_BLOCK_ENTITIES = DeferredRegister.create(ForgeRegistries.BLOCK_ENTITY_TYPES, IntegratedIndustrialCraft.MODID);

    public static final TileEntityTypeRegistryObject<ElectricFenceBlockEntity> ELECTRIC_FENCE = BLOCK_ENTITY_TYPES.register(BlockRegistry.ELECTRIC_FENCE, ElectricFenceBlockEntity::new);

    // 能量底座方块实体 - 使用标准 Forge 注册
    public static final RegistryObject<BlockEntityType<EnergyPedestalBlockEntity>> ENERGY_PEDESTAL = STANDARD_BLOCK_ENTITIES.register("energy_pedestal",
            () -> BlockEntityType.Builder.of(EnergyPedestalBlockEntity::new, BlockRegistry.ENERGY_PEDESTAL.getBlock()).build(null));

    public static final TileEntityTypeRegistryObject<LaserTurretBlockEntity> BASIC_LASER_TURRET = BLOCK_ENTITY_TYPES.register(BlockRegistry.BASIC_LASER_TURRET, (pos, state) -> new LaserTurretBlockEntity(BlockRegistry.BASIC_LASER_TURRET, pos, state));
    public static final TileEntityTypeRegistryObject<LaserTurretBlockEntity> ADVANCED_LASER_TURRET = BLOCK_ENTITY_TYPES.register(BlockRegistry.ADVANCED_LASER_TURRET, (pos, state) -> new LaserTurretBlockEntity(BlockRegistry.ADVANCED_LASER_TURRET, pos, state));
    public static final TileEntityTypeRegistryObject<LaserTurretBlockEntity> ELITE_LASER_TURRET = BLOCK_ENTITY_TYPES.register(BlockRegistry.ELITE_LASER_TURRET, (pos, state) -> new LaserTurretBlockEntity(BlockRegistry.ELITE_LASER_TURRET, pos, state));
    public static final TileEntityTypeRegistryObject<LaserTurretBlockEntity> ULTIMATE_LASER_TURRET = BLOCK_ENTITY_TYPES.register(BlockRegistry.ULTIMATE_LASER_TURRET, (pos, state) -> new LaserTurretBlockEntity(BlockRegistry.ULTIMATE_LASER_TURRET, pos, state));

    public static final TileEntityTypeRegistryObject<FlameThrowerTurretBlockEntity> FLAMETHROWER_TURRET = BLOCK_ENTITY_TYPES.register(BlockRegistry.FLAMETHROWER_TURRET, (pos, state) -> new FlameThrowerTurretBlockEntity(BlockRegistry.FLAMETHROWER_TURRET, pos, state));
}
