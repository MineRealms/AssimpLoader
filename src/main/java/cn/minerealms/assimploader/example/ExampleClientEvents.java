package cn.minerealms.assimploader.example;

import cn.minerealms.assimploader.AssimpLoader;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.client.event.EntityRenderersEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

/**
 * Client-side event handler for registering renderers.
 */
@Mod.EventBusSubscriber(modid = AssimpLoader.MODID, bus = Mod.EventBusSubscriber.Bus.MOD, value = Dist.CLIENT)
public class ExampleClientEvents {

    @SubscribeEvent
    public static void registerRenderers(EntityRenderersEvent.RegisterRenderers event) {
        event.registerBlockEntityRenderer(
            ExampleRegistry.CNC_MACHINE_BLOCK_ENTITY.get(),
            CNCMachineRenderer::new
        );

        AssimpLoader.LOGGER.info("Registered CNC Machine BlockEntityRenderer");
    }
}
