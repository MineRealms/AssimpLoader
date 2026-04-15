package cn.minerealms.iic.commands;

import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.context.CommandContext;
import cn.minerealms.iic.industrial.SporeApiDiagnostics;
import cn.minerealms.iic.industrial.TriAxisConfig;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;

/**
 * Spore 调试命令
 * /im spore debug - 切换 Spore 调试信息开关
 * /im spore diagnose - 诊断 Spore API
 */
public class SporeDebugCommand {

    public static void register(CommandDispatcher<CommandSourceStack> dispatcher) {
        dispatcher.register(Commands.literal("im")
                .then(Commands.literal("spore")
                        .then(Commands.literal("debug")
                                .requires(src -> src.hasPermission(2))
                                .executes(SporeDebugCommand::toggleSporeDebug))
                        .then(Commands.literal("diagnose")
                                .requires(src -> src.hasPermission(2))
                                .executes(SporeDebugCommand::diagnoseSporeApi)))
        );
    }

    private static int toggleSporeDebug(CommandContext<CommandSourceStack> ctx) {
        try {
            // 切换开关
            TriAxisConfig.enableSporeDebug = !TriAxisConfig.enableSporeDebug;

            // 保存配置
            TriAxisConfig.save();

            // 发送反馈
            String messageKey = TriAxisConfig.enableSporeDebug ?
                    "integratedindustrialcraft.command.spore.debug.enabled" :
                    "integratedindustrialcraft.command.spore.debug.disabled";

            ctx.getSource().sendSuccess(() ->
                    Component.translatable(messageKey), true);

            return 1;
        } catch (Exception e) {
            ctx.getSource().sendFailure(Component.literal("§cError: " + e.getMessage()));
            return 0;
        }
    }

    private static int diagnoseSporeApi(CommandContext<CommandSourceStack> ctx) {
        try {
            ServerPlayer player = ctx.getSource().getPlayerOrException();

            ctx.getSource().sendSuccess(() ->
                    Component.literal("§6[Spore] Running API diagnostics... Check server console for details"), false);

            // 在服务端运行诊断
            SporeApiDiagnostics.diagnoseSporeApi(player.serverLevel());

            ctx.getSource().sendSuccess(() ->
                    Component.literal("§a[Spore] Diagnostics complete! Check server console"), false);

            return 1;
        } catch (Exception e) {
            ctx.getSource().sendFailure(Component.literal("§cError: " + e.getMessage()));
            e.printStackTrace();
            return 0;
        }
    }
}
