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

    // CNC Machine block with FBX model (using BlockEntityRenderer)
    public static final RegistryObject<Block> CNC_MACHINE = BLOCKS.register("cnc_machine",
        () -> new TestModelBlock(BlockBehaviour.Properties.of()
            .mapColor(MapColor.METAL)
            .strength(3.0F, 6.0F)
            .sound(SoundType.METAL)
            .requiresCorrectToolForDrops()
        )
    );

    // CCB block with FBX model (using BlockEntityRenderer)
    public static final RegistryObject<Block> CCB = BLOCKS.register("ccb",
        () -> new CCBBlock(BlockBehaviour.Properties.of()
            .mapColor(MapColor.METAL)
            .strength(3.0F, 6.0F)
            .sound(SoundType.METAL)
            .requiresCorrectToolForDrops()
        )
    );

    // Circle block with animated FBX model (using BlockEntityRenderer)
    public static final RegistryObject<Block> CIRCLE = BLOCKS.register("circle",
        () -> new CircleBlock(BlockBehaviour.Properties.of()
            .mapColor(MapColor.METAL)
            .strength(3.0F, 6.0F)
            .sound(SoundType.METAL)
            .requiresCorrectToolForDrops()
        )
    );

    // Gaussian Splat block with 3DGS model (using BlockEntityRenderer)
    public static final RegistryObject<Block> GAUSSIAN_SPLAT = BLOCKS.register("gaussian_splat",
        () -> new GaussianSplatBlock(BlockBehaviour.Properties.of()
            .mapColor(MapColor.METAL)
            .strength(3.0F, 6.0F)
            .sound(SoundType.METAL)
            .requiresCorrectToolForDrops()
        )
    );

    // Block entity
    public static final RegistryObject<BlockEntityType<TestModelBlockEntity>> TEST_MODEL_BLOCK_ENTITY =
        BLOCK_ENTITIES.register("test_model_block_entity",
            () -> BlockEntityType.Builder.of(TestModelBlockEntity::new, CNC_MACHINE.get()).build(null)
        );

    // CCB block entity
    public static final RegistryObject<BlockEntityType<CCBBlockEntity>> CCB_BLOCK_ENTITY =
        BLOCK_ENTITIES.register("ccb_block_entity",
            () -> BlockEntityType.Builder.of(CCBBlockEntity::new, CCB.get()).build(null)
        );

    // Circle block entity
    public static final RegistryObject<BlockEntityType<CircleBlockEntity>> CIRCLE_BLOCK_ENTITY =
        BLOCK_ENTITIES.register("circle_block_entity",
            () -> BlockEntityType.Builder.of(CircleBlockEntity::new, CIRCLE.get()).build(null)
        );

    // Gaussian Splat block entity
    public static final RegistryObject<BlockEntityType<GaussianSplatBlockEntity>> GAUSSIAN_SPLAT_BLOCK_ENTITY =
        BLOCK_ENTITIES.register("gaussian_splat_block_entity",
            () -> BlockEntityType.Builder.of(GaussianSplatBlockEntity::new, GAUSSIAN_SPLAT.get()).build(null)
        );

    // Block item
    public static final RegistryObject<Item> CNC_MACHINE_ITEM = ITEMS.register("cnc_machine",
        () -> new AssimpBlockItem(CNC_MACHINE.get(), new Item.Properties())
    );

    // CCB block item
    public static final RegistryObject<Item> CCB_ITEM = ITEMS.register("ccb",
        () -> new AssimpBlockItem(CCB.get(), new Item.Properties())
    );

    // Circle block item
    public static final RegistryObject<Item> CIRCLE_ITEM = ITEMS.register("circle",
        () -> new AssimpBlockItem(CIRCLE.get(), new Item.Properties())
    );

    // Gaussian Splat block item
    public static final RegistryObject<Item> GAUSSIAN_SPLAT_ITEM = ITEMS.register("gaussian_splat",
        () -> new AssimpBlockItem(GAUSSIAN_SPLAT.get(), new Item.Properties())
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
            event.accept(CNC_MACHINE_ITEM);
            event.accept(CCB_ITEM);
            event.accept(CIRCLE_ITEM);
            event.accept(GAUSSIAN_SPLAT_ITEM);
        }
    }
}
