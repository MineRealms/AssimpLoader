package io.github.flemmli97.improvedmobs.commands;

import com.mojang.authlib.GameProfile;
import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.arguments.FloatArgumentType;
import com.mojang.brigadier.arguments.IntegerArgumentType;
import com.mojang.brigadier.context.CommandContext;
import com.mojang.brigadier.exceptions.CommandSyntaxException;
import io.github.flemmli97.improvedmobs.config.Config;
import io.github.flemmli97.improvedmobs.config.EquipmentList;
import io.github.flemmli97.improvedmobs.difficulty.DifficultyData;
import io.github.flemmli97.improvedmobs.difficulty.PlayerDifficulty;
import io.github.flemmli97.improvedmobs.platform.CrossPlatformStuff;
import net.minecraft.ChatFormatting;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.commands.arguments.GameProfileArgument;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.Style;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;

import java.util.Collection;

import io.github.flemmli97.improvedmobs.industrial.IndustrialDifficultyManager;
import io.github.flemmli97.improvedmobs.industrial.MachineScanner;
import io.github.flemmli97.improvedmobs.industrial.HazardScanner;
import io.github.flemmli97.improvedmobs.industrial.DifficultySmoother;

// TODO: make command feedback translatable (test translation lib a bit more before)
public class IMCommand {

    public static void register(CommandDispatcher<CommandSourceStack> dispatcher) {
        dispatcher.register(Commands.literal("improvedmobs")
                .executes(IMCommand::getDifficulty)
                .then(Commands.literal("reloadJson").requires(src -> src.hasPermission(2)).executes(IMCommand::reloadJson))
                .then(Commands.literal("industrial").executes(IMCommand::getIndustrialDebug))
                .then(Commands.literal("spore").executes(IMCommand::getSporeStatus))
                .then(Commands.literal("difficulty").requires(src -> src.hasPermission(2))
                        .then(Commands.literal("player").then(Commands.argument("players", GameProfileArgument.gameProfile())
                                .then(Commands.literal("set").then(Commands.argument("val", FloatArgumentType.floatArg()).executes(IMCommand::setDifficultyPlayer)))
                                .then(Commands.literal("add").then(Commands.argument("val", FloatArgumentType.floatArg()).executes(IMCommand::addDifficultyPlayer)))))
                        .then(Commands.literal("set").then(Commands.argument("val", FloatArgumentType.floatArg()).executes(IMCommand::setDifficulty)))
                        .then(Commands.literal("add").then(Commands.argument("val", FloatArgumentType.floatArg()).executes(IMCommand::addDifficulty)))
                        .then(Commands.literal("pause")
                                .then(Commands.literal("player").then(Commands.argument("players", GameProfileArgument.gameProfile()).executes(src -> IMCommand.pauseDifficulty(src, GameProfileArgument.getGameProfiles(src, "players"), true))))
                                .executes(src -> IMCommand.pauseDifficulty(src, null, true)))
                        .then(Commands.literal("unpause")
                                .then(Commands.literal("player").then(Commands.argument("players", GameProfileArgument.gameProfile()).executes(src -> IMCommand.pauseDifficulty(src, GameProfileArgument.getGameProfiles(src, "players"), false))))
                                .executes(src -> IMCommand.pauseDifficulty(src, null, false)))
                        .then(Commands.literal("simulate")
                                .then(Commands.argument("steps", IntegerArgumentType.integer(1))
                                        .then(Commands.literal("player").then(Commands.argument("players", GameProfileArgument.gameProfile()).executes(src -> IMCommand.simulateDifficulty(src, GameProfileArgument.getGameProfiles(src, "players"), IntegerArgumentType.getInteger(src, "steps")))))
                                        .executes(src -> IMCommand.simulateDifficulty(src, null, IntegerArgumentType.getInteger(src, "steps")))))
                ));
    }

    private static int reloadJson(CommandContext<CommandSourceStack> src) {
        src.getSource().sendSuccess(() -> Component.literal("Reloading equipment.json"), true);
        try {
            EquipmentList.initEquip();
        } catch (EquipmentList.InvalidItemNameException e) {
            src.getSource().sendSuccess(() -> Component.literal(e.getMessage()), false);
        }
        return 1;
    }

    private static int setDifficulty(CommandContext<CommandSourceStack> src) {
        DifficultyData data = DifficultyData.get(src.getSource().getServer());
        data.setDifficulty(FloatArgumentType.getFloat(src, "val"), src.getSource().getServer());
        src.getSource().sendSuccess(() -> Component.literal("Difficulty set to " + data.getDifficulty()).setStyle(Style.EMPTY.withColor(ChatFormatting.GOLD)), true);
        return 1;
    }

    private static int addDifficulty(CommandContext<CommandSourceStack> src) {
        DifficultyData data = DifficultyData.get(src.getSource().getServer());
        data.addDifficulty(FloatArgumentType.getFloat(src, "val"), src.getSource().getServer());
        src.getSource().sendSuccess(() -> Component.literal("Difficulty set to " + data.getDifficulty()).setStyle(Style.EMPTY.withColor(ChatFormatting.GOLD)), true);
        return 1;
    }

    private static int setDifficultyPlayer(CommandContext<CommandSourceStack> src) throws CommandSyntaxException {
        Collection<GameProfile> profs = GameProfileArgument.getGameProfiles(src, "players");
        MinecraftServer server = src.getSource().getServer();
        for (GameProfile prof : profs) {
            ServerPlayer player = server.getPlayerList().getPlayer(prof.getId());
            CrossPlatformStuff.INSTANCE.getPlayerDifficultyData(player).ifPresent(data -> {
                data.setDifficultyLevel(FloatArgumentType.getFloat(src, "val"));
                CrossPlatformStuff.INSTANCE.sendDifficultyDataTo(player, server);
                src.getSource().sendSuccess(() -> Component.literal("Difficulty for " + prof.getName() + " set to " + data.getDifficultyLevel()).setStyle(Style.EMPTY.withColor(ChatFormatting.GOLD)), true);
            });
        }
        return profs.size();
    }

    private static int addDifficultyPlayer(CommandContext<CommandSourceStack> src) throws CommandSyntaxException {
        Collection<GameProfile> profs = GameProfileArgument.getGameProfiles(src, "players");
        MinecraftServer server = src.getSource().getServer();
        for (GameProfile prof : profs) {
            ServerPlayer player = server.getPlayerList().getPlayer(prof.getId());
            CrossPlatformStuff.INSTANCE.getPlayerDifficultyData(player).ifPresent(data -> {
                data.setDifficultyLevel(data.getDifficultyLevel() + FloatArgumentType.getFloat(src, "val"));
                CrossPlatformStuff.INSTANCE.sendDifficultyDataTo(player, server);
                src.getSource().sendSuccess(() -> Component.literal("Difficulty for " + prof.getName() + " set to " + data.getDifficultyLevel()).setStyle(Style.EMPTY.withColor(ChatFormatting.GOLD)), true);
            });
        }
        return profs.size();
    }

    private static int getDifficulty(CommandContext<CommandSourceStack> src) throws CommandSyntaxException {
        float diff;
        if (Config.CommonConfig.difficultyType == Config.DifficultyType.GLOBAL)
            diff = DifficultyData.get(src.getSource().getServer())
                    .getDifficulty();
        else {
            ServerPlayer player = src.getSource().getPlayerOrException();
            diff = CrossPlatformStuff.INSTANCE.getPlayerDifficultyData(player).map(PlayerDifficulty::getDifficultyLevel).orElse(0f);
        }
        src.getSource().sendSuccess(() -> Component.literal("Difficulty: " + diff).setStyle(Style.EMPTY.withColor(ChatFormatting.GOLD)), true);
        return 1;
    }

    private static int pauseDifficulty(CommandContext<CommandSourceStack> src, Collection<GameProfile> profs, boolean pause) throws CommandSyntaxException {
        if (profs != null) {
            MinecraftServer server = src.getSource().getServer();
            for (GameProfile prof : profs) {
                ServerPlayer player = server.getPlayerList().getPlayer(prof.getId());
                CrossPlatformStuff.INSTANCE.getPlayerDifficultyData(player).ifPresent(data -> data.setPaused(pause));
            }
            src.getSource().sendSuccess(() -> Component.literal("Difficulty " + (pause ? "paused" : "unpaused") + " for given players").setStyle(Style.EMPTY.withColor(ChatFormatting.GOLD)), true);
            return profs.size();
        }
        DifficultyData data = DifficultyData.get(src.getSource().getServer());
        data.setPaused(pause);
        src.getSource().sendSuccess(() -> Component.literal("Difficulty " + (pause ? "paused" : "unpaused")).setStyle(Style.EMPTY.withColor(ChatFormatting.GOLD)), true);
        return 1;
    }

    private static int getIndustrialDebug(CommandContext<CommandSourceStack> src) throws CommandSyntaxException {
        ServerPlayer player = src.getSource().getPlayerOrException();
        
        // 1. Scan Machines
        MachineScanner.ScanResult result = MachineScanner.scanNearbyMachines(player, 32);
        float medianTier = DifficultySmoother.weightedMedian(result.tiers(), result.weights());
        
        // 2. Scan Hazards
        double pollution = HazardScanner.getPollutionLevel(player);
        
        // 3. Current Manager State
        float currentBonus = IndustrialDifficultyManager.getDifficultyFor(player);

        src.getSource().sendSuccess(() -> Component.literal("=== Industrial Debug ===").withStyle(ChatFormatting.AQUA), false);
        src.getSource().sendSuccess(() -> Component.literal("Machines Active: ").append(Component.literal(String.valueOf(result.tiers().size())).withStyle(ChatFormatting.YELLOW)), false);
        src.getSource().sendSuccess(() -> Component.literal("Median Voltage Tier: ").append(Component.literal(String.format("%.2f", medianTier)).withStyle(ChatFormatting.YELLOW)), false);
        src.getSource().sendSuccess(() -> Component.literal("Local Pollution: ").append(Component.literal(String.format("%.2f", pollution)).withStyle(ChatFormatting.YELLOW)), false);
        src.getSource().sendSuccess(() -> Component.literal("Current Difficulty Bonus: ").append(Component.literal(String.format("%.2f", currentBonus)).withStyle(ChatFormatting.GOLD)), false);
        
        return 1;
    }

    private static int getSporeStatus(CommandContext<CommandSourceStack> src) throws CommandSyntaxException {
        ServerPlayer player = src.getSource().getPlayerOrException();
        double currentDifficulty = DifficultyData.getDifficulty(player.serverLevel(), player);
        double globalPollution = io.github.flemmli97.improvedmobs.industrial.PollutionManager.getPermanentPollution() + currentDifficulty;
        
        int tempHiveminds = 0;
        try {
            tempHiveminds = io.github.flemmli97.improvedmobs.industrial.SporeIntegration.getActiveHiveminds(player.serverLevel());
        } catch(Exception e) {}
        
        final int hiveminds = tempHiveminds;

        src.getSource().sendSuccess(() -> Component.literal("=== Spore Integration Status ===").withStyle(ChatFormatting.DARK_PURPLE), false);
        src.getSource().sendSuccess(() -> Component.literal("Global Pollution & Difficulty: ").append(Component.literal(String.format("%.2f", globalPollution)).withStyle(ChatFormatting.RED)), false);
        
        // 当全球污染每10点，孢子生物会额外增加10%的生命值和力量
        double bonusMultiplier = 1.0 + (globalPollution / 100.0);
        src.getSource().sendSuccess(() -> Component.literal("Spore Evolution Bonus Multiplier: ").append(Component.literal(String.format("%.2f", bonusMultiplier) + "x").withStyle(ChatFormatting.RED)), false);
        
        src.getSource().sendSuccess(() -> Component.literal("Active Spore Hiveminds (Proto) in World: ").append(Component.literal(String.valueOf(hiveminds)).withStyle(ChatFormatting.LIGHT_PURPLE)), false);
        
        return 1;
    }

    private static int simulateDifficulty(CommandContext<CommandSourceStack> src, Collection<GameProfile> profs, int steps) {
        if (profs != null) {
            MinecraftServer server = src.getSource().getServer();
            for (GameProfile prof : profs) {
                ServerPlayer player = server.getPlayerList().getPlayer(prof.getId());
                CrossPlatformStuff.INSTANCE.getPlayerDifficultyData(player).ifPresent(data -> {
                    int i = steps;
                    while (i > 0) {
                        float current = data.getDifficultyLevel();
                        data.setDifficultyLevel(current + Config.CommonConfig.increaseHandler.get(current).getRight().start());
                        i--;
                    }
                });
                CrossPlatformStuff.INSTANCE.sendDifficultyDataTo(player, server);
            }
            src.getSource().sendSuccess(() -> Component.literal(String.format("Simulated %s difficulty steps for given players", steps)).setStyle(Style.EMPTY.withColor(ChatFormatting.GOLD)), true);
            return profs.size();
        }
        DifficultyData data = DifficultyData.get(src.getSource().getServer());
        int i = steps;
        float current = data.getDifficulty();
        while (i > 0) {
            current += Config.CommonConfig.increaseHandler.get(current).getRight().start();
            i--;
        }
        data.setDifficulty(current, src.getSource().getServer());
        src.getSource().sendSuccess(() -> Component.literal(String.format("Simulated %s difficulty steps globally. Now at %s", steps, data.getDifficulty())).setStyle(Style.EMPTY.withColor(ChatFormatting.GOLD)), true);
        return 1;
    }
}
