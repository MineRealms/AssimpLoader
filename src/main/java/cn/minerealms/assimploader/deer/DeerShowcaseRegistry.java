package cn.minerealms.assimploader.deer;

import cn.minerealms.assimploader.AssimpLoader;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.MobCategory;
import net.minecraftforge.event.entity.EntityAttributeCreationEvent;
import net.minecraftforge.eventbus.api.IEventBus;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.registries.DeferredRegister;
import net.minecraftforge.registries.ForgeRegistries;
import net.minecraftforge.registries.RegistryObject;

@Mod.EventBusSubscriber(modid = AssimpLoader.MODID, bus = Mod.EventBusSubscriber.Bus.MOD)
public final class DeerShowcaseRegistry {
    private static final DeferredRegister<EntityType<?>> ENTITIES =
            DeferredRegister.create(ForgeRegistries.ENTITY_TYPES, AssimpLoader.MODID);
    public static final RegistryObject<EntityType<DeerShowcaseEntity>> DEER = ENTITIES.register(
            DeerShowcaseEntity.ID, () -> EntityType.Builder.of(DeerShowcaseEntity::new, MobCategory.CREATURE)
                    .sized(.9f, 1.8f).clientTrackingRange(10).updateInterval(2)
                    .build(AssimpLoader.MODID + ":" + DeerShowcaseEntity.ID));

    private DeerShowcaseRegistry() {}
    public static void register(IEventBus bus) { ENTITIES.register(bus); }

    @SubscribeEvent public static void attributes(EntityAttributeCreationEvent event) {
        event.put(DEER.get(), DeerShowcaseEntity.attributes().build());
    }
}
