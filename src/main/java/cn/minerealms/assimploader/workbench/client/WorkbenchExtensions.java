package cn.minerealms.assimploader.workbench.client;

import cn.minerealms.assimploader.model.AssimpScene;
import cn.minerealms.assimploader.workbench.model.WorkbenchDocument;
import cn.minerealms.assimploader.workbench.model.WorkbenchSession;
import net.minecraft.client.gui.screens.Screen;
import java.nio.file.Path;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;

/** Consumer modules contribute typed panels and application without becoming engine dependencies. */
public final class WorkbenchExtensions {
    public record Context(AssimpScene scene, WorkbenchSession session, Screen parent) {
        /** Call on the client thread after committing extension edits to this session. */
        public CompletableFuture<WorkbenchDocument> saveProject() {
            return parent instanceof ProjectSaver saver?saver.saveProject(session):
                    CompletableFuture.failedFuture(new IllegalStateException("This editor has no owning workbench project"));
        }
        public Path projectPath() {return parent instanceof ProjectSaver saver?saver.projectPath():null;}
    }
    public interface ProjectSaver {
        /** Completes on the client thread after persistence, with the exact saved snapshot; never applies extensions. */
        CompletableFuture<WorkbenchDocument> saveProject(WorkbenchSession expectedSession);
        default Path projectPath() {return null;}
    }
    public interface Extension {
        String id();
        String title();
        boolean supports(Context context);
        Screen open(Context context);
        /** Project open/import only, after formal/recovery selection; may stage newer consumer data as dirty edits. */
        default void opened(Context context) {}
        default boolean handlesApplication() { return false; }
        default void apply(Context context) {}
    }
    private static final Map<String, Extension> EXTENSIONS = new LinkedHashMap<>();
    private WorkbenchExtensions() {}
    public static void register(Extension extension) { EXTENSIONS.put(extension.id(), extension); }
    public static List<Extension> available(Context context) { return EXTENSIONS.values().stream().filter(e -> e.supports(context)).toList(); }
}
