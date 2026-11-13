package cn.minerealms.assimploader.hotreload;

import com.mojang.brigadier.CommandDispatcher;
import net.minecraft.client.Minecraft;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.network.chat.Component;

/** Client command: changing local model resources does not require or modify a server. */
public final class ModelHotReloadCommand {
    private ModelHotReloadCommand() {}

    public static void register(CommandDispatcher<CommandSourceStack> dispatcher) {
        dispatcher.register(Commands.literal("assimploader").then(Commands.literal("reload").executes(context -> {
            if (!ModelHotReloadManager.isEnabled()) {
                context.getSource().sendFailure(Component.literal("AssimpLoader model hot reload is disabled."));
                return 0;
            }
            var source = context.getSource();
            source.sendSuccess(() -> Component.literal("Reloading external Assimp models..."), false);
            ModelHotReloadManager.reloadModelsAsync().whenComplete((count, failure) -> Minecraft.getInstance().execute(() -> {
                if (failure == null) {
                    source.sendSuccess(() -> Component.literal("Reloaded " + count + " external model variants."), false);
                } else {
                    Throwable cause = failure.getCause() != null ? failure.getCause() : failure;
                    source.sendFailure(Component.literal("Model reload failed: " + cause.getMessage()));
                }
            }));
            return 1;
        })));
    }
}
