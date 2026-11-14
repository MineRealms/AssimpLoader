package cn.minerealms.assimploader.vehicle.client;

import cn.minerealms.assimploader.AssimpLoader;
import cn.minerealms.assimploader.vehicle.VehicleRegistry;
import net.minecraft.client.Minecraft;
import net.minecraft.resources.ResourceLocation;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.client.event.EntityRenderersEvent;
import net.minecraftforge.client.event.ModelEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

@Mod.EventBusSubscriber(modid = AssimpLoader.MODID, bus = Mod.EventBusSubscriber.Bus.MOD, value = Dist.CLIENT)
public final class VehicleRenderRegistration {
    private VehicleRenderRegistration() {}

    @SubscribeEvent
    public static void renderers(EntityRenderersEvent.RegisterRenderers event) {
        event.registerEntityRenderer(VehicleRegistry.ARMORED_CAR.get(), ArmoredCarRenderer::new);
    }

    @SubscribeEvent
    public static void models(ModelEvent.RegisterAdditional event) {
        var asset = new ResourceLocation(AssimpLoader.MODID, "models/entity/armored_car/armored_car.fbx");
        if (Minecraft.getInstance().getResourceManager().getResource(asset).isPresent())
            event.register(new ResourceLocation(AssimpLoader.MODID, "vehicle/armored_car"));
    }
}
