package cn.minerealms.iic.turrets.common.registry;
import cn.minerealms.iic.IntegratedIndustrialCraft;
import io.github.flemmli97.improvedmobs.ImprovedMobs;

import cn.minerealms.iic.turrets.common.block_entity.LaserTurretBlockEntity;
import mekanism.common.inventory.container.tile.MekanismTileContainer;
import mekanism.common.registration.impl.ContainerTypeDeferredRegister;
import mekanism.common.registration.impl.ContainerTypeRegistryObject;
import mekanism.common.tile.TileEntityFluidTank;

public class ContainerTypeRegistry {

    public static final ContainerTypeDeferredRegister CONTAINER_TYPES = new ContainerTypeDeferredRegister(IntegratedIndustrialCraft.MODID);

    public static final ContainerTypeRegistryObject<MekanismTileContainer<LaserTurretBlockEntity>> LASER_TURRET = CONTAINER_TYPES.custom("laser_turret", LaserTurretBlockEntity.class).build();

}
