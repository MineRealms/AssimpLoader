package cn.minerealms.assimploader.example;

import cn.minerealms.assimploader.AssimpLoader;
import cn.minerealms.assimploader.animation.ClientAnimationManager;
import cn.minerealms.assimploader.debug.DebugManager;
import cn.minerealms.assimploader.debug.DebugManager.DebugCategory;
import cn.minerealms.assimploader.render.AssimpItemRenderer;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.BlockEntityWithoutLevelRenderer;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.client.event.EntityRenderersEvent;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.event.lifecycle.FMLClientSetupEvent;

/**
 * Client-side event handlers for example blocks
 */
@Mod.EventBusSubscriber(modid = AssimpLoader.MODID, bus = Mod.EventBusSubscriber.Bus.MOD, value = Dist.CLIENT)
public class ExampleClientEvents {

    private static AssimpItemRenderer itemRenderer;

    @SubscribeEvent
    public static void registerRenderers(EntityRenderersEvent.RegisterRenderers event) {
        event.registerBlockEntityRenderer(
            ExampleRegistry.TEST_MODEL_BLOCK_ENTITY.get(),
            TestModelBlockEntityRenderer::new
        );
        event.registerBlockEntityRenderer(
            ExampleRegistry.CCB_BLOCK_ENTITY.get(),
            CCBBlockEntityRenderer::new
        );
        event.registerBlockEntityRenderer(
            ExampleRegistry.CIRCLE_BLOCK_ENTITY.get(),
            CircleBlockEntityRenderer::new
        );
        DebugManager.info(DebugCategory.CORE, "Registered block entity renderers: TestModel, CCB, Circle");
    }

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
        // Update animation manager once per frame (at END phase to avoid double-ticking)
        if (event.phase == TickEvent.Phase.END) {
            ClientAnimationManager.getInstance().tick();
        }
    }
}
