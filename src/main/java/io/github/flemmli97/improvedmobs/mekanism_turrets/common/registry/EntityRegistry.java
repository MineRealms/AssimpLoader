package io.github.flemmli97.improvedmobs.mekanism_turrets.common.registry;
import io.github.flemmli97.improvedmobs.ImprovedMobs;

import io.github.flemmli97.improvedmobs.mekanism_turrets.common.entity.LaserEntity;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.MobCategory;
import net.minecraftforge.registries.DeferredRegister;
import net.minecraftforge.registries.ForgeRegistries;
import net.minecraftforge.registries.RegistryObject;

public class EntityRegistry {

    public static final DeferredRegister<EntityType<?>> ENTITY_TYPES = DeferredRegister.create(ForgeRegistries.ENTITY_TYPES, ImprovedMobs.MODID);

    public static final RegistryObject<EntityType<LaserEntity>> LASER = ENTITY_TYPES.register("laser",
            () -> EntityType.Builder.<LaserEntity>of(LaserEntity::new, MobCategory.MISC)
                    .sized(0.25F, 0.25F)
                    .noSave()
                    .clientTrackingRange(128)
                    .updateInterval(1)
                    .build(new ResourceLocation(ImprovedMobs.MODID, "laser").toString()));
}
