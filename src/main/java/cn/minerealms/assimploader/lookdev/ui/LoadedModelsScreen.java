package cn.minerealms.assimploader.lookdev.ui;

import cn.minerealms.assimploader.AssimpLoader;
import cn.minerealms.assimploader.client.ui.FlatUi;
import cn.minerealms.assimploader.core.AssimpModelLoader;
import cn.minerealms.assimploader.lookdev.preview.LoadedModelCatalog;
import cn.minerealms.assimploader.lookdev.preview.PreviewCamera;
import cn.minerealms.assimploader.lookdev.preview.PreviewGpuRenderer;
import cn.minerealms.assimploader.lookdev.preview.PreviewSceneData;
import cn.minerealms.assimploader.model.AssimpScene;
import cn.minerealms.assimploader.model.AssimpNode;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import org.joml.Vector3f;
import org.lwjgl.glfw.GLFW;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/** A render-thread Minecraft screen. Legacy ModernUI editor fragments are not instantiated. */
public final class LoadedModelsScreen extends Screen {
    private static final int ROW_HEIGHT = 32, LIST_TOP = 92;
    private final PreviewCamera camera = new PreviewCamera();
    private List<LoadedModelCatalog.Entry> models = List.of();
    private List<LoadedModelCatalog.Entry> filtered = List.of();
    private LoadedModelCatalog.Entry selected;
    private PreviewSceneData sceneData;
    private PreviewGpuRenderer renderer;
    private EditBox search;
    private Button fit;
    private Button reset;
    private Button zoomIn;
    private Button zoomOut;
    private int sidebar;
    private int firstRow;
    private int refreshCountdown;
    private long cacheGeneration = -1;
    private int dragButton = -1;
    private boolean nodesTab;
    private boolean renderFailed;
    private boolean previewRendered;
    private String dimensions = "";
    private List<NodeRow> nodes = List.of();
    private int firstNode;
    private record NodeRow(AssimpNode node, int depth) {}

    public LoadedModelsScreen() {
        this(false);
    }
    public LoadedModelsScreen(boolean nodes) {
        super(Component.literal("LookDev"));
        nodesTab=nodes;
    }

    @Override
    protected void init() {
        sidebar = Math.max(132, Math.min(260, width / 3));
        cn.minerealms.assimploader.client.ui.LookDevNavigation.install(this,widget->addRenderableWidget(widget),nodesTab?
                cn.minerealms.assimploader.client.ui.LookDevNavigation.Page.NODES:cn.minerealms.assimploader.client.ui.LookDevNavigation.Page.MODELS);
        String query = search == null ? "" : search.getValue();
        search = FlatUi.field(font, 10, 54, sidebar - 20, 26, Component.translatable("screen.assimploader.filter"));
        search.setMaxLength(256);
        search.setHint(Component.translatable("screen.assimploader.filter"));
        search.setValue(query);
        search.setResponder(value -> {
            filtered = LoadedModelCatalog.filter(models, value);
            firstRow = 0;
        });
        addRenderableWidget(search);
        int toolX = sidebar + 10;
        fit = addRenderableWidget(FlatUi.icon(toolX, 54, FlatUi.Icon.FIT,
                Component.translatable("screen.assimploader.fit"), button -> fitCamera(false)));
        reset = addRenderableWidget(FlatUi.icon(toolX + 30, 54, FlatUi.Icon.RESET,
                Component.translatable("screen.assimploader.reset"), button -> fitCamera(true)));
        zoomOut = addRenderableWidget(FlatUi.icon(toolX + 68, 54, FlatUi.Icon.MINUS,
                Component.translatable("screen.assimploader.zoom_out"), button -> camera.zoom(-1)));
        zoomIn = addRenderableWidget(FlatUi.icon(toolX + 98, 54, FlatUi.Icon.PLUS,
                Component.translatable("screen.assimploader.zoom_in"), button -> camera.zoom(1)));
        refreshCatalog();
        updateControls();
    }

    public void setTab(boolean nodes) {
        if (nodesTab == nodes) return;
        nodesTab = nodes;
        dragButton = -1;
        if (minecraft != null) rebuildWidgets();
    }

    private void updateControls() {
        boolean active = !nodesTab && selected != null;
        fit.active = reset.active = zoomIn.active = zoomOut.active = active;
    }

    private void fitCamera(boolean restoreRotation) {
        if (sceneData == null) return;
        float aspect = (float) previewWidth() / previewHeight();
        if (restoreRotation) camera.reset(sceneData.radius(), aspect);
        else camera.fit(sceneData.radius(), aspect);
    }

    @Override
    public void tick() {
        //? if forge {
        // 1.21 deleted EditBox#tick(); the caret now advances inside EditBox#renderWidget.
        if (search != null) search.tick();
        //?}
        if (--refreshCountdown <= 0 || cacheGeneration != AssimpModelLoader.getCacheGeneration()) {
            refreshCountdown = 20;
            refreshCatalog();
        }
    }

    private void refreshCatalog() {
        long currentGeneration = AssimpModelLoader.getCacheGeneration();
        if (cacheGeneration != currentGeneration) {
            releaseRenderer();
            renderFailed = false;
        }
        cacheGeneration = currentGeneration;
        models = LoadedModelCatalog.snapshot(AssimpModelLoader.getLoadedModels());
        filtered = LoadedModelCatalog.filter(models, search == null ? "" : search.getValue());
        LoadedModelCatalog.Entry replacement = null;
        if (selected != null) {
            for (LoadedModelCatalog.Entry entry : models) {
                if (entry.scene() == selected.scene()) { replacement = entry; break; }
                if (entry.name().equals(selected.name())
                        && entry.scene().getImportMode() == selected.scene().getImportMode()) replacement = entry;
            }
        }
        if (replacement == null && !filtered.isEmpty()) replacement = filtered.get(0);
        if (replacement == null || selected == null || replacement.scene() != selected.scene()) select(replacement);
        else selected = replacement;
        firstRow = Math.min(firstRow, Math.max(0, filtered.size() - visibleRows()));
    }

    private void select(LoadedModelCatalog.Entry entry) {
        releaseRenderer();
        selected = entry;
        renderFailed = false;
        previewRendered = false;
        sceneData = selected == null ? null : new PreviewSceneData(selected.scene());
        firstNode = 0;
        var hierarchy = new ArrayList<NodeRow>();
        if (selected != null) collectNodes(selected.scene().getRootNode(), 0, hierarchy);
        nodes = List.copyOf(hierarchy);
        if (sceneData != null) {
            Vector3f size = sceneData.size();
            dimensions = String.format(Locale.ROOT, "%.3g x %.3g x %.3g", size.x, size.y, size.z);
        } else dimensions = "";
        fitCamera(true);
        if (fit != null) updateControls();
    }

    private static void collectNodes(AssimpNode node, int depth, List<NodeRow> rows) {
        rows.add(new NodeRow(node, depth));
        for (AssimpNode child : node.getChildren()) collectNodes(child, depth + 1, rows);
    }

    private void releaseRenderer() {
        previewRendered = false;
        if (renderer != null) {
            renderer.close();
            renderer = null;
        }
    }

    private int previewX() { return sidebar + 1; }
    private int previewY() { return 90; }
    private int previewWidth() { return Math.max(1, width - previewX()); }
    private int previewHeight() { return Math.max(1, height - previewY() - 24); }
    private int listBottom() { return Math.max(LIST_TOP + ROW_HEIGHT, height - 82); }
    private int visibleRows() { return Math.max(1, (listBottom() - LIST_TOP) / ROW_HEIGHT); }
    private int visibleNodes() { return Math.max(1, (previewHeight() - 32) / 24); }

    @Override
    public void render(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
        graphics.fill(0, 0, width, height, FlatUi.BACKGROUND);
        graphics.fill(0, 0, width, 46, FlatUi.SURFACE);
        graphics.fill(0, 46, sidebar, height, FlatUi.SURFACE);
        graphics.fill(sidebar, 46, width, 89, 0xff24282c);
        graphics.fill(sidebar, 46, sidebar + 1, height, FlatUi.LINE);
        graphics.fill(0, 45, width, 46, FlatUi.LINE);
        graphics.fill(sidebar, 89, width, 90, FlatUi.LINE);
        graphics.fill(previewX(), previewY(), width, height - 24, 0xff282d31);
        cn.minerealms.assimploader.client.ui.LookDevNavigation.render(graphics,width);
        renderModelList(graphics, mouseX, mouseY);
        renderSelectionInfo(graphics);
        graphics.flush();
        if (!nodesTab && selected != null && !renderFailed) renderPreview();
        if (nodesTab) renderNodes(graphics, mouseX, mouseY);
        else if (renderFailed || selected == null) graphics.drawCenteredString(font,
                renderFailed ? Component.translatable("screen.assimploader.preview_failed") : Component.literal("No model selected"),
                previewX() + previewWidth() / 2, previewY() + previewHeight() / 2, renderFailed ? FlatUi.ERROR : FlatUi.MUTED);
        super.render(graphics, mouseX, mouseY, partialTick);
        renderHoveredName(graphics, mouseX, mouseY);
    }

    private void renderModelList(GuiGraphics graphics, int mouseX, int mouseY) {
        graphics.enableScissor(0, LIST_TOP, sidebar, listBottom());
        for (int row = 0; row < visibleRows() && firstRow + row < filtered.size(); row++) {
            LoadedModelCatalog.Entry entry = filtered.get(firstRow + row);
            int y = LIST_TOP + row * ROW_HEIGHT;
            boolean active = selected != null && entry.scene() == selected.scene();
            boolean hover = mouseX >= 0 && mouseX < sidebar && mouseY >= y && mouseY < y + ROW_HEIGHT;
            if (active || hover) graphics.fill(0, y, sidebar, y + ROW_HEIGHT - 1,
                    active ? FlatUi.SELECTED : 0xff2c3238);
            if (active) graphics.fill(0, y, 2, y + ROW_HEIGHT - 1, FlatUi.ACCENT);
            String name = entry.name();
            int mode = name.lastIndexOf('#');
            if (mode >= 0) name = name.substring(0, mode);
            int slash = name.lastIndexOf('/');
            if (slash >= 0) name = name.substring(slash + 1);
            graphics.drawString(font, clipped(name, sidebar - 24), 12, y + 5, active ? FlatUi.ACCENT : FlatUi.TEXT, false);
            graphics.drawString(font, entry.scene().getImportMode().name(), 12, y + 18, FlatUi.MUTED, false);
        }
        if (filtered.isEmpty()) graphics.drawString(font, Component.literal(models.isEmpty() ? "No loaded models" : "No matches"), 12, LIST_TOP + 8, FlatUi.MUTED, false);
        if (filtered.size() > visibleRows()) {
            int trackHeight = Math.max(1, listBottom() - LIST_TOP);
            int thumb = Math.max(8, trackHeight * visibleRows() / filtered.size());
            int position = (trackHeight - thumb) * firstRow / Math.max(1, filtered.size() - visibleRows());
            graphics.fill(sidebar - 3, LIST_TOP, sidebar - 1, listBottom(), FlatUi.LINE);
            graphics.fill(sidebar - 3, LIST_TOP + position, sidebar - 1, LIST_TOP + position + thumb, FlatUi.ACCENT);
        }
        graphics.disableScissor();
    }

    private void renderSelectionInfo(GuiGraphics graphics) {
        int y = Math.max(LIST_TOP + ROW_HEIGHT, height - 69);
        graphics.fill(0, y - 8, sidebar, y - 7, FlatUi.LINE);
        graphics.fill(sidebar, height - 24, width, height - 23, FlatUi.LINE);
        graphics.drawString(font, Component.translatable("screen.assimploader.models", models.size()), 8, y, 0xff9ca9ae, false);
        if (selected != null && sceneData != null) {
            AssimpScene scene = selected.scene();
            graphics.drawString(font, Component.translatable("screen.assimploader.meshes", scene.getMeshCount()), 8, y + 13, 0xffd2d9db, false);
            graphics.drawString(font, Component.translatable("screen.assimploader.triangles", sceneData.triangles()), 8, y + 26, 0xffd2d9db, false);
            graphics.drawString(font, Component.translatable("screen.assimploader.embedded", scene.getTextureCount()), 8, y + 39, 0xffd2d9db, false);
            int reservedWidth = previewWidth() > 380 ? font.width(dimensions) + 20 : 0;
            graphics.drawString(font, clipped(selected.name(), previewWidth() - 16 - reservedWidth), previewX() + 8, height - 17,
                    0xffc4d1d0, false);
            if (previewWidth() > 380) graphics.drawString(font, dimensions,
                    width - font.width(dimensions) - 8, height - 17, 0xff9ca9ae, false);
        }
    }

    private void renderNodes(GuiGraphics graphics, int mouseX, int mouseY) {
        int x = previewX() + 12, y = previewY() + 11;
        graphics.drawString(font, "Scene hierarchy", x, y, FlatUi.MUTED, false);
        String count = Integer.toString(nodes.size());
        graphics.drawString(font, count, width - 14 - font.width(count), y, FlatUi.MUTED, false);
        graphics.enableScissor(previewX(), previewY() + 26, width, height - 24);
        for (int row = 0; row < visibleNodes() && firstNode + row < nodes.size(); row++) {
            NodeRow entry = nodes.get(firstNode + row);
            int yy = previewY() + 30 + row * 24;
            boolean hovered = mouseX >= previewX() && mouseX < width && mouseY >= yy && mouseY < yy + 24;
            if (hovered) graphics.fill(previewX(), yy - 2, width, yy + 21, 0xff343c42);
            int indent = Math.min(entry.depth() * 12, previewWidth() / 3);
            if (entry.depth() > 0) {
                graphics.fill(x + indent - 7, yy - 6, x + indent - 6, yy + 5, FlatUi.LINE);
                graphics.fill(x + indent - 6, yy + 4, x + indent - 2, yy + 5, FlatUi.LINE);
            }
            String suffix = entry.node().getMeshIndices().length == 0 ? "" : "  [" + entry.node().getMeshIndices().length + "]";
            graphics.drawString(font, clipped(entry.node().getName() + suffix, previewWidth() - indent - 28),
                    x + indent, yy + 1, entry.node().getMeshIndices().length == 0 ? FlatUi.TEXT : FlatUi.ACCENT, false);
        }
        if (nodes.size() > visibleNodes()) {
            int trackY = previewY() + 28, trackHeight = Math.max(1, previewHeight() - 34);
            int thumb = Math.max(8, trackHeight * visibleNodes() / nodes.size());
            int offset = (trackHeight - thumb) * firstNode / Math.max(1, nodes.size() - visibleNodes());
            graphics.fill(width - 4, trackY, width - 2, trackY + trackHeight, FlatUi.LINE);
            graphics.fill(width - 4, trackY + offset, width - 2, trackY + offset + thumb, FlatUi.ACCENT);
        }
        graphics.disableScissor();
        if (nodes.isEmpty()) graphics.drawCenteredString(font, Component.literal("No scene nodes"),
                previewX() + previewWidth() / 2, previewY() + previewHeight() / 2, FlatUi.MUTED);
    }

    private void renderPreview() {
        try {
            if (renderer == null) renderer = new PreviewGpuRenderer(selected.scene(), sceneData);
            double scaleX = (double) minecraft.getWindow().getWidth() / width;
            double scaleY = (double) minecraft.getWindow().getHeight() / height;
            int x = (int) Math.floor(previewX() * scaleX);
            int right = (int) Math.ceil((previewX() + previewWidth()) * scaleX);
            int bottom = minecraft.getWindow().getHeight() - (int) Math.ceil((previewY() + previewHeight()) * scaleY);
            int top = minecraft.getWindow().getHeight() - (int) Math.floor(previewY() * scaleY);
            renderer.render(camera, x, bottom, right - x, top - bottom);
            previewRendered = true;
        } catch (Exception failure) {
            renderFailed = true;
            releaseRenderer();
            AssimpLoader.LOGGER.error("LookDev preview failed for {}", selected.name(), failure);
        }
    }

    private void renderHoveredName(GuiGraphics graphics, int x, int y) {
        if (nodesTab && x >= previewX() && x < width && y >= previewY() + 30 && y < height - 24) {
            int index = firstNode + (y - previewY() - 30) / 24;
            if (index < nodes.size()) graphics.renderTooltip(font, Component.literal(nodes.get(index).node().getName()), x, y);
        } else if (x >= 0 && x < sidebar && y >= LIST_TOP && y < listBottom()) {
            int index = firstRow + (y - LIST_TOP) / ROW_HEIGHT;
            if (index < filtered.size()) graphics.renderTooltip(font, Component.literal(filtered.get(index).name()), x, y);
        }
    }

    private String clipped(String value, int maxWidth) {
        if (font.width(value) <= maxWidth) return value;
        return font.plainSubstrByWidth(value, Math.max(0, maxWidth - font.width("..."))) + "...";
    }

    private boolean inPreview(double x, double y) {
        return !nodesTab && x >= previewX() && x < width && y >= previewY() && y < height - 24;
    }

    @Override
    public boolean mouseClicked(double x, double y, int button) {
        if (super.mouseClicked(x, y, button)) return true;
        search.setFocused(false);
        if (button == 0 && x >= 0 && x < sidebar && y >= LIST_TOP && y < listBottom()) {
            int index = firstRow + ((int) y - LIST_TOP) / ROW_HEIGHT;
            if (index < filtered.size()) select(filtered.get(index));
            return true;
        }
        if (inPreview(x, y) && button >= 0 && button <= 2) {
            dragButton = button;
            return true;
        }
        return false;
    }

    @Override
    public boolean mouseDragged(double x, double y, int button, double dx, double dy) {
        if (dragButton == button && !nodesTab) {
            if (button == 0 && !hasShiftDown()) camera.orbit(dx, dy);
            else camera.pan(dx, dy, previewHeight());
            return true;
        }
        return super.mouseDragged(x, y, button, dx, dy);
    }

    @Override
    public boolean mouseReleased(double x, double y, int button) {
        if (dragButton == button) { dragButton = -1; return true; }
        return super.mouseReleased(x, y, button);
    }

    private boolean scrolled(double x, double y, double amount) {
        if (inPreview(x, y)) { camera.zoom(amount); return true; }
        if (nodesTab && x >= previewX() && x < width && y >= previewY() && y < height - 24) {
            firstNode = Math.max(0, Math.min(Math.max(0, nodes.size() - visibleNodes()), firstNode - (int)Math.signum(amount) * 3));
            return true;
        }
        if (x >= 0 && x < sidebar && y >= LIST_TOP && y < listBottom()) {
            firstRow = Math.max(0, Math.min(Math.max(0, filtered.size() - visibleRows()),
                    firstRow - (int) Math.signum(amount) * Math.max(1, (int) Math.abs(amount))));
            return true;
        }
        return false;
    }

    //? if forge {
    @Override
    public boolean mouseScrolled(double x, double y, double amount) {
        return scrolled(x, y, amount) || super.mouseScrolled(x, y, amount);
    }
    //?}
    //? if neoforge {
    /*
    // 1.21 split the scroll delta into horizontal and vertical axes; only the vertical one is used here.
    @Override
    public boolean mouseScrolled(double x, double y, double deltaX, double deltaY) {
        return scrolled(x, y, deltaY) || super.mouseScrolled(x, y, deltaX, deltaY);
    }
    *///?}

    @Override
    public boolean keyPressed(int key, int scanCode, int modifiers) {
        if (search.isFocused()) return super.keyPressed(key, scanCode, modifiers);
        if (!nodesTab && key == GLFW.GLFW_KEY_F) { fitCamera(false); return true; }
        if (!nodesTab && key == GLFW.GLFW_KEY_R) { fitCamera(true); return true; }
        return super.keyPressed(key, scanCode, modifiers);
    }

    @Override public void removed() { releaseRenderer(); }
    @Override public void onClose() { cn.minerealms.assimploader.client.ui.LookDevNavigation.close(null); }
    @Override public boolean isPauseScreen() { return false; }

    public boolean selectModel(String name) {
        AssimpScene resolved = AssimpModelLoader.getCachedModel(name);
        for (LoadedModelCatalog.Entry entry : models) {
            if (entry.scene() == resolved || entry.name().equals(name)) {
                select(entry);
                return true;
            }
        }
        return false;
    }

    public boolean isPreviewRendered() { return previewRendered; }
    public boolean isPreviewFailed() { return renderFailed; }
}
