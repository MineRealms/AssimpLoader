package cn.minerealms.iic.command;

import com.mojang.brigadier.arguments.DoubleArgumentType;
import com.mojang.brigadier.arguments.IntegerArgumentType;
import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import com.mojang.brigadier.context.CommandContext;
import cn.minerealms.iic.difficulty.HazardScanner;
import cn.minerealms.iic.pollution.PollutionManager;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.commands.arguments.coordinates.BlockPosArgument;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.ChunkPos;

/**
 * Pollution manipulation commands.
 * /im pollution add <amount> [pos] - Add temporary pollution to chunk
 * /im pollution remove <amount> [pos] - Remove temporary pollution from chunk
 * /im pollution set <amount> [pos] - Set temporary pollution in chunk
 * /im pollution clear [radius] - Clear temporary pollution in area
 * /im pollution permanent add <amount> - Add permanent pollution
 * /im pollution permanent remove <amount> - Remove permanent pollution
 * /im pollution permanent set <amount> - Set permanent pollution
 * /im pollution status [pos] - Show pollution status
 */
public class PollutionCommand {

    public static LiteralArgumentBuilder<CommandSourceStack> register() {
        return Commands.literal("pollution").requires(src -> src.hasPermission(2))
                // Add temporary pollution
                .then(Commands.literal("add")
                        .then(Commands.argument("amount", DoubleArgumentType.doubleArg(0.0, 10000.0))
                                .executes(ctx -> addPollution(ctx, DoubleArgumentType.getDouble(ctx, "amount"), null))
                                .then(Commands.argument("pos", BlockPosArgument.blockPos())
                                        .executes(ctx -> addPollution(ctx, DoubleArgumentType.getDouble(ctx, "amount"),
                                                BlockPosArgument.getBlockPos(ctx, "pos"))))))

                // Remove temporary pollution
                .then(Commands.literal("remove")
                        .then(Commands.argument("amount", DoubleArgumentType.doubleArg(0.0, 10000.0))
                                .executes(ctx -> removePollution(ctx, DoubleArgumentType.getDouble(ctx, "amount"), null))
                                .then(Commands.argument("pos", BlockPosArgument.blockPos())
                                        .executes(ctx -> removePollution(ctx, DoubleArgumentType.getDouble(ctx, "amount"),
                                                BlockPosArgument.getBlockPos(ctx, "pos"))))))

                // Set temporary pollution
                .then(Commands.literal("set")
                        .then(Commands.argument("amount", DoubleArgumentType.doubleArg(0.0, 10000.0))
                                .executes(ctx -> setPollution(ctx, DoubleArgumentType.getDouble(ctx, "amount"), null))
                                .then(Commands.argument("pos", BlockPosArgument.blockPos())
                                        .executes(ctx -> setPollution(ctx, DoubleArgumentType.getDouble(ctx, "amount"),
                                                BlockPosArgument.getBlockPos(ctx, "pos"))))))

                // Clear temporary pollution in area
                .then(Commands.literal("clear")
                        .executes(ctx -> clearPollution(ctx, 5))
                        .then(Commands.argument("radius", IntegerArgumentType.integer(1, 32))
                                .executes(ctx -> clearPollution(ctx, IntegerArgumentType.getInteger(ctx, "radius")))))

                // Permanent pollution commands
                .then(Commands.literal("permanent")
                        .then(Commands.literal("add")
                                .then(Commands.argument("amount", DoubleArgumentType.doubleArg(0.0, 10000.0))
                                        .executes(ctx -> addPermanentPollution(ctx, DoubleArgumentType.getDouble(ctx, "amount")))))
                        .then(Commands.literal("remove")
                                .then(Commands.argument("amount", DoubleArgumentType.doubleArg(0.0, 10000.0))
                                        .executes(ctx -> removePermanentPollution(ctx, DoubleArgumentType.getDouble(ctx, "amount")))))
                        .then(Commands.literal("set")
                                .then(Commands.argument("amount", DoubleArgumentType.doubleArg(0.0, 10000.0))
                                        .executes(ctx -> setPermanentPollution(ctx, DoubleArgumentType.getDouble(ctx, "amount")))))
                        .executes(PollutionCommand::showPermanentPollution))

                // Show pollution status
                .then(Commands.literal("status")
                        .executes(ctx -> showStatus(ctx, null))
                        .then(Commands.argument("pos", BlockPosArgument.blockPos())
                                .executes(ctx -> showStatus(ctx, BlockPosArgument.getBlockPos(ctx, "pos")))))

                // Default: show status
                .executes(ctx -> showStatus(ctx, null));
    }

    private static int addPollution(CommandContext<CommandSourceStack> ctx, double amount, BlockPos pos) {
        try {
            ServerPlayer player = ctx.getSource().getPlayerOrException();
            ServerLevel level = player.serverLevel();
            BlockPos targetPos = pos != null ? pos : player.blockPosition();
            ChunkPos chunkPos = new ChunkPos(targetPos);

            double current = PollutionManager.getChunkPollution(level, chunkPos);
            double newAmount = current + amount;
            PollutionManager.setChunkPollution(level, chunkPos, newAmount);

            ctx.getSource().sendSuccess(() ->
                    Component.literal(String.format("§a✓ Added §e%.1f §apollution to chunk [%d, %d]",
                            amount, chunkPos.x, chunkPos.z)), true);
            ctx.getSource().sendSuccess(() ->
                    Component.literal(String.format("§7  Previous: §f%.1f §7→ New: §f%.1f", current, newAmount)), false);
            return 1;
        } catch (Exception e) {
            ctx.getSource().sendFailure(Component.literal("§cError: " + e.getMessage()));
            return 0;
        }
    }

    private static int removePollution(CommandContext<CommandSourceStack> ctx, double amount, BlockPos pos) {
        try {
            ServerPlayer player = ctx.getSource().getPlayerOrException();
            ServerLevel level = player.serverLevel();
            BlockPos targetPos = pos != null ? pos : player.blockPosition();
            ChunkPos chunkPos = new ChunkPos(targetPos);

            double current = PollutionManager.getChunkPollution(level, chunkPos);
            double newAmount = Math.max(0, current - amount);
            PollutionManager.setChunkPollution(level, chunkPos, newAmount);

            ctx.getSource().sendSuccess(() ->
                    Component.literal(String.format("§a✓ Removed §e%.1f §apollution from chunk [%d, %d]",
                            amount, chunkPos.x, chunkPos.z)), true);
            ctx.getSource().sendSuccess(() ->
                    Component.literal(String.format("§7  Previous: §f%.1f §7→ New: §f%.1f", current, newAmount)), false);
            return 1;
        } catch (Exception e) {
            ctx.getSource().sendFailure(Component.literal("§cError: " + e.getMessage()));
            return 0;
        }
    }

    private static int setPollution(CommandContext<CommandSourceStack> ctx, double amount, BlockPos pos) {
        try {
            ServerPlayer player = ctx.getSource().getPlayerOrException();
            ServerLevel level = player.serverLevel();
            BlockPos targetPos = pos != null ? pos : player.blockPosition();
            ChunkPos chunkPos = new ChunkPos(targetPos);

            double current = PollutionManager.getChunkPollution(level, chunkPos);
            PollutionManager.setChunkPollution(level, chunkPos, amount);

            ctx.getSource().sendSuccess(() ->
                    Component.literal(String.format("§a✓ Set pollution in chunk [%d, %d] to §e%.1f",
                            chunkPos.x, chunkPos.z, amount)), true);
            ctx.getSource().sendSuccess(() ->
                    Component.literal(String.format("§7  Previous: §f%.1f", current)), false);
            return 1;
        } catch (Exception e) {
            ctx.getSource().sendFailure(Component.literal("§cError: " + e.getMessage()));
            return 0;
        }
    }

    private static int clearPollution(CommandContext<CommandSourceStack> ctx, int radius) {
        try {
            ServerPlayer player = ctx.getSource().getPlayerOrException();
            ServerLevel level = player.serverLevel();
            ChunkPos centerChunk = new ChunkPos(player.blockPosition());

            int cleared = 0;
            double totalRemoved = 0;

            for (int x = -radius; x <= radius; x++) {
                for (int z = -radius; z <= radius; z++) {
                    ChunkPos chunkPos = new ChunkPos(centerChunk.x + x, centerChunk.z + z);
                    double pollution = PollutionManager.getChunkPollution(level, chunkPos);
                    if (pollution > 0) {
                        PollutionManager.setChunkPollution(level, chunkPos, 0);
                        cleared++;
                        totalRemoved += pollution;
                    }
                }
            }

            final int finalCleared = cleared;
            final double finalRemoved = totalRemoved;
            ctx.getSource().sendSuccess(() ->
                    Component.literal(String.format("§a✓ Cleared pollution in §e%d §achunks (radius %d)",
                            finalCleared, radius)), true);
            ctx.getSource().sendSuccess(() ->
                    Component.literal(String.format("§7  Total pollution removed: §f%.1f", finalRemoved)), false);
            return 1;
        } catch (Exception e) {
            ctx.getSource().sendFailure(Component.literal("§cError: " + e.getMessage()));
            return 0;
        }
    }

    private static int addPermanentPollution(CommandContext<CommandSourceStack> ctx, double amount) {
        double current = PollutionManager.getPermanentPollution();
        PollutionManager.addPermanentPollution(amount);
        double newAmount = PollutionManager.getPermanentPollution();

        ctx.getSource().sendSuccess(() ->
                Component.literal(String.format("§a✓ Added §e%.1f §apermanent pollution", amount)), true);
        ctx.getSource().sendSuccess(() ->
                Component.literal(String.format("§7  Previous: §f%.1f §7→ New: §f%.1f", current, newAmount)), false);
        return 1;
    }

    private static int removePermanentPollution(CommandContext<CommandSourceStack> ctx, double amount) {
        double current = PollutionManager.getPermanentPollution();
        double newAmount = Math.max(0, current - amount);
        PollutionManager.setPermanentPollution(newAmount);

        ctx.getSource().sendSuccess(() ->
                Component.literal(String.format("§a✓ Removed §e%.1f §apermanent pollution", amount)), true);
        ctx.getSource().sendSuccess(() ->
                Component.literal(String.format("§7  Previous: §f%.1f §7→ New: §f%.1f", current, newAmount)), false);
        return 1;
    }

    private static int setPermanentPollution(CommandContext<CommandSourceStack> ctx, double amount) {
        double current = PollutionManager.getPermanentPollution();
        PollutionManager.setPermanentPollution(amount);

        ctx.getSource().sendSuccess(() ->
                Component.literal(String.format("§a✓ Set permanent pollution to §e%.1f", amount)), true);
        ctx.getSource().sendSuccess(() ->
                Component.literal(String.format("§7  Previous: §f%.1f", current)), false);
        return 1;
    }

    private static int showPermanentPollution(CommandContext<CommandSourceStack> ctx) {
        double permanent = PollutionManager.getPermanentPollution();
        ctx.getSource().sendSuccess(() ->
                Component.literal(String.format("§6Permanent Pollution: §e%.1f", permanent)), false);
        return 1;
    }

    private static int showStatus(CommandContext<CommandSourceStack> ctx, BlockPos pos) {
        try {
            ServerPlayer player = ctx.getSource().getPlayerOrException();
            ServerLevel level = player.serverLevel();
            BlockPos targetPos = pos != null ? pos : player.blockPosition();
            ChunkPos chunkPos = new ChunkPos(targetPos);

            double chunkPollution = PollutionManager.getChunkPollution(level, chunkPos);
            double localPollution = HazardScanner.getPollutionLevel(player);
            double permanentPollution = PollutionManager.getPermanentPollution();

            ctx.getSource().sendSuccess(() -> Component.literal("§b§l=== Pollution Status ==="), false);
            ctx.getSource().sendSuccess(() ->
                    Component.literal(String.format("§6Chunk [%d, %d]: §e%.1f §7(temporary)",
                            chunkPos.x, chunkPos.z, chunkPollution)), false);
            ctx.getSource().sendSuccess(() ->
                    Component.literal(String.format("§6Local Area: §e%.1f §7(weighted average)", localPollution)), false);
            ctx.getSource().sendSuccess(() ->
                    Component.literal(String.format("§6Global: §e%.1f §7(permanent)", permanentPollution)), false);
            ctx.getSource().sendSuccess(() -> Component.literal(""), false);
            ctx.getSource().sendSuccess(() -> Component.literal("§7Threat Levels:"), false);
            if (localPollution >= 200) {
                ctx.getSource().sendSuccess(() -> Component.literal("  §c⚠ SEVERE - Charged Creepers"), false);
            } else if (localPollution >= 120) {
                ctx.getSource().sendSuccess(() -> Component.literal("  §6⚠ HIGH - Creeper Spawns"), false);
            } else if (localPollution >= 80) {
                ctx.getSource().sendSuccess(() -> Component.literal("  §e⚠ MODERATE - Zombie Spawns"), false);
            } else if (localPollution >= 50) {
                ctx.getSource().sendSuccess(() -> Component.literal("  §a⚠ LOW - Machine Attacks"), false);
            } else {
                ctx.getSource().sendSuccess(() -> Component.literal("  §7○ SAFE"), false);
            }
            return 1;
        } catch (Exception e) {
            ctx.getSource().sendFailure(Component.literal("§cError: " + e.getMessage()));
            return 0;
        }
    }
}
