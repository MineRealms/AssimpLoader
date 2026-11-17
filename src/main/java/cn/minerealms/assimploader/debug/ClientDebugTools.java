package cn.minerealms.assimploader.debug;

import cn.minerealms.assimploader.AssimpLoader;
import cn.minerealms.assimploader.config.AssimpLoaderConfig;
import com.mojang.brigadier.arguments.BoolArgumentType;
import net.minecraft.client.Minecraft;
import net.minecraft.commands.Commands;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.network.chat.Component;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.client.event.RegisterClientCommandsEvent;
import net.minecraftforge.common.ForgeConfigSpec;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import com.mojang.brigadier.builder.LiteralArgumentBuilder;

/** Client dispatcher only. No packet, permission or server command is registered. */
@Mod.EventBusSubscriber(modid=AssimpLoader.MODID,value=Dist.CLIENT)
public final class ClientDebugTools {
    private ClientDebugTools(){}
    public static void reset() {
        var c=AssimpLoaderConfig.CLIENT.debugView;
        for(var value:new ForgeConfigSpec.BooleanValue[]{c.enabled,c.hud,c.skeleton,c.collision,c.worldCollision,c.axes,c.attachments})value.set(false);
        c.bounds.set(true);c.material.set(AssimpLoaderConfig.MaterialView.DEFAULT);c.hudScale.set(1);c.enabled.save();
    }
    public static boolean enabled(){return AssimpLoaderConfig.getOrDefault(AssimpLoaderConfig.CLIENT.debugView.enabled::get,false);}
    @SubscribeEvent public static void commands(RegisterClientCommandsEvent event) {
        var config=AssimpLoaderConfig.CLIENT;var c=config.debugView;
        var debug=Commands.literal("debug").executes(context->{
            context.getSource().sendSuccess(()->Component.literal("AssimpLoader debug="+enabled()+" HUD="+c.hud.get()+" material="+c.material.get()),false);return 1;
        });
        debug.then(Commands.literal("on").executes(context->{c.enabled.set(true);c.enabled.save();return 1;}));
        debug.then(Commands.literal("off").executes(context->{c.enabled.set(false);c.enabled.save();return 1;}));
        debug.then(Commands.literal("reset").executes(context->{reset();return 1;}));
        debug.then(Commands.literal("menu").executes(context->{Minecraft.getInstance().execute(()->cn.minerealms.assimploader.client.ui.LookDevNavigation.open(cn.minerealms.assimploader.client.ui.LookDevNavigation.Page.DEBUG));return 1;}));
        debug.then(toggle("hud",c.hud));debug.then(toggle("bounds",c.bounds));debug.then(toggle("skeleton",c.skeleton));
        debug.then(toggle("collision",c.collision));debug.then(toggle("world_collision",c.worldCollision));
        debug.then(toggle("axes",c.axes));debug.then(toggle("attachments",c.attachments));
        debug.then(toggle("pbr",config.pbrEnabled));debug.then(toggle("gpu_skinning",config.gpuSkinningEnabled));
        debug.then(toggle("pose_cache",config.oculusPoseCacheEnabled));
        var material=Commands.literal("material");
        for(var view:AssimpLoaderConfig.MaterialView.values())material.then(Commands.literal(view.name().toLowerCase(java.util.Locale.ROOT)).executes(context->{
            c.material.set(view);c.enabled.set(true);c.enabled.save();return 1;
        }));
        debug.then(material);
        var shader=Commands.literal("shader");
        for(var mode:AssimpLoaderConfig.ShaderMode.values())shader.then(Commands.literal(mode.name().toLowerCase(java.util.Locale.ROOT)).executes(context->{config.shaderMode.set(mode);config.shaderMode.save();return 1;}));
        debug.then(shader);
        event.getDispatcher().register(Commands.literal("assimploader").then(debug));
        event.getDispatcher().register(Commands.literal("assimploader").then(toggle("hud",c.hud)));
    }
    private static LiteralArgumentBuilder<CommandSourceStack> toggle(String name,ForgeConfigSpec.BooleanValue value) {
        var command=Commands.literal(name).executes(context->{value.set(!value.get());value.save();return 1;});
        command.then(Commands.argument("enabled",BoolArgumentType.bool()).executes(context->{value.set(BoolArgumentType.getBool(context,"enabled"));value.save();return 1;}));
        command.then(Commands.literal("on").executes(context->{value.set(true);value.save();return 1;}));
        command.then(Commands.literal("off").executes(context->{value.set(false);value.save();return 1;}));
        return command;
    }
}
