package cn.minerealms.iic.scanner.client;

import cn.minerealms.iic.scanner.ScanData;
import cn.minerealms.iic.scanner.network.PacketScannerRequest;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.network.chat.Component;

public class ScannerScreen extends Screen {
    private static final int MAP_SIZE = 112;
    private static final int GUI_WIDTH = 160;
    private static final int GUI_HEIGHT = 160;

    private ScanData cachedData;
    private boolean scanRequested = false;
    private long animationStartTime = 0;
    private boolean isScanning = false;
    private boolean animationPlayed = false;

    private int guiLeft;
    private int guiTop;

    public ScannerScreen() {
        super(Component.literal("Terrain Scanner"));
    }

    @Override
    protected void init() {
        super.init();
        this.guiLeft = (this.width - GUI_WIDTH) / 2;
        this.guiTop = (this.height - GUI_HEIGHT) / 2;

        this.addRenderableWidget(Button.builder(Component.literal("Scan"), button -> {
            this.requestScan();
        }).pos(this.guiLeft + 50, this.guiTop + 130).width(60).build());

        this.animationStartTime = System.currentTimeMillis();
        this.isScanning = true;
        this.animationPlayed = false;

        if (this.cachedData == null && !this.scanRequested) {
            this.requestScan();
        }
    }

    private void requestScan() {
        this.scanRequested = true;
        this.isScanning = true;
        this.animationStartTime = System.currentTimeMillis();
        this.animationPlayed = false;
        
        LocalPlayer player = this.minecraft.player;
        if (player != null) {
            PacketScannerRequest.sendToServer(player);
        }
    }

    public void onScanDataReceived(ScanData data) {
        this.cachedData = data;
        this.isScanning = false;
    }

    @Override
    public void render(GuiGraphics guiGraphics, int mouseX, int mouseY, float partialTick) {
        this.renderBackground(guiGraphics);

        guiGraphics.fill(this.guiLeft, this.guiTop, this.guiLeft + GUI_WIDTH, this.guiTop + GUI_HEIGHT, 0xE0000000);

        int mapOffsetX = this.guiLeft + (GUI_WIDTH - MAP_SIZE) / 2;
        int mapOffsetY = this.guiTop + (GUI_HEIGHT - MAP_SIZE) / 2;

        if (this.cachedData != null) {
            this.renderMap(guiGraphics, mapOffsetX, mapOffsetY);
        }

        if (this.isScanning && !this.animationPlayed) {
            this.renderScanAnimation(guiGraphics, mapOffsetX, mapOffsetY);
        }

        guiGraphics.drawCenteredString(this.font, this.title.getString(), this.width / 2, this.guiTop + 5, 0xFFFFFF);

        super.render(guiGraphics, mouseX, mouseY, partialTick);
    }

    private void renderMap(GuiGraphics guiGraphics, int mapX, int mapY) {
        int[] pixels = this.cachedData.getPixels();
        
        long elapsed = System.currentTimeMillis() - this.animationStartTime;
        int scanRadius = (int) (elapsed / 30);

        for (int z = 0; z < MAP_SIZE; z++) {
            for (int x = 0; x < MAP_SIZE; x++) {
                int distFromCenter = (int) Math.sqrt(
                        Math.pow(x - MAP_SIZE / 2, 2) + Math.pow(z - MAP_SIZE / 2, 2));

                if (distFromCenter <= scanRadius) {
                    int color = pixels[z * MAP_SIZE + x];
                    if (color != 0) {
                        guiGraphics.fill(mapX + x, mapY + z, mapX + x + 1, mapY + z + 1, color);
                    }
                }
            }
        }

        int centerPx = mapX + MAP_SIZE / 2;
        int centerPz = mapY + MAP_SIZE / 2;
        guiGraphics.fill(centerPx - 2, centerPz - 2, centerPx + 3, centerPz + 3, 0xFFFF0000);
    }

    private void renderScanAnimation(GuiGraphics guiGraphics, int mapX, int mapY) {
        long elapsed = System.currentTimeMillis() - this.animationStartTime;
        int radius = (int) (elapsed / 20);

        if (radius > 0) {
            for (int angle = 0; angle < 360; angle += 5) {
                double rad = Math.toRadians(angle);
                int x = mapX + MAP_SIZE / 2 + (int) (Math.cos(rad) * radius);
                int y = mapY + MAP_SIZE / 2 + (int) (Math.sin(rad) * radius);

                if (x >= mapX && x < mapX + MAP_SIZE && y >= mapY && y < mapY + MAP_SIZE) {
                    guiGraphics.fill(x, y, x + 1, y + 1, 0xCCFFFFFF);
                }
            }
        }

        if (radius > MAP_SIZE + 10) {
            this.animationPlayed = true;
            this.isScanning = false;
        }
    }

    @Override
    public boolean isPauseScreen() {
        return false;
    }
}