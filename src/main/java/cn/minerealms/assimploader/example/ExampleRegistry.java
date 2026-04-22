package cn.minerealms.assimploader.example;

import cn.minerealms.assimploader.AssimpLoader;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.CreativeModeTabs;
import net.minecraft.world.item.Item;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.SoundType;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraft.world.level.material.MapColor;
import net.minecraftforge.event.BuildCreativeModeTabContentsEvent;
import net.minecraftforge.eventbus.api.IEventBus;
import net.minecraftforge.registries.DeferredRegister;
import net.minecraftforge.registries.ForgeRegistries;
import net.minecraftforge.registries.RegistryObject;

/**
 * Registry for example blocks and items.
 * Demonstrates how to register blocks that use Assimp models with BlockEntityRenderer.
 */
public class ExampleRegistry {
    public static final DeferredRegister<Block> BLOCKS =
        DeferredRegister.create(ForgeRegistries.BLOCKS, AssimpLoader.MODID);

    public static final DeferredRegister<Item> ITEMS =
        DeferredRegister.create(ForgeRegistries.ITEMS, AssimpLoader.MODID);

    public static final DeferredRegister<BlockEntityType<?>> BLOCK_ENTITIES =
        DeferredRegister.create(ForgeRegistries.BLOCK_ENTITY_TYPES, AssimpLoader.MODID);

    // CNC Machine block with FBX model
    public static final RegistryObject<Block> CNC_MACHINE = BLOCKS.register("cnc_machine",
        () -> new TestModelBlock(BlockBehaviour.Properties.of()
            .mapColor(MapColor.METAL)
            .strength(3.0F, 6.0F)
            .sound(SoundType.METAL)
            .requiresCorrectToolForDrops()
        )
    );

    // Block item
    public static final RegistryObject<Item> CNC_MACHINE_ITEM = ITEMS.register("cnc_machine",
        () -> new BlockItem(CNC_MACHINE.get(), new Item.Properties())
    );

    // BlockEntity
    public static final RegistryObject<BlockEntityType<CNCMachineBlockEntity>> CNC_MACHINE_BLOCK_ENTITY =
        BLOCK_ENTITIES.register("cnc_machine", () ->
            BlockEntityType.Builder.of(CNCMachineBlockEntity::new, CNC_MACHINE.get()).build(null)
        );

    public static void register(IEventBus modEventBus) {
        BLOCKS.register(modEventBus);
        ITEMS.register(modEventBus);
        BLOCK_ENTITIES.register(modEventBus);

        modEventBus.addListener(ExampleRegistry::addCreative);

        AssimpLoader.LOGGER.info("Example registry initialized");
    }

    private static void addCreative(BuildCreativeModeTabContentsEvent event) {
        if (event.getTabKey() == CreativeModeTabs.BUILDING_BLOCKS) {
            event.accept(CNC_MACHINE_ITEM);
        }
    }
}
