package cn.minerealms.assimpshowcase.showcase;

import cn.minerealms.assimploader.AssimpLoader;
import cn.minerealms.assimpshowcase.example.AssimpBlockItem;
import net.minecraft.world.item.CreativeModeTabs;
import net.minecraft.world.item.Item;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.SoundType;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraft.world.level.material.MapColor;
//? if forge {
import net.minecraftforge.event.BuildCreativeModeTabContentsEvent;
import net.minecraftforge.eventbus.api.IEventBus;
import net.minecraftforge.registries.DeferredRegister;
import net.minecraftforge.registries.ForgeRegistries;
import net.minecraftforge.registries.RegistryObject;
//?}
//? if neoforge {
/*
import net.minecraft.core.registries.BuiltInRegistries;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.event.BuildCreativeModeTabContentsEvent;
import net.neoforged.neoforge.registries.DeferredHolder;
import net.neoforged.neoforge.registries.DeferredRegister;
*///?}

import java.util.EnumMap;
import java.util.Map;

public final class ShowcaseRegistry {
    //? if forge {
    private static final DeferredRegister<Block> BLOCKS = DeferredRegister.create(ForgeRegistries.BLOCKS, AssimpLoader.MODID);
    private static final DeferredRegister<Item> ITEMS = DeferredRegister.create(ForgeRegistries.ITEMS, AssimpLoader.MODID);
    private static final DeferredRegister<BlockEntityType<?>> BLOCK_ENTITIES =
            DeferredRegister.create(ForgeRegistries.BLOCK_ENTITY_TYPES, AssimpLoader.MODID);
    private static final Map<ShowcaseModel, RegistryObject<ShowcaseBlock>> MODELS = new EnumMap<>(ShowcaseModel.class);
    private static final Map<ShowcaseModel, RegistryObject<Item>> MODEL_ITEMS = new EnumMap<>(ShowcaseModel.class);
    //?}
    //? if neoforge {
    /*
    private static final DeferredRegister<Block> BLOCKS = DeferredRegister.create(BuiltInRegistries.BLOCK, AssimpLoader.MODID);
    private static final DeferredRegister<Item> ITEMS = DeferredRegister.create(BuiltInRegistries.ITEM, AssimpLoader.MODID);
    private static final DeferredRegister<BlockEntityType<?>> BLOCK_ENTITIES =
            DeferredRegister.create(BuiltInRegistries.BLOCK_ENTITY_TYPE, AssimpLoader.MODID);
    private static final Map<ShowcaseModel, DeferredHolder<Block, ShowcaseBlock>> MODELS = new EnumMap<>(ShowcaseModel.class);
    private static final Map<ShowcaseModel, DeferredHolder<Item, Item>> MODEL_ITEMS = new EnumMap<>(ShowcaseModel.class);
    *///?}

    static {
        for (ShowcaseModel model : ShowcaseModel.values()) {
            //? if forge {
            RegistryObject<ShowcaseBlock> block = BLOCKS.register(model.registryName(), () -> new ShowcaseBlock(model,
                    BlockBehaviour.Properties.of().mapColor(MapColor.METAL).strength(2.0f)
                            .sound(SoundType.METAL).noOcclusion()));
            MODELS.put(model, block);
            MODEL_ITEMS.put(model, ITEMS.register(model.registryName(),
                    () -> new AssimpBlockItem(block.get(), new Item.Properties(), model.modelName())));
            //?}
            //? if neoforge {
            /*
            DeferredHolder<Block, ShowcaseBlock> block = BLOCKS.register(model.registryName(), () -> new ShowcaseBlock(model,
                    BlockBehaviour.Properties.of().mapColor(MapColor.METAL).strength(2.0f)
                            .sound(SoundType.METAL).noOcclusion()));
            MODELS.put(model, block);
            MODEL_ITEMS.put(model, ITEMS.register(model.registryName(),
                    () -> new AssimpBlockItem(block.get(), new Item.Properties(), model.modelName())));
            *///?}
        }
    }

    //? if forge {
    public static final RegistryObject<BlockEntityType<ShowcaseBlockEntity>> BLOCK_ENTITY =
            BLOCK_ENTITIES.register("showcase", () -> BlockEntityType.Builder.of(ShowcaseBlockEntity::new,
                    MODELS.values().stream().map(RegistryObject::get).toArray(Block[]::new)).build(null));
    //?}
    //? if neoforge {
    /*
    public static final DeferredHolder<BlockEntityType<?>, BlockEntityType<ShowcaseBlockEntity>> BLOCK_ENTITY =
            BLOCK_ENTITIES.register("showcase", () -> BlockEntityType.Builder.of(ShowcaseBlockEntity::new,
                    MODELS.values().stream().map(DeferredHolder::get).toArray(Block[]::new)).build(null));
    *///?}

    public static void register(IEventBus bus) {
        BLOCKS.register(bus);
        ITEMS.register(bus);
        BLOCK_ENTITIES.register(bus);
        bus.addListener(ShowcaseRegistry::addCreative);
    }

    public static ShowcaseBlock block(ShowcaseModel model) { return MODELS.get(model).get(); }

    private static void addCreative(BuildCreativeModeTabContentsEvent event) {
        if (event.getTabKey() == CreativeModeTabs.BUILDING_BLOCKS) {
            for (ShowcaseModel model : ShowcaseModel.values()) event.accept(MODEL_ITEMS.get(model).get());
        }
    }

    private ShowcaseRegistry() { }
}
