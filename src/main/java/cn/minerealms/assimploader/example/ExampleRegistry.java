package cn.minerealms.assimploader.example;

import cn.minerealms.assimploader.AssimpLoader;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.CreativeModeTabs;
import net.minecraft.world.item.Item;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.SoundType;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraft.world.level.material.MapColor;
import net.minecraftforge.event.BuildCreativeModeTabContentsEvent;
import net.minecraftforge.eventbus.api.IEventBus;
import net.minecraftforge.registries.DeferredRegister;
import net.minecraftforge.registries.ForgeRegistries;
import net.minecraftforge.registries.RegistryObject;

/**
 * Registry for example blocks and items.
 * Demonstrates how to register blocks that use Assimp models.
 */
public class ExampleRegistry {
    public static final DeferredRegister<Block> BLOCKS =
        DeferredRegister.create(ForgeRegistries.BLOCKS, AssimpLoader.MODID);

    public static final DeferredRegister<Item> ITEMS =
        DeferredRegister.create(ForgeRegistries.ITEMS, AssimpLoader.MODID);

    // Example block with custom model
    public static final RegistryObject<Block> TEST_MODEL_BLOCK = BLOCKS.register("test_model_block",
        () -> new TestModelBlock(BlockBehaviour.Properties.of()
            .mapColor(MapColor.METAL)
            .strength(3.0F, 6.0F)
            .sound(SoundType.METAL)
            .requiresCorrectToolForDrops()
        )
    );

    // Block item
    public static final RegistryObject<Item> TEST_MODEL_BLOCK_ITEM = ITEMS.register("test_model_block",
        () -> new BlockItem(TEST_MODEL_BLOCK.get(), new Item.Properties())
    );

    public static void register(IEventBus modEventBus) {
        BLOCKS.register(modEventBus);
        ITEMS.register(modEventBus);

        modEventBus.addListener(ExampleRegistry::addCreative);

        AssimpLoader.LOGGER.info("Example registry initialized");
    }

    private static void addCreative(BuildCreativeModeTabContentsEvent event) {
        if (event.getTabKey() == CreativeModeTabs.BUILDING_BLOCKS) {
            event.accept(TEST_MODEL_BLOCK_ITEM);
        }
    }
}
