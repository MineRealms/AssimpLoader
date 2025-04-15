package cn.minerealms.iic.forge;

import cn.minerealms.iic.commands.ImprovedMobsCommand;
import cn.minerealms.iic.industrial.IndustrialDifficultyManager;
import net.minecraftforge.event.RegisterCommandsEvent;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.event.server.ServerStartingEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

@Mod.EventBusSubscriber
public class EventHandler {

    @SubscribeEvent
    public static void onServerStarting(ServerStartingEvent event) {
        // Initialize systems
    }

    @SubscribeEvent
    public static void onRegisterCommands(RegisterCommandsEvent event) {
        ImprovedMobsCommand.register(event.getDispatcher());
    }

    @SubscribeEvent
    public static void onServerTick(TickEvent.ServerTickEvent event) {
        if (event.phase == TickEvent.Phase.END) {
            // Tick for each player
            event.getServer().getPlayerList().getPlayers().forEach(player -> {
                IndustrialDifficultyManager.tick(player);
            });
        }
    }
}
