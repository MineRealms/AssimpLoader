package cn.minerealms.assimpshowcase.example;

import net.minecraft.client.renderer.BlockEntityWithoutLevelRenderer;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.level.block.Block;
import net.minecraftforge.client.extensions.common.IClientItemExtensions;

import java.util.function.Consumer;

/**
 * Custom BlockItem that uses AssimpItemRenderer for rendering.
 * This allows FBX models with embedded textures to render correctly in hand and inventory.
 */
public class AssimpBlockItem extends BlockItem implements cn.minerealms.assimploader.render.AssimpModelItem {

    private final String modelName;

    public AssimpBlockItem(Block block, Properties properties) {
        this(block, properties, null);
    }

    public AssimpBlockItem(Block block, Properties properties, String modelName) {
        super(block, properties);
        this.modelName = modelName;
    }

    public String getModelName() {
        return modelName;
    }

    @Override
    public void initializeClient(Consumer<IClientItemExtensions> consumer) {
        consumer.accept(new IClientItemExtensions() {
            @Override
            public BlockEntityWithoutLevelRenderer getCustomRenderer() {
                return ExampleClientEvents.getItemRenderer();
            }
        });
    }
}
