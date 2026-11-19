package cn.minerealms.assimploader.client.ui;

import cn.minerealms.assimploader.AssimpLoader;
import cn.minerealms.assimploader.lookdev.ui.LoadedModelsScreen;
import cn.minerealms.assimploader.workbench.client.ModelWorkbenchScreen;
import cn.minerealms.assimploader.workbench.client.WorkbenchExtensions;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.AbstractWidget;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.Tooltip;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.client.event.ClientPlayerNetworkEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import java.util.ArrayList;
import java.util.EnumMap;
import java.util.HashMap;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.function.BooleanSupplier;
import java.util.function.Consumer;
import java.util.function.Supplier;

/** Shared two-level workspace shell. Consumer mods own their page declarations and screens. */
@Mod.EventBusSubscriber(modid = AssimpLoader.MODID, value = Dist.CLIENT)
public final class LookDevNavigation {
    public static final int HEADER_HEIGHT = 50;
    public static final String MODULE = "assimploader";
    public static final WorkbenchPageRegistry.Key MODELS = new WorkbenchPageRegistry.Key(MODULE, "models");
    public static final WorkbenchPageRegistry.Key EDIT = new WorkbenchPageRegistry.Key(MODULE, "edit");
    public static final WorkbenchPageRegistry.Key DEBUG = new WorkbenchPageRegistry.Key(MODULE, "debug");
    /** Compatibility aliases for older integrations. New consumers register their own module/page keys. */
    public enum Page { MODELS, WORKBENCH, NODES, PLAYER, DEBUG }
    private static final WorkbenchPageRegistry<Screen> REGISTRY = new WorkbenchPageRegistry<>();
    private static final Map<Page, WorkbenchPageRegistry.Key> ALIASES = new EnumMap<>(Page.class);
    private static final Map<Screen, List<Runnable>> HEADERS = new IdentityHashMap<>();
    private static Workspace workspace;

    static {
        registerModule(new WorkbenchPageRegistry.Module(MODULE, "AssimpLoader", 0));
        registerPage(new WorkbenchPageRegistry.Page<>(MODELS, "Models", 0, LoadedModelsScreen::new));
        registerPage(new WorkbenchPageRegistry.Page<>(EDIT, "Edit", 10, ModelWorkbenchScreen::new));
        registerPage(new WorkbenchPageRegistry.Page<>(DEBUG, "Debug", 20, () -> new DebugSettingsScreen(null)));
        ALIASES.put(Page.MODELS, MODELS); ALIASES.put(Page.WORKBENCH, EDIT);
        ALIASES.put(Page.NODES, EDIT); ALIASES.put(Page.DEBUG, DEBUG);
    }

    private static final class Workspace {
        final Map<WorkbenchPageRegistry.Key, Screen> screens = new HashMap<>();
        final Map<String, WorkbenchPageRegistry.Key> selected = new HashMap<>();
    }
    private record Tab(String title, boolean selected, BooleanSupplier available, Supplier<String> tip, Runnable open) {}
    private LookDevNavigation() {}

    /** Register during client setup on the client thread, after the owning module. */
    public static void registerModule(WorkbenchPageRegistry.Module module) { REGISTRY.register(module); }
    public static void registerPage(WorkbenchPageRegistry.Page<Screen> page) { REGISTRY.register(page); }
    public static List<WorkbenchPageRegistry.Module> modules() { return REGISTRY.modules(); }
    public static List<WorkbenchPageRegistry.Page<Screen>> pages(String module) { return REGISTRY.pages(module); }
    public static void registerAlias(Page alias, WorkbenchPageRegistry.Key key) {
        if (alias != Page.PLAYER) throw new IllegalArgumentException("Cannot replace a built-in workspace alias: " + alias);
        REGISTRY.page(key); ALIASES.put(alias, key);
    }

    @Deprecated public static void registerPage(Page page, Supplier<Screen> factory) {
        if (page != Page.PLAYER) throw new IllegalArgumentException("Built-in page: " + page);
        registerModule(new WorkbenchPageRegistry.Module("legacy", "Extensions", 1000));
        var key = new WorkbenchPageRegistry.Key("legacy", "player");
        registerPage(new WorkbenchPageRegistry.Page<>(key, "Player", 0, factory));
        registerAlias(page, key);
    }

    public static void open(Page page) {
        var key = ALIASES.get(page);
        if (key != null) open(key);
        if (page == Page.NODES && Minecraft.getInstance().screen instanceof ModelWorkbenchScreen editor) editor.showNodes();
    }

    public static void open(WorkbenchPageRegistry.Key key) {
        var definition = REGISTRY.page(key);
        if (!definition.available().getAsBoolean()) return;
        if (workspace == null) workspace = new Workspace();
        Minecraft minecraft = Minecraft.getInstance();
        Screen target = workspace.screens.get(key);
        if (target == minecraft.screen && target != null) return;
        if (target == null || !definition.retain()) {
            HEADERS.remove(target);
            target = Objects.requireNonNull(definition.factory().get(), "Workspace page factory returned null: " + key);
        }
        workspace.screens.put(key, target);
        workspace.selected.put(key.module(), key);
        minecraft.setScreen(target);
    }

    /** The selected asset/project is shared through a typed context, with no consumer classes in the engine. */
    public static WorkbenchExtensions.Context modelContext() {
        if (workspace == null || !(workspace.screens.get(EDIT) instanceof ModelWorkbenchScreen editor)
                || editor.scene() == null || editor.session() == null || editor.busy()) return null;
        return new WorkbenchExtensions.Context(editor.scene(), editor.session(), editor);
    }

    public static void close(Screen fallback) {
        Minecraft minecraft = Minecraft.getInstance();
        if (workspace != null && workspace.screens.get(EDIT) instanceof ModelWorkbenchScreen editor
                && editor != minecraft.screen && editor.session() != null && editor.session().dirty()) {
            minecraft.setScreen(editor); editor.onClose(); return;
        }
        workspace = null; HEADERS.clear();
        minecraft.setScreen(fallback);
    }

    public static void install(Screen host, Consumer<AbstractWidget> add, Page selected) {
        var key = ALIASES.get(selected);
        if (key != null) install(host, add, key);
    }

    public static void install(Screen host, Consumer<AbstractWidget> add, WorkbenchPageRegistry.Key selected) {
        REGISTRY.page(selected);
        if (workspace == null) workspace = new Workspace();
        Screen previous = workspace.screens.put(selected, host);
        if (previous != host) HEADERS.remove(previous);
        workspace.selected.put(selected.module(), selected);
        List<Runnable> refresh = new ArrayList<>(); HEADERS.put(host, refresh);
        var modules = modules().stream().map(module -> new Tab(module.title(), module.id().equals(selected.module()),
                () -> true, () -> module.title(), () -> openModule(module.id()))).toList();
        var pages = pages(selected.module()).stream().map(page -> new Tab(page.title(), page.key().equals(selected), page.available(),
                () -> page.available().getAsBoolean() ? page.title() : page.unavailableReason().get(), () -> open(page.key()))).toList();
        row(add, modules, 2, 22, host.width - 44, 104, refresh);
        row(add, pages, 27, 20, host.width - 8, 88, refresh);
        add.accept(FlatUi.icon(host.width - 34, 2, FlatUi.Icon.CLOSE, Component.literal("Close workbench"), button -> host.onClose()));
    }

    private static void openModule(String module) {
        var selected = workspace == null ? null : workspace.selected.get(module);
        if (selected != null && REGISTRY.page(selected).available().getAsBoolean()) { open(selected); return; }
        pages(module).stream().filter(page -> page.available().getAsBoolean()).findFirst().ifPresent(page -> open(page.key()));
    }

    /** Overflow scrolls each tab row independently, keeping labels and hit targets usable at small GUI scales. */
    private static void row(Consumer<AbstractWidget> add, List<Tab> tabs, int y, int height,
                            int end, int preferredWidth, List<Runnable> refresh) {
        if (tabs.isEmpty()) return;
        int available = Math.max(1, end - 8), selected = 0;
        for (int i = 0; i < tabs.size(); i++) if (tabs.get(i).selected()) selected = i;
        boolean overflow = (long) tabs.size() * preferredWidth > available;
        int count = overflow ? Math.max(1, (available - 60) / preferredWidth) : tabs.size();
        count = Math.min(count, tabs.size());
        int[] first = {Math.min(Math.max(0, selected - count + 1), tabs.size() - count)};
        int start = overflow ? 38 : 8;
        int cell = Math.max(1, Math.min(preferredWidth + 24, (end - start - (overflow ? 30 : 0)) / count));
        List<Button> buttons = new ArrayList<>();
        List<String> tooltipText = new ArrayList<>();
        for (int i = 0; i < count; i++) {
            int slot = i;
            var button = FlatUi.choice(start + i * cell, y, Math.max(1, cell - 2), height, Component.empty(),
                    () -> tabs.get(first[0] + slot).selected(), b -> tabs.get(first[0] + slot).open().run());
            buttons.add(button); tooltipText.add(null); add.accept(button);
        }
        Runnable update = () -> {
            for (int i = 0; i < buttons.size(); i++) {
                var tab = tabs.get(first[0] + i); var button = buttons.get(i);
                if (!button.getMessage().getString().equals(tab.title())) button.setMessage(Component.literal(tab.title()));
                button.active = tab.available().getAsBoolean();
                String tip = tab.tip().get();
                if (!Objects.equals(tooltipText.get(i), tip)) {
                    button.setTooltip(Tooltip.create(Component.literal(tip))); tooltipText.set(i, tip);
                }
            }
        };
        if (overflow) {
            int visible = count;
            var previous = FlatUi.icon(8, y, FlatUi.Icon.PREVIOUS, Component.literal("Previous tabs"), b -> { first[0] = Math.max(0, first[0] - visible); update.run(); });
            var next = FlatUi.icon(end - 26, y, FlatUi.Icon.NEXT, Component.literal("More tabs"), b -> { first[0] = Math.min(tabs.size() - visible, first[0] + visible); update.run(); });
            previous.setHeight(height); next.setHeight(height); add.accept(previous); add.accept(next);
            refresh.add(() -> { previous.active = first[0] > 0; next.active = first[0] + visible < tabs.size(); });
        }
        refresh.add(update); update.run();
    }

    public static void render(GuiGraphics graphics, int width) {
        var refresh = HEADERS.get(Minecraft.getInstance().screen);
        if (refresh != null) refresh.forEach(Runnable::run);
        graphics.fill(0, 0, width, HEADER_HEIGHT, FlatUi.SURFACE);
        graphics.fill(0, 24, width, 25, FlatUi.LINE);
        graphics.fill(0, HEADER_HEIGHT - 1, width, HEADER_HEIGHT, FlatUi.LINE);
    }

    @SubscribeEvent public static void logout(ClientPlayerNetworkEvent.LoggingOut event) {
        workspace = null; HEADERS.clear();
    }
}
