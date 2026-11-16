package cn.minerealms.assimploader.deer.client;

import cn.minerealms.assimploader.AssimpLoader;
import cn.minerealms.assimploader.deer.DeerShowcaseRegistry;
import net.minecraft.client.Minecraft;
import net.minecraft.resources.ResourceLocation;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.client.event.EntityRenderersEvent;
import net.minecraftforge.client.event.ModelEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

@Mod.EventBusSubscriber(modid = AssimpLoader.MODID, bus = Mod.EventBusSubscriber.Bus.MOD, value = Dist.CLIENT)
public final class DeerShowcaseRenderRegistration {
    private DeerShowcaseRenderRegistration() {}

    @SubscribeEvent public static void renderers(EntityRenderersEvent.RegisterRenderers event) {
        event.registerEntityRenderer(DeerShowcaseRegistry.DEER.get(), DeerShowcaseRenderer::new);
    }

    @SubscribeEvent public static void models(ModelEvent.RegisterAdditional event) {
        if (Minecraft.getInstance().getResourceManager().getResource(DeerShowcaseRenderer.MODEL_RESOURCE).isPresent()) {
            event.register(new ResourceLocation(AssimpLoader.MODID, "entity/deer_showcase"));
        }
    }
}
