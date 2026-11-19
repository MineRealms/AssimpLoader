package cn.minerealms.assimploader.util;

import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.Screen;

/**
 * Version-neutral {@link Screen} entry points that changed signature between 1.20.1 and 1.21.
 * <p>
 * {@code Screen#renderBackground} gained {@code mouseX/mouseY/partialTick} parameters in 1.21 so the
 * background could be blurred relative to the widget under the cursor; 1.20.1 only took the graphics
 * object. Kept as a helper rather than inline conditionals because every screen subclass repeats it.
 */
public final class ScreenCompat {
    private ScreenCompat() {}

    public static void renderBackground(Screen screen, GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
        //? if forge {
        screen.renderBackground(graphics);
        //?}
        //? if neoforge {
        /*
        screen.renderBackground(graphics, mouseX, mouseY, partialTick);
        *///?}
    }
}
