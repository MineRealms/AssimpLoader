package cn.minerealms.iic.command;

import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import com.mojang.brigadier.context.CommandContext;
import cn.minerealms.iic.network.PacketHandler;
import cn.minerealms.iic.network.SyncHudDataPacket;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;

/**
 * HUD display control commands.
 * /im hud toggle - Toggle HUD display
 * /im hud on - Enable HUD
 * /im hud off - Disable HUD
 */
public class HudCommand {

    public static LiteralArgumentBuilder<CommandSourceStack> register() {
        return Commands.literal("hud")
                .then(Commands.literal("toggle")
                        .executes(HudCommand::toggleHud))
                .then(Commands.literal("on")
                        .executes(ctx -> setHud(ctx, true)))
                .then(Commands.literal("off")
                        .executes(ctx -> setHud(ctx, false)))
                .executes(HudCommand::toggleHud);
    }

    private static int toggleHud(CommandContext<CommandSourceStack> ctx) {
        try {
            ServerPlayer player = ctx.getSource().getPlayerOrException();
            boolean currentState = cn.minerealms.iic.commands.HudCommands.isHudEnabled(player.getUUID());
            boolean newState = !currentState;

            // Send packet to update HUD state
            SyncHudDataPacket packet = new SyncHudDataPacket(
                    0.0, 0.0, 0.0, 0.0, 0, 0.0, 0.0, 0.0, 0.0,
                    0, 0, 0.0f, 0, 0, 0, 0.0,
                    0.0, "SAFE", 0.0, newState
            );
            PacketHandler.sendHudDataToPlayer(packet, player);

            String status = newState ? "§aON" : "§cOFF";
            ctx.getSource().sendSuccess(() ->
                    Component.literal(String.format("§6Difficulty HUD: %s", status)), false);

            return 1;
        } catch (Exception e) {
            ctx.getSource().sendFailure(Component.literal("§cError: " + e.getMessage()));
            return 0;
        }
    }

    private static int setHud(CommandContext<CommandSourceStack> ctx, boolean enabled) {
        try {
            ServerPlayer player = ctx.getSource().getPlayerOrException();

            // Send packet to update HUD state
            SyncHudDataPacket packet = new SyncHudDataPacket(
                    0.0, 0.0, 0.0, 0.0, 0, 0.0, 0.0, 0.0, 0.0,
                    0, 0, 0.0f, 0, 0, 0, 0.0,
                    0.0, "SAFE", 0.0, enabled
            );
            PacketHandler.sendHudDataToPlayer(packet, player);

            String status = enabled ? "§aenabled" : "§cdisabled";
            ctx.getSource().sendSuccess(() ->
                    Component.literal(String.format("§6Difficulty HUD %s", status)), false);

            return 1;
        } catch (Exception e) {
            ctx.getSource().sendFailure(Component.literal("§cError: " + e.getMessage()));
            return 0;
        }
    }
}
