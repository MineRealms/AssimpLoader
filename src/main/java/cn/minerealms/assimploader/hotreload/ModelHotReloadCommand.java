package cn.minerealms.assimploader.hotreload;

import cn.minerealms.assimploader.AssimpLoader;
import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.context.CommandContext;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.network.chat.Component;

/**
 * Command for hot-reloading FBX models from external directory
 */
public class ModelHotReloadCommand {

    public static void register(CommandDispatcher<CommandSourceStack> dispatcher) {
        dispatcher.register(
            Commands.literal("assimploader")
                .then(Commands.literal("reload")
                    .requires(source -> source.hasPermission(2)) // Require OP level 2
                    .executes(ModelHotReloadCommand::executeReload)
                )
        );
    }

    private static int executeReload(CommandContext<CommandSourceStack> context) {
        CommandSourceStack source = context.getSource();

        try {
            source.sendSuccess(() -> Component.literal("§e[AssimpLoader] Starting model hot-reload..."), true);

            // Trigger hot reload
            int reloadedCount = ModelHotReloadManager.reloadModels();

            source.sendSuccess(() -> Component.literal(
                String.format("§a[AssimpLoader] Successfully reloaded %d model(s)!", reloadedCount)
            ), true);

            return reloadedCount;
        } catch (Exception e) {
            AssimpLoader.LOGGER.error("Failed to hot-reload models", e);
            source.sendFailure(Component.literal(
                "§c[AssimpLoader] Failed to reload models: " + e.getMessage()
            ));
            return 0;
        }
    }
}
