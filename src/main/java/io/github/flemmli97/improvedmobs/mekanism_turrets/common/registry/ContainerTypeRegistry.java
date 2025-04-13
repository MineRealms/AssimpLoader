package io.github.flemmli97.improvedmobs.mekanism_turrets.common.registry;
import io.github.flemmli97.improvedmobs.ImprovedMobs;

import io.github.flemmli97.improvedmobs.mekanism_turrets.common.block_entity.LaserTurretBlockEntity;
import mekanism.common.inventory.container.tile.MekanismTileContainer;
import mekanism.common.registration.impl.ContainerTypeDeferredRegister;
import mekanism.common.registration.impl.ContainerTypeRegistryObject;
import mekanism.common.tile.TileEntityFluidTank;

public class ContainerTypeRegistry {

    public static final ContainerTypeDeferredRegister CONTAINER_TYPES = new ContainerTypeDeferredRegister(ImprovedMobs.MODID);

    public static final ContainerTypeRegistryObject<MekanismTileContainer<LaserTurretBlockEntity>> LASER_TURRET = CONTAINER_TYPES.custom("laser_turret", LaserTurretBlockEntity.class).build();

}
