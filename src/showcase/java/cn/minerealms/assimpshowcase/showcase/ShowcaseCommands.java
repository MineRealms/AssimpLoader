package cn.minerealms.assimpshowcase.showcase;

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
import java.util.Arrays;
import java.util.Map;

@Mod.EventBusSubscriber(modid = "assimpshowcase", bus = Mod.EventBusSubscriber.Bus.FORGE)
public final class ShowcaseCommands {
    private static final DynamicCommandExceptionType BLOCKED = new DynamicCommandExceptionType(
            pos -> Component.translatable("commands.assimploader.demo.blocked", pos));
    private static final DynamicCommandExceptionType ROCKET_BLOCKED = new DynamicCommandExceptionType(
            pos -> Component.translatable("commands.assimploader.demo.rocket_blocked", pos));

    @SubscribeEvent
    public static void register(RegisterCommandsEvent event) {
        event.getDispatcher().register(Commands.literal("assimpdemo").requires(source -> source.hasPermission(2))
                .executes(context -> place(context.getSource(), BlockPos.containing(context.getSource().getPosition()).offset(4, 0, 4)))
                .then(Commands.literal("rocket")
                        .executes(context -> placeRocket(context.getSource(), BlockPos.containing(context.getSource().getPosition()).offset(8, 0, 8)))
                        .then(Commands.argument("pos", BlockPosArgument.blockPos())
                                .executes(context -> placeRocket(context.getSource(), BlockPosArgument.getLoadedBlockPos(context, "pos")))))
                .then(Commands.argument("pos", BlockPosArgument.blockPos())
                        .executes(context -> place(context.getSource(), BlockPosArgument.getLoadedBlockPos(context, "pos")))));
    }

    private static int place(CommandSourceStack source, BlockPos origin) throws CommandSyntaxException {
        ServerLevel level = source.getLevel();
        Map<BlockPos, BlockState> planned = new LinkedHashMap<>();
        Map<BlockPos, ShowcaseModel> labels = new LinkedHashMap<>();
        ShowcaseModel[] models = Arrays.stream(ShowcaseModel.values()).filter(ShowcaseModel::isGalleryExhibit)
                .toArray(ShowcaseModel[]::new);

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

    private static int placeRocket(CommandSourceStack source, BlockPos origin) throws CommandSyntaxException {
        ServerLevel level = source.getLevel();
        for (BlockPos pos : BlockPos.betweenClosed(origin.offset(-5, 0, -5), origin.offset(5, 19, 5))) {
            if (level.isOutsideBuildHeight(pos) || !level.getWorldBorder().isWithinBounds(pos)
                    || !level.hasChunkAt(pos) || level.getBlockEntity(pos) != null
                    || !level.getBlockState(pos).canBeReplaced()) {
                throw ROCKET_BLOCKED.create(pos.toShortString());
            }
        }
        for (int x = -4; x <= 4; x++) {
            for (int z = -4; z <= 4; z++) {
                level.setBlockAndUpdate(origin.offset(x, 0, z), Blocks.SMOOTH_STONE.defaultBlockState());
            }
        }
        for (int x : new int[]{-3, 3}) {
            for (int y = 1; y <= 18; y++) {
                level.setBlockAndUpdate(origin.offset(x, y, 2), Blocks.IRON_BLOCK.defaultBlockState());
            }
        }
        for (int x = -3; x <= 3; x++) {
            level.setBlockAndUpdate(origin.offset(x, 18, 2), Blocks.IRON_BLOCK.defaultBlockState());
        }
        for (int z = 0; z <= 2; z++) {
            level.setBlockAndUpdate(origin.offset(0, 18, z), Blocks.IRON_BLOCK.defaultBlockState());
        }
        // A dark segmented backboard makes refracted background edges and local shadows inspectable.
        for (int x = -2; x <= 2; x++) for (int y = 1; y <= 11; y++) {
            var block = y % 3 == 0 ? Blocks.GRAY_CONCRETE : Blocks.BLACK_CONCRETE;
            level.setBlockAndUpdate(origin.offset(x, y, 3), block.defaultBlockState());
        }
        level.setBlockAndUpdate(origin.above(12), ShowcaseRegistry.block(ShowcaseModel.ROCKET_ENGINE).defaultBlockState());
        source.sendSuccess(() -> Component.translatable("commands.assimploader.demo.rocket_placed",
                origin.above(12).toShortString()), true);
        return 1;
    }

    private ShowcaseCommands() { }
}
