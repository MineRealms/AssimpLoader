package cn.minerealms.assimploader.character.client;

import cn.minerealms.assimploader.config.AssimpLoaderConfig;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.CycleButton;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.components.Tooltip;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;

import java.util.ArrayList;
import java.util.List;

/** Resource-pack profile selection; model loading stays in the existing async owner. */
public final class CharacterSelectionScreen extends Screen {
    private final Screen parent;
    private final List<Button> rows = new ArrayList<>();
    private List<ResourceLocation> profiles = List.of();
    private EditBox profile;
    private Button apply, previous, next;
    private int first, left, panelWidth, visibleRows;

    public CharacterSelectionScreen(Screen parent) {
        super(Component.literal("Character"));
        this.parent = parent;
    }

    @Override protected void init() {
        panelWidth = Math.min(460, width - 24);
        left = (width - panelWidth) / 2;
        visibleRows = Math.max(1, Math.min(8, (height - 165) / 23));
        String value = profile == null ? LocalPlayerCharacter.settings().profile() : profile.getValue();
        addRenderableWidget(CycleButton.onOffBuilder(LocalPlayerCharacter.settings().enabled())
                .create(left, 29, 130, 20, Component.literal("Character"), (button, enabled) -> {
                    var setting = AssimpLoaderConfig.CLIENT.character.enabled;
                    setting.set(enabled); setting.save();
                    LocalPlayerCharacter.refreshConfiguration(); LocalPlayerCharacter.reload();
                }));
        addRenderableWidget(Button.builder(Component.literal("Reload"), button -> LocalPlayerCharacter.reload())
                .bounds(left + panelWidth - 74, 29, 74, 20).build());
        profile = new EditBox(font, left, 58, panelWidth - 78, 20, Component.literal("Profile resource"));
        profile.setMaxLength(512);
        profile.setValue(value);
        profile.setResponder(ignored -> updateApply());
        addRenderableWidget(profile);
        apply = addRenderableWidget(Button.builder(Component.literal("Apply"), button -> applyProfile())
                .bounds(left + panelWidth - 74, 58, 74, 20).build());
        profiles = minecraft.getResourceManager().listResources("models", id -> id.getPath().endsWith("/profile.json"))
                .keySet().stream().sorted().toList();
        rows.clear();
        for (int index = 0; index < visibleRows; index++) {
            final int row = index;
            rows.add(addRenderableWidget(Button.builder(Component.empty(), button -> {
                int selected = first + row;
                if (selected < profiles.size()) profile.setValue(profiles.get(selected).toString());
            }).bounds(left, 89 + index * 23, panelWidth, 20).build()));
        }
        int bottom = 94 + visibleRows * 23;
        previous = addRenderableWidget(Button.builder(Component.literal("<"), button -> scroll(-visibleRows))
                .bounds(left, bottom, 24, 20).tooltip(Tooltip.create(Component.literal("Previous profiles"))).build());
        next = addRenderableWidget(Button.builder(Component.literal(">"), button -> scroll(visibleRows))
                .bounds(left + 28, bottom, 24, 20).tooltip(Tooltip.create(Component.literal("Next profiles"))).build());
        addRenderableWidget(Button.builder(Component.translatable("gui.done"), button -> onClose())
                .bounds(left + panelWidth - 74, bottom, 74, 20).build());
        updateRows(); updateApply();
    }

    private void updateApply() {
        if (apply != null) apply.active = ResourceLocation.tryParse(profile.getValue()) != null;
    }

    private void applyProfile() {
        ResourceLocation selected = ResourceLocation.tryParse(profile.getValue());
        if (selected == null) return;
        var setting = AssimpLoaderConfig.CLIENT.character.profile;
        setting.set(selected.toString()); setting.save();
        LocalPlayerCharacter.refreshConfiguration(); LocalPlayerCharacter.reload();
    }

    private void scroll(int amount) {
        first = Math.max(0, Math.min(Math.max(0, profiles.size() - visibleRows), first + amount));
        updateRows();
    }

    private void updateRows() {
        first = Math.max(0, Math.min(Math.max(0, profiles.size() - visibleRows), first));
        for (int index = 0; index < rows.size(); index++) {
            Button button = rows.get(index);
            int entry = first + index;
            button.visible = entry < profiles.size();
            if (button.visible) {
                String id = profiles.get(entry).toString();
                button.setMessage(Component.literal(font.plainSubstrByWidth(id, panelWidth - 16)));
                button.setTooltip(Tooltip.create(Component.literal(id)));
            }
        }
        previous.active = first > 0;
        next.active = first + visibleRows < profiles.size();
    }

    @Override public void tick() { profile.tick(); }

    @Override public void render(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
        renderBackground(graphics);
        graphics.drawCenteredString(font, title, width / 2, 9, 0xffffff);
        super.render(graphics, mouseX, mouseY, partialTick);
        int y = 120 + visibleRows * 23;
        var diagnostics = LocalPlayerCharacter.diagnostics();
        graphics.drawString(font, font.plainSubstrByWidth(diagnostics.status() + "  " + diagnostics.selectedAnimation(), panelWidth),
                left, y, diagnostics.status() == LocalPlayerCharacter.Status.FAILED ? 0xff7777 : 0xdddddd);
        if (!diagnostics.failure().isBlank()) {
            var lines = font.split(Component.literal(diagnostics.failure()), panelWidth);
            for (int index = 0; index < Math.min(lines.size(), Math.max(0, (height - y - 20) / 10)); index++)
                graphics.drawString(font, lines.get(index), left, y + 13 + index * 10, 0xffaaaa);
        }
    }

    @Override public boolean mouseScrolled(double x, double y, double amount) {
        if (x >= left && x < left + panelWidth && y >= 89 && y < 89 + visibleRows * 23) {
            scroll(-(int) Math.signum(amount)); return true;
        }
        return super.mouseScrolled(x, y, amount);
    }

    @Override public void onClose() { minecraft.setScreen(parent); }
    @Override public boolean isPauseScreen() { return false; }
}
