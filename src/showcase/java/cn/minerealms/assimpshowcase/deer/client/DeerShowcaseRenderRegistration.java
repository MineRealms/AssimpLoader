package cn.minerealms.assimpshowcase.deer.client;

import cn.minerealms.assimploader.AssimpLoader;
import cn.minerealms.assimpshowcase.deer.DeerShowcaseRegistry;
import net.minecraft.client.Minecraft;
import net.minecraft.resources.ResourceLocation;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.client.event.EntityRenderersEvent;
import net.minecraftforge.client.event.ModelEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import cn.minerealms.assimploader.util.ResourceLocations;
@Mod.EventBusSubscriber(modid = "assimpshowcase", bus = Mod.EventBusSubscriber.Bus.MOD, value = Dist.CLIENT)
public final class DeerShowcaseRenderRegistration {
    private DeerShowcaseRenderRegistration() {}

    @SubscribeEvent public static void renderers(EntityRenderersEvent.RegisterRenderers event) {
        event.registerEntityRenderer(DeerShowcaseRegistry.DEER.get(), DeerShowcaseRenderer::new);
    }

    @SubscribeEvent public static void models(ModelEvent.RegisterAdditional event) {
        if (Minecraft.getInstance().getResourceManager().getResource(DeerShowcaseRenderer.MODEL_RESOURCE).isPresent()) {
            //? if forge {
            event.register(ResourceLocations.of(AssimpLoader.MODID, "entity/deer_showcase"));
            //?}
            //? if neoforge {
            /*
            // 1.21 requires the standalone-variant wrapper instead of a bare id.
            event.register(net.minecraft.client.resources.model.ModelResourceLocation.standalone(
                    ResourceLocations.of(AssimpLoader.MODID, "entity/deer_showcase")));
            *///?}
        }
    }
}
