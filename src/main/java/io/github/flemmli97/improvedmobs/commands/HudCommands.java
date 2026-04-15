package io.github.flemmli97.improvedmobs.commands;

import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.context.CommandContext;
import io.github.flemmli97.improvedmobs.industrial.TriAxisConfig;
import io.github.flemmli97.improvedmobs.network.SyncHudDataPacket;
import io.github.flemmli97.improvedmobs.forge.network.PacketHandler;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

/**
 * HUD显示控制指令
 * /im hud toggle - 切换HUD显示
 * /im hud on - 开启HUD
 * /im hud off - 关闭HUD
 */
public class HudCommands {

    // 服务端存储每个玩家的HUD显示状态
    private static final Map<UUID, Boolean> playerHudState = new HashMap<>();

    public static void register(CommandDispatcher<CommandSourceStack> dispatcher) {
        dispatcher.register(Commands.literal("im")
                .then(Commands.literal("hud")
                        .then(Commands.literal("toggle").executes(HudCommands::toggleHud))
                        .then(Commands.literal("on").executes(ctx -> setHud(ctx, true)))
                        .then(Commands.literal("off").executes(ctx -> setHud(ctx, false)))
                        .executes(HudCommands::toggleHud))
        );
    }

    private static int toggleHud(CommandContext<CommandSourceStack> ctx) {
        try {
            ServerPlayer player = ctx.getSource().getPlayerOrException();
            UUID playerId = player.getUUID();

            boolean currentState = playerHudState.getOrDefault(playerId, false);
            boolean newState = !currentState;
            playerHudState.put(playerId, newState);

            // 发送空数据包，只更新显示状态
            SyncHudDataPacket packet = new SyncHudDataPacket(
                    0.0, 0.0, 0.0, 0.0, 0, 0.0, 0.0, 0.0, 0.0,
                    0, 0, 0.0f, 0, 0, 0.0,
                    0.0, "SAFE", 0.0, newState
            );
            PacketHandler.sendHudDataToPlayer(packet, player);

            String status = newState ? "§aON" : "§cOFF";
            ctx.getSource().sendSuccess(() ->
                    Component.literal(String.format("§6Difficulty HUD: %s", status)), false);

            return 1;
        } catch (Exception e) {
            ctx.getSource().sendFailure(Component.literal("§cError: Command must be executed by a player"));
            return 0;
        }
    }

    private static int setHud(CommandContext<CommandSourceStack> ctx, boolean enabled) {
        try {
            ServerPlayer player = ctx.getSource().getPlayerOrException();
            UUID playerId = player.getUUID();
            playerHudState.put(playerId, enabled);

            SyncHudDataPacket packet = new SyncHudDataPacket(
                    0.0, 0.0, 0.0, 0.0, 0, 0.0, 0.0, 0.0, 0.0,
                    0, 0, 0.0f, 0, 0, 0.0,
                    0.0, "SAFE", 0.0, enabled
            );
            PacketHandler.sendHudDataToPlayer(packet, player);

            String status = enabled ? "§aenabled" : "§cdisabled";
            ctx.getSource().sendSuccess(() ->
                    Component.literal(String.format("§6Difficulty HUD %s", status)), false);

            return 1;
        } catch (Exception e) {
            ctx.getSource().sendFailure(Component.literal("§cError: Command must be executed by a player"));
            return 0;
        }
    }

    public static boolean isHudEnabled(UUID playerId) {
        return playerHudState.getOrDefault(playerId, false);
    }

    public static void clearPlayerData(UUID playerId) {
        playerHudState.remove(playerId);
    }
}
