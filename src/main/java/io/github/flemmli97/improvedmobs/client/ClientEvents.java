package io.github.flemmli97.improvedmobs.client;

import io.github.flemmli97.improvedmobs.ImprovedMobs;
import io.github.flemmli97.improvedmobs.config.Config;
import io.github.flemmli97.improvedmobs.industrial.GTIntegration;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.phys.AABB;
import org.joml.Matrix4f;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

public class ClientEvents {

    private static float clientDifficulty;
    private static final ResourceLocation tex = new ResourceLocation(ImprovedMobs.MODID, "textures/gui/difficulty_bar.png");
    
    public static boolean highlightPollution = false;
    private static List<BlockPos> highlightedMachines = new ArrayList<>();
    private static int scanTick = 0;

    public static void showDifficulty(GuiGraphics graphics) {
        if (!Config.ClientConfig.showDifficultyServerSync || !Config.ClientConfig.showDifficulty || Minecraft.getInstance().options.renderDebug)
            return;
        graphics.pose().pushPose();
        Font font = Minecraft.getInstance().font;
        MutableComponent txt = Component.translatable("improvedmobs.overlay.difficulty", String.format(Locale.US, "%.1f", clientDifficulty)).withStyle(Config.ClientConfig.color);
        float scale = Config.ClientConfig.scale;
        graphics.pose().scale(scale, scale, scale);
        int width = font.width(txt);
        int x = Config.ClientConfig.guiX;
        int y = Config.ClientConfig.guiY;
        switch (Config.ClientConfig.location) {
            case TOPRIGHT ->
                    x = Minecraft.getInstance().getWindow().getGuiScaledWidth() - 7 - width - Config.ClientConfig.guiX;
            case BOTTOMRIGHT -> {
                x = Minecraft.getInstance().getWindow().getGuiScaledWidth() - 7 - width - Config.ClientConfig.guiX;
                y = Minecraft.getInstance().getWindow().getGuiScaledHeight() - 17 - Config.ClientConfig.guiY;
            }
            case BOTTOMLEFT ->
                    y = Minecraft.getInstance().getWindow().getGuiScaledHeight() - 17 - Config.ClientConfig.guiY;
        }
        graphics.blit(tex, x, y, 0, 0, 4 + width, 17, 256, 256);
        graphics.blit(tex, x + 4 + width, y, 183, 0, 3, 17, 256, 256);
        graphics.drawString(font, Component.translatable("improvedmobs.overlay.difficulty", String.format(java.util.Locale.US, "%.1f", clientDifficulty)).withStyle(Config.ClientConfig.color), x + 4, y + 5, 0, false);
        graphics.pose().popPose();
    }

    public static void updateClientDifficulty(float difficulty) {
        clientDifficulty = difficulty;
    }

    public static void toggleHighlight() {
        highlightPollution = !highlightPollution;
        Minecraft.getInstance().player.displayClientMessage(Component.literal("Pollution Highlight: " + (highlightPollution ? "ON" : "OFF")), false);
        if (!highlightPollution) {
            highlightedMachines.clear();
        }
    }

    public static void tick() {
        if (!highlightPollution) return;
        scanTick++;
        if (scanTick % 20 == 0) { // scan every second
            LocalPlayer player = Minecraft.getInstance().player;
            if (player == null) return;
            Level level = player.level();
            BlockPos center = player.blockPosition();
            int radius = 32;
            List<BlockPos> newMachines = new ArrayList<>();
            for (BlockPos pos : BlockPos.betweenClosed(center.offset(-radius, -radius / 2, -radius), 
                                                       center.offset(radius, radius / 2, radius))) {
                BlockEntity be = level.getBlockEntity(pos);
                if (GTIntegration.isGTMachine(be) && GTIntegration.isMachineActive(be)) {
                    int tier = GTIntegration.getVoltageTier(be);
                    if (tier >= 0) {
                        newMachines.add(pos.immutable());
                    }
                }
            }
            highlightedMachines = newMachines;
        }
    }

    public static void renderMachines(PoseStack poseStack, double camX, double camY, double camZ) {
        if (!highlightPollution || highlightedMachines.isEmpty()) return;
        
        com.mojang.blaze3d.systems.RenderSystem.disableDepthTest();
        com.mojang.blaze3d.systems.RenderSystem.depthMask(false);
        
        poseStack.pushPose();
        poseStack.translate(-camX, -camY, -camZ);
        
        VertexConsumer buffer = Minecraft.getInstance().renderBuffers().bufferSource().getBuffer(net.minecraft.client.renderer.RenderType.lines());
        
        for (BlockPos pos : highlightedMachines) {
            AABB box = new AABB(pos).inflate(0.002D);
            // Render glowing red/yellow box (1.0f, 0.2f, 0.2f, 1.0f)
            net.minecraft.client.renderer.LevelRenderer.renderLineBox(poseStack, buffer, box, 1.0f, 0.2f, 0.2f, 1.0f);
        }
        
        Minecraft.getInstance().renderBuffers().bufferSource().endBatch(net.minecraft.client.renderer.RenderType.lines());
        
        poseStack.popPose();
        
        com.mojang.blaze3d.systems.RenderSystem.depthMask(true);
        com.mojang.blaze3d.systems.RenderSystem.enableDepthTest();
    }
}
