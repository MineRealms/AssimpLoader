package cn.minerealms.assimploader.showcase;

import cn.minerealms.assimploader.AssimpLoader;
import com.mojang.brigadier.exceptions.CommandSyntaxException;
import com.mojang.brigadier.exceptions.DynamicCommandExceptionType;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.commands.arguments.coordinates.BlockPosArgument;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.entity.SignBlockEntity;
import net.minecraft.world.level.block.entity.SignText;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraftforge.event.RegisterCommandsEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

import java.util.LinkedHashMap;
import java.util.Map;

@Mod.EventBusSubscriber(modid = AssimpLoader.MODID, bus = Mod.EventBusSubscriber.Bus.FORGE)
public final class ShowcaseCommands {
    private static final DynamicCommandExceptionType BLOCKED = new DynamicCommandExceptionType(
            pos -> Component.translatable("commands.assimploader.demo.blocked", pos));

    @SubscribeEvent
    public static void register(RegisterCommandsEvent event) {
        event.getDispatcher().register(Commands.literal("assimpdemo").requires(source -> source.hasPermission(2))
                .executes(context -> place(context.getSource(), BlockPos.containing(context.getSource().getPosition()).offset(4, 0, 4)))
                .then(Commands.argument("pos", BlockPosArgument.blockPos())
                        .executes(context -> place(context.getSource(), BlockPosArgument.getLoadedBlockPos(context, "pos")))));
    }

    private static int place(CommandSourceStack source, BlockPos origin) throws CommandSyntaxException {
        ServerLevel level = source.getLevel();
        Map<BlockPos, BlockState> planned = new LinkedHashMap<>();
        Map<BlockPos, ShowcaseModel> labels = new LinkedHashMap<>();
        ShowcaseModel[] models = ShowcaseModel.values();

        // Check the full display volume before changing any block, including model overhangs.
        for (int i = 0; i < models.length; i++) {
            BlockPos center = origin.offset((i % 3) * 6, 0, (i / 3) * 6);
            for (BlockPos pos : BlockPos.betweenClosed(center.offset(-2, 0, -2), center.offset(2, 5, 2))) {
                if (level.isOutsideBuildHeight(pos) || !level.getWorldBorder().isWithinBounds(pos)
                        || !level.hasChunkAt(pos) || level.getBlockEntity(pos) != null
                        || !level.getBlockState(pos).canBeReplaced()) {
                    throw BLOCKED.create(pos.toShortString());
                }
            }
            for (int x = -1; x <= 1; x++) {
                for (int z = -1; z <= 1; z++) planned.put(center.offset(x, 0, z), Blocks.SMOOTH_STONE.defaultBlockState());
            }
            planned.put(center.above(), ShowcaseRegistry.block(models[i]).defaultBlockState());
            BlockPos sign = center.offset(0, 1, -2);
            planned.put(sign.below(), Blocks.SMOOTH_STONE.defaultBlockState());
            planned.put(sign, Blocks.OAK_SIGN.defaultBlockState());
            labels.put(sign, models[i]);
        }

        for (Map.Entry<BlockPos, BlockState> entry : planned.entrySet()) {
            level.setBlockAndUpdate(entry.getKey(), entry.getValue());
        }
        for (Map.Entry<BlockPos, ShowcaseModel> entry : labels.entrySet()) {
            if (level.getBlockEntity(entry.getKey()) instanceof SignBlockEntity sign) {
                ShowcaseModel model = entry.getValue();
                SignText text = new SignText().setMessage(0, Component.literal(model.title()))
                        .setMessage(1, Component.literal(model.subtitle()))
                        .setMessage(2, Component.literal(model.modelName()))
                        .setMessage(3, Component.literal("AssimpLoader FBX"));
                sign.setText(text, true);
                sign.setText(text, false);
                sign.setChanged();
                level.sendBlockUpdated(entry.getKey(), sign.getBlockState(), sign.getBlockState(), 3);
            }
        }
        source.sendSuccess(() -> Component.translatable("commands.assimploader.demo.placed", models.length, origin.toShortString()), true);
        return models.length;
    }

    private ShowcaseCommands() { }
}
