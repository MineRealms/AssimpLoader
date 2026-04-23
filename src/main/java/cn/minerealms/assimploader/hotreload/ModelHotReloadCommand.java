package cn.minerealms.assimploader.hotreload;

import cn.minerealms.assimploader.AssimpLoader;
import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.context.CommandContext;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.fml.DistExecutor;

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

        // Check if we're on the client side
        if (source.getLevel().isClientSide()) {
            source.sendFailure(Component.literal("§c[AssimpLoader] This command must be run on the server!"));
            return 0;
        }

        source.sendSuccess(() -> Component.literal("§e[AssimpLoader] Starting model hot-reload..."), true);

        // Execute on client thread via DistExecutor
        DistExecutor.unsafeRunWhenOn(Dist.CLIENT, () -> () -> {
            try {
                // Run on client's main thread
                net.minecraft.client.Minecraft.getInstance().execute(() -> {
                    try {
                        int reloadedCount = ModelHotReloadManager.reloadModels();

                        source.sendSuccess(() -> Component.literal(
                            String.format("§a[AssimpLoader] Successfully reloaded %d model(s)!", reloadedCount)
                        ), true);
                    } catch (Exception e) {
                        AssimpLoader.LOGGER.error("Failed to hot-reload models", e);
                        source.sendFailure(Component.literal(
                            "§c[AssimpLoader] Failed to reload models: " + e.getMessage()
                        ));
                    }
                });
            } catch (Exception e) {
                AssimpLoader.LOGGER.error("Failed to schedule hot-reload on client thread", e);
                source.sendFailure(Component.literal(
                    "§c[AssimpLoader] Failed to schedule reload: " + e.getMessage()
                ));
            }
        });

        return 1;
    }
}
