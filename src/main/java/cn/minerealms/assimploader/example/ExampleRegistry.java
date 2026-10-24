package cn.minerealms.assimploader.example;

import cn.minerealms.assimploader.AssimpLoader;
import cn.minerealms.assimploader.debug.DebugManager;
import cn.minerealms.assimploader.debug.DebugManager.DebugCategory;
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

    // CNC (CCB) block with FBX model (using BlockEntityRenderer)
    public static final RegistryObject<Block> CNC_BLOCK = BLOCKS.register("cnc",
        () -> new CCBBlock(BlockBehaviour.Properties.of()
            .mapColor(MapColor.METAL)
            .strength(3.0F, 6.0F)
            .sound(SoundType.METAL)
            .requiresCorrectToolForDrops()
        )
    );

    // CNC block entity
    public static final RegistryObject<BlockEntityType<CCBBlockEntity>> CNC_BLOCK_ENTITY =
        BLOCK_ENTITIES.register("cnc_block_entity",
            () -> BlockEntityType.Builder.of(CCBBlockEntity::new, CNC_BLOCK.get()).build(null)
        );

    // CNC block item
    public static final RegistryObject<Item> CNC_ITEM = ITEMS.register("cnc",
        () -> new AssimpBlockItem(CNC_BLOCK.get(), new Item.Properties())
    );

    public static void register(IEventBus modEventBus) {
        BLOCKS.register(modEventBus);
        ITEMS.register(modEventBus);
        BLOCK_ENTITIES.register(modEventBus);

        modEventBus.addListener(ExampleRegistry::addCreative);

        DebugManager.info(DebugCategory.CORE, "Example registry initialized");
    }

    private static void addCreative(BuildCreativeModeTabContentsEvent event) {
        if (event.getTabKey() == CreativeModeTabs.BUILDING_BLOCKS) {
            event.accept(CNC_ITEM);
        }
    }
}
