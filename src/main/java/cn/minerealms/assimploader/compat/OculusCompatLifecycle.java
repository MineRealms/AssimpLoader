package cn.minerealms.assimploader.compat;

import net.minecraft.server.packs.resources.ResourceManagerReloadListener;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.client.event.ClientPlayerNetworkEvent;
import net.minecraftforge.client.event.RegisterClientReloadListenersEvent;
import net.minecraftforge.event.GameShuttingDownEvent;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

@Mod.EventBusSubscriber(modid = "assimploader", value = Dist.CLIENT)
public final class OculusCompatLifecycle {
    private OculusCompatLifecycle() {}

    @SubscribeEvent public static void frame(TickEvent.RenderTickEvent event) {
        if (event.phase == TickEvent.Phase.START) OculusCompat.beginFrame();
    }

    @SubscribeEvent public static void logout(ClientPlayerNetworkEvent.LoggingOut event) { OculusCompat.reset(); }
    @SubscribeEvent public static void shutdown(GameShuttingDownEvent event) { OculusCompat.reset(); }

    @Mod.EventBusSubscriber(modid = "assimploader", value = Dist.CLIENT, bus = Mod.EventBusSubscriber.Bus.MOD)
    public static final class Reload {
        private Reload() {}
        @SubscribeEvent public static void register(RegisterClientReloadListenersEvent event) {
            event.registerReloadListener((ResourceManagerReloadListener) resources -> OculusCompat.reset());
        }
    }
}
