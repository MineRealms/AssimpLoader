package cn.minerealms.assimploader.hotreload;

import cn.minerealms.assimploader.AssimpLoader;
import net.minecraftforge.event.RegisterCommandsEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

/**
 * Event handler for registering hot-reload commands
 */
@Mod.EventBusSubscriber(modid = "assimploader", bus = Mod.EventBusSubscriber.Bus.FORGE)
public class HotReloadEventHandler {

    @SubscribeEvent
    public static void onRegisterCommands(RegisterCommandsEvent event) {
        AssimpLoader.LOGGER.info("Registering hot-reload commands...");
        ModelHotReloadCommand.register(event.getDispatcher());
        AssimpLoader.LOGGER.info("Hot-reload commands registered successfully");
    }
}
