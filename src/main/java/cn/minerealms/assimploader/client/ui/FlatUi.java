package cn.minerealms.assimploader.client.ui;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.components.Tooltip;
import net.minecraft.network.chat.Component;

import java.util.function.BooleanSupplier;

/** Shared, texture-independent controls for the active AssimpLoader screens. */
public final class FlatUi {
    public static final int BACKGROUND = 0xff181a1d, SURFACE = 0xff202327, FIELD = 0xff111417;
    public static final int LINE = 0xff373c42, TEXT = 0xffe5e9ed, MUTED = 0xff9da6b0;
    public static final int ACCENT = 0xff58d5bd, SELECTED = 0xff203c38, ERROR = 0xffff8e8e;
    public enum Icon { CLOSE, RELOAD, FIT, RESET, PLUS, MINUS, PREVIOUS, NEXT, MOVE, ROTATE, SCALE, OPEN, IMPORT, EXPORT, SAVE, UNDO, REDO, PLAY, PAUSE }

    private FlatUi() {}

    public static Button button(int x, int y, int width, int height, Component label, Button.OnPress action) {
        return new FlatButton(x, y, width, height, label, action, () -> false, null);
    }

    public static Button choice(int x, int y, int width, int height, Component label,
                                BooleanSupplier selected, Button.OnPress action) {
        return new FlatButton(x, y, width, height, label, action, selected, null);
    }

    public static Button toggle(int x, int y, int width, int height, Component label,
                                BooleanSupplier selected, Button.OnPress action) {
        FlatButton button = new FlatButton(x, y, width, height, label, action, selected, null);
        button.toggle = true;
        return button;
    }

    public static Button row(int x, int y, int width, int height, Component label,
                            BooleanSupplier selected, Button.OnPress action) {
        FlatButton button = new FlatButton(x, y, width, height, label, action, selected, null);
        button.leftAligned = true;
        return button;
    }

    public static Button icon(int x, int y, Icon icon, Component tooltip, Button.OnPress action) {
        Button button = new FlatButton(x, y, 26, 26, tooltip, action, () -> false, icon);
        button.setTooltip(Tooltip.create(tooltip));
        return button;
    }

    public static Button tool(int x, int y, Icon icon, Component tooltip, BooleanSupplier selected, Button.OnPress action) {
        Button button = new FlatButton(x, y, 26, 26, tooltip, action, selected, icon);
        button.setTooltip(Tooltip.create(tooltip));
        return button;
    }

    public static EditBox field(Font font, int x, int y, int width, int height, Component label) {
        return new FlatField(font, x, y, width, height, label);
    }

    public static String clip(Font font, String text, int width) {
        if (font.width(text) <= width) return text;
        return font.plainSubstrByWidth(text, Math.max(0, width - font.width("..."))) + "...";
    }

    private static final class FlatField extends EditBox {
        private FlatField(Font font, int x, int y, int width, int height, Component label) {
            super(font, x + 7, y + (height - 8) / 2, width - 14, 10, label);
            setBordered(false);
            setTextColor(TEXT);
            setTextColorUneditable(MUTED);
        }

        @Override public void renderWidget(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
            int x = getX() - 7, y = getY() - 9;
            graphics.fill(x, y, x + getWidth() + 14, y + 26, FIELD);
            graphics.fill(x, y + 25, x + getWidth() + 14, y + 26, isFocused() ? ACCENT : LINE);
            super.renderWidget(graphics, mouseX, mouseY, partialTick);
        }

        @Override public boolean isMouseOver(double mouseX, double mouseY) {
            return mouseX >= getX() - 7 && mouseX < getX() + getWidth() + 7
                    && mouseY >= getY() - 9 && mouseY < getY() + 17;
        }

        @Override public boolean mouseClicked(double mouseX, double mouseY, int button) {
            if (button == 0 && active && visible && isMouseOver(mouseX, mouseY)) {
                return super.mouseClicked(Math.max(getX(), Math.min(getX() + getWidth() - 1, mouseX)),
                        Math.max(getY(), Math.min(getY() + getHeight() - 1, mouseY)), button);
            }
            return super.mouseClicked(mouseX, mouseY, button);
        }
    }

    private static final class FlatButton extends Button {
        private final BooleanSupplier selected;
        private final Icon icon;
        private boolean toggle, leftAligned;

        private FlatButton(int x, int y, int width, int height, Component label, OnPress action,
                           BooleanSupplier selected, Icon icon) {
            super(x, y, width, height, label, action, DEFAULT_NARRATION);
            this.selected = selected;
            this.icon = icon;
        }

        @Override public void renderWidget(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
            boolean chosen = selected.getAsBoolean(), hover = isHoveredOrFocused();
            int x = getX(), y = getY(), w = getWidth(), h = getHeight();
            graphics.fill(x, y, x + w, y + h, !active ? 0xff24272b
                    : chosen ? SELECTED : hover ? 0xff363d43 : 0xff2a2f34);
            if (chosen || isFocused()) graphics.fill(x, y + h - 2, x + w, y + h, ACCENT);
            int color = !active ? 0xff69717a : chosen ? ACCENT : TEXT;
            if (icon != null) drawIcon(graphics, x + w / 2, y + h / 2, color);
            else {
                Font font = Minecraft.getInstance().font;
                String label = clip(font, getMessage().getString(), w - (toggle ? 48 : 20));
                graphics.drawString(font, label, toggle || leftAligned ? x + 10 : x + (w - font.width(label)) / 2,
                        y + (h - 8) / 2, color, false);
                if (toggle) {
                    int switchX = x + w - 32, switchY = y + h / 2 - 5;
                    graphics.fill(switchX, switchY, switchX + 22, switchY + 10, chosen ? ACCENT : 0xff68727b);
                    int knob = switchX + (chosen ? 13 : 1);
                    graphics.fill(knob, switchY + 1, knob + 8, switchY + 9, FIELD);
                }
            }
        }

        private void drawIcon(GuiGraphics g, int x, int y, int color) {
            switch (icon) {
                case OPEN -> {g.fill(x-7,y-4,x+6,y+6,color);g.fill(x-6,y-6,x-1,y-3,color);g.fill(x-5,y-2,x+7,y+5,FIELD);}
                case SAVE -> {g.fill(x-6,y-6,x+6,y+6,color);g.fill(x-3,y-5,x+3,y-1,FIELD);g.fill(x-4,y+1,x+4,y+5,FIELD);}
                case IMPORT, EXPORT -> {int s=icon==Icon.IMPORT?1:-1;g.fill(x,y-6,x+1,y+4,color);for(int i=0;i<4;i++)g.fill(x-i,y+s*(3-i),x+i+1,y+s*(3-i)+1,color);g.fill(x-6,y+6,x+7,y+7,color);}
                case UNDO, REDO -> {int s=icon==Icon.UNDO?-1:1;g.fill(x-5,y-2,x+6,y-1,color);g.fill(x+s*5,y-1,x+s*5+1,y+5,color);for(int i=0;i<4;i++){int xx=x+s*(-5+i);g.fill(xx,y-2-i,xx+1,y-1+i,color);}}
                case PLAY -> {for(int i=0;i<8;i++)g.fill(x-3+i,y-5+i/2,x-2+i,y+6-i/2,color);}
                case PAUSE -> {g.fill(x-5,y-5,x-2,y+6,color);g.fill(x+2,y-5,x+5,y+6,color);}
                case PLUS -> { g.fill(x - 5, y, x + 6, y + 1, color); g.fill(x, y - 5, x + 1, y + 6, color); }
                case MINUS -> g.fill(x - 5, y, x + 6, y + 1, color);
                case MOVE -> {
                    g.fill(x - 6, y, x + 7, y + 1, color); g.fill(x, y - 6, x + 1, y + 7, color);
                    for (int i = 0; i < 3; i++) {
                        g.fill(x - i, y - 6 + i, x + i + 1, y - 5 + i, color);
                        g.fill(x - i, y + 6 - i, x + i + 1, y + 7 - i, color);
                        g.fill(x - 6 + i, y - i, x - 5 + i, y + i + 1, color);
                        g.fill(x + 6 - i, y - i, x + 7 - i, y + i + 1, color);
                    }
                }
                case ROTATE -> {
                    g.fill(x - 4, y - 6, x + 4, y - 5, color); g.fill(x - 6, y - 4, x - 5, y + 4, color);
                    g.fill(x - 4, y + 5, x + 4, y + 6, color); g.fill(x + 5, y, x + 6, y + 4, color);
                    g.fill(x + 4, y - 7, x + 5, y - 2, color); g.fill(x, y - 3, x + 5, y - 2, color);
                }
                case SCALE -> {
                    g.fill(x - 6, y + 2, x - 1, y + 7, color);
                    for (int i = -2; i <= 5; i++) g.fill(x + i, y - i, x + i + 1, y - i + 1, color);
                    g.fill(x + 1, y - 6, x + 7, y - 5, color); g.fill(x + 6, y - 6, x + 7, y, color);
                }
                case CLOSE -> { for (int i = -4; i <= 4; i++) { g.fill(x + i, y + i, x + i + 1, y + i + 1, color); g.fill(x - i, y + i, x - i + 1, y + i + 1, color); } }
                case PREVIOUS, NEXT -> { int sign = icon == Icon.NEXT ? 1 : -1; for (int i = 0; i < 5; i++) { int dx = sign * (2 - i); g.fill(x + dx, y - i, x + dx + 1, y - i + 1, color); g.fill(x + dx, y + i, x + dx + 1, y + i + 1, color); } }
                case FIT -> { for (int sx : new int[]{-1, 1}) for (int sy : new int[]{-1, 1}) { int xx = x + sx * 5, yy = y + sy * 5; g.fill(xx, Math.min(yy, yy - sy * 3), xx + 1, Math.max(yy, yy - sy * 3) + 1, color); g.fill(Math.min(xx, xx - sx * 3), yy, Math.max(xx, xx - sx * 3) + 1, yy + 1, color); } }
                case RELOAD, RESET -> { g.fill(x - 4, y - 5, x + 5, y - 4, color); g.fill(x - 5, y - 4, x - 4, y + 4, color); g.fill(x - 4, y + 4, x + 4, y + 5, color); g.fill(x + 4, y, x + 5, y + 4, color); g.fill(x + 3, y - 7, x + 4, y - 2, color); g.fill(x, y - 3, x + 4, y - 2, color); }
            }
        }

    }
}
