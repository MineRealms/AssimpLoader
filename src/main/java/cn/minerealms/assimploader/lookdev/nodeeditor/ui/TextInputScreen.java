package cn.minerealms.assimploader.lookdev.nodeeditor.ui;

import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;

import java.util.function.Consumer;

/**
 * Simple text input screen for editing node properties
 *
 * This is a temporary solution using Minecraft's native GUI
 * until ModernUI TextField support is available.
 */
public class TextInputScreen extends Screen {

    private final Screen parent;
    private final String initialValue;
    private final Consumer<String> onConfirm;
    private EditBox textField;

    public TextInputScreen(Component title, Screen parent, String initialValue, Consumer<String> onConfirm) {
        super(title);
        this.parent = parent;
        this.initialValue = initialValue;
        this.onConfirm = onConfirm;
    }

    @Override
    protected void init() {
        super.init();

        // Create text field
        int fieldWidth = 300;
        int fieldHeight = 20;
        int fieldX = (this.width - fieldWidth) / 2;
        int fieldY = this.height / 2 - 30;

        textField = new EditBox(
            this.font,
            fieldX,
            fieldY,
            fieldWidth,
            fieldHeight,
            Component.literal("Text Input")
        );
        textField.setMaxLength(256);
        textField.setValue(initialValue);
        textField.setFocused(true);
        addRenderableWidget(textField);

        // Confirm button
        addRenderableWidget(Button.builder(
            Component.literal("Confirm"),
            button -> {
                onConfirm.accept(textField.getValue());
                minecraft.setScreen(parent);
            }
        ).bounds(this.width / 2 - 155, this.height / 2 + 20, 150, 20).build());

        // Cancel button
        addRenderableWidget(Button.builder(
            Component.literal("Cancel"),
            button -> minecraft.setScreen(parent)
        ).bounds(this.width / 2 + 5, this.height / 2 + 20, 150, 20).build());
    }

    @Override
    public void render(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
        // Draw background
        renderBackground(graphics);

        // Draw title
        graphics.drawCenteredString(this.font, this.title, this.width / 2, this.height / 2 - 60, 0xFFFFFF);

        // Render widgets
        super.render(graphics, mouseX, mouseY, partialTick);
    }

    @Override
    public boolean keyPressed(int keyCode, int scanCode, int modifiers) {
        // Enter key confirms
        if (keyCode == 257 || keyCode == 335) { // ENTER or NUMPAD_ENTER
            onConfirm.accept(textField.getValue());
            minecraft.setScreen(parent);
            return true;
        }

        // ESC cancels
        if (keyCode == 256) { // ESC
            minecraft.setScreen(parent);
            return true;
        }

        return super.keyPressed(keyCode, scanCode, modifiers);
    }

    @Override
    public boolean isPauseScreen() {
        return false;
    }
}
