package cn.minerealms.assimpshowcase.vehicle.client;

import cn.minerealms.assimploader.AssimpLoader;
import cn.minerealms.assimpshowcase.vehicle.VehicleRegistry;
import net.minecraft.client.Minecraft;
import net.minecraft.resources.ResourceLocation;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.client.event.EntityRenderersEvent;
import net.minecraftforge.client.event.ModelEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import cn.minerealms.assimploader.util.ResourceLocations;
@Mod.EventBusSubscriber(modid = "assimpshowcase", bus = Mod.EventBusSubscriber.Bus.MOD, value = Dist.CLIENT)
public final class VehicleRenderRegistration {
    private VehicleRenderRegistration() {}

    @SubscribeEvent
    public static void renderers(EntityRenderersEvent.RegisterRenderers event) {
        event.registerEntityRenderer(VehicleRegistry.ARMORED_CAR.get(), ArmoredCarRenderer::new);
    }

    @SubscribeEvent
    public static void models(ModelEvent.RegisterAdditional event) {
        var asset = ResourceLocations.of(AssimpLoader.MODID, "models/entity/armored_car/armored_car.fbx");
        if (Minecraft.getInstance().getResourceManager().getResource(asset).isPresent()) {
            //? if forge {
            event.register(ResourceLocations.of(AssimpLoader.MODID, "vehicle/armored_car"));
            //?}
            //? if neoforge {
            /*
            // 1.21 requires the standalone-variant wrapper instead of a bare id.
            event.register(net.minecraft.client.resources.model.ModelResourceLocation.standalone(
                    ResourceLocations.of(AssimpLoader.MODID, "vehicle/armored_car")));
            *///?}
        }
    }
}
