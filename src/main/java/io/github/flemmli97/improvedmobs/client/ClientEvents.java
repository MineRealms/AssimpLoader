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

    public static void renderMachines(PoseStack poseStack, VertexConsumer buffer, double camX, double camY, double camZ) {
        if (!highlightPollution || highlightedMachines.isEmpty()) return;
        
        Matrix4f pose = poseStack.last().pose();
        for (BlockPos pos : highlightedMachines) {
            AABB aabb = new AABB(pos).move(-camX, -camY, -camZ);
            
            // Render glowing red box
            float r = 1.0f;
            float g = 0.2f;
            float b = 0.2f;
            float a = 0.8f;
            
            // Draw box edges
            // Bottom
            buffer.vertex(pose, (float)aabb.minX, (float)aabb.minY, (float)aabb.minZ).color(r, g, b, a).endVertex();
            buffer.vertex(pose, (float)aabb.maxX, (float)aabb.minY, (float)aabb.minZ).color(r, g, b, a).endVertex();
            buffer.vertex(pose, (float)aabb.maxX, (float)aabb.minY, (float)aabb.minZ).color(r, g, b, a).endVertex();
            buffer.vertex(pose, (float)aabb.maxX, (float)aabb.minY, (float)aabb.maxZ).color(r, g, b, a).endVertex();
            buffer.vertex(pose, (float)aabb.maxX, (float)aabb.minY, (float)aabb.maxZ).color(r, g, b, a).endVertex();
            buffer.vertex(pose, (float)aabb.minX, (float)aabb.minY, (float)aabb.maxZ).color(r, g, b, a).endVertex();
            buffer.vertex(pose, (float)aabb.minX, (float)aabb.minY, (float)aabb.maxZ).color(r, g, b, a).endVertex();
            buffer.vertex(pose, (float)aabb.minX, (float)aabb.minY, (float)aabb.minZ).color(r, g, b, a).endVertex();
            
            // Top
            buffer.vertex(pose, (float)aabb.minX, (float)aabb.maxY, (float)aabb.minZ).color(r, g, b, a).endVertex();
            buffer.vertex(pose, (float)aabb.maxX, (float)aabb.maxY, (float)aabb.minZ).color(r, g, b, a).endVertex();
            buffer.vertex(pose, (float)aabb.maxX, (float)aabb.maxY, (float)aabb.minZ).color(r, g, b, a).endVertex();
            buffer.vertex(pose, (float)aabb.maxX, (float)aabb.maxY, (float)aabb.maxZ).color(r, g, b, a).endVertex();
            buffer.vertex(pose, (float)aabb.maxX, (float)aabb.maxY, (float)aabb.maxZ).color(r, g, b, a).endVertex();
            buffer.vertex(pose, (float)aabb.minX, (float)aabb.maxY, (float)aabb.maxZ).color(r, g, b, a).endVertex();
            buffer.vertex(pose, (float)aabb.minX, (float)aabb.maxY, (float)aabb.maxZ).color(r, g, b, a).endVertex();
            buffer.vertex(pose, (float)aabb.minX, (float)aabb.maxY, (float)aabb.minZ).color(r, g, b, a).endVertex();
            
            // Pillars
            buffer.vertex(pose, (float)aabb.minX, (float)aabb.minY, (float)aabb.minZ).color(r, g, b, a).endVertex();
            buffer.vertex(pose, (float)aabb.minX, (float)aabb.maxY, (float)aabb.minZ).color(r, g, b, a).endVertex();
            buffer.vertex(pose, (float)aabb.maxX, (float)aabb.minY, (float)aabb.minZ).color(r, g, b, a).endVertex();
            buffer.vertex(pose, (float)aabb.maxX, (float)aabb.maxY, (float)aabb.minZ).color(r, g, b, a).endVertex();
            buffer.vertex(pose, (float)aabb.maxX, (float)aabb.minY, (float)aabb.maxZ).color(r, g, b, a).endVertex();
            buffer.vertex(pose, (float)aabb.maxX, (float)aabb.maxY, (float)aabb.maxZ).color(r, g, b, a).endVertex();
            buffer.vertex(pose, (float)aabb.minX, (float)aabb.minY, (float)aabb.maxZ).color(r, g, b, a).endVertex();
            buffer.vertex(pose, (float)aabb.minX, (float)aabb.maxY, (float)aabb.maxZ).color(r, g, b, a).endVertex();
        }
    }
}
