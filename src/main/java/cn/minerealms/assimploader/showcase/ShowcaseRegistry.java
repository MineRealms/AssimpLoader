package cn.minerealms.assimploader.showcase;

import cn.minerealms.assimploader.AssimpLoader;
import cn.minerealms.assimploader.example.AssimpBlockItem;
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

import java.util.EnumMap;
import java.util.Map;

public final class ShowcaseRegistry {
    private static final DeferredRegister<Block> BLOCKS = DeferredRegister.create(ForgeRegistries.BLOCKS, AssimpLoader.MODID);
    private static final DeferredRegister<Item> ITEMS = DeferredRegister.create(ForgeRegistries.ITEMS, AssimpLoader.MODID);
    private static final DeferredRegister<BlockEntityType<?>> BLOCK_ENTITIES =
            DeferredRegister.create(ForgeRegistries.BLOCK_ENTITY_TYPES, AssimpLoader.MODID);
    private static final Map<ShowcaseModel, RegistryObject<ShowcaseBlock>> MODELS = new EnumMap<>(ShowcaseModel.class);
    private static final Map<ShowcaseModel, RegistryObject<Item>> MODEL_ITEMS = new EnumMap<>(ShowcaseModel.class);

    static {
        for (ShowcaseModel model : ShowcaseModel.values()) {
            RegistryObject<ShowcaseBlock> block = BLOCKS.register(model.registryName(), () -> new ShowcaseBlock(model,
                    BlockBehaviour.Properties.of().mapColor(MapColor.METAL).strength(2.0f)
                            .sound(SoundType.METAL).noOcclusion()));
            MODELS.put(model, block);
            MODEL_ITEMS.put(model, ITEMS.register(model.registryName(),
                    () -> new AssimpBlockItem(block.get(), new Item.Properties(), model.modelName())));
        }
    }

    public static final RegistryObject<BlockEntityType<ShowcaseBlockEntity>> BLOCK_ENTITY =
            BLOCK_ENTITIES.register("showcase", () -> BlockEntityType.Builder.of(ShowcaseBlockEntity::new,
                    MODELS.values().stream().map(RegistryObject::get).toArray(Block[]::new)).build(null));

    public static void register(IEventBus bus) {
        BLOCKS.register(bus);
        ITEMS.register(bus);
        BLOCK_ENTITIES.register(bus);
        bus.addListener(ShowcaseRegistry::addCreative);
    }

    public static ShowcaseBlock block(ShowcaseModel model) { return MODELS.get(model).get(); }

    private static void addCreative(BuildCreativeModeTabContentsEvent event) {
        if (event.getTabKey() == CreativeModeTabs.BUILDING_BLOCKS) {
            for (ShowcaseModel model : ShowcaseModel.values()) event.accept(MODEL_ITEMS.get(model));
        }
    }

    private ShowcaseRegistry() { }
}
