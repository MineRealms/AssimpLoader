package cn.minerealms.assimploader.workbench.client;

import cn.minerealms.assimploader.model.AssimpScene;
import cn.minerealms.assimploader.workbench.model.WorkbenchDocument;
import cn.minerealms.assimploader.workbench.model.WorkbenchSession;
import net.minecraft.client.gui.screens.Screen;
import java.nio.file.Path;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.CompletableFuture;
import java.util.function.Consumer;
import java.util.function.BooleanSupplier;
import java.util.function.Supplier;
import cn.minerealms.assimploader.workbench.model.ModelTransform;

/** Consumer modules contribute typed panels and application without becoming engine dependencies. */
public final class WorkbenchExtensions {
    public record Context(AssimpScene scene, WorkbenchSession session, Screen parent) {
        /** Call on the client thread after committing extension edits to this session. */
        public CompletableFuture<WorkbenchDocument> saveProject() {
            return parent instanceof ProjectSaver saver?saver.saveProject(session):
                    CompletableFuture.failedFuture(new IllegalStateException("This editor has no owning workbench project"));
        }
        public Path projectPath() {return parent instanceof ProjectSaver saver?saver.projectPath():null;}
        public void refresh() {if(parent instanceof ModelWorkbenchScreen editor)editor.refreshExtension();}
        public void previewClip(String clip) {if(parent instanceof ModelWorkbenchScreen editor)editor.previewClip(clip);}
        public CompletableFuture<Void> runTask(String label,Supplier<CompletableFuture<Void>> work) {
            return parent instanceof ModelWorkbenchScreen editor?editor.runExtensionTask(session,label,work):
                    CompletableFuture.failedFuture(new IllegalStateException("This editor has no owning workbench project"));
        }
        public CompletableFuture<Integer> choose(String title,String message,List<String> options) {
            return parent instanceof ModelWorkbenchScreen editor?editor.chooseExtension(session,title,message,options):
                    CompletableFuture.failedFuture(new IllegalStateException("This editor has no owning workbench project"));
        }
    }
    public interface ProjectSaver {
        /** Completes on the client thread after persistence and the consumer's saved hook. */
        CompletableFuture<WorkbenchDocument> saveProject(WorkbenchSession expectedSession);
        default Path projectPath() {return null;}
    }
    public interface Extension {
        String id();
        String title();
        boolean supports(Context context);
        Screen open(Context context);
        /** Import is explicit. Merely having bones never activates a consumer. */
        default boolean canImport() {return false;}
        default CompletableFuture<Void> initialize(Context context) {return CompletableFuture.completedFuture(null);}
        /** Explicit file import, including a portable project with existing extension metadata. */
        default CompletableFuture<Void> imported(Context context) {return initialize(context);}
        default List<Panel> panels(Context context) {return List.of();}
        default boolean ownsRootTransform() {return false;}
        default ModelTransform rootTransform(Context context) {return context.session().document().transform();}
        default void rootTransform(Context context,ModelTransform transform) {context.session().edit(d->d.withTransform(transform));}
        /** Captures external authoring files asynchronously; completion must return to the client thread. */
        default CompletableFuture<Void> prepareAsync(Context context) {return CompletableFuture.completedFuture(null);}
        /** Stages derived consumer data before the shared Save/Apply/Export snapshot. */
        default void prepare(Context context) {}
        default void saved(Context context) {}
        /** Optional data groups shown in the shared Export dialog. IDs must be stable and extension-prefixed. */
        default List<ExportOption> exportOptions(Context context) {
            return packageFiles(context).isEmpty()?List.of():List.of(new ExportOption(id()+":files",title()+" files",true));
        }
        /** Allows consumers to filter their project metadata using the selected export groups. */
        default WorkbenchDocument exportDocument(Context context,Set<String> selectedOptions) {
            return context.session().document();
        }
        /** Extra ZIP files, filtered by the export dialog. */
        default Map<String,byte[]> packageFiles(Context context,Set<String> selectedOptions) {
            return selectedOptions.contains(id()+":files")?packageFiles(context):Map.of();
        }
        /**
         * Extra ZIP files, using portable paths under extensions/. To publish a complete directory
         * snapshot, include its trailing-slash path with an empty byte array (for example
         * extensions/rigworks/behavior/). Its old disk contents are excluded, including deleted
         * files. Keep this marker for an empty snapshot; an empty map alone deletes nothing.
         * Markers are not ZIP entries. Only claim directories owned by this extension.
         */
        default Map<String,byte[]> packageFiles(Context context) {return Map.of();}
        /** Optional registered page used by the shared navigation shell. */
        default cn.minerealms.assimploader.client.ui.WorkbenchPageRegistry.Key navigationPage() { return null; }
        /** Project open/import only, after formal/recovery selection; may stage newer consumer data as dirty edits. */
        default void opened(Context context) {}
        default boolean handlesApplication() { return false; }
        default void apply(Context context) {}
    }
    public interface Panel {
        String id();
        String title();
        void build(Inspector inspector);
    }
    public record ExportOption(String id,String title,boolean selectedByDefault) {
        public ExportOption {
            if(id==null||!id.matches("[a-z0-9_.-]+:[a-z0-9_.-]{1,64}")||title==null||title.isBlank())
                throw new IllegalArgumentException("Invalid workbench export option");
        }
    }
    /** Controls are laid out, clipped and owned by the engine's single inspector. */
    public interface Inspector {
        Context context();
        void label(String text);
        void text(String title,String value,Consumer<String> setter);
        void number(String title,float value,Consumer<Float> setter);
        void toggle(String title,BooleanSupplier value,Runnable action);
        void action(String title,Runnable action);
        void select(String title,List<String> values,String selected,Consumer<String> setter);
    }
    private static final Map<String, Extension> EXTENSIONS = new LinkedHashMap<>();
    private WorkbenchExtensions() {}
    public static void register(Extension extension) {
        java.util.Objects.requireNonNull(extension);
        if(extension.id()==null||!extension.id().matches("[a-z0-9_.-]{1,64}")||extension.title()==null||extension.title().isBlank())
            throw new IllegalArgumentException("A workbench extension needs a stable ID and title");
        var previous=EXTENSIONS.putIfAbsent(extension.id(),extension);
        if(previous!=null&&previous!=extension)throw new IllegalArgumentException("Duplicate workbench extension: "+extension.id());
    }
    public static List<Extension> all() {return List.copyOf(EXTENSIONS.values());}
    public static Extension get(String id) {var value=EXTENSIONS.get(id);if(value==null)throw new IllegalArgumentException("Unknown workbench extension: "+id);return value;}
    public static List<Extension> available(Context context) { return EXTENSIONS.values().stream().filter(e -> e.supports(context)).toList(); }
    /** A marked archive always receives the same consumer import hook, regardless of its UI entry point. */
    public static List<Extension> importTargets(Context context,String explicitExtension) {
        var selected=new LinkedHashMap<String,Extension>();
        for(var extension:available(context))selected.put(extension.id(),extension);
        if(explicitExtension!=null){
            var extension=get(explicitExtension);
            if(!extension.canImport())throw new IllegalArgumentException("This extension cannot import models: "+explicitExtension);
            selected.put(extension.id(),extension);
        }
        return List.copyOf(selected.values());
    }
}
