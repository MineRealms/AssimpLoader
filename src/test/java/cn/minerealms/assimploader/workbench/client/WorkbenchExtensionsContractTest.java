package cn.minerealms.assimploader.workbench.client;

import cn.minerealms.assimploader.workbench.model.ModelTransform;
import cn.minerealms.assimploader.workbench.io.WorkbenchCodec;
import cn.minerealms.assimploader.workbench.model.WorkbenchDocument;
import cn.minerealms.assimploader.workbench.model.WorkbenchSession;
import com.google.gson.JsonObject;
import net.minecraft.client.gui.screens.Screen;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletionException;
import java.util.function.BooleanSupplier;
import java.util.function.Consumer;

import static org.junit.jupiter.api.Assertions.*;

class WorkbenchExtensionsContractTest {
    @Test void panelAndGenericTransformShareHistoryAndRetainUnknownConsumerData() {
        var initial = document();
        var session = new WorkbenchSession(initial);
        var context = new WorkbenchExtensions.Context(null, session, null);
        var extension = new SpeedExtension();
        var controls = new CapturedInspector(context);
        extension.panels(context).get(0).build(controls);

        controls.number.accept(2.5f);
        var afterPanel = session.document();
        var transform = new ModelTransform(1, 2, 3, 0, 90, 0, 2, 2, 2);
        extension.rootTransform(context, transform);
        assertSame(session, controls.context().session());
        assertEquals(transform, extension.rootTransform(context));
        assertEquals(2.5f, session.document().extensions().getAsJsonObject("vehicle").get("speed").getAsFloat());
        assertEquals(initial.extensions().get("unknown-consumer"), session.document().extensions().get("unknown-consumer"));
        assertEquals(initial, document(), "Editing a defensive JSON copy must not mutate the initial snapshot");

        session.undo();
        assertEquals(afterPanel, session.document());
        session.undo();
        assertEquals(initial, session.document());
        session.redo(); session.redo();
        var restored = WorkbenchCodec.fromJson(WorkbenchCodec.toJson(session.document()));
        assertEquals(session.document(), restored);
        assertEquals(transform, restored.transform());
        assertEquals(initial.extensions().get("unknown-consumer"), restored.extensions().get("unknown-consumer"));
    }

    @Test void unavailableProjectOwnerRejectsSaveWithoutChangingDraftOrSavedBaseline() {
        var session = new WorkbenchSession(document());
        var baseline = session.savedDocument();
        session.edit(doc -> doc.withTransform(new ModelTransform(0, 1, 0, 0, 0, 0, 1, 1, 1)));
        var draft = session.document();
        var context = new WorkbenchExtensions.Context(null, session, null);
        var failure = assertThrows(CompletionException.class, () -> context.saveProject().join());
        assertInstanceOf(IllegalStateException.class, failure.getCause());
        assertNull(context.projectPath());
        assertSame(draft, session.document());
        assertSame(baseline, session.savedDocument());
        assertTrue(session.dirty());
    }

    private static WorkbenchDocument document() {
        var extensions = new JsonObject();
        var vehicle = new JsonObject(); vehicle.addProperty("speed", 1);
        var future = new JsonObject(); future.addProperty("version", 27); future.add("opaque", new JsonObject());
        extensions.add("vehicle", vehicle); extensions.add("unknown-consumer", future);
        return new WorkbenchDocument(1, "test:model", "assets/model.fbx", "", ModelTransform.IDENTITY,
                Map.of(), Map.of(), Map.of(), WorkbenchDocument.AnimationPreview.DEFAULT, extensions);
    }

    private static final class SpeedExtension implements WorkbenchExtensions.Extension {
        @Override public String id() { return "vehicle"; }
        @Override public String title() { return "Vehicle"; }
        @Override public boolean supports(WorkbenchExtensions.Context context) { return context.session().document().extensions().has(id()); }
        @Override public Screen open(WorkbenchExtensions.Context context) { return context.parent(); }
        @Override public List<WorkbenchExtensions.Panel> panels(WorkbenchExtensions.Context context) {
            return List.of(new WorkbenchExtensions.Panel() {
                @Override public String id() { return "drive"; }
                @Override public String title() { return "Drive"; }
                @Override public void build(WorkbenchExtensions.Inspector inspector) {
                    var data = inspector.context().session().document().extensions().getAsJsonObject("vehicle");
                    inspector.number("Speed", data.get("speed").getAsFloat(), value -> inspector.context().session().edit(document -> {
                        var next = document.extensions().getAsJsonObject("vehicle");
                        next.addProperty("speed", value);
                        return document.withExtension("vehicle", next);
                    }));
                }
            });
        }
    }

    private static final class CapturedInspector implements WorkbenchExtensions.Inspector {
        private final WorkbenchExtensions.Context context;
        private Consumer<Float> number;
        private CapturedInspector(WorkbenchExtensions.Context context) { this.context = context; }
        @Override public WorkbenchExtensions.Context context() { return context; }
        @Override public void number(String title, float value, Consumer<Float> setter) { number = setter; }
        @Override public void label(String text) {}
        @Override public void text(String title, String value, Consumer<String> setter) {}
        @Override public void toggle(String title, BooleanSupplier value, Runnable action) {}
        @Override public void action(String title, Runnable action) {}
        @Override public void select(String title, List<String> values, String selected, Consumer<String> setter) {}
    }
}
