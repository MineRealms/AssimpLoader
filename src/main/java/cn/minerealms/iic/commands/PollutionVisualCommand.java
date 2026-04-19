package cn.minerealms.iic.commands;

import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.context.CommandContext;
import cn.minerealms.iic.industrial.PollutionVisualEffects;
import cn.minerealms.iic.network.PacketHandler;
import cn.minerealms.iic.network.TriggerPollutionEffectPacket;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;

/**
 * Pollution Visual Effects Debug Command
 * /im pollution-visual debug - Toggle debug logging
 * /im pollution-visual test <type> - Test specific effect
 * /im pollution-visual diagnostics - Show diagnostic info
 */
public class PollutionVisualCommand {

    public static void register(CommandDispatcher<CommandSourceStack> dispatcher) {
        dispatcher.register(Commands.literal("im")
                .then(Commands.literal("pollution-visual")
                        .then(Commands.literal("debug")
                                .requires(src -> src.hasPermission(2))
                                .executes(PollutionVisualCommand::toggleDebug))
                        .then(Commands.literal("test")
                                .requires(src -> src.hasPermission(2))
                                .then(Commands.argument("type", StringArgumentType.word())
                                        .suggests((ctx, builder) -> {
                                            builder.suggest("light");
                                            builder.suggest("moderate");
                                            builder.suggest("heavy");
                                            builder.suggest("severe");
                                            builder.suggest("all");
                                            return builder.buildFuture();
                                        })
                                        .executes(PollutionVisualCommand::testEffect)))
                        .then(Commands.literal("diagnostics")
                                .requires(src -> src.hasPermission(2))
                                .executes(PollutionVisualCommand::showDiagnostics)))
        );
    }

    /**
     * Toggle debug logging
     */
    private static int toggleDebug(CommandContext<CommandSourceStack> ctx) {
        try {
            boolean newState = !PollutionVisualEffects.isDebugEnabled();
            PollutionVisualEffects.setDebugEnabled(newState);

            String messageKey = newState ?
                    "integratedindustrialcraft.command.pollution_visual.debug.enabled" :
                    "integratedindustrialcraft.command.pollution_visual.debug.disabled";

            ctx.getSource().sendSuccess(() ->
                    Component.translatable(messageKey), true);

            return 1;
        } catch (Exception e) {
            ctx.getSource().sendFailure(Component.literal("§cError: " + e.getMessage()));
            return 0;
        }
    }

    /**
     * Test specific effect type
     */
    private static int testEffect(CommandContext<CommandSourceStack> ctx) {
        try {
            ServerPlayer player = ctx.getSource().getPlayerOrException();
            String effectType = StringArgumentType.getString(ctx, "type");

            ctx.getSource().sendSuccess(() ->
                    Component.literal(String.format(
                            "§6[Pollution Visual] Triggering '%s' effect...", effectType)), false);

            // Send packet to client to trigger effect
            PacketHandler.sendToClient(new TriggerPollutionEffectPacket(effectType), player);

            ctx.getSource().sendSuccess(() ->
                    Component.literal(String.format(
                            "§a[Pollution Visual] Effect '%s' triggered successfully!", effectType)), false);

            return 1;
        } catch (Exception e) {
            ctx.getSource().sendFailure(Component.literal("§cError: " + e.getMessage()));
            e.printStackTrace();
            return 0;
        }
    }

    /**
     * Show diagnostic information
     */
    private static int showDiagnostics(CommandContext<CommandSourceStack> ctx) {
        try {
            String diagnostics = PollutionVisualEffects.getDiagnostics();

            ctx.getSource().sendSuccess(() ->
                    Component.literal("§b" + diagnostics), false);

            return 1;
        } catch (Exception e) {
            ctx.getSource().sendFailure(Component.literal("§cError: " + e.getMessage()));
            e.printStackTrace();
            return 0;
        }
    }
}
