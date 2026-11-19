package cn.minerealms.assimploader.client.ui;

import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.function.BooleanSupplier;
import java.util.function.Supplier;

/** Ordered module/page declarations. Registration never creates a screen or queries world state. */
public final class WorkbenchPageRegistry<S> {
    public record Key(String module, String page) {
        public Key {
            if (module == null || !module.matches("[a-z0-9_.-]+"))
                throw new IllegalArgumentException("Invalid workspace module id: " + module);
            if (page == null || !page.matches("[a-z0-9_./-]+"))
                throw new IllegalArgumentException("Invalid workspace page id: " + page);
        }
        @Override public String toString() { return module + ":" + page; }
    }

    public record Module(String id, String title, int order) {
        public Module { new Key(id, "validation"); requireTitle(title); }
    }

    public record Page<S>(Key key, String title, int order, Supplier<? extends S> factory,
                          BooleanSupplier available, Supplier<String> unavailableReason, boolean retain) {
        public Page {
            Objects.requireNonNull(key); requireTitle(title); Objects.requireNonNull(factory);
            Objects.requireNonNull(available); Objects.requireNonNull(unavailableReason);
        }
        public Page(Key key, String title, int order, Supplier<? extends S> factory) {
            this(key, title, order, factory, () -> true, () -> "", true);
        }
    }

    private final Map<String, Module> modules = new LinkedHashMap<>();
    private final Map<Key, Page<S>> pages = new LinkedHashMap<>();

    public void register(Module module) {
        Objects.requireNonNull(module);
        if (modules.putIfAbsent(module.id(), module) != null)
            throw new IllegalArgumentException("Workspace module already registered: " + module.id());
    }

    public void register(Page<S> page) {
        Objects.requireNonNull(page);
        if (!modules.containsKey(page.key().module()))
            throw new IllegalArgumentException("Register the workspace module first: " + page.key().module());
        if (pages.putIfAbsent(page.key(), page) != null)
            throw new IllegalArgumentException("Workspace page already registered: " + page.key());
    }

    public List<Module> modules() {
        return modules.values().stream().filter(module -> pages.keySet().stream().anyMatch(key -> key.module().equals(module.id())))
                .sorted(Comparator.comparingInt(Module::order).thenComparing(Module::id)).toList();
    }

    public List<Page<S>> pages(String module) {
        return pages.values().stream().filter(page -> page.key().module().equals(module))
                .sorted(Comparator.<Page<S>>comparingInt(Page::order).thenComparing(page -> page.key().page())).toList();
    }

    public Page<S> page(Key key) {
        Page<S> page = pages.get(Objects.requireNonNull(key));
        if (page == null) throw new IllegalArgumentException("Unknown workspace page: " + key);
        return page;
    }

    private static void requireTitle(String title) {
        if (title == null || title.isBlank()) throw new IllegalArgumentException("Workspace title must not be blank");
    }
}
