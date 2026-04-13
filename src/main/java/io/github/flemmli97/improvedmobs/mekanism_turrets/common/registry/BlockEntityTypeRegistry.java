package io.github.flemmli97.improvedmobs.mekanism_turrets.common.registry;
import io.github.flemmli97.improvedmobs.ImprovedMobs;

import io.github.flemmli97.improvedmobs.mekanism_turrets.common.block_entity.ElectricFenceBlockEntity;
import io.github.flemmli97.improvedmobs.mekanism_turrets.common.block_entity.LaserTurretBlockEntity;
import mekanism.common.registration.impl.TileEntityTypeDeferredRegister;
import mekanism.common.registration.impl.TileEntityTypeRegistryObject;
import mekanism.common.registries.MekanismBlocks;
import mekanism.common.tile.TileEntityFluidTank;

public class BlockEntityTypeRegistry {

    public static final TileEntityTypeDeferredRegister BLOCK_ENTITY_TYPES = new TileEntityTypeDeferredRegister(ImprovedMobs.MODID);

    public static final TileEntityTypeRegistryObject<ElectricFenceBlockEntity> ELECTRIC_FENCE = BLOCK_ENTITY_TYPES.register(BlockRegistry.ELECTRIC_FENCE, ElectricFenceBlockEntity::new);
    public static final TileEntityTypeRegistryObject<LaserTurretBlockEntity> BASIC_LASER_TURRET = BLOCK_ENTITY_TYPES.register(BlockRegistry.BASIC_LASER_TURRET, (pos, state) -> new LaserTurretBlockEntity(BlockRegistry.BASIC_LASER_TURRET, pos, state));
    public static final TileEntityTypeRegistryObject<LaserTurretBlockEntity> ADVANCED_LASER_TURRET = BLOCK_ENTITY_TYPES.register(BlockRegistry.ADVANCED_LASER_TURRET, (pos, state) -> new LaserTurretBlockEntity(BlockRegistry.ADVANCED_LASER_TURRET, pos, state));
    public static final TileEntityTypeRegistryObject<LaserTurretBlockEntity> ELITE_LASER_TURRET = BLOCK_ENTITY_TYPES.register(BlockRegistry.ELITE_LASER_TURRET, (pos, state) -> new LaserTurretBlockEntity(BlockRegistry.ELITE_LASER_TURRET, pos, state));
    public static final TileEntityTypeRegistryObject<LaserTurretBlockEntity> ULTIMATE_LASER_TURRET = BLOCK_ENTITY_TYPES.register(BlockRegistry.ULTIMATE_LASER_TURRET, (pos, state) -> new LaserTurretBlockEntity(BlockRegistry.ULTIMATE_LASER_TURRET, pos, state));
}
