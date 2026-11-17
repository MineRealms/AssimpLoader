package cn.minerealms.assimpshowcase.showcase;

import cn.minerealms.assimpshowcase.deer.DeerShowcaseRegistry;
import cn.minerealms.assimpshowcase.vehicle.VehicleClientInput;
import cn.minerealms.assimpshowcase.vehicle.VehicleNetworking;
import cn.minerealms.assimpshowcase.vehicle.VehicleRegistry;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.event.lifecycle.FMLCommonSetupEvent;
import net.minecraftforge.fml.javafmlmod.FMLJavaModLoadingContext;
import net.minecraftforge.fml.loading.FMLEnvironment;

/** Optional world content; the model engine never registers these gameplay objects. */
@Mod(AssimpShowcase.MODID)
public final class AssimpShowcase {
    public static final String MODID = "assimpshowcase";

    public AssimpShowcase() {
        var bus = FMLJavaModLoadingContext.get().getModEventBus();
        ShowcaseRegistry.register(bus);
        DeerShowcaseRegistry.register(bus);
        VehicleRegistry.register(bus);
        bus.addListener(this::commonSetup);
        if (FMLEnvironment.dist == Dist.CLIENT) {
            cn.minerealms.assimpshowcase.ShowcaseConfig.register();
            bus.addListener(VehicleClientInput::registerKeyMappings);
            MinecraftForge.EVENT_BUS.register(VehicleClientInput.class);
        }
    }

    private void commonSetup(FMLCommonSetupEvent event) {
        event.enqueueWork(VehicleNetworking::register);
    }
}
