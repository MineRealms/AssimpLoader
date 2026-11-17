package cn.minerealms.assimpshowcase.deer;

import cn.minerealms.assimploader.AssimpLoader;
import com.mojang.brigadier.arguments.BoolArgumentType;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.exceptions.CommandSyntaxException;
import com.mojang.brigadier.exceptions.SimpleCommandExceptionType;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.commands.arguments.EntityArgument;
import net.minecraft.commands.arguments.coordinates.Vec3Argument;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.event.RegisterCommandsEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

import java.util.Collection;

@Mod.EventBusSubscriber(modid = "assimpshowcase")
public final class DeerShowcaseCommands {
    private static final SimpleCommandExceptionType BLOCKED = new SimpleCommandExceptionType(
            Component.literal("Deer position must be loaded, inside the world border and clear of collision."));
    private static final SimpleCommandExceptionType NO_DEER = new SimpleCommandExceptionType(
            Component.literal("No deer showcase creatures matched."));
    private static final SimpleCommandExceptionType INVALID_STATE = new SimpleCommandExceptionType(
            Component.literal("State must be 1-64 letters, digits, underscores, dots or hyphens."));

    private DeerShowcaseCommands() {}

    @SubscribeEvent public static void register(RegisterCommandsEvent event) {
        event.getDispatcher().register(Commands.literal("assimpdeer").requires(source -> source.hasPermission(2))
                .then(Commands.literal("spawn").executes(context -> spawn(context.getSource(), nearby(context.getSource())))
                        .then(Commands.argument("pos", Vec3Argument.vec3())
                                .executes(context -> spawn(context.getSource(), Vec3Argument.getVec3(context, "pos")))))
                .then(Commands.literal("state").then(Commands.argument("targets", EntityArgument.entities())
                        .then(Commands.argument("state", StringArgumentType.word()).suggests((context, builder) -> {
                            for (String state : new String[]{"auto", "idle", "walk", "gallop", "ik_probe"}) builder.suggest(state);
                            return builder.buildFuture();
                        }).executes(context -> state(context.getSource(), EntityArgument.getEntities(context, "targets"),
                                StringArgumentType.getString(context, "state"))))))
                .then(Commands.literal("ground").then(Commands.argument("targets", EntityArgument.entities())
                        .then(Commands.argument("enabled", BoolArgumentType.bool()).executes(context -> option(context.getSource(),
                                EntityArgument.getEntities(context,"targets"),BoolArgumentType.getBool(context,"enabled"),true)))))
                .then(Commands.literal("effects").then(Commands.argument("targets", EntityArgument.entities())
                        .then(Commands.argument("enabled", BoolArgumentType.bool()).executes(context -> option(context.getSource(),
                                EntityArgument.getEntities(context,"targets"),BoolArgumentType.getBool(context,"enabled"),false)))))
                .then(Commands.literal("ai").then(Commands.argument("targets", EntityArgument.entities())
                        .then(Commands.argument("enabled", BoolArgumentType.bool()).executes(context -> ai(context.getSource(),
                                EntityArgument.getEntities(context, "targets"), BoolArgumentType.getBool(context, "enabled")))))));
        event.getDispatcher().register(Commands.literal("assimpdemo").requires(source -> source.hasPermission(2))
                .then(Commands.literal("deer").executes(context -> spawn(context.getSource(), nearby(context.getSource())))
                        .then(Commands.argument("pos", Vec3Argument.vec3())
                                .executes(context -> spawn(context.getSource(), Vec3Argument.getVec3(context, "pos"))))));
    }

    private static Vec3 nearby(CommandSourceStack source) { return source.getPosition().add(3, 0, 3); }
    private static int option(CommandSourceStack source,Collection<? extends Entity> targets,boolean enabled,boolean ground) throws CommandSyntaxException {
        int count=0;
        for(Entity target:targets) if(target instanceof DeerShowcaseEntity deer) {
            if(ground) deer.setGroundIkEnabled(enabled); else deer.setEffectsEnabled(enabled);
            count++;
        }
        if(count==0) throw NO_DEER.create();
        int changed=count;
        source.sendSuccess(()->Component.literal((ground?"Ground IK":"World effects")+" "+enabled+" for "+changed+" deer"),true);
        return count;
    }

    private static int spawn(CommandSourceStack source, Vec3 position) throws CommandSyntaxException {
        var level = source.getLevel();
        BlockPos block = BlockPos.containing(position);
        if (level.isOutsideBuildHeight(block) || !level.hasChunkAt(block) || !level.getWorldBorder().isWithinBounds(block)) throw BLOCKED.create();
        DeerShowcaseEntity deer = DeerShowcaseRegistry.DEER.get().create(level);
        if (deer == null) throw BLOCKED.create();
        deer.moveTo(position.x, position.y, position.z, source.getRotation().y, 0);
        var bounds = deer.getBoundingBox();
        if (bounds.maxY > level.getMaxBuildHeight() || !level.getWorldBorder().isWithinBounds(bounds)
                || !level.hasChunksAt(BlockPos.containing(bounds.minX, bounds.minY, bounds.minZ),
                        BlockPos.containing(bounds.maxX, bounds.maxY, bounds.maxZ))
                || !level.noCollision(deer, bounds)) throw BLOCKED.create();
        deer.setNoAi(true);
        deer.setPersistenceRequired();
        deer.setShowcaseState("idle");
        if (!level.addFreshEntity(deer)) throw BLOCKED.create();
        source.sendSuccess(() -> Component.literal("Spawned stationary deer showcase " + deer.getUUID()
                + ". Use /assimpdeer state or /assimpdeer ai to change it."), true);
        return 1;
    }

    private static int state(CommandSourceStack source, Collection<? extends Entity> targets, String state) throws CommandSyntaxException {
        if (!DeerShowcaseEntity.validState(state)) throw INVALID_STATE.create();
        int count = 0;
        for (Entity target : targets) if (target instanceof DeerShowcaseEntity deer) { deer.setShowcaseState(state); count++; }
        if (count == 0) throw NO_DEER.create();
        int changed = count;
        source.sendSuccess(() -> Component.literal("Requested deer state '" + state + "' for " + changed
                + " creatures. Unknown client resource states fall back to idle."), true);
        return count;
    }

    private static int ai(CommandSourceStack source, Collection<? extends Entity> targets, boolean enabled) throws CommandSyntaxException {
        int count = 0;
        for (Entity target : targets) if (target instanceof DeerShowcaseEntity deer) {
            deer.setNoAi(!enabled);
            if (enabled) deer.setShowcaseState("auto");
            count++;
        }
        if (count == 0) throw NO_DEER.create();
        int changed = count;
        source.sendSuccess(() -> Component.literal("Deer AI " + (enabled ? "enabled" : "disabled") + " for " + changed + " creatures."), true);
        return count;
    }
}
