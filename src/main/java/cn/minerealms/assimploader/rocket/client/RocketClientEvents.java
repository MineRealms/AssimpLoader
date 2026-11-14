package cn.minerealms.assimploader.rocket.client;

import cn.minerealms.assimploader.AssimpLoader;
import cn.minerealms.assimploader.compat.OculusCompat;
import net.minecraft.server.packs.resources.ResourceManagerReloadListener;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.client.event.ClientPlayerNetworkEvent;
import net.minecraftforge.client.event.RegisterClientReloadListenersEvent;
import net.minecraftforge.client.event.RenderLevelStageEvent;
import net.minecraftforge.event.GameShuttingDownEvent;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import org.joml.Matrix4f;

@Mod.EventBusSubscriber(modid = AssimpLoader.MODID, value = Dist.CLIENT)
public final class RocketClientEvents {
    private static final Matrix4f WORLD_VIEW = new Matrix4f();
    private static boolean worldViewCaptured;
    private RocketClientEvents() {}
    @SubscribeEvent public static void tick(TickEvent.ClientTickEvent event) {
        if (event.phase == TickEvent.Phase.END) RocketExhausts.tick();
    }
    @SubscribeEvent public static void frame(TickEvent.RenderTickEvent event) {
        if (event.phase == TickEvent.Phase.START) {
            RocketExhausts.beginFrame();
            worldViewCaptured = false;
        }
    }

    @SubscribeEvent public static void render(RenderLevelStageEvent event) {
        RocketExhausts.setFrustum(event.getFrustum());
        if (event.getStage() == RenderLevelStageEvent.Stage.AFTER_ENTITIES && !OculusCompat.isRenderingShadowPass()) {
            WORLD_VIEW.set(event.getPoseStack().last().pose());
            worldViewCaptured = true;
        }
        // Forge GameRenderer passes a separate, unrotated local stack to AFTER_LEVEL.
        if (event.getStage() == RenderLevelStageEvent.Stage.AFTER_LEVEL && worldViewCaptured && !OculusCompat.isShadersActive())
            VanillaExhaustRenderer.render(event.getProjectionMatrix(), WORLD_VIEW);
    }

    @SubscribeEvent public static void logout(ClientPlayerNetworkEvent.LoggingOut event) { clear(); }
    @SubscribeEvent public static void shutdown(GameShuttingDownEvent event) { clear(); }
    private static void clear() { RocketExhausts.clear(); VanillaExhaustRenderer.clear(); }

    @Mod.EventBusSubscriber(modid = AssimpLoader.MODID, value = Dist.CLIENT, bus = Mod.EventBusSubscriber.Bus.MOD)
    public static final class Reload {
        @SubscribeEvent public static void register(RegisterClientReloadListenersEvent event) {
            event.registerReloadListener(new ExhaustProfiles());
            event.registerReloadListener((ResourceManagerReloadListener) resources -> {
                VanillaExhaustRenderer.clear(); RocketExhausts.clear();
            });
        }
    }
}
