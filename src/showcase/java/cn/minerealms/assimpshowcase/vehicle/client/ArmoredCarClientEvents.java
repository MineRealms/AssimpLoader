package cn.minerealms.assimpshowcase.vehicle.client;

import cn.minerealms.assimploader.AssimpLoader;
import cn.minerealms.assimpshowcase.vehicle.ArmoredCarEntity;
import net.minecraft.client.CameraType;
import net.minecraft.client.Minecraft;
import net.minecraft.world.entity.player.Player;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.client.event.RenderPlayerEvent;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

import java.util.UUID;

@Mod.EventBusSubscriber(modid = "assimpshowcase", value = Dist.CLIENT)
public final class ArmoredCarClientEvents {
    private static UUID mountedVehicle;
    private static CameraType previousCamera;

    private ArmoredCarClientEvents() {}

    @SubscribeEvent
    public static void tick(TickEvent.ClientTickEvent event) {
        if (event.phase != TickEvent.Phase.END) return;
        Minecraft minecraft = Minecraft.getInstance();
        Player player = minecraft.player;
        if (player == null || minecraft.level == null) {
            restore(minecraft);
            return;
        }
        if (player.getVehicle() instanceof ArmoredCarEntity car) {
            if (!car.getUUID().equals(mountedVehicle)) {
                restore(minecraft);
                mountedVehicle = car.getUUID();
                previousCamera = minecraft.options.getCameraType();
                player.setYRot(car.getYRot());
                player.setYHeadRot(car.getYRot());
                player.setXRot(12.0f);
            }
            if (minecraft.options.getCameraType() != CameraType.THIRD_PERSON_BACK)
                minecraft.options.setCameraType(CameraType.THIRD_PERSON_BACK);
        } else {
            restore(minecraft);
        }
    }

    @SubscribeEvent
    public static void hidePassengerModel(RenderPlayerEvent.Pre event) {
        if (event.getEntity().getVehicle() instanceof ArmoredCarEntity) event.setCanceled(true);
    }

    private static void restore(Minecraft minecraft) {
        if (mountedVehicle == null) return;
        if (previousCamera != null) minecraft.options.setCameraType(previousCamera);
        mountedVehicle = null;
        previousCamera = null;
    }
}
