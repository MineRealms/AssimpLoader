package io.github.flemmli97.improvedmobs.forge.client;

import io.github.flemmli97.improvedmobs.ImprovedMobs;
import io.github.flemmli97.improvedmobs.client.ClientCalls;
import io.github.flemmli97.improvedmobs.client.ClientEvents;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.resources.ResourceLocation;
import net.minecraftforge.client.event.ClientPlayerNetworkEvent;
import net.minecraftforge.client.event.RegisterGuiOverlaysEvent;
import net.minecraftforge.client.event.RenderLevelStageEvent;
import net.minecraftforge.client.gui.overlay.VanillaGuiOverlay;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.fml.javafmlmod.FMLJavaModLoadingContext;
import com.mojang.blaze3d.vertex.VertexConsumer;

public class ClientEventHandler {

    public static final ResourceLocation overlayID = new ResourceLocation(ImprovedMobs.MODID, "difficulty_overlay");

    public static void setup() {
        FMLJavaModLoadingContext.get().getModEventBus()
                .addListener(ClientEventHandler::showDifficulty);
        MinecraftForge.EVENT_BUS.addListener(ClientEventHandler::leave);
        MinecraftForge.EVENT_BUS.addListener(ClientEventHandler::onClientTick);
        MinecraftForge.EVENT_BUS.addListener(ClientEventHandler::onRenderLevel);
        MinecraftForge.EVENT_BUS.addListener(ClientEventHandler::onClientCommand);
    }

    public static void onClientCommand(net.minecraftforge.client.event.RegisterClientCommandsEvent event) {
        event.getDispatcher().register(net.minecraft.commands.Commands.literal("improvedmobs_highlight")
                .executes(context -> {
                    ClientEvents.toggleHighlight();
                    return 1;
                }));
    }

    public static void showDifficulty(RegisterGuiOverlaysEvent e) {
        e.registerBelow(VanillaGuiOverlay.EXPERIENCE_BAR.id(), overlayID.getPath(),
                (forgeGui, graphics, partialTicks, width, length) -> {
                    ClientEvents.showDifficulty(graphics);
                });
    }

    public static void leave(ClientPlayerNetworkEvent.LoggingOut event) {
        ClientCalls.disconnect();
    }

    public static void onClientTick(TickEvent.ClientTickEvent event) {
        if (event.phase == TickEvent.Phase.END) {
            ClientEvents.tick();
        }
    }

    public static void onRenderLevel(RenderLevelStageEvent event) {
        if (event.getStage() == RenderLevelStageEvent.Stage.AFTER_PARTICLES) {
            VertexConsumer buffer = net.minecraft.client.Minecraft.getInstance().renderBuffers().bufferSource().getBuffer(RenderType.lines());
            ClientEvents.renderMachines(event.getPoseStack(), buffer, event.getCamera().getPosition().x, event.getCamera().getPosition().y, event.getCamera().getPosition().z);
            net.minecraft.client.Minecraft.getInstance().renderBuffers().bufferSource().endBatch(RenderType.lines());
        }
    }
}
