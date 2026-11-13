package cn.minerealms.assimploader.lookdev;

import cn.minerealms.assimploader.AssimpLoader;
import cn.minerealms.assimploader.debug.DebugManager;
import cn.minerealms.assimploader.debug.DebugManager.DebugCategory;
import cn.minerealms.assimploader.lookdev.ui.LoadedModelsScreen;
import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.context.CommandContext;
import net.minecraft.client.Minecraft;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.network.chat.Component;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.client.event.RegisterClientCommandsEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

/**
 * Command to open the LookDev viewer screen.
 *
 * Usage: /lookdev
 */
@Mod.EventBusSubscriber(modid = AssimpLoader.MODID, bus = Mod.EventBusSubscriber.Bus.FORGE, value = Dist.CLIENT)
public class LookDevCommand {

    @SubscribeEvent
    public static void onRegisterClientCommands(RegisterClientCommandsEvent event) {
        try {
            CommandDispatcher<CommandSourceStack> dispatcher = event.getDispatcher();

            dispatcher.register(
                Commands.literal("lookdev")
                    .requires(source -> cn.minerealms.assimploader.config.AssimpLoaderConfig.getOrDefault(
                            cn.minerealms.assimploader.config.AssimpLoaderConfig.CLIENT.devLookdevEnabled::get, true))
                    .executes(LookDevCommand::openLookDevScreen)
            );

            DebugManager.info(DebugCategory.LOOKDEV, "Registered /lookdev command");
        } catch (Exception e) {
            DebugManager.error(DebugCategory.LOOKDEV, "Failed to register /lookdev command", e);
        }
    }

    /**
     * Open the LookDev viewer screen
     */
    private static int openLookDevScreen(CommandContext<CommandSourceStack> context) {
        try {
            DebugManager.info(DebugCategory.LOOKDEV, "Opening LookDev screen via command");

            Minecraft.getInstance().execute(() -> Minecraft.getInstance().setScreen(new LoadedModelsScreen()));

            DebugManager.info(DebugCategory.LOOKDEV, "LookDev screen opened successfully");
            return 1;

        } catch (Exception e) {
            DebugManager.error(DebugCategory.LOOKDEV, "Failed to open LookDev screen", e);

            context.getSource().sendFailure(
                Component.literal("Failed to open LookDev Viewer: " + e.getMessage())
            );

            return 0;
        }
    }
}
