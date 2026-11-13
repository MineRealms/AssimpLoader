package cn.minerealms.assimploader.hotreload;

import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.client.event.RegisterClientCommandsEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

/**
 * Event handler for registering hot-reload commands
 */
@Mod.EventBusSubscriber(modid = "assimploader", bus = Mod.EventBusSubscriber.Bus.FORGE, value = Dist.CLIENT)
public class HotReloadEventHandler {

    @SubscribeEvent
    public static void onRegisterCommands(RegisterClientCommandsEvent event) {
        ModelHotReloadCommand.register(event.getDispatcher());
    }
}
