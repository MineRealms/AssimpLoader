package cn.minerealms.assimploader.lookdev.ui;

import cn.minerealms.assimploader.AssimpLoader;
import cn.minerealms.assimploader.core.AssimpModelLoader;
import cn.minerealms.assimploader.lookdev.preview.LoadedModelCatalog;
import cn.minerealms.assimploader.lookdev.preview.PreviewCamera;
import cn.minerealms.assimploader.lookdev.preview.PreviewGpuRenderer;
import cn.minerealms.assimploader.lookdev.preview.PreviewSceneData;
import cn.minerealms.assimploader.model.AssimpScene;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.components.Tooltip;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import org.joml.Vector3f;
import org.lwjgl.glfw.GLFW;

import java.util.List;
import java.util.Locale;

/** A render-thread Minecraft screen. Legacy ModernUI editor fragments are not instantiated. */
public final class LoadedModelsScreen extends Screen {
    private static final int ROW_HEIGHT = 28;
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

    public LoadedModelsScreen() {
        super(Component.literal("LookDev"));
    }

    @Override
    protected void init() {
        sidebar = Math.max(112, Math.min(210, width / 3));
        addRenderableWidget(Button.builder(Component.translatable("screen.assimploader.preview"), button -> setTab(false))
                .bounds(84, 5, 62, 20).build());
        addRenderableWidget(Button.builder(Component.translatable("screen.assimploader.nodes"), button -> setTab(true))
                .bounds(149, 5, 52, 20).build());
        addRenderableWidget(Button.builder(Component.literal("x"), button -> onClose())
                .bounds(width - 26, 5, 20, 20).tooltip(Tooltip.create(Component.translatable("gui.done"))).build());
        String query = search == null ? "" : search.getValue();
        search = new EditBox(font, 8, 37, sidebar - 16, 18, Component.translatable("screen.assimploader.filter"));
        search.setMaxLength(256);
        search.setHint(Component.translatable("screen.assimploader.filter"));
        search.setValue(query);
        search.setResponder(value -> {
            filtered = LoadedModelCatalog.filter(models, value);
            firstRow = 0;
        });
        addRenderableWidget(search);
        int toolX = sidebar + 8;
        fit = addRenderableWidget(Button.builder(Component.translatable("screen.assimploader.fit"), button -> fitCamera(false))
                .bounds(toolX, 35, 36, 20).build());
        reset = addRenderableWidget(Button.builder(Component.translatable("screen.assimploader.reset"), button -> fitCamera(true))
                .bounds(toolX + 40, 35, 44, 20).build());
        zoomOut = addRenderableWidget(Button.builder(Component.literal("-"), button -> camera.zoom(-1))
                .bounds(toolX + 88, 35, 20, 20).tooltip(Tooltip.create(Component.translatable("screen.assimploader.zoom_out"))).build());
        zoomIn = addRenderableWidget(Button.builder(Component.literal("+"), button -> camera.zoom(1))
                .bounds(toolX + 112, 35, 20, 20).tooltip(Tooltip.create(Component.translatable("screen.assimploader.zoom_in"))).build());
        refreshCatalog();
        fitCamera(false);
        updateControls();
    }

    private void setTab(boolean nodes) {
        nodesTab = nodes;
        dragButton = -1;
        updateControls();
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
        if (search != null) search.tick();
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
        if (sceneData != null) {
            Vector3f size = sceneData.size();
            dimensions = String.format(Locale.ROOT, "%.3g x %.3g x %.3g", size.x, size.y, size.z);
        } else dimensions = "";
        fitCamera(true);
        if (fit != null) updateControls();
    }

    private void releaseRenderer() {
        previewRendered = false;
        if (renderer != null) {
            renderer.close();
            renderer = null;
        }
    }

    private int previewX() { return sidebar + 1; }
    private int previewY() { return 62; }
    private int previewWidth() { return Math.max(1, width - previewX()); }
    private int previewHeight() { return Math.max(1, height - previewY() - 24); }
    private int listBottom() { return Math.max(64, height - 78); }
    private int visibleRows() { return Math.max(1, (listBottom() - 64) / ROW_HEIGHT); }

    @Override
    public void render(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
        graphics.fill(0, 0, width, height, 0xff17191c);
        graphics.fill(0, 30, sidebar, height, 0xff222529);
        graphics.fill(sidebar, 30, width, 61, 0xff25292d);
        graphics.fill(sidebar, 30, sidebar + 1, height, 0xff42484c);
        graphics.fill(previewX(), previewY(), width, height - 24, 0xff30363a);
        graphics.drawString(font, title, 8, 11, 0xffe9edef, false);
        renderModelList(graphics, mouseX, mouseY);
        renderSelectionInfo(graphics);
        super.render(graphics, mouseX, mouseY, partialTick);
        graphics.flush();
        if (!nodesTab && selected != null && !renderFailed) renderPreview();
        if (!nodesTab && renderFailed) {
            graphics.drawCenteredString(font, Component.translatable("screen.assimploader.preview_failed"),
                    previewX() + previewWidth() / 2, previewY() + previewHeight() / 2, 0xffe8b8a5);
        }
        renderHoveredName(graphics, mouseX, mouseY);
    }

    private void renderModelList(GuiGraphics graphics, int mouseX, int mouseY) {
        graphics.enableScissor(0, 62, sidebar, listBottom());
        for (int row = 0; row < visibleRows() && firstRow + row < filtered.size(); row++) {
            LoadedModelCatalog.Entry entry = filtered.get(firstRow + row);
            int y = 64 + row * ROW_HEIGHT;
            boolean active = selected != null && entry.scene() == selected.scene();
            boolean hover = mouseX >= 0 && mouseX < sidebar && mouseY >= y && mouseY < y + ROW_HEIGHT;
            if (active || hover) graphics.fill(3, y, sidebar - 3, y + ROW_HEIGHT - 1,
                    active ? 0xff3b5957 : 0xff32383c);
            String name = entry.name();
            int mode = name.lastIndexOf('#');
            if (mode >= 0) name = name.substring(0, mode);
            int slash = name.lastIndexOf('/');
            if (slash >= 0) name = name.substring(slash + 1);
            graphics.drawString(font, clipped(name, sidebar - 20), 9, y + 3, 0xffe9edef, false);
            graphics.drawString(font, entry.scene().getImportMode().name(), 9, y + 15, 0xff9ca9ae, false);
        }
        if (filtered.isEmpty()) graphics.drawString(font, Component.translatable("screen.assimploader.models", 0), 9, 70, 0xff9ca9ae, false);
        if (filtered.size() > visibleRows()) {
            int trackHeight = Math.max(1, listBottom() - 64);
            int thumb = Math.max(8, trackHeight * visibleRows() / filtered.size());
            int position = (trackHeight - thumb) * firstRow / Math.max(1, filtered.size() - visibleRows());
            graphics.fill(sidebar - 3, 64, sidebar - 1, listBottom(), 0xff30373a);
            graphics.fill(sidebar - 3, 64 + position, sidebar - 1, 64 + position + thumb, 0xff80948e);
        }
        graphics.disableScissor();
    }

    private void renderSelectionInfo(GuiGraphics graphics) {
        int y = Math.max(64, height - 69);
        graphics.fill(0, y - 5, sidebar, y - 4, 0xff42484c);
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
        if (x < 0 || x >= sidebar || y < 64 || y >= listBottom()) return;
        int index = firstRow + (y - 64) / ROW_HEIGHT;
        if (index < filtered.size()) graphics.renderTooltip(font, Component.literal(filtered.get(index).name()), x, y);
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
        if (button == 0 && x >= 0 && x < sidebar && y >= 64 && y < listBottom()) {
            int index = firstRow + ((int) y - 64) / ROW_HEIGHT;
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

    @Override
    public boolean mouseScrolled(double x, double y, double amount) {
        if (inPreview(x, y)) { camera.zoom(amount); return true; }
        if (x >= 0 && x < sidebar && y >= 62 && y < listBottom()) {
            firstRow = Math.max(0, Math.min(Math.max(0, filtered.size() - visibleRows()),
                    firstRow - (int) Math.signum(amount) * Math.max(1, (int) Math.abs(amount))));
            return true;
        }
        return super.mouseScrolled(x, y, amount);
    }

    @Override
    public boolean keyPressed(int key, int scanCode, int modifiers) {
        if (search.isFocused()) return super.keyPressed(key, scanCode, modifiers);
        if (key == GLFW.GLFW_KEY_F) { fitCamera(false); return true; }
        if (key == GLFW.GLFW_KEY_R) { fitCamera(true); return true; }
        return super.keyPressed(key, scanCode, modifiers);
    }

    @Override public void removed() { releaseRenderer(); }
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
