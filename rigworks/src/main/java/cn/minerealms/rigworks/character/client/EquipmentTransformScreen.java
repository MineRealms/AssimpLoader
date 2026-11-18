package cn.minerealms.rigworks.character.client;

import cn.minerealms.assimploader.client.transform.EditableTransform;
import cn.minerealms.assimploader.client.transform.WorldEditCamera;
import cn.minerealms.assimploader.client.ui.FlatUi;
import net.minecraft.client.KeyMapping;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.components.Tooltip;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.HumanoidArm;
import net.minecraft.world.phys.Vec3;
import org.lwjgl.glfw.GLFW;

import java.io.IOException;
import java.util.ArrayDeque;
import java.util.Deque;
import java.util.EnumMap;
import java.util.Locale;

/** A non-pausing editor over the real world, using the normal character/item renderers. */
public final class EquipmentTransformScreen extends Screen {
    private record Change(HumanoidArm hand, EditableTransform before, EditableTransform after) {}

    private final Screen parent;
    private final CharacterEquipmentEditorSession session;
    private final WorldEditCamera camera = new WorldEditCamera();
    private EquipmentTransformGizmo gizmo;
    private CharacterWorkbenchPose.Lease frozenPose;
    private EditableTransform gizmoStart;
    private final EditBox[] fields = new EditBox[7];
    private final EnumMap<HumanoidArm, EditableTransform> saved = new EnumMap<>(HumanoidArm.class);
    private final EnumMap<HumanoidArm, String> itemNames = new EnumMap<>(HumanoidArm.class);
    private final Deque<Change> undo = new ArrayDeque<>(), redo = new ArrayDeque<>();
    private HumanoidArm hand = HumanoidArm.RIGHT;
    private Button save, undoButton, redoButton, reset;
    private int left, panelWidth, firstField, rowGap, actionY, scrub = -1, drag = -1;
    private boolean updating, invalid, handFocus, closed;
    private String notice = "";
    private boolean error;

    private EquipmentTransformScreen(Screen parent, CharacterEquipmentEditorSession session) {
        super(Component.literal("Equipment Workbench"));
        this.parent = parent;
        this.session = session;
        var player = Minecraft.getInstance().player;
        for (var arm : HumanoidArm.values()) {
            if (session.available(arm)) saved.put(arm, session.get(arm));
            var stack = player.getMainArm() == arm ? player.getMainHandItem() : player.getOffhandItem();
            itemNames.put(arm, stack.isEmpty() ? "Empty hand" : stack.getHoverName().getString());
        }
        if (!session.available(hand)) hand = HumanoidArm.LEFT;
    }

    public static void open() { open(null); }

    public static void open(Screen parent) {
        Minecraft mc = Minecraft.getInstance();
        try {
            var session = CharacterEquipmentEditorSession.open();
            KeyMapping.releaseAll();
            mc.setScreen(new EquipmentTransformScreen(parent, session));
        } catch (IllegalStateException exception) {
            if (mc.player != null) mc.player.displayClientMessage(Component.literal(exception.getMessage()), false);
        }
    }

    @Override protected void init() {
        panelWidth = Math.min(220, Math.max(200, width - 150));
        left = width - panelWidth - 8;
        firstField = height < 290 ? 79 : 84;
        rowGap = height < 290 ? 31 : 44;
        actionY = height - 58;
        if (frozenPose == null) frozenPose = CharacterWorkbenchPose.freeze();
        if (!camera.active()) camera.open(this::focusPoint);
        camera.frame((double) left / width / 2);
        if (gizmo == null) {
            gizmo = new EquipmentTransformGizmo(session);
            gizmo.changeListener(() -> { notice = ""; refreshFields(); });
        }
        gizmo.hand(hand);
        EquipmentTransformGizmo.Mode[] modes = EquipmentTransformGizmo.Mode.values();
        FlatUi.Icon[] icons = {FlatUi.Icon.MOVE, FlatUi.Icon.ROTATE, FlatUi.Icon.SCALE};
        String[] tips = {"Translate (W)", "Rotate (E)", "Scale (R)"};
        for (int i = 0; i < modes.length; i++) {
            var mode = modes[i];
            addRenderableWidget(FlatUi.tool(8 + i * 30, 8, icons[i], Component.literal(tips[i]),
                    () -> gizmo.mode() == mode, button -> { gizmo.mode(mode); setFocused(null); }));
        }
        addRenderableWidget(FlatUi.icon(left + panelWidth - 32, 8, FlatUi.Icon.CLOSE,
                Component.translatable("gui.done"), button -> onClose()));
        int half = (panelWidth - 24) / 2;
        for (var arm : HumanoidArm.values()) {
            var choice = addRenderableWidget(FlatUi.choice(left + 10 + (arm == HumanoidArm.RIGHT ? 0 : half + 4),
                    height < 290 ? 30 : 34, half, 22, Component.literal(arm == HumanoidArm.RIGHT ? "Right" : "Left"),
                    () -> hand == arm, button -> select(arm)));
            choice.active = session.available(arm);
        }
        int inner = panelWidth - 20, cell = inner / 3;
        for (int index = 0; index < fields.length; index++) {
            int row = index / 3, column = index % 3;
            int x = index == 6 ? left + 10 : left + 10 + column * cell + 11;
            int w = index == 6 ? inner : cell - 14;
            fields[index] = addRenderableWidget(FlatUi.field(font, x, firstField + row * rowGap, w, 26,
                    Component.literal(index == 6 ? "Uniform scale" : (row == 0 ? "Position " : "Rotation ") + "XYZ".charAt(column))));
            fields[index].setMaxLength(18);
            fields[index].setTooltip(Tooltip.create(Component.literal(index == 6 ? "Uniform item scale"
                    : index < 3 ? "Offset along bone-local " + "XYZ".charAt(column) + " (metres)"
                    : "Intrinsic XYZ rotation: " + "XYZ".charAt(column) + " (degrees)")));
            fields[index].setResponder(ignored -> edited());
        }
        undoButton = addRenderableWidget(FlatUi.icon(left + 10, actionY, FlatUi.Icon.PREVIOUS,
                Component.literal("Undo (Ctrl+Z)"), button -> undo()));
        redoButton = addRenderableWidget(FlatUi.icon(left + 40, actionY, FlatUi.Icon.NEXT,
                Component.literal("Redo (Ctrl+Y)"), button -> redo()));
        reset = addRenderableWidget(FlatUi.icon(left + 70, actionY, FlatUi.Icon.RESET,
                Component.literal("Reset selected hand to resource definition"), button -> reset()));
        addRenderableWidget(FlatUi.icon(left + 100, actionY, FlatUi.Icon.FIT,
                Component.literal("Focus body / selected hand (F)"), button -> toggleFocus()));
        save = addRenderableWidget(FlatUi.button(left + 10, height - 30, (inner-4)/2, 24,
                Component.literal("Save"), button -> save()));
        save.setTooltip(Tooltip.create(Component.literal(session.outputPath().toString())));
        addRenderableWidget(FlatUi.button(left+14+(inner-4)/2,height-30,(inner-4)/2,24,
                Component.literal("Share"),button->{
                    if(invalid||!session.valid())return;
                    try{session.share();notice=CharacterVisualSharing.status();error=false;}
                    catch(RuntimeException exception){notice=exception.getMessage();error=true;}
                })).setTooltip(Tooltip.create(Component.literal("Apply the current visual configuration to the server and other players")));
        refreshFields();
    }

    private Vec3 focusPoint(Float partialTick) {
        if (handFocus && session.valid() && session.available(hand)) return session.focus(hand, partialTick);
        var player = Minecraft.getInstance().player;
        if (player == null) return Vec3.ZERO;
        return CharacterWorkbenchPose.bodyFocus(player, partialTick);
    }

    private void select(HumanoidArm arm) {
        hand = arm;
        if (gizmo != null) gizmo.hand(arm);
        refreshFields();
        if (handFocus) camera.focus();
    }

    private static float[] values(EditableTransform value) {
        return new float[]{value.x(), value.y(), value.z(), value.rx(), value.ry(), value.rz(), value.scale()};
    }

    private void refreshFields() {
        updating = true;
        float[] current = values(session.get(hand));
        for (int i = 0; i < fields.length; i++) {
            fields[i].setValue(format(current[i]));
            fields[i].setCursorPosition(0);
            fields[i].setHighlightPos(0);
        }
        updating = false;
        invalid = false;
        for (var field : fields) field.setTextColor(FlatUi.TEXT);
        updateButtons();
    }

    private static String format(float value) {
        if (value == 0) return "0";
        return String.format(Locale.ROOT, "%.4f", value).replaceAll("0+$", "").replaceAll("\\.$", "");
    }

    private void edited() {
        if (updating || fields[6] == null) return;
        try {
            float[] v = new float[7];
            for (int i = 0; i < v.length; i++) v[i] = Float.parseFloat(fields[i].getValue());
            change(new EditableTransform(v[0], v[1], v[2], v[3], v[4], v[5], v[6]), false);
            invalid = false;
            notice = "";
            for (var field : fields) field.setTextColor(FlatUi.TEXT);
        } catch (IllegalArgumentException exception) {
            invalid = true;
            for (var field : fields) field.setTextColor(FlatUi.ERROR);
        }
        updateButtons();
    }

    private void change(EditableTransform value, boolean refresh) {
        EditableTransform before = session.get(hand);
        if (!value.equals(before)) {
            session.preview(hand, value);
            undo.push(new Change(hand, before, value));
            if (undo.size() > 128) undo.removeLast();
            redo.clear();
        }
        if (refresh) refreshFields();
    }

    private void reset() {
        EditableTransform before = session.get(hand);
        session.reset(hand);
        EditableTransform after = session.get(hand);
        if (!before.equals(after)) {
            undo.push(new Change(hand, before, after));
            redo.clear();
        }
        refreshFields();
    }

    private void undo() {
        if (undo.isEmpty()) return;
        Change edit = undo.pop();
        session.preview(edit.hand, edit.before);
        redo.push(edit);
        select(edit.hand);
    }

    private void redo() {
        if (redo.isEmpty()) return;
        Change edit = redo.pop();
        session.preview(edit.hand, edit.after);
        undo.push(edit);
        select(edit.hand);
    }

    private void save() {
        if (invalid || !session.valid()) return;
        try {
            session.save();
            for (var arm : HumanoidArm.values()) if (session.available(arm)) saved.put(arm, session.get(arm));
            notice = "Saved";
            error = false;
        } catch (IOException | RuntimeException exception) {
            notice = exception.getMessage() == null ? "Save failed" : exception.getMessage();
            error = true;
        }
        updateButtons();
    }

    private boolean dirty() {
        return saved.entrySet().stream().anyMatch(entry -> !entry.getValue().equals(session.get(entry.getKey())));
    }

    private void updateButtons() {
        if (save == null) return;
        save.active = session.valid() && !invalid;
        reset.active = session.valid();
        undoButton.active = !undo.isEmpty();
        redoButton.active = !redo.isEmpty();
    }

    private void toggleFocus() {
        handFocus = !handFocus;
        camera.focus();
        setFocused(null);
    }

    @Override public void tick() {
        if (!session.valid() || frozenPose == null || !frozenPose.valid()
                || minecraft.player == null || minecraft.level == null) { onClose(); return; }
        for (var field : fields) field.tick();
        if(!CharacterVisualSharing.status().isBlank()&&(notice.startsWith("Waiting for server")||notice.startsWith("Shared revision")))notice=CharacterVisualSharing.status();
    }

    @Override public void render(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
        if (!session.valid()) return;
        graphics.drawManaged(() -> gizmo.render(graphics, mouseX, mouseY, partialTick));
        graphics.fill(left, 4, width - 8, height - 4, 0xe8181a1d);
        graphics.drawString(font, title, left + 10, 13, FlatUi.TEXT, false);
        graphics.drawString(font, FlatUi.clip(font, session.node(hand), panelWidth - 20), left + 10, height < 290 ? 56 : 60, FlatUi.MUTED, false);
        String[] labels = {"Position (m)", "Rotation (deg)", "Scale"};
        int cell = (panelWidth - 20) / 3;
        int[] axisColors = {0xffff8585, 0xff86df9b, 0xff81baff};
        for (int row = 0; row < 3; row++) {
            int y = firstField + row * rowGap;
            graphics.drawString(font, labels[row], left + 10, y - 11, FlatUi.MUTED, false);
            if (row < 2) for (int axis = 0; axis < 3; axis++)
                graphics.drawString(font, "XYZ".substring(axis, axis + 1), left + 10 + axis * cell, y + 9, axisColors[axis], false);
        }
        String status = invalid ? "Invalid transform" : !notice.isBlank() ? notice : dirty() ? "Unsaved changes" : "";
        graphics.drawString(font, FlatUi.clip(font, status, panelWidth - 20), left + 10, actionY - 12,
                invalid || error ? FlatUi.ERROR : FlatUi.ACCENT, false);
        graphics.drawString(font, "Frozen pose / Bone local", 10, 40, FlatUi.TEXT, true);
        graphics.drawString(font, handFocus ? (hand == HumanoidArm.RIGHT ? "Right hand" : "Left hand") : "Body",
                10, 52, FlatUi.MUTED, true);
        graphics.drawString(font, FlatUi.clip(font, itemNames.get(hand), Math.max(20, left - 20)),
                10, 64, FlatUi.TEXT, true);
        super.render(graphics, mouseX, mouseY, partialTick);
        if (mouseX < left && !gizmo.tooltip().isBlank())
            graphics.renderTooltip(font, font.split(Component.literal(gizmo.tooltip()), Math.min(220, left - 16)), mouseX, mouseY);
    }

    @Override public boolean mouseClicked(double x, double y, int button) {
        if (y >= 8 && y < 34 && x >= 8 && x < 98) return super.mouseClicked(x, y, button);
        if (x < left) {
            setFocused(null);
            if (gizmo.mouseClicked(x, y, button)) {
                gizmoStart = session.get(hand);
                return true;
            }
            drag = button;
            return true;
        }
        if (button == 0) {
            int cell = (panelWidth - 20) / 3;
            for (int row = 0; row < 2; row++) for (int axis = 0; axis < 3; axis++) {
                int labelX = left + 10 + axis * cell, labelY = firstField + row * rowGap;
                if (x >= labelX && x < labelX + 11 && y >= labelY && y < labelY + 26) {
                    scrub = row * 3 + axis;
                    setFocused(null);
                    return true;
                }
            }
        }
        return super.mouseClicked(x, y, button);
    }

    private void step(int index, double amount) {
        float[] v = values(session.get(hand));
        v[index] += (float) amount;
        if (index < 3) v[index] = Mth.clamp(v[index], -10, 10);
        if (index == 6) v[index] = Mth.clamp(v[index], .001f, 10);
        try { change(new EditableTransform(v[0], v[1], v[2], v[3], v[4], v[5], v[6]), true); }
        catch (IllegalArgumentException ignored) { }
    }

    @Override public boolean mouseDragged(double x, double y, int button, double dx, double dy) {
        if (gizmoStart != null && gizmo.mouseDragged(x, y, button, dx, dy)) return true;
        if (scrub >= 0) {
            step(scrub, dx * (scrub < 3 ? .005 : .5) * (hasShiftDown() ? .1 : 1));
            return true;
        }
        if (drag >= 0) {
            if (drag == 0 && !hasShiftDown()) camera.orbit(dx, dy);
            else camera.pan(dx, dy);
            return true;
        }
        return super.mouseDragged(x, y, button, dx, dy);
    }

    @Override public boolean mouseReleased(double x, double y, int button) {
        if (gizmo.mouseReleased(x, y, button) && gizmoStart != null) {
            EditableTransform after = session.get(hand);
            if (!after.equals(gizmoStart)) {
                undo.push(new Change(hand, gizmoStart, after));
                if (undo.size() > 128) undo.removeLast();
                redo.clear();
            }
            gizmoStart = null;
            updateButtons();
            return true;
        }
        if (scrub >= 0 || drag >= 0) { scrub = drag = -1; return true; }
        return super.mouseReleased(x, y, button);
    }

    @Override public boolean mouseScrolled(double x, double y, double wheel) {
        if (hasShiftDown()) camera.lift(wheel);
        else camera.zoom(wheel);
        return true;
    }

    @Override public boolean keyPressed(int key, int scan, int modifiers) {
        if (hasControlDown() && key == GLFW.GLFW_KEY_Z) { if (hasShiftDown()) redo(); else undo(); return true; }
        if (hasControlDown() && key == GLFW.GLFW_KEY_Y) { redo(); return true; }
        if (hasControlDown() && key == GLFW.GLFW_KEY_S) { save(); return true; }
        if (key == GLFW.GLFW_KEY_F) { toggleFocus(); return true; }
        if (!(getFocused() instanceof EditBox)) {
            if (key == GLFW.GLFW_KEY_W) { gizmo.mode(EquipmentTransformGizmo.Mode.TRANSLATE); return true; }
            if (key == GLFW.GLFW_KEY_E) { gizmo.mode(EquipmentTransformGizmo.Mode.ROTATE); return true; }
            if (key == GLFW.GLFW_KEY_R) { gizmo.mode(EquipmentTransformGizmo.Mode.SCALE); return true; }
        }
        if (key == GLFW.GLFW_KEY_UP || key == GLFW.GLFW_KEY_DOWN) {
            for (int i = 0; i < fields.length; i++) if (fields[i].isFocused()) {
                double increment = i < 3 ? .01 : i == 6 ? .01 : 1;
                step(i, (key == GLFW.GLFW_KEY_UP ? increment : -increment) * (hasShiftDown() ? .1 : 1));
                return true;
            }
        }
        return super.keyPressed(key, scan, modifiers);
    }

    @Override public void onClose() {
        finish();
        minecraft.setScreen(minecraft.level == null ? null : parent);
    }

    private void finish() {
        if (closed) return;
        closed = true;
        if (gizmo != null) gizmo.close();
        session.cancel();
        if (frozenPose != null) frozenPose.close();
        camera.close();
    }

    @Override public void removed() { finish(); }
    @Override public boolean isPauseScreen() { return false; }
}
