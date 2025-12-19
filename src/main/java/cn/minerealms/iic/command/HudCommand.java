package cn.minerealms.iic.command;

import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import com.mojang.brigadier.context.CommandContext;
import cn.minerealms.iic.network.PacketHandler;
import cn.minerealms.iic.network.SyncHudDataPacket;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

/**
 * HUD display control commands.
 * /im hud toggle - Toggle HUD display
 * /im hud on - Enable HUD
 * /im hud off - Disable HUD
 */
public class HudCommand {

    // Server-side storage for each player's HUD display state
    private static final Map<UUID, Boolean> playerHudState = new HashMap<>();

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
            UUID playerId = player.getUUID();

            boolean currentState = playerHudState.getOrDefault(playerId, false);
            boolean newState = !currentState;
            playerHudState.put(playerId, newState);

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
            UUID playerId = player.getUUID();
            playerHudState.put(playerId, enabled);

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

    /**
     * Check if HUD is enabled for a player.
     *
     * @param playerId the player's UUID
     * @return true if HUD is enabled, false otherwise
     */
    public static boolean isHudEnabled(UUID playerId) {
        return playerHudState.getOrDefault(playerId, false);
    }

    /**
     * Clear HUD state for a player (called on logout).
     *
     * @param playerId the player's UUID
     */
    public static void clearPlayerData(UUID playerId) {
        playerHudState.remove(playerId);
    }
}
