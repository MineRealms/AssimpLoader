package cn.minerealms.iic.turrets.common.item;

import cn.minerealms.iic.turrets.client.model.DefaultedBlockItemGeoModel;
import cn.minerealms.iic.turrets.common.block.FlameThrowerTurretBlock;
import cn.minerealms.iic.turrets.common.block_entity.FlameThrowerTurretTier;
import mekanism.common.block.attribute.Attribute;
import mekanism.common.item.block.machine.ItemBlockMachine;
import net.minecraft.client.renderer.BlockEntityWithoutLevelRenderer;
import net.minecraftforge.client.extensions.common.IClientItemExtensions;
import net.minecraftforge.registries.ForgeRegistries;
import software.bernie.geckolib.animatable.GeoItem;
import software.bernie.geckolib.core.animatable.instance.AnimatableInstanceCache;
import software.bernie.geckolib.core.animation.AnimatableManager;
import software.bernie.geckolib.renderer.GeoItemRenderer;
import software.bernie.geckolib.util.GeckoLibUtil;

import java.util.function.Consumer;

/**
 * 火焰喷射器炮塔物品
 */
public class FlameThrowerTurretBlockItem extends ItemBlockMachine implements GeoItem {

    private BlockEntityWithoutLevelRenderer renderer;
    private final AnimatableInstanceCache cache = GeckoLibUtil.createInstanceCache(this);

    public FlameThrowerTurretBlockItem(FlameThrowerTurretBlock block) {
        super(block);
    }

    @Override
    public FlameThrowerTurretTier getTier() {
        return Attribute.getTier(getBlock(), FlameThrowerTurretTier.class);
    }

    @Override
    public void initializeClient(Consumer<IClientItemExtensions> consumer) {
        consumer.accept(new IClientItemExtensions() {

            @Override
            public BlockEntityWithoutLevelRenderer getCustomRenderer() {
                if (renderer == null) {
                    renderer = new GeoItemRenderer<GeckoBlockItem>(new DefaultedBlockItemGeoModel<>(ForgeRegistries.ITEMS.getKey(FlameThrowerTurretBlockItem.this)));
                }
                return renderer;
            }
        });
    }

    @Override
    public void registerControllers(AnimatableManager.ControllerRegistrar controllers) {

    }

    @Override
    public AnimatableInstanceCache getAnimatableInstanceCache() {
        return cache;
    }
}
