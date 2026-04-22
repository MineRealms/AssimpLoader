package cn.minerealms.assimploader.hotreload;

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
        ModelHotReloadCommand.register(event.getDispatcher());
    }
}
