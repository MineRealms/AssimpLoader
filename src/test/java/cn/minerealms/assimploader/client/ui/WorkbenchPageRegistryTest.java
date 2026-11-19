package cn.minerealms.assimploader.client.ui;

import org.junit.jupiter.api.Test;
import java.util.concurrent.atomic.AtomicInteger;
import static org.junit.jupiter.api.Assertions.*;

class WorkbenchPageRegistryTest {
    @Test void consumersRegisterIndependentlyAndFactoriesStayLazy() {
        var registry = new WorkbenchPageRegistry<String>();
        var calls = new AtomicInteger();
        registry.register(new WorkbenchPageRegistry.Module("consumer", "Consumer", 20));
        registry.register(new WorkbenchPageRegistry.Module("assimploader", "AssimpLoader", 0));
        var key = new WorkbenchPageRegistry.Key("consumer", "tools/equipment");
        registry.register(new WorkbenchPageRegistry.Page<>(key, "Equipment", 0, () -> { calls.incrementAndGet(); return "screen"; },
                () -> { calls.incrementAndGet(); return false; }, () -> "Load a model", false));
        registry.register(new WorkbenchPageRegistry.Page<>(new WorkbenchPageRegistry.Key("assimploader", "edit"), "Edit", 0, () -> "edit"));
        assertEquals(0, calls.get());
        assertEquals(java.util.List.of("assimploader", "consumer"), registry.modules().stream().map(WorkbenchPageRegistry.Module::id).toList());
        assertFalse(registry.page(key).available().getAsBoolean());
        assertEquals("Load a model", registry.page(key).unavailableReason().get());
        assertFalse(registry.page(key).retain());
        assertEquals("screen", registry.page(key).factory().get());
        assertEquals(2, calls.get());
    }

    @Test void duplicateAndUnownedPagesCannotSilentlyReplaceAnotherMod() {
        var registry = new WorkbenchPageRegistry<String>();
        var module = new WorkbenchPageRegistry.Module("example", "Example", 0);
        var page = new WorkbenchPageRegistry.Page<>(new WorkbenchPageRegistry.Key("example", "edit"), "Edit", 0, () -> "first");
        assertThrows(IllegalArgumentException.class, () -> registry.register(page));
        registry.register(module); registry.register(page);
        assertThrows(IllegalArgumentException.class, () -> registry.register(module));
        assertThrows(IllegalArgumentException.class, () -> registry.register(page));
        assertEquals("first", registry.page(page.key()).factory().get());
        assertThrows(UnsupportedOperationException.class, () -> registry.pages("example").clear());
    }

    @Test void pageOrderingIsDeterministicAndIdentifiersAreValidated() {
        var registry = new WorkbenchPageRegistry<String>();
        registry.register(new WorkbenchPageRegistry.Module("example", "Example", 0));
        for (String id : java.util.List.of("z", "a")) registry.register(new WorkbenchPageRegistry.Page<>(new WorkbenchPageRegistry.Key("example", id), id, 0, () -> id));
        assertEquals(java.util.List.of("a", "z"), registry.pages("example").stream().map(page -> page.key().page()).toList());
        assertThrows(IllegalArgumentException.class, () -> new WorkbenchPageRegistry.Key("Bad module", "edit"));
        assertThrows(IllegalArgumentException.class, () -> new WorkbenchPageRegistry.Key("example", "bad page"));
        assertThrows(IllegalArgumentException.class, () -> registry.page(new WorkbenchPageRegistry.Key("example", "missing")));
    }
}
