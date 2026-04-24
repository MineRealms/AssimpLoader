package cn.minerealms.assimploader.lookdev;

import cn.minerealms.assimploader.AssimpLoader;
import cn.minerealms.assimploader.lookdev.ui.LookDevScreen;
import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.context.CommandContext;
import icyllis.modernui.mc.MuiModApi;
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
        CommandDispatcher<CommandSourceStack> dispatcher = event.getDispatcher();

        dispatcher.register(
            Commands.literal("lookdev")
                .executes(LookDevCommand::openLookDevScreen)
        );

        AssimpLoader.LOGGER.info("Registered /lookdev command");
    }

    /**
     * Open the LookDev viewer screen
     */
    private static int openLookDevScreen(CommandContext<CommandSourceStack> context) {
        try {
            // Create and open the LookDev screen using ModernUI
            LookDevScreen fragment = new LookDevScreen();
            MuiModApi.openScreen(fragment);

            context.getSource().sendSuccess(
                () -> Component.literal("Opening LookDev Viewer..."),
                false
            );

            AssimpLoader.LOGGER.info("LookDev screen opened");
            return 1;

        } catch (Exception e) {
            AssimpLoader.LOGGER.error("Failed to open LookDev screen", e);

            context.getSource().sendFailure(
                Component.literal("Failed to open LookDev Viewer: " + e.getMessage())
            );

            return 0;
        }
    }
}
