package cn.minerealms.assimploader.resource;

import cn.minerealms.assimploader.core.AssimpModelLoader;
import cn.minerealms.assimploader.core.AsyncModelLoader;
import cn.minerealms.assimploader.hotreload.ModelHotReloadManager;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.client.event.ModelEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

/** ModelBakery fires this after reading JSON and before baking any imported geometry. */
@Mod.EventBusSubscriber(modid = "assimploader", bus = Mod.EventBusSubscriber.Bus.MOD, value = Dist.CLIENT)
public final class ModelCacheLifecycle {
    private ModelCacheLifecycle() {}

    @SubscribeEvent
    public static void beginReload(ModelEvent.RegisterAdditional event) {
        ModelHotReloadManager.forgetOverrides();
        AssimpModelLoader.clearCache();
    }

    /** Called from Minecraft.close, after the final tick rather than the stop-request event. */
    public static void shutdown() {
        try {
            ModelHotReloadManager.forgetOverrides();
            AssimpModelLoader.clearCache();
        } finally {
            AsyncModelLoader.shutdown();
        }
    }
}
