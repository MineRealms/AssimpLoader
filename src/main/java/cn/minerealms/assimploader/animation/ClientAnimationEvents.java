package cn.minerealms.assimploader.animation;

import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

/** The core animation clock also runs when no example content mod is installed. */
@Mod.EventBusSubscriber(modid = "assimploader", value = Dist.CLIENT)
public final class ClientAnimationEvents {
    private ClientAnimationEvents() {}

    @SubscribeEvent
    public static void tick(TickEvent.ClientTickEvent event) {
        if (event.phase == TickEvent.Phase.END) ClientAnimationManager.getInstance().tick();
    }
}
