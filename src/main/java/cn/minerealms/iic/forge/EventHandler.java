package cn.minerealms.iic.forge;

import cn.minerealms.iic.command.IICCommand;
import cn.minerealms.iic.commands.ImprovedMobsCommand;
import cn.minerealms.iic.industrial.IndustrialDifficultyManager;
import cn.minerealms.iic.industrial.HordeIntegrationManager;
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
        // Register old command system (for backward compatibility)
        ImprovedMobsCommand.register(event.getDispatcher());

        // Register new unified command system
        IICCommand.register(event.getDispatcher());
    }

    @SubscribeEvent
    public static void onServerTick(TickEvent.ServerTickEvent event) {
        if (event.phase == TickEvent.Phase.END) {
            // Tick for each player
            event.getServer().getPlayerList().getPlayers().forEach(player -> {
                IndustrialDifficultyManager.tick(player);
            });

            // Tick Hordes integration for each level
            event.getServer().getAllLevels().forEach(level -> {
                HordeIntegrationManager.tick(level);
            });
        }
    }
}
