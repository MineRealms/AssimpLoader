package cn.minerealms.iic.client;

import cn.minerealms.iic.client.hud.DifficultyHudRenderer;
import cn.minerealms.iic.scanner.client.ScannerScreen;
import cn.minerealms.iic.turrets.client.renderer.LaserRenderer;
import cn.minerealms.iic.turrets.client.renderer.LaserTurretRenderer;
import cn.minerealms.iic.turrets.common.block_entity.LaserTurretBlockEntity;
import cn.minerealms.iic.turrets.common.entity.LaserEntity;
import cn.minerealms.iic.turrets.common.registry.BlockEntityTypeRegistry;
import cn.minerealms.iic.turrets.common.registry.ContainerTypeRegistry;
import cn.minerealms.iic.turrets.common.registry.EntityRegistry;
import net.minecraft.client.gui.screens.MenuScreens;
import net.minecraft.client.renderer.blockentity.BlockEntityRenderers;
import net.minecraft.client.renderer.entity.EntityRenderers;
import net.minecraftforge.client.event.RegisterGuiOverlaysEvent;
import net.minecraftforge.client.gui.overlay.VanillaGuiOverlay;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.eventbus.api.IEventBus;
import net.minecraftforge.fml.event.lifecycle.FMLClientSetupEvent;
import net.minecraftforge.fml.javafmlmod.FMLJavaModLoadingContext;

public class ClientEventHandler {

    public static void setup() {
        IEventBus modBus = FMLJavaModLoadingContext.get().getModEventBus();
        modBus.addListener(ClientEventHandler::onClientSetup);
        modBus.addListener(ClientEventHandler::onRegisterGuiOverlays);
    }

    private static void onClientSetup(FMLClientSetupEvent event) {
        event.enqueueWork(() -> {
            // Screens are registered in ClientSetup via Mekanism's ClientRegistrationUtil
            // Block entity renderers are also registered in ClientSetup
            // Entity renderers are also registered in ClientSetup
            // Nothing to do here - all client registration is handled by ClientSetup
        });
    }

    private static void onRegisterGuiOverlays(RegisterGuiOverlaysEvent event) {
        event.registerAbove(VanillaGuiOverlay.HOTBAR.id(), "difficulty_hud",
                (gui, graphics, partialTick, width, height) -> {
                    DifficultyHudRenderer.render(graphics, partialTick);
                });
    }
}
