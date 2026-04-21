package cn.minerealms.iic.turrets.client.gui;

import mekanism.client.gui.IGuiWrapper;
import mekanism.client.gui.element.button.MekanismButton;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.network.chat.Component;
import org.jetbrains.annotations.NotNull;

import java.util.function.Supplier;

/**
 * 简单的模式切换按钮
 */
public class ModeToggleButton extends MekanismButton {

    private final Supplier<Component> textSupplier;

    public ModeToggleButton(IGuiWrapper gui, int x, int y, int width, int height,
                           Supplier<Component> textSupplier, Runnable onPress, IHoverable onHover) {
        super(gui, x, y, width, height, Component.empty(), onPress, onHover);
        this.textSupplier = textSupplier;
    }

    @Override
    public void drawBackground(@NotNull GuiGraphics guiGraphics, int mouseX, int mouseY, float partialTicks) {
        // 绘制按钮背景
        int color = isMouseOver(mouseX, mouseY) ? 0xFF8B8B8B : 0xFF5A5A5A;
        guiGraphics.fill(getX(), getY(), getX() + getWidth(), getY() + getHeight(), color);

        // 绘制边框
        guiGraphics.fill(getX(), getY(), getX() + getWidth(), getY() + 1, 0xFFFFFFFF); // 上
        guiGraphics.fill(getX(), getY() + getHeight() - 1, getX() + getWidth(), getY() + getHeight(), 0xFF000000); // 下
        guiGraphics.fill(getX(), getY(), getX() + 1, getY() + getHeight(), 0xFFFFFFFF); // 左
        guiGraphics.fill(getX() + getWidth() - 1, getY(), getX() + getWidth(), getY() + getHeight(), 0xFF000000); // 右

        // 绘制文本
        Component text = textSupplier.get();
        int textWidth = minecraft.font.width(text);
        int textX = getX() + (getWidth() - textWidth) / 2;
        int textY = getY() + (getHeight() - 8) / 2;
        guiGraphics.drawString(minecraft.font, text, textX, textY, 0xFFFFFFFF, false);
    }
}
