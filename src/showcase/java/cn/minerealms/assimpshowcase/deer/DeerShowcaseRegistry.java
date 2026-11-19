package cn.minerealms.assimpshowcase.deer;

import cn.minerealms.assimploader.AssimpLoader;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.MobCategory;
//? if forge {
import net.minecraftforge.event.entity.EntityAttributeCreationEvent;
import net.minecraftforge.eventbus.api.IEventBus;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.registries.DeferredRegister;
import net.minecraftforge.registries.ForgeRegistries;
import net.minecraftforge.registries.RegistryObject;
//?}
//? if neoforge {
/*
import net.minecraft.core.registries.BuiltInRegistries;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.Mod;
import net.neoforged.neoforge.event.entity.EntityAttributeCreationEvent;
import net.neoforged.neoforge.registries.DeferredHolder;
import net.neoforged.neoforge.registries.DeferredRegister;
*///?}

@Mod.EventBusSubscriber(modid = "assimpshowcase", bus = Mod.EventBusSubscriber.Bus.MOD)
public final class DeerShowcaseRegistry {
    //? if forge {
    private static final DeferredRegister<EntityType<?>> ENTITIES =
            DeferredRegister.create(ForgeRegistries.ENTITY_TYPES, AssimpLoader.MODID);
    public static final RegistryObject<EntityType<DeerShowcaseEntity>> DEER = ENTITIES.register(
            DeerShowcaseEntity.ID, () -> EntityType.Builder.of(DeerShowcaseEntity::new, MobCategory.CREATURE)
                    .sized(.9f, 1.8f).clientTrackingRange(10).updateInterval(2)
                    .build(AssimpLoader.MODID + ":" + DeerShowcaseEntity.ID));
    //?}
    //? if neoforge {
    /*
    // 21.1 replaced `RegistryObject` with `DeferredHolder` and moved the vanilla registry handles
    // from `NeoForgeRegistries` to `BuiltInRegistries`.
    private static final DeferredRegister<EntityType<?>> ENTITIES =
            DeferredRegister.create(BuiltInRegistries.ENTITY_TYPE, AssimpLoader.MODID);
    public static final DeferredHolder<EntityType<?>, EntityType<DeerShowcaseEntity>> DEER = ENTITIES.register(
            DeerShowcaseEntity.ID, () -> EntityType.Builder.of(DeerShowcaseEntity::new, MobCategory.CREATURE)
                    .sized(.9f, 1.8f).clientTrackingRange(10).updateInterval(2)
                    .build(AssimpLoader.MODID + ":" + DeerShowcaseEntity.ID));
    *///?}

    private DeerShowcaseRegistry() {}
    public static void register(IEventBus bus) { ENTITIES.register(bus); }

    @SubscribeEvent public static void attributes(EntityAttributeCreationEvent event) {
        event.put(DEER.get(), DeerShowcaseEntity.attributes().build());
    }
}
