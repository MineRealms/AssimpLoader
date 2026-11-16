package cn.minerealms.assimploader.example;

import cn.minerealms.assimploader.AssimpLoader;
import cn.minerealms.assimploader.animation.ClientAnimationManager;
import cn.minerealms.assimploader.debug.DebugManager;
import cn.minerealms.assimploader.debug.DebugManager.DebugCategory;
import cn.minerealms.assimploader.render.AssimpItemRenderer;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.BlockEntityWithoutLevelRenderer;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

/**
 * Shared client renderer for Assimp model items.
 */
public class ExampleClientEvents {

    private static AssimpItemRenderer itemRenderer;

    public static BlockEntityWithoutLevelRenderer getItemRenderer() {
        if (itemRenderer == null) {
            itemRenderer = new AssimpItemRenderer(Minecraft.getInstance().getBlockEntityRenderDispatcher());
            DebugManager.info(DebugCategory.CORE, "Lazy-initialized AssimpItemRenderer for FBX items");
        }
        return itemRenderer;
    }
}

/**
 * Forge event handlers for client tick (must be on FORGE bus, not MOD bus)
 */
@Mod.EventBusSubscriber(modid = AssimpLoader.MODID, bus = Mod.EventBusSubscriber.Bus.FORGE, value = Dist.CLIENT)
class ClientTickHandler {
    @SubscribeEvent
    public static void onClientTick(TickEvent.ClientTickEvent event) {
        // Advance simulation once per client tick; render calls only consume snapshots.
        if (event.phase == TickEvent.Phase.END) {
            ClientAnimationManager.getInstance().tick();
        }
    }
}
