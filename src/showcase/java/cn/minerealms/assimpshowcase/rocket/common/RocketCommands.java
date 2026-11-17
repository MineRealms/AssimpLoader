package cn.minerealms.assimpshowcase.rocket.common;

import cn.minerealms.assimploader.AssimpLoader;
import cn.minerealms.assimpshowcase.showcase.ShowcaseBlockEntity;
import cn.minerealms.assimpshowcase.showcase.ShowcaseModel;
import com.mojang.brigadier.context.CommandContext;
import com.mojang.brigadier.exceptions.CommandSyntaxException;
import com.mojang.brigadier.exceptions.SimpleCommandExceptionType;
import com.mojang.brigadier.arguments.DoubleArgumentType;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.commands.arguments.ResourceLocationArgument;
import net.minecraft.commands.arguments.coordinates.BlockPosArgument;
import net.minecraft.network.chat.Component;
import net.minecraftforge.event.RegisterCommandsEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

@Mod.EventBusSubscriber(modid = "assimpshowcase")
public final class RocketCommands {
    private static final SimpleCommandExceptionType NOT_ENGINE = new SimpleCommandExceptionType(
            Component.literal("No AssimpLoader Showcase rocket engine at that position"));
    private RocketCommands() {}

    @SubscribeEvent public static void register(RegisterCommandsEvent event) {
        event.getDispatcher().register(Commands.literal("assimpexhaust").requires(source -> source.hasPermission(2))
                .then(Commands.argument("pos", BlockPosArgument.blockPos())
                        .then(Commands.literal("start").executes(context -> burn(context, true)))
                        .then(Commands.literal("stop").executes(context -> burn(context, false)))
                        .then(Commands.literal("flow")
                                .then(Commands.literal("auto").executes(context -> flow(context, -1)))
                                .then(Commands.argument("kg_s", DoubleArgumentType.doubleArg(0, 1_000_000))
                                        .executes(context -> flow(context, DoubleArgumentType.getDouble(context, "kg_s")))))
                        .then(Commands.literal("status").executes(context -> {
                            var engine = engine(context);
                            String request = engine.getTargetMassFlowKgS() < 0 ? "nominal (profile)"
                                    : String.format(java.util.Locale.ROOT, "%.3f kg/s", engine.getTargetMassFlowKgS());
                            context.getSource().sendSuccess(() -> Component.literal("Engine " + engine.getExhaustProfile()
                                    + "; requested mass flow: " + request + "; burning: " + engine.isRocketBurning()), false);
                            return 1;
                        }))
                        .then(Commands.literal("profile").then(Commands.argument("id", ResourceLocationArgument.id())
                                .executes(context -> {
                                    var engine = engine(context);
                                    engine.setExhaustProfile(ResourceLocationArgument.getId(context, "id"));
                                    context.getSource().sendSuccess(() -> Component.literal("Exhaust profile: " + engine.getExhaustProfile()), true);
                                    return 1;
                                })))));
    }

    private static int burn(CommandContext<CommandSourceStack> context, boolean burning) throws CommandSyntaxException {
        engine(context).setRocketBurning(burning);
        context.getSource().sendSuccess(() -> Component.literal(burning ? "Engine starting" : "Engine shutting down"), true);
        return 1;
    }

    private static int flow(CommandContext<CommandSourceStack> context, double kgS) throws CommandSyntaxException {
        var engine = engine(context);
        engine.setTargetMassFlowKgS(kgS);
        engine.setRocketBurning(true);
        context.getSource().sendSuccess(() -> Component.literal(kgS < 0 ? "Using profile nominal mass flow"
                : String.format(java.util.Locale.ROOT, "Commanded exhaust mass flow: %.3f kg/s", kgS)), true);
        return 1;
    }

    private static ShowcaseBlockEntity engine(CommandContext<CommandSourceStack> context) throws CommandSyntaxException {
        var block = context.getSource().getLevel().getBlockEntity(BlockPosArgument.getLoadedBlockPos(context, "pos"));
        if (block instanceof ShowcaseBlockEntity engine && engine.getModel() == ShowcaseModel.ROCKET_ENGINE) return engine;
        throw NOT_ENGINE.create();
    }
}
