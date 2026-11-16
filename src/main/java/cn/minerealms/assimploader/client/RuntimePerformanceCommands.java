package cn.minerealms.assimploader.client;

import cn.minerealms.assimploader.AssimpLoader;
import cn.minerealms.assimploader.animation.ClientAnimationManager;
import cn.minerealms.assimploader.config.RuntimePerformancePolicy;
import net.minecraft.commands.Commands;
import net.minecraft.network.chat.Component;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.client.event.RegisterClientCommandsEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

/** Read-only client diagnostics; policy changes continue to use the Forge config file. */
@Mod.EventBusSubscriber(modid = AssimpLoader.MODID, value = Dist.CLIENT)
public final class RuntimePerformanceCommands {
    private RuntimePerformanceCommands() {}

    @SubscribeEvent
    public static void register(RegisterClientCommandsEvent event) {
        event.getDispatcher().register(Commands.literal("assimploader").then(Commands.literal("performance").executes(context -> {
            var policy = RuntimePerformancePolicy.current();
            var stats = ClientAnimationManager.getInstance().getPerformanceStatistics();
            context.getSource().sendSuccess(() -> Component.literal("Assimp runtime: " + policy.mode()
                    + ", update ticks near/far/hidden=" + policy.nearUpdateTicks() + "/" + policy.farUpdateTicks()
                    + "/" + policy.hiddenUpdateTicks() + ", far=" + policy.farDistance() + " blocks"), false);
            context.getSource().sendSuccess(() -> Component.literal("Instances=" + stats.instances()
                    + ", last tick evaluated/skipped/paused=" + stats.evaluated() + "/" + stats.skipped() + "/" + stats.paused()
                    + ", total evaluated/skipped=" + stats.totalEvaluated() + "/" + stats.totalSkipped()), false);
            context.getSource().sendSuccess(() -> Component.literal("Oculus poses: "
                    + cn.minerealms.assimploader.rendering.OculusEntityBufferCache.poseStatistics()),false);
            context.getSource().sendSuccess(() -> Component.literal("Compute skinning: "
                    + cn.minerealms.assimploader.render.ClientRenderResources.computeStatistics()),false);
            return 1;
        })));
    }
}
