package io.github.flemmli97.improvedmobs.mekanism_turrets.common.registry;
import io.github.flemmli97.improvedmobs.ImprovedMobs;

import net.minecraft.core.registries.Registries;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.CreativeModeTab;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.Items;
import net.minecraftforge.registries.DeferredRegister;
import net.minecraftforge.registries.ForgeRegistries;
import net.minecraftforge.registries.RegistryObject;

public class ItemRegistry {

    public static final DeferredRegister<Item> ITEMS = DeferredRegister.create(ForgeRegistries.ITEMS, ImprovedMobs.MODID);

    public static class ModItemTab {

        public static final DeferredRegister<CreativeModeTab> CREATIVE_MODE_TABS = DeferredRegister.create(Registries.CREATIVE_MODE_TAB, ImprovedMobs.MODID);

        public static final RegistryObject<CreativeModeTab> MEKT_ITEM_TAB = CREATIVE_MODE_TABS.register("main", () -> CreativeModeTab.builder()
                .icon(() -> BlockRegistry.ADVANCED_LASER_TURRET.asItem().getDefaultInstance())
                .title(Component.translatable("item_group." + ImprovedMobs.MODID))
                .displayItems((displayParameters, output) -> {
                    ItemRegistry.ITEMS.getEntries().forEach(itemRegistryObject -> output.accept(itemRegistryObject.get()));
                    BlockRegistry.BLOCKS.getAllBlocks().forEach(iBlockProvider -> output.accept(iBlockProvider.getItemStack()));
                })
                .build());
    }
}
