package cn.minerealms.assimploader.client.ui;

import org.lwjgl.PointerBuffer;
import org.lwjgl.system.MemoryStack;
import org.lwjgl.util.tinyfd.TinyFileDialogs;

import java.io.File;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Optional;
import java.util.Set;

import static org.lwjgl.system.MemoryUtil.memAddress;

/** Blocking desktop file picker. Call on an IO worker, never in the render loop. */
public final class NativeFileDialogs {
    private static final Object LOCK=new Object();
    private static final Set<String> FILE_PICKERS=Set.of("windows","windows_wchar","applescript",
            "kdialog","zenity","zenity3","matedialog","shellementary","qarma","yad",
            "python2-tkinter","python3-tkinter");

    private NativeFileDialogs() {}

    public static Optional<Path> openFile(String title,Path initialPath,List<String> patterns,String description) {
        synchronized(LOCK) {
            try {
                int console=TinyFileDialogs.tinyfd_getGlobalInt(TinyFileDialogs.tinyfd_forceConsole);
                int curses=TinyFileDialogs.tinyfd_getGlobalInt(TinyFileDialogs.tinyfd_allowCursesDialogs);
                int utf8=TinyFileDialogs.tinyfd_getGlobalInt(TinyFileDialogs.tinyfd_winUtf8);
                try(MemoryStack stack=MemoryStack.stackPush()) {
                    TinyFileDialogs.tinyfd_setGlobalInt(TinyFileDialogs.tinyfd_forceConsole,0);
                    TinyFileDialogs.tinyfd_setGlobalInt(TinyFileDialogs.tinyfd_allowCursesDialogs,0);
                    TinyFileDialogs.tinyfd_setGlobalInt(TinyFileDialogs.tinyfd_winUtf8,1);
                    // Query returns the pointer sentinel 1, not a string; do not decode it with the String overload.
                    long graphical=TinyFileDialogs.ntinyfd_openFileDialog(memAddress(stack.UTF8("tinyfd_query")),0,0,0,0,0);
                    String backend=TinyFileDialogs.tinyfd_getGlobalChar(TinyFileDialogs.tinyfd_response);
                    if(graphical==0||!FILE_PICKERS.contains(backend==null?"":backend))
                        throw new IllegalStateException("System file picker unavailable; on Linux install Zenity or KDialog");
                    PointerBuffer filters=stack.mallocPointer(patterns.size());
                    for(String pattern:patterns)filters.put(memAddress(stack.UTF8(pattern)));
                    filters.flip();
                    String selected=TinyFileDialogs.tinyfd_openFileDialog(title,initialSelection(initialPath),filters,description,false);
                    return selected==null||selected.isBlank()?Optional.empty():Optional.of(Path.of(selected).toAbsolutePath().normalize());
                } finally {
                    TinyFileDialogs.tinyfd_setGlobalInt(TinyFileDialogs.tinyfd_forceConsole,console);
                    TinyFileDialogs.tinyfd_setGlobalInt(TinyFileDialogs.tinyfd_allowCursesDialogs,curses);
                    TinyFileDialogs.tinyfd_setGlobalInt(TinyFileDialogs.tinyfd_winUtf8,utf8);
                }
            } catch(LinkageError unavailable) {
                throw new IllegalStateException("System file picker could not load its native library",unavailable);
            }
        }
    }

    private static String initialSelection(Path input) {
        if(input==null)return "";
        Path path=input.toAbsolutePath().normalize();
        if(Files.isRegularFile(path))return path.toString();
        while(path!=null&&!Files.isDirectory(path))path=path.getParent();
        return path==null?"":path+File.separator;
    }
}
