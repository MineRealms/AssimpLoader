package cn.minerealms.rigworks.character.client;

import cn.minerealms.assimploader.client.ui.FlatUi;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
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
    private int first, left, panelWidth, visibleRows, listY, footerY, controlsY, motionY, profileY;
    private boolean compact;

    public CharacterSelectionScreen(Screen parent) {
        super(Component.literal("Character"));
        this.parent = parent;
    }

    @Override protected void init() {
        panelWidth = Math.min(760, width - 32);
        left = (width - panelWidth) / 2;
        compact = height < 360;
        controlsY = compact ? 50 : 62;
        motionY = compact ? 88 : 106;
        profileY = compact ? 126 : 150;
        cn.minerealms.assimploader.client.ui.LookDevNavigation.install(this,widget->addRenderableWidget(widget),
                cn.minerealms.assimploader.client.ui.LookDevNavigation.Page.PLAYER);
        String value = profile == null ? LocalPlayerCharacter.settings().profile() : profile.getValue();
        int toggleWidth = Math.max(64, (panelWidth - 44) / 3);
        addRenderableWidget(FlatUi.toggle(left, controlsY, toggleWidth, 26, Component.literal("Character"),
                () -> LocalPlayerCharacter.settings().enabled(), button -> {
                    cn.minerealms.rigworks.RigWorksConfig.update(edit->edit.set(cn.minerealms.rigworks.RigWorksConfig.CLIENT.enabled,
                            !LocalPlayerCharacter.settings().enabled()));
                    LocalPlayerCharacter.refreshConfiguration(); LocalPlayerCharacter.reload();
                })).setTooltip(Tooltip.create(Component.literal("Enable custom character")));
        addRenderableWidget(FlatUi.toggle(left + toggleWidth + 6, controlsY, toggleWidth, 26, Component.literal("Rig physics"),
                LocalPlayerCharacter::physicsEnabled,
                button -> LocalPlayerCharacter.setPhysicsEnabled(!LocalPlayerCharacter.physicsEnabled())))
                .setTooltip(Tooltip.create(Component.literal("Enable character rigid-body physics")));
        addRenderableWidget(FlatUi.toggle(left + 2 * (toggleWidth + 6),controlsY,toggleWidth,26,Component.literal("Contacts"),
                LocalPlayerCharacter::handContactsEnabled,button->LocalPlayerCharacter.setHandContactsEnabled(!LocalPlayerCharacter.handContactsEnabled())))
                .setTooltip(Tooltip.create(Component.literal("Support-hand contacts from equipment JSON")));
        addRenderableWidget(FlatUi.icon(left + panelWidth - 26, controlsY, FlatUi.Icon.RELOAD,
                Component.literal("Reload character"), button -> LocalPlayerCharacter.reload()));

        var modes = cn.minerealms.rigworks.RigWorksConfig.CharacterAnimationMode.values();
        int modeWidth = (panelWidth - 4) / modes.length;
        for (int index = 0; index < modes.length; index++) {
            var mode = modes[index];
            String label = switch (mode) { case VANILLA -> "Vanilla"; case SCRIPTED -> "Scripted"; case CLIPS -> "Clips"; };
            addRenderableWidget(FlatUi.choice(left + index * (modeWidth + 2), motionY,
                    index == modes.length - 1 ? panelWidth - index * (modeWidth + 2) : modeWidth, 26,
                    Component.literal(label), () -> LocalPlayerCharacter.animationMode() == mode,
                    button -> LocalPlayerCharacter.setAnimationMode(mode)));
        }

        profile = FlatUi.field(font, left, profileY, panelWidth - 82, 26, Component.literal("Profile resource"));
        profile.setMaxLength(512);
        profile.setValue(value);
        profile.setHint(Component.literal("namespace:models/character/profile.json"));
        profile.setResponder(ignored -> { updateApply(); updateRows(); });
        addRenderableWidget(profile);
        apply = addRenderableWidget(FlatUi.button(left + panelWidth - 76, profileY, 76, 26, Component.literal("Apply"),
                button -> applyProfile()));

        profiles = minecraft.getResourceManager().listResources("models", id -> id.getPath().endsWith("/profile.json"))
                .keySet().stream().sorted().toList();
        listY = compact ? 174 : 200;
        footerY = height - (compact ? 28 : 58);
        visibleRows = Math.max(1, Math.min(12, (footerY - listY - 12) / 30));
        rows.clear();
        for (int index = 0; index < visibleRows; index++) {
            final int row = index;
            rows.add(addRenderableWidget(FlatUi.row(left, listY + index * 30, panelWidth, 28, Component.empty(),
                    () -> first + row < profiles.size() && profiles.get(first + row).toString().equals(profile.getValue()),
                    button -> {
                        int selected = first + row;
                        if (selected < profiles.size()) profile.setValue(profiles.get(selected).toString());
                    })));
        }
        previous = addRenderableWidget(FlatUi.icon(left, footerY - 1, FlatUi.Icon.PREVIOUS,
                Component.literal("Previous profiles"), button -> scroll(-visibleRows)));
        next = addRenderableWidget(FlatUi.icon(left + 30, footerY - 1, FlatUi.Icon.NEXT,
                Component.literal("Next profiles"), button -> scroll(visibleRows)));
        addRenderableWidget(FlatUi.button(left + panelWidth - 172, footerY - 1, 90, 26,
                Component.literal("Equipment"), button -> EquipmentTransformScreen.open(this)));
        addRenderableWidget(FlatUi.button(left + panelWidth - 76, footerY - 1, 76, 26,
                Component.translatable("gui.done"), button -> onClose()));
        updateRows(); updateApply();
    }

    private void updateApply() {
        if (apply != null) apply.active = ResourceLocation.tryParse(profile.getValue()) != null;
    }

    private void applyProfile() {
        ResourceLocation selected = ResourceLocation.tryParse(profile.getValue());
        if (selected == null) return;
        cn.minerealms.rigworks.RigWorksConfig.update(edit->edit.set(cn.minerealms.rigworks.RigWorksConfig.CLIENT.profile,selected.toString()));
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
                button.setMessage(Component.literal(FlatUi.clip(font, id, panelWidth - 24)));
                button.setTooltip(Tooltip.create(Component.literal(id)));
            }
        }
        if (previous != null) previous.active = first > 0;
        if (next != null) next.active = first + visibleRows < profiles.size();
    }

    @Override public void tick() { profile.tick(); }

    @Override public void render(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
        graphics.fill(0, 0, width, height, FlatUi.BACKGROUND);
        cn.minerealms.assimploader.client.ui.LookDevNavigation.render(graphics,width);
        graphics.drawString(font, "Motion", left, motionY - 11, FlatUi.MUTED, false);
        graphics.drawString(font, "Profile", left, profileY - 11, FlatUi.MUTED, false);
        graphics.drawString(font, "Available profiles", left, listY - 13, FlatUi.MUTED, false);
        String count = profiles.isEmpty() ? "0" : (first + 1) + "-" + Math.min(profiles.size(), first + visibleRows) + " / " + profiles.size();
        graphics.drawString(font, count, left + panelWidth - font.width(count), listY - 13, FlatUi.MUTED, false);
        if (profiles.isEmpty()) graphics.drawString(font, "No character profiles", left + 9, listY + 9, FlatUi.MUTED, false);
        graphics.fill(left, footerY - 10, left + panelWidth, footerY - 9, FlatUi.LINE);
        var diagnostics = LocalPlayerCharacter.diagnostics();
        String status = diagnostics.status() + (diagnostics.selectedAnimation().isBlank() ? "" : "  /  " + diagnostics.selectedAnimation());
        graphics.drawString(font, FlatUi.clip(font, status, Math.max(0, panelWidth - 246)), left + 66, footerY + 8,
                diagnostics.status() == LocalPlayerCharacter.Status.FAILED ? FlatUi.ERROR : FlatUi.MUTED, false);
        if (!compact && !diagnostics.failure().isBlank()) graphics.drawString(font,
                FlatUi.clip(font, diagnostics.failure(), panelWidth), left, height - 17, FlatUi.ERROR, false);
        super.render(graphics, mouseX, mouseY, partialTick);
        if (!diagnostics.failure().isBlank() && mouseX >= left && mouseX < left + panelWidth && mouseY >= footerY)
            graphics.renderTooltip(font, font.split(Component.literal(diagnostics.failure()), panelWidth), mouseX, mouseY);
    }

    @Override public boolean mouseScrolled(double x, double y, double amount) {
        if (x >= left && x < left + panelWidth && y >= listY && y < listY + visibleRows * 30) {
            scroll(-(int) Math.signum(amount)); return true;
        }
        return super.mouseScrolled(x, y, amount);
    }

    @Override public void onClose() { cn.minerealms.assimploader.client.ui.LookDevNavigation.close(parent); }
    @Override public boolean isPauseScreen() { return false; }
}
