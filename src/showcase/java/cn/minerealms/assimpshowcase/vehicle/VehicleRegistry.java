package cn.minerealms.assimpshowcase.vehicle;

import cn.minerealms.assimploader.AssimpLoader;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.MobCategory;
import net.minecraftforge.eventbus.api.IEventBus;
import net.minecraftforge.registries.DeferredRegister;
import net.minecraftforge.registries.ForgeRegistries;
import net.minecraftforge.registries.RegistryObject;

public final class VehicleRegistry {
    private static final DeferredRegister<EntityType<?>> ENTITY_TYPES =
            DeferredRegister.create(ForgeRegistries.ENTITY_TYPES, AssimpLoader.MODID);

    public static final RegistryObject<EntityType<ArmoredCarEntity>> ARMORED_CAR = ENTITY_TYPES.register(
            ArmoredCarEntity.MODEL_NAME,
            () -> EntityType.Builder.<ArmoredCarEntity>of(ArmoredCarEntity::new, MobCategory.MISC)
                    .sized(ArmoredCarEntity.MODEL_WIDTH, ArmoredCarEntity.MODEL_HEIGHT)
                    .clientTrackingRange(10)
                    .updateInterval(1)
                    .build(AssimpLoader.MODID + ":" + ArmoredCarEntity.MODEL_NAME));

    public static void register(IEventBus modBus) { ENTITY_TYPES.register(modBus); }

    private VehicleRegistry() {}
}
