package cn.minerealms.iic.command;

import com.mojang.brigadier.CommandDispatcher;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;

/**
 * Root command for Integrated Industrial Craft.
 * All IIC commands are registered under /im
 */
public class IICCommand {

    public static void register(CommandDispatcher<CommandSourceStack> dispatcher) {
        dispatcher.register(Commands.literal("im")
                .then(ConfigCommand.register())
                .then(PollutionCommand.register())
                .then(DifficultyCommand.register())
                .then(HudCommand.register())
                .then(DebugCommand.register())
                .then(HordeCommand.register())
        );
    }
}
