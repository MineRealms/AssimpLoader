package cn.minerealms.iic.scanner;

import io.github.flemmli97.improvedmobs.ImprovedMobs;
import cn.minerealms.iic.scanner.client.ScannerScreen;
import net.minecraft.client.Minecraft;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResultHolder;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.UseAnim;
import net.minecraft.world.level.Level;

public class ScannerItem extends Item {

    public static final String REGISTRY_NAME = "terrain_scanner";
    public static Item INSTANCE;

    public ScannerItem() {
        super(new Properties().stacksTo(1));
    }

    @Override
    public UseAnim getUseAnimation(ItemStack stack) {
        return UseAnim.CUSTOM;
    }

    @Override
    public InteractionResultHolder<ItemStack> use(Level level, Player player, InteractionHand hand) {
        if (level.isClientSide) {
            Minecraft.getInstance().setScreen(new ScannerScreen());
            return InteractionResultHolder.consume(player.getItemInHand(hand));
        }
        return InteractionResultHolder.pass(player.getItemInHand(hand));
    }
}