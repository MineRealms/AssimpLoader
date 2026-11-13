package cn.minerealms.assimploader.resource;

import cn.minerealms.assimploader.core.AssimpModelLoader;
import cn.minerealms.assimploader.core.AsyncModelLoader;
import cn.minerealms.assimploader.hotreload.ModelHotReloadManager;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.client.event.ModelEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.event.GameShuttingDownEvent;

/** ModelBakery fires this after reading JSON and before baking any imported geometry. */
@Mod.EventBusSubscriber(modid = "assimploader", bus = Mod.EventBusSubscriber.Bus.MOD, value = Dist.CLIENT)
public final class ModelCacheLifecycle {
    private ModelCacheLifecycle() {}

    @SubscribeEvent
    public static void beginReload(ModelEvent.RegisterAdditional event) {
        ModelHotReloadManager.forgetOverrides();
        AssimpModelLoader.clearCache();
    }

    @Mod.EventBusSubscriber(modid = "assimploader", value = Dist.CLIENT)
    public static final class Shutdown {
        private Shutdown() {}

        @SubscribeEvent
        public static void shutdown(GameShuttingDownEvent event) {
            AsyncModelLoader.shutdown();
            ModelHotReloadManager.forgetOverrides();
            AssimpModelLoader.clearCache();
        }
    }
}
