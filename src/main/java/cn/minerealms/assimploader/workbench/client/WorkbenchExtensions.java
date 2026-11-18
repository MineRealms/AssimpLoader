package cn.minerealms.assimploader.workbench.client;

import cn.minerealms.assimploader.model.AssimpScene;
import cn.minerealms.assimploader.workbench.model.WorkbenchSession;
import net.minecraft.client.gui.screens.Screen;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/** Consumer modules contribute typed panels and application without becoming engine dependencies. */
public final class WorkbenchExtensions {
    public record Context(AssimpScene scene, WorkbenchSession session, Screen parent) {}
    public interface Extension {
        String id();
        String title();
        boolean supports(Context context);
        Screen open(Context context);
        default boolean handlesApplication() { return false; }
        default void apply(Context context) {}
    }
    private static final Map<String, Extension> EXTENSIONS = new LinkedHashMap<>();
    private WorkbenchExtensions() {}
    public static void register(Extension extension) { EXTENSIONS.put(extension.id(), extension); }
    public static List<Extension> available(Context context) { return EXTENSIONS.values().stream().filter(e -> e.supports(context)).toList(); }
}
